package tv.bestseat.launcher;

import android.os.Process;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

/** Apps auf dem USB-Stick: nach dem Standby warten, bis der Stick wieder da ist, dann starten. */
final class StorageWait {

    private static final String TAG = "Launcher.Home";
    private static final long STORAGE_TIMEOUT_MS = 90_000;

    private final HomeActivity home;
    private String waitingPkg;
    private long waitingSince;
    private Dialogs.Panel waitingPanel;

    StorageWait(HomeActivity home) {
        this.home = home;
    }

    /** App vom Stick angetippt, der noch fehlt: Hinweis zeigen und starten, sobald er da ist. */
    void start(Tile tile) {
        waitingPkg = tile.pkg;
        waitingSince = System.currentTimeMillis();
        Dialogs.Panel p = Dialogs.panel(home, "Waiting for USB storage…", 480);
        p.content.addView(Ui.text(home, tile.label + " opens as soon as the USB stick is ready.", 16,
                Ui.TEXT_DIM, false));
        View spacer = new View(home);
        p.content.addView(spacer, new LinearLayout.LayoutParams(1, Ui.dp(home, 18)));
        LinearLayout cancel = Dialogs.actionRow(home, p, "Cancel", () -> waitingPkg = null);
        cancel.setGravity(Gravity.CENTER);
        p.dialog.setOnCancelListener(d -> waitingPkg = null);
        waitingPanel = p;
        home.show(p);
        cancel.requestFocus();
        home.handler.removeCallbacks(pollStorage);
        home.handler.postDelayed(pollStorage, 1000);
    }

    // Lambda statt anonymer Klasse: d8 stürzt bei anonymen Klassen in diesem Build ab.
    private final Runnable pollStorage = this::pollStorageTick;

    private void pollStorageTick() {
        if (waitingPkg == null) return;
        if (isAvailable(waitingPkg)) {
            home.reload();
            return;
        }
        if (System.currentTimeMillis() - waitingSince > STORAGE_TIMEOUT_MS) {
            if (waitingPanel != null) waitingPanel.dialog.dismiss();
            waitingPkg = null;
            Toast.makeText(home, "USB storage didn't come back", Toast.LENGTH_LONG).show();
            return;
        }
        home.handler.postDelayed(pollStorage, 1000);
    }

    boolean isAvailable(String pkg) {
        try {
            return !home.launcherApps.getActivityList(pkg, Process.myUserHandle()).isEmpty();
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Nach dem Neuladen: wartet jemand auf eine App, die jetzt da ist? */
    void check() {
        if (waitingPkg == null) return;
        for (Tile t : home.allTiles) {
            if (waitingPkg.equals(t.pkg) && t.available) {
                waitingPkg = null;
                home.handler.removeCallbacks(pollStorage);
                if (waitingPanel != null) waitingPanel.dialog.dismiss();
                Log.i(TAG, "Storage back, launching " + t.pkg);
                home.launch(t);
                return;
            }
        }
    }
}
