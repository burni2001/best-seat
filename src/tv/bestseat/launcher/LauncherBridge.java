package tv.bestseat.launcher;

import android.app.Activity;
import android.content.Context;
import android.widget.Switch;

/** Öffentlicher Zugang für Unterpakete (All Apps) zu Bausteinen des Launchers. */
public final class LauncherBridge {

    private LauncherBridge() {
    }

    public static Switch toggle(Context context) {
        return Ui.toggle(context);
    }

    public static void about(Activity activity) {
        Dialogs.about(activity);
    }

    /** Ob die App auf der Startseite ausgeblendet ist. */
    public static boolean isHidden(Context context, String pkg) {
        return LayoutStore.get(context).hidden.contains(Tile.appKey(pkg));
    }

    public static void setHidden(Context context, String pkg, boolean hide) {
        LayoutStore.get(context).setHidden(Tile.appKey(pkg), hide);
    }
}
