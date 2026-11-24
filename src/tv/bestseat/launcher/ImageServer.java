package tv.bestseat.launcher;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

/**
 * Kleine Webseite im WLAN, um ein Kachelbild vom Handy oder Rechner zu schicken (W126).
 * Läuft nur, solange das Fenster „Change Image“ offen ist; die Adresse enthält ein zufälliges
 * Kürzel. Die Seite kann eine Bild-URL schicken (der TV lädt sie) oder eine Bilddatei hochladen.
 */
final class ImageServer {

    interface Listener {
        /** Auf dem Haupt-Thread: Bild gespeichert. */
        void onSaved();
    }

    private static final String TAG = "Launcher.ImageServer";
    private static final int PORT = 8765;
    private static final int MAX_BYTES = 15 * 1024 * 1024;

    private final Context context;
    private final String pkg;
    private final String label;
    private final Listener listener;
    private final String token;
    private final Handler main = new Handler(Looper.getMainLooper());
    private ServerSocket server;
    private volatile boolean running;

    ImageServer(Context context, String pkg, String label, Listener listener) {
        this.context = context.getApplicationContext();
        this.pkg = pkg;
        this.label = label;
        this.listener = listener;
        byte[] b = new byte[3];
        new SecureRandom().nextBytes(b);
        StringBuilder t = new StringBuilder();
        for (byte x : b) t.append(String.format("%02x", x));
        token = t.toString();
    }

    /** Startet den Server; liefert die Adresse für das Handy oder null. */
    String start() {
        String ip = lanAddress(context);
        if (ip == null) return null;
        try {
            try {
                server = new ServerSocket(PORT);
            } catch (Exception busy) {
                server = new ServerSocket(0);
            }
        } catch (Exception e) {
            Log.w(TAG, "No server: " + e);
            return null;
        }
        running = true;
        Thread t = new Thread(this::loop, "image-server");
        t.setDaemon(true);
        t.start();
        return "http://" + ip + ":" + server.getLocalPort() + "/" + token;
    }

    void stop() {
        running = false;
        try {
            if (server != null) server.close();
        } catch (Exception ignored) {
            // schon zu
        }
    }

    private void loop() {
        while (running) {
            try (Socket s = server.accept()) {
                s.setSoTimeout(30_000);
                handle(s);
            } catch (Exception e) {
                if (running) Log.w(TAG, "Request failed: " + e);
            }
        }
    }

    private void handle(Socket s) throws Exception {
        InputStream in = new BufferedInputStream(s.getInputStream());
        String requestLine = readLine(in);
        if (requestLine == null) return;
        int length = 0;
        String line;
        while ((line = readLine(in)) != null && !line.isEmpty()) {
            int colon = line.indexOf(':');
            if (colon > 0 && line.substring(0, colon).trim().equalsIgnoreCase("Content-Length")) {
                length = Integer.parseInt(line.substring(colon + 1).trim());
            }
        }
        String[] parts = requestLine.split(" ");
        String method = parts.length > 0 ? parts[0] : "";
        String path = parts.length > 1 ? parts[1] : "/";
        OutputStream out = s.getOutputStream();
        String base = "/" + token;
        if (!path.equals(base) && !path.startsWith(base + "/")) {
            respond(out, 404, "text/plain", "Not found");
            return;
        }
        if ("GET".equals(method)) {
            respond(out, 200, "text/html; charset=utf-8", page());
            return;
        }
        if (!"POST".equals(method) || length <= 0 || length > MAX_BYTES) {
            respond(out, 400, "text/plain", "Bad request");
            return;
        }
        byte[] body = readBody(in, length);
        byte[] image;
        if (path.equals(base + "/url")) {
            image = download(new String(body, StandardCharsets.UTF_8).trim());
            if (image == null) {
                respond(out, 400, "text/plain", "Could not load that address.");
                return;
            }
        } else if (path.equals(base + "/upload")) {
            image = body;
        } else {
            respond(out, 404, "text/plain", "Not found");
            return;
        }
        if (!TileImages.save(context, pkg, image)) {
            respond(out, 400, "text/plain", "That is not an image the TV can read.");
            return;
        }
        respond(out, 200, "text/plain", "Done. The tile shows the new image.");
        main.post(listener::onSaved);
    }

