package tv.bestseat.launcher.switcher;

import android.content.Context;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;

/**
 * Einzelne adb-Befehle an den adb-Dienst des TVs selbst (Netzwerk-Debugging auf Port 5555) und die
 * Merker, ob der eigene Schlüssel dort schon freigegeben ist. Das Protokoll steckt in AdbSession.
 */
public final class AdbClient {

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
        return AdbSession.hasKey(context);
    }

    /**
     * Führt einen Shell-Befehl über eine eigene, kurze adb-Verbindung aus und liefert dessen Ausgabe.
     *
     * @param approvalTimeoutMs wie lange auf die Freigabe am TV gewartet wird
     */
    public static String shell(Context context, String command, int approvalTimeoutMs)
            throws IOException, GeneralSecurityException {
        try (AdbSession session = AdbSession.open(context, approvalTimeoutMs)) {
            return session.shell(command);
        }
    }
}
