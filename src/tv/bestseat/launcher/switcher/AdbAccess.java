package tv.bestseat.launcher.switcher;

import android.app.Activity;
import android.widget.Toast;

/**
 * Einmalige Freigabe des eigenen adb-Schlüssels. Die Rückfrage „Debugging zulassen?“ bleibt nur so
 * lange stehen, wie eine Verbindung wartet; deshalb hier 60 s statt der 1,5–5 s der Leiste.
 */
public final class AdbAccess {

    private AdbAccess() {
    }

    /** Wirft, solange die adb-Freigabe am TV nicht erteilt ist (damit keine Rückfrage aufpoppt). */
    public static void requireGranted(android.content.Context context) throws java.io.IOException {
        AdbClient.requireGranted(context);
    }

    public static boolean hasKey(Activity activity) {
        return AdbClient.hasKey(activity);
    }

    public static void request(Activity activity) {
        Toast.makeText(activity, "Confirm “Allow USB debugging” on the TV (Always allow)", Toast.LENGTH_LONG).show();
        new Thread(() -> {
            String result;
            try {
                String out = AdbClient.shell(activity.getApplicationContext(), "echo ok", 60000);
                result = out != null && out.contains("ok") ? "adb access is ready" : "adb gave no answer";
            } catch (Exception e) {
                result = "adb access failed: " + e.getMessage();
            }
            final String message = result;
            activity.runOnUiThread(() -> Toast.makeText(activity, message, Toast.LENGTH_LONG).show());
        }, "adb-access").start();
    }
}
