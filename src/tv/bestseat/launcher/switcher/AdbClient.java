package tv.bestseat.launcher.switcher;

import android.content.Context;
import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

/**
 * Minimaler adb-Client, der sich mit dem adb-Dienst des TVs selbst verbindet
 * (Netzwerk-Debugging auf Port 5555) und einen Shell-Befehl ausführt.
 * Beim ersten Mal muss am TV „Debugging zulassen“ bestätigt werden.
 */
public final class AdbClient {

    private static final String HOST = "127.0.0.1";
    private static final int PORT = 5555;

    private static final int A_CNXN = 0x4e584e43;
    private static final int A_AUTH = 0x48545541;
    private static final int A_OPEN = 0x4e45504f;
    private static final int A_OKAY = 0x59414b4f;
    private static final int A_CLSE = 0x45534c43;
    private static final int A_WRTE = 0x45545257;

    private static final int A_VERSION = 0x01000001;
    private static final int MAX_PAYLOAD = 256 * 1024;

    private static final int AUTH_TOKEN = 1;
    private static final int AUTH_SIGNATURE = 2;
    private static final int AUTH_RSAPUBLICKEY = 3;

    /** DigestInfo-Präfix für SHA-1: adb behandelt das Token als fertigen SHA-1-Hash. */
    private static final byte[] SHA1_DIGEST_INFO = {
            0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14};

    private static final int LOCAL_ID = 1;

    private AdbClient() {}

    /** Ob der eigene Schlüssel am TV schon einmal angenommen wurde. */
    static boolean isGranted(Context context) {
        return new File(context.getFilesDir(), "adb_granted").exists();
    }

    static void markGranted(Context context) {
        try {
            new File(context.getFilesDir(), "adb_granted").createNewFile();
        } catch (IOException ignored) {
            // Dann wird beim nächsten Mal erneut gefragt.
        }
    }

    /**
     * Kurze Verbindungen ohne Freigabe würden eine Rückfrage auslösen, die adbd danach als
     * „pending“ festhält; bis dahin also gar nicht erst verbinden.
     */
    static void requireGranted(Context context) throws IOException {
        if (!isGranted(context)) {
            throw new IOException("adb access not granted yet");
        }
    }

    /** Ob schon ein Schlüssel existiert, also die Freigabe am TV wahrscheinlich erteilt ist. */
    static boolean hasKey(Context context) {
        return new File(context.getFilesDir(), "adbkey").exists();
    }

    /**
     * Führt einen Shell-Befehl über adb aus und liefert dessen Ausgabe.
     *
     * @param approvalTimeoutMs wie lange auf die Freigabe am TV gewartet wird
     */
    public static String shell(Context context, String command, int approvalTimeoutMs)
            throws IOException, GeneralSecurityException {
        KeyPair keys = loadOrCreateKeys(context);
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(HOST, PORT), 3000);
            socket.setSoTimeout(5000);
            DataInputStream in = new DataInputStream(socket.getInputStream());
            OutputStream out = socket.getOutputStream();

            send(out, A_CNXN, A_VERSION, MAX_PAYLOAD, "host::\0".getBytes(StandardCharsets.UTF_8));

            boolean signatureSent = false;
            boolean publicKeySent = false;
            while (true) {
                Message msg = read(in);
                if (msg.command == A_CNXN) {
                    markGranted(context);
                    break;
                }
                if (msg.command != A_AUTH || msg.arg0 != AUTH_TOKEN) {
                    throw new IOException("Unexpected response from adb: " + Integer.toHexString(msg.command));
                }
                if (!signatureSent) {
                    send(out, A_AUTH, AUTH_SIGNATURE, 0, sign(keys.getPrivate(), msg.data));
                    signatureSent = true;
                } else if (!publicKeySent) {
                    // Schlüssel noch unbekannt: TV zeigt jetzt „Debugging zulassen?“.
                    send(out, A_AUTH, AUTH_RSAPUBLICKEY, 0,
                            adbPublicKey((RSAPublicKey) keys.getPublic()));
                    publicKeySent = true;
                    socket.setSoTimeout(approvalTimeoutMs);
                } else {
                    throw new IOException("Debugging not allowed on the TV");
                }
            }
            socket.setSoTimeout(10000);

