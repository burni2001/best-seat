package tv.bestseat.launcher.switcher;

import android.content.Context;
import tv.bestseat.launcher.Companions;
import tv.bestseat.launcher.Prefs;
import android.content.SharedPreferences;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Einstellungen: globaler Schalter und die Blacklist der ausgeblendeten Apps. */
public final class SwitcherPrefs {

    private static final String KEY_ENABLED = "enabled";
    /** Hintergrund der Leiste unscharf (Bildschirmfoto) statt nur abgedunkelt. */
    private static final String KEY_BLUR = "blur_background";
    /** Pro Paket nur die Abweichung von der Voreinstellung (true = ausgeblendet). */
    private static final String BLACKLIST = "blacklist";

    /** Hilfs-Apps, die ab Werk ausgeblendet sind. In den Einstellungen änderbar. */
    private static final Set<String> DEFAULT_BLACKLIST = new HashSet<>(Arrays.asList(
            "dev.vodik7.tvquickactions.free",
            "dev.vodik7.tvquickactions",
            "ch.rmy.android.http_shortcuts",
            "eu.chainfire.tv.sideloadlauncher",
            Companions.VPNSTATUS,
            Companions.HUESYNC,
            Companions.TVHELPER,
            Companions.ALLAPPS,
            Companions.APPSWITCHER,
            "com.android.systemui",
            "org.droidtv.tvsystemui",
            "org.droidtv.philipstvdrawer",
            "com.google.android.apps.tv.launcherx",
            "com.android.tv.settings"));

    /** System-Apps von Philips (TV, Guide, Medien ...), ab Werk ausgeblendet. */
    private static final String[] DEFAULT_PREFIXES = {"org.droidtv.", "com.tpv.", "com.mediatek."};

    private SwitcherPrefs() {}

    /** Globaler Schalter: aus heißt, Leiste und „Letzte App“ tun nichts. */
    public static boolean isEnabled(Context context) {
        return Prefs.settings(context)
                .getBoolean(KEY_ENABLED, true);
    }

    public static void setEnabled(Context context, boolean enabled) {
        Prefs.settings(context)
                .edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    /** Darstellung: 0 = Karten (tvOS), 1 = Leiste mit Kacheln nebeneinander (alter App Switcher, W125). */
    public static final String[] STYLE_NAMES = {"Cards", "Bar"};
    public static final int STYLE_CARDS = 0;
    public static final int STYLE_BAR = 1;
    private static final String KEY_STYLE = "switcher_style";

    public static int style(Context context) {
        return Prefs.settings(context).getInt(KEY_STYLE, STYLE_CARDS);
    }

    public static void setStyle(Context context, int style) {
        Prefs.settings(context).edit().putInt(KEY_STYLE, style).apply();
    }

    /** Globaler Schalter „Blur effects“ der Launcher-Einstellungen. */
    public static boolean allBlur(Context context) {
        return Prefs.blurEnabled(context);
    }

    public static boolean isBlurEnabled(Context context) {
        return Prefs.settings(context)
                .getBoolean(KEY_BLUR, true);
    }

    public static void setBlurEnabled(Context context, boolean enabled) {
        Prefs.settings(context)
                .edit().putBoolean(KEY_BLUR, enabled).apply();
    }

    /** Ob eine App in Leiste und „Letzte App“ ausgeblendet ist. */
    public static boolean isBlacklisted(Context context, String pkg) {
        SharedPreferences prefs = context.getSharedPreferences(BLACKLIST, Context.MODE_PRIVATE);
        if (prefs.contains(pkg)) {
            return prefs.getBoolean(pkg, false);
        }
        return isDefaultBlacklisted(pkg);
    }

    public static void setBlacklisted(Context context, String pkg, boolean hidden) {
        SharedPreferences.Editor editor =
                context.getSharedPreferences(BLACKLIST, Context.MODE_PRIVATE).edit();
        if (hidden == isDefaultBlacklisted(pkg)) {
            // Entspricht wieder der Voreinstellung, nichts zu merken.
            editor.remove(pkg);
        } else {
            editor.putBoolean(pkg, hidden);
        }
        editor.apply();
    }

    private static boolean isDefaultBlacklisted(String pkg) {
        if (DEFAULT_BLACKLIST.contains(pkg)) {
            return true;
        }
        for (String prefix : DEFAULT_PREFIXES) {
            if (pkg.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
