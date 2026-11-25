package tv.bestseat.launcher;

import android.app.Activity;
import android.content.Intent;
import android.net.VpnService;
import android.os.Bundle;
import android.widget.Toast;

/**
 * Invisible "Disconnect VPN" command (Control Center, tvQuickActions: Intent, Activity,
 * action tv.bestseat.launcher.VPN_DISCONNECT). Taken over from the former Google TV Helper.
 *
 * Android allows only one VPN at a time. VpnTakeoverService briefly sets up an empty VPN and
 * closes it at once; the system then drops the previous VPN (Surfshark).
 */
public final class VpnDisconnectActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!VpnGate.connected(this)) {
            Toast.makeText(this, "VPN is not connected", Toast.LENGTH_SHORT).show();
        } else if (VpnService.prepare(this) != null) {
            // Permission missing: adb shell appops set tv.bestseat.launcher ACTIVATE_VPN allow
            Toast.makeText(this, "VPN permission missing", Toast.LENGTH_LONG).show();
        } else {
            startService(new Intent(this, VpnTakeoverService.class));
        }
        finish();
    }
}
