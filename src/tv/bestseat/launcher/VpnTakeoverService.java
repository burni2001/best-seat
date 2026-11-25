package tv.bestseat.launcher;

import android.content.Intent;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.widget.Toast;

/**
 * Sets up an empty VPN without routes (it redirects no traffic) and closes it at once.
 * Android then takes the connection away from the previous VPN (Surfshark).
 */
public final class VpnTakeoverService extends VpnService {

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        boolean ok = false;
        try {
            ParcelFileDescriptor tun = new Builder()
                    .setSession("Best Seat")
                    .addAddress("10.255.255.254", 32)
                    .establish();
            if (tun != null) {
                tun.close();
                ok = true;
            }
        } catch (Exception e) {
            Log.w("Launcher.Vpn", "Could not disconnect VPN", e);
        }
        Toast.makeText(getApplicationContext(),
                ok ? "VPN disconnected" : "Could not disconnect VPN", Toast.LENGTH_SHORT).show();
        stopSelf();
        return START_NOT_STICKY;
    }
}
