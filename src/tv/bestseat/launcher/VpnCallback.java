package tv.bestseat.launcher;

import android.net.ConnectivityManager;
import android.net.Network;

import java.util.HashSet;
import java.util.Set;

/** Zählt VPN-Netzwerke (Surfshark) für die Anzeige in der Kopfzeile. */
final class VpnCallback extends ConnectivityManager.NetworkCallback {

    interface Listener {
        void onVpnChanged(boolean connected);
    }

    private final Set<Network> networks = new HashSet<>();
    private final Listener listener;

    VpnCallback(Listener listener) {
        this.listener = listener;
    }

    @Override
    public void onAvailable(Network network) {
        boolean now;
        synchronized (networks) {
            networks.add(network);
            now = !networks.isEmpty();
        }
        listener.onVpnChanged(now);
    }

    @Override
    public void onLost(Network network) {
        boolean now;
        synchronized (networks) {
            networks.remove(network);
            now = !networks.isEmpty();
        }
        listener.onVpnChanged(now);
    }
}