            send(out, A_OPEN, LOCAL_ID, 0, ("shell:" + command + "\0").getBytes(StandardCharsets.UTF_8));
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            while (true) {
                Message msg = read(in);
                if (msg.command == A_WRTE) {
                    output.write(msg.data);
                    send(out, A_OKAY, LOCAL_ID, msg.arg0, new byte[0]);
                } else if (msg.command == A_CLSE) {
                    send(out, A_CLSE, LOCAL_ID, msg.arg0, new byte[0]);
                    break;
                }
                // A_OKAY bestätigt nur das Öffnen des Kanals.
            }
            return output.toString("UTF-8").trim();
        }
    }

    private static final class Message {
        int command;
        int arg0;
        int arg1;
        byte[] data;
    }

    private static void send(OutputStream out, int command, int arg0, int arg1, byte[] data)
            throws IOException {
        int checksum = 0;
        for (byte b : data) {
            checksum += b & 0xff;
        }
        ByteBuffer header = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);
        header.putInt(command).putInt(arg0).putInt(arg1)
                .putInt(data.length).putInt(checksum).putInt(~command);
        out.write(header.array());
        out.write(data);
        out.flush();
    }

    private static Message read(DataInputStream in) throws IOException {
        byte[] raw = new byte[24];
        in.readFully(raw);
        ByteBuffer header = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
        Message msg = new Message();
        msg.command = header.getInt();
        msg.arg0 = header.getInt();
        msg.arg1 = header.getInt();
        int length = header.getInt();
        msg.data = new byte[length];
        in.readFully(msg.data);
        return msg;
    }

    private static byte[] sign(PrivateKey key, byte[] token) throws GeneralSecurityException {
        Signature signature = Signature.getInstance("NONEwithRSA");
        signature.initSign(key);
        signature.update(SHA1_DIGEST_INFO);
        signature.update(token);
        return signature.sign();
    }

    /** Öffentlicher Schlüssel im adb-eigenen Format (wie in ~/.android/adbkey.pub). */
    private static byte[] adbPublicKey(RSAPublicKey key) {
        int words = key.getModulus().bitLength() / 32;
        BigInteger n = key.getModulus();
        BigInteger r32 = BigInteger.ONE.shiftLeft(32);
        BigInteger n0inv = r32.subtract(n.mod(r32).modInverse(r32));
        BigInteger rr = BigInteger.ONE.shiftLeft(words * 64).mod(n);

        ByteBuffer buf = ByteBuffer.allocate(4 + 4 + words * 4 * 2 + 4).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(words);
        buf.putInt(n0inv.intValue());
        putLittleEndianWords(buf, n, words);
        putLittleEndianWords(buf, rr, words);
        buf.putInt(key.getPublicExponent().intValue());

        String encoded = Base64.encodeToString(buf.array(), Base64.NO_WRAP);
        return (encoded + " launcher@tv\0").getBytes(StandardCharsets.UTF_8);
    }

    private static void putLittleEndianWords(ByteBuffer buf, BigInteger value, int words) {
        for (int i = 0; i < words; i++) {
            buf.putInt(value.shiftRight(32 * i).intValue());
        }
    }

    private static KeyPair loadOrCreateKeys(Context context) throws IOException, GeneralSecurityException {
        File privateFile = new File(context.getFilesDir(), "adbkey");
        File publicFile = new File(context.getFilesDir(), "adbkey.pub");
        KeyFactory factory = KeyFactory.getInstance("RSA");
        if (privateFile.exists() && publicFile.exists()) {
            PrivateKey privateKey = factory.generatePrivate(
                    new PKCS8EncodedKeySpec(Files.readAllBytes(privateFile.toPath())));
            RSAPublicKey publicKey = (RSAPublicKey) factory.generatePublic(
                    new X509EncodedKeySpec(Files.readAllBytes(publicFile.toPath())));
            return new KeyPair(publicKey, privateKey);
        }
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keys = generator.generateKeyPair();
        try (FileOutputStream out = new FileOutputStream(privateFile)) {
            out.write(keys.getPrivate().getEncoded());
        }
        try (FileOutputStream out = new FileOutputStream(publicFile)) {
            out.write(keys.getPublic().getEncoded());
        }
        return keys;
    }
}