    private static byte[] download(String address) {
        if (!address.startsWith("http://") && !address.startsWith("https://")) return null;
        try {
            HttpURLConnection con = (HttpURLConnection) new URL(address).openConnection();
            con.setConnectTimeout(8000);
            con.setReadTimeout(15000);
            con.setInstanceFollowRedirects(true);
            con.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) BestSeat");
            try {
                if (con.getResponseCode() / 100 != 2) return null;
                try (InputStream in = con.getInputStream()) {
                    ByteArrayOutputStream buf = new ByteArrayOutputStream();
                    byte[] b = new byte[16384];
                    int n;
                    while ((n = in.read(b)) > 0) {
                        buf.write(b, 0, n);
                        if (buf.size() > MAX_BYTES) return null;
                    }
                    return buf.toByteArray();
                }
            } finally {
                con.disconnect();
            }
        } catch (Exception e) {
            Log.w(TAG, "Download failed: " + e);
            return null;
        }
    }

    private String page() {
        String name = label.replace("&", "&amp;").replace("<", "&lt;");
        return "<!doctype html><html><head><meta charset=utf-8>"
                + "<meta name=viewport content='width=device-width,initial-scale=1'>"
                + "<title>Best Seat – tile image</title><style>"
                + "body{font-family:system-ui,sans-serif;background:#111;color:#eee;margin:0;padding:24px;max-width:560px}"
                + "h1{font-size:22px}p{color:#aaa}input,button{font-size:17px;width:100%;box-sizing:border-box;"
                + "padding:12px;margin:6px 0;border-radius:10px;border:1px solid #444;background:#222;color:#eee}"
                + "button{background:#3a7bff;border:0;color:#fff;font-weight:600}#msg{margin-top:16px;font-weight:600}"
                + "</style></head><body><h1>New image for " + name + "</h1>"
                + "<p>Paste the address of an image, or choose a picture. A wide image (16:9) fits best.</p>"
                + "<input id=u type=url placeholder='https://…'><button onclick=sendUrl()>Use this address</button>"
                + "<input id=f type=file accept='image/*'><button onclick=sendFile()>Upload picture</button>"
                + "<div id=msg></div><script>"
                + "function show(r){r.text().then(t=>{document.getElementById('msg').textContent=t})}"
                + "function sendUrl(){fetch(location.pathname+'/url',{method:'POST',body:document.getElementById('u').value})"
                + ".then(show).catch(()=>{msg.textContent='The TV did not answer.'})}"
                + "function sendFile(){const f=document.getElementById('f').files[0];if(!f)return;"
                + "fetch(location.pathname+'/upload',{method:'POST',body:f}).then(show)"
                + ".catch(()=>{msg.textContent='The TV did not answer.'})}"
                + "</script></body></html>";
    }

    private static String readLine(InputStream in) throws Exception {
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') break;
            if (c != '\r') sb.append((char) c);
            if (sb.length() > 8192) return null;
        }
        return c == -1 && sb.length() == 0 ? null : sb.toString();
    }

    private static byte[] readBody(InputStream in, int length) throws Exception {
        byte[] body = new byte[length];
        int read = 0;
        while (read < length) {
            int n = in.read(body, read, length - read);
            if (n < 0) break;
            read += n;
        }
        return body;
    }

    private static void respond(OutputStream out, int code, String type, String text) throws Exception {
        byte[] body = text.getBytes(StandardCharsets.UTF_8);
        String status = code == 200 ? "OK" : code == 404 ? "Not Found" : "Bad Request";
        String head = "HTTP/1.1 " + code + " " + status + "\r\nContent-Type: " + type
                + "\r\nContent-Length: " + body.length + "\r\nConnection: close\r\nCache-Control: no-store\r\n\r\n";
        out.write(head.getBytes(StandardCharsets.UTF_8));
        out.write(body);
        out.flush();
    }

    /** IPv4-Adresse im Heimnetz (nicht die des VPN). */
    static String lanAddress(Context context) {
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            for (Network n : cm.getAllNetworks()) {
                NetworkCapabilities caps = cm.getNetworkCapabilities(n);
                if (caps == null || caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) continue;
                LinkProperties lp = cm.getLinkProperties(n);
                if (lp == null) continue;
                for (LinkAddress a : lp.getLinkAddresses()) {
                    InetAddress addr = a.getAddress();
                    if (addr instanceof Inet4Address && addr.isSiteLocalAddress()) return addr.getHostAddress();
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "No LAN address: " + e);
        }
        return null;
    }
}
