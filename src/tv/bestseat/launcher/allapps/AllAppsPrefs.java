package tv.bestseat.launcher.allapps;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Einstellungen aus dem Menü am linken Rand von All Apps, gespeichert in
 * SharedPreferences „allapps“ (getrennt von „settings“ des App Switchers).
 */
final class AllAppsPrefs {

    static final String SHOW_SYSTEM = "show_system";
    static final String SHOW_NON_TV = "show_non_tv";
    static final String SORT_BY_INSTALL = "sort_by_install";
    static final String CLOSE_WHEN_LEAVING = "close_when_leaving";

    private AllAppsPrefs() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences("allapps", Context.MODE_PRIVATE);
    }

    /** Voreinstellung: alles zeigen, nach Name sortieren, App bleibt offen. */
    static boolean get(Context context, String key) {
        boolean def = SHOW_SYSTEM.equals(key) || SHOW_NON_TV.equals(key);
        return prefs(context).getBoolean(key, def);
    }

    static void toggle(Context context, String key) {
        prefs(context).edit().putBoolean(key, !get(context, key)).apply();
    }

    // Hintergrund (Backdrop): schwarz, Bildschirmfoto unscharf, oder Petrol-Verlauf.
    static final String BACKGROUND = "background";
    static final String BG_BLACK = "black";
    static final String BG_BLURRED = "blurred";
    static final String BG_PETROL = "petrol";
    private static final String[] BACKGROUNDS = {BG_BLACK, BG_BLURRED, BG_PETROL};

    /** Voreinstellung Petrol: sieht aus wie ein AT4K-Ordner und kostet beim Öffnen keine Zeit. */
    static String background(Context context) {
        return prefs(context).getString(BACKGROUND, BG_PETROL);
    }

    /** Schaltet reihum weiter: Black → Blurred → Petrol → Black. */
    static String nextBackground(Context context) {
        String current = background(context);
        int index = 0;
        for (int i = 0; i < BACKGROUNDS.length; i++) {
            if (BACKGROUNDS[i].equals(current)) {
                index = i;
            }
        }
        String next = BACKGROUNDS[(index + 1) % BACKGROUNDS.length];
        prefs(context).edit().putString(BACKGROUND, next).apply();
        return next;
    }
}
