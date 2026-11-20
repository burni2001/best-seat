package tv.bernhard.launcher;

import android.content.pm.LauncherApps;
import android.os.UserHandle;

/** Meldet App-Änderungen an die Startseite (eigene Klasse statt innerer Klasse wegen d8). */
final class PackageCallback extends LauncherApps.Callback {

    interface Listener {
        void onPackagesChanged(String[] packages, boolean available, boolean removed);
    }

    private final Listener listener;

    PackageCallback(Listener listener) {
        this.listener = listener;
    }

    @Override
    public void onPackageRemoved(String packageName, UserHandle user) {
        listener.onPackagesChanged(new String[] {packageName}, false, true);
    }

    @Override
    public void onPackageAdded(String packageName, UserHandle user) {
        listener.onPackagesChanged(new String[] {packageName}, true, false);
    }

    @Override
    public void onPackageChanged(String packageName, UserHandle user) {
        listener.onPackagesChanged(new String[] {packageName}, true, false);
    }

    @Override
    public void onPackagesAvailable(String[] packageNames, UserHandle user, boolean replacing) {
        listener.onPackagesChanged(packageNames, true, false);
    }

    @Override
    public void onPackagesUnavailable(String[] packageNames, UserHandle user, boolean replacing) {
        listener.onPackagesChanged(packageNames, false, false);
    }
}
