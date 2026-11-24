package tv.bestseat.launcher;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

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

    /** Hell/Dunkel wie die Startseite übernehmen. */
    public static void applyTheme(Context context) {
        Ui.apply(context);
    }

    /** Zeile wie in den Einstellungen der Startseite (Fokus hell, leicht größer). */
    public static LinearLayout settingsRow(Context context) {
        return Ui.row(context);
    }

    /** Text in Farbe der Einstellungen; dim = grau (auch mit Fokus), z. B. Werte rechts. */
    public static TextView text(Context context, CharSequence value, int sp, boolean dim, boolean bold) {
        if (dim) {
            TextView t = Ui.text(context, value, sp, Ui.TEXT_DIM, bold);
            t.setTag(Boolean.TRUE);
            return t;
        }
        return Ui.text(context, value, sp, Ui.TEXT, bold);
    }

    public static Drawable material(Context context, float radiusPx) {
        return Ui.material(context, radiusPx);
    }

    /** Kontextmenü wie auf der Startseite (langes OK), neben der Kachel. */
    public static void contextMenu(Activity activity, View anchor, String[] labels, Runnable[] actions) {
        Dialogs.Panel p = Dialogs.menu(activity, null).anchor(anchor);
        LinearLayout first = null;
        for (int i = 0; i < labels.length; i++) {
            Runnable action = actions[i];
            LinearLayout row = Dialogs.actionRow(activity, p, labels[i], action);
            if (first == null) first = row;
        }
        p.dialog.show();
        if (first != null) first.requestFocus();
    }

    /** VPN-Abfrage vor dem Start (W127); true = Fenster gezeigt, open startet später. */
    public static boolean vpnCheck(Activity activity, String pkg, String label, android.content.Intent launch,
            Runnable open) {
        return VpnGate.check(activity, pkg, label, launch, open);
    }
}
