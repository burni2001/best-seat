package tv.bestseat.launcher;

import android.app.Activity;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.net.VpnService;
import android.os.Process;
import android.provider.Settings;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * Permissions Best Seat needs, for Settings > Permissions and the setup assistant.
 * A missing one opens the matching system page or dialog. With adb access, Best Seat grants
 * the others itself (Google TV often hides the usage access page).
 */
final class Permissions {

    static final int ADB = 0, USAGE = 1, TV = 2, VPN = 3;
    static final int COUNT = 4;

    private static final String[] NAMES = {"adb Access", "Usage Access", "TV Listings", "VPN"};
    private static final String[] HINTS = {
            "Allow adb for the App Switcher, force quit, backups and the screensaver in idle apps.",
            "Allow usage access for recent apps and the screensaver timer.",
            "Allow TV listings to show the TV channels.",
            "Allow the VPN permission to disconnect the VPN from the Control Center."};

    private Permissions() {}

    static String name(int p) {
        return NAMES[p];
    }

    static String hint(int p) {
        return HINTS[p];
    }

    static boolean granted(Context c, int p) {
        switch (p) {
            case ADB:
                try {
                    tv.bestseat.launcher.switcher.AdbAccess.requireGranted(c);
                    return true;
                } catch (Exception e) {
                    return false;
                }
            case USAGE:
                AppOpsManager ops = (AppOpsManager) c.getSystemService(Context.APP_OPS_SERVICE);
                return ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(),
                        c.getPackageName()) == AppOpsManager.MODE_ALLOWED;
            case TV:
                return c.checkSelfPermission("android.permission.READ_TV_LISTINGS")
                        == PackageManager.PERMISSION_GRANTED;
            case VPN:
                return VpnService.prepare(c) == null;
            default:
                return true;
        }
    }

    /** Missing permissions, adb first. */
    static List<Integer> missing(Context c) {
        List<Integer> out = new ArrayList<>();
        for (int p = 0; p < COUNT; p++) if (!granted(c, p)) out.add(p);
        return out;
    }

    private static String adbCommand(Activity a, int p) {
        String pkg = a.getPackageName();
        switch (p) {
            case USAGE: return "appops set " + pkg + " GET_USAGE_STATS allow";
            case TV: return "pm grant " + pkg + " android.permission.READ_TV_LISTINGS";
            case VPN: return "appops set " + pkg + " ACTIVATE_VPN allow";
            default: return "echo ok";
        }
    }

    /**
     * Fixes one permission. done runs on the UI thread once an adb grant finished, or right after
     * a system page or dialog was opened (its result shows when the caller resumes).
     */
    static void fix(Activity a, int p, Runnable done) {
        boolean viaAdb = p == ADB || granted(a, ADB);
        if (viaAdb) {
            if (p == ADB) {
                Toast.makeText(a, "Confirm “Allow USB debugging” on the TV (Always allow)", Toast.LENGTH_LONG).show();
            }
            Context app = a.getApplicationContext();
            String command = adbCommand(a, p);
            new Thread(() -> {
                boolean ok;
                try {
                    String out = tv.bestseat.launcher.switcher.AdbClient.shell(app, command, p == ADB ? 60000 : 5000);
                    ok = p != ADB || (out != null && out.contains("ok"));
                } catch (Exception e) {
                    ok = false;
                }
                boolean result = ok;
                a.runOnUiThread(() -> {
                    if (a.isDestroyed()) return;
                    if (!result || !granted(a, p)) {
                        Toast.makeText(a, "Could not allow " + name(p), Toast.LENGTH_LONG).show();
                    }
                    done.run();
                });
            }, "permission-" + p).start();
            return;
        }
        try {
            switch (p) {
                case USAGE:
                    a.startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS,
                            Uri.parse("package:" + a.getPackageName())));
                    break;
                case TV:
                    a.requestPermissions(new String[]{"android.permission.READ_TV_LISTINGS"}, 41);
                    break;
                case VPN:
                    Intent consent = VpnService.prepare(a);
                    if (consent != null) a.startActivityForResult(consent, 42);
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            Toast.makeText(a, name(p) + " has no settings page on this TV. Allow adb access first.",
                    Toast.LENGTH_LONG).show();
        }
        done.run();
    }
}
