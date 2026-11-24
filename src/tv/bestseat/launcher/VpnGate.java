package tv.bestseat.launcher;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.LinearLayout;
import android.widget.Toast;

/**
 * VPN-Abfrage vor dem Start gewählter Apps (W127). Ist das VPN aus, fragt ein Fenster:
 * „Connect VPN“ öffnet die VPN-App; sobald das VPN steht, startet Best Seat die gewählte App.
 * Surfshark lässt sich auf dem TV nicht direkt verbinden (keine Schnelleinstellungen unter Google TV).
 */
final class VpnGate {

    private static final String TAG = "Launcher.VpnGate";
    static final String VPN_APP = "com.surfshark.vpnclient.android";
    private static final long WAIT_MS = 120_000;

    private static ConnectivityManager.NetworkCallback waiting;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private VpnGate() {}

    static boolean connected(Context c) {
        try {
            ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
            for (Network n : cm.getAllNetworks()) {
                NetworkCapabilities caps = cm.getNetworkCapabilities(n);
                if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return true;
            }
        } catch (RuntimeException ignored) {
            // dann gilt: nicht verbunden
        }
        return false;
    }

    /**
     * Vor dem Start: Gehört die App zur Liste und ist das VPN aus, das Fenster zeigen und true liefern.
     * open startet die App wie gewohnt (auch für „Open Without VPN“).
     */
    static boolean check(Activity a, String pkg, String label, Intent launch, Runnable open) {
        if (pkg == null || launch == null || !Prefs.vpnApps(a).contains(pkg) || connected(a)) return false;
        Dialogs.Panel p = Dialogs.alert(a, "VPN is off", "Connect the VPN before " + label + " opens?");
        LinearLayout first = Dialogs.alertButton(a, p, "Connect VPN", () -> connectThenOpen(a, label, launch));
        Dialogs.alertButton(a, p, "Open Without VPN", open);
        p.dialog.show();
        first.requestFocus();
        return true;
    }

    private static void connectThenOpen(Activity a, String label, Intent launch) {
        Context app = a.getApplicationContext();
        Intent vpn = a.getPackageManager().getLeanbackLaunchIntentForPackage(VPN_APP);
        if (vpn == null) vpn = a.getPackageManager().getLaunchIntentForPackage(VPN_APP);
        if (vpn == null) {
            Toast.makeText(a, "VPN app not installed", Toast.LENGTH_SHORT).show();
            return;
        }
        stopWaiting(app);
        ConnectivityManager cm = (ConnectivityManager) app.getSystemService(Context.CONNECTIVITY_SERVICE);
        final Intent target = new Intent(launch).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ConnectivityManager.NetworkCallback cb = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                MAIN.post(() -> {
                    if (waiting != this) return;
                    stopWaiting(app);
                    Log.i(TAG, "VPN up, opening " + label);
                    open(app, target);
                });
            }
        };
        try {
            NetworkRequest request = new NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
                    .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                    .build();
            cm.registerNetworkCallback(request, cb);
            waiting = cb;
            MAIN.postDelayed(() -> {
                if (waiting == cb) stopWaiting(app);
            }, WAIT_MS);
        } catch (RuntimeException e) {
            Log.w(TAG, "No VPN callback: " + e);
        }
        Toast.makeText(a, label + " opens as soon as the VPN is connected", Toast.LENGTH_LONG).show();
        a.startActivity(vpn.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    /** App starten, während die VPN-App vorne ist: über die eigene adb-Verbindung, sonst direkt. */
    private static void open(Context app, Intent target) {
        if (target.getComponent() != null) {
            String cmd = "am start -n " + target.getComponent().flattenToShortString();
            new Thread(() -> {
                try {
                    tv.bestseat.launcher.switcher.AdbAccess.requireGranted(app);
                    tv.bestseat.launcher.switcher.AdbClient.shell(app, cmd, 5000);
                } catch (Exception e) {
                    Log.w(TAG, "adb start failed, trying directly: " + e.getMessage());
                    MAIN.post(() -> startDirect(app, target));
                }
            }, "vpn-gate").start();
            return;
        }
        startDirect(app, target);
    }

    private static void startDirect(Context app, Intent target) {
        try {
            app.startActivity(target);
        } catch (RuntimeException e) {
            Log.w(TAG, "Could not open the app: " + e);
        }
    }

    private static void stopWaiting(Context app) {
        if (waiting == null) return;
        try {
            ((ConnectivityManager) app.getSystemService(Context.CONNECTIVITY_SERVICE)).unregisterNetworkCallback(waiting);
        } catch (RuntimeException ignored) {
            // schon abgemeldet
        }
        waiting = null;
    }
}
