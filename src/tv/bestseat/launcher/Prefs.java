package tv.bestseat.launcher;

import android.content.Context;
import android.content.SharedPreferences;

/** Einstellungen der Oberfläche. */
public final class Prefs {

    // Schlüssel in der Datei „settings“ (auch im Layout-Backup enthalten, deshalb nie umbenennen).
    private static final String K_APPEARANCE = "appearance";
    private static final String K_SETUP_DONE = "setup_done";
    private static final String K_LIQUID_GLASS = "liquid_glass";
    private static final String K_GLASS_OPACITY = "glass_opacity";
    private static final String K_BACKGROUND = "background";
    private static final String K_BLUR_ALL = "blur_all";
    private static final String K_CLOCK_OFFSET = "clock_offset";
    private static final String K_COLUMNS = "columns";
    private static final String K_FAVORITES = "favorites";
    private static final String K_FONT = "font";
    private static final String K_HIDE_USB_NOTICE = "hide_usb_notice";
    private static final String K_NON_TV_APPS = "non_tv_apps";
    private static final String K_REDUCE_MOTION = "reduce_motion";
    private static final String K_SAVER_CLOCK = "saver_clock";
    private static final String K_SAVER_DELAY = "saver_delay";
    private static final String K_SAVER_IN_APPS = "saver_in_apps";
    private static final String K_SAVER_LOCATION = "saver_location";
    private static final String K_SAVER_SOURCE = "saver_source";
    private static final String K_SAVER_TOPIC = "saver_topic";
    private static final String K_SHINE = "shine";
    private static final String K_SHINE_SHELF = "shine_shelf";
    private static final String K_SHOW_CLOCK = "show_clock";
    private static final String K_SHOW_VPN = "show_vpn";
    private static final String K_TILE_HIDDEN = "tile_hidden";
    private static final String K_TILE_SETTINGS = "tile_settings";
    private static final String K_CC_ORDER = "cc_order";
    private static final String K_CC_HIDDEN = "cc_hidden";

    static final int[] COLUMN_CHOICES = {5, 6, 7, 8};

    static final int SAVER_AERIALS = 0;
    static final int SAVER_PHOTOS = 1;
    static final int SAVER_CLOCK = 2;
    static final String[] SAVER_NAMES = {"Aerial videos", "Online photos", "Clock"};
    /** Minuten bis zum Schoner, 0 = aus. */
    static final int[] SAVER_DELAYS = {1, 2, 3, 4, 5, 0};

    private Prefs() {}

    /** Datei der Einstellungen; Profile und App Switcher legen ihre Werte mit hinein. */
    public static SharedPreferences settings(Context c) {
        return c.getSharedPreferences("settings", Context.MODE_PRIVATE);
    }

    private static SharedPreferences sp(Context c) {
        return settings(c);
    }

    private static int getInt(Context c, String key, int def) {
        return sp(c).getInt(key, def);
    }

    private static boolean getBool(Context c, String key, boolean def) {
        return sp(c).getBoolean(key, def);
    }

    private static void put(Context c, String key, int value) {
        sp(c).edit().putInt(key, value).apply();
    }

    private static void put(Context c, String key, boolean value) {
        sp(c).edit().putBoolean(key, value).apply();
    }

    static int columns(Context c) {
        return getInt(c, K_COLUMNS, 6);
    }

    static void setColumns(Context c, int value) {
        put(c, K_COLUMNS, value);
    }

    static boolean nonTvApps(Context c) {
        return getBool(c, K_NON_TV_APPS, false);
    }

    static void setNonTvApps(Context c, boolean value) {
        put(c, K_NON_TV_APPS, value);
    }

    private static final String K_VPN_APPS = "vpn_prompt_apps";

    /** Apps, vor deren Start bei getrenntem VPN gefragt wird (W127). */
    static java.util.Set<String> vpnApps(Context c) {
        java.util.Set<String> out = new java.util.HashSet<>();
        for (String p : sp(c).getString(K_VPN_APPS, "").split(",")) {
            if (!p.isEmpty()) out.add(p);
        }
        return out;
    }

    static void setVpnApp(Context c, String pkg, boolean ask) {
        java.util.Set<String> set = vpnApps(c);
        if (ask) set.add(pkg); else set.remove(pkg);
        sp(c).edit().putString(K_VPN_APPS, String.join(",", set)).apply();
    }

    static boolean showVpn(Context c) {
        return getBool(c, K_SHOW_VPN, true);
    }

    static void setShowVpn(Context c, boolean value) {
        put(c, K_SHOW_VPN, value);
    }

    static int saverSource(Context c) {
        return getInt(c, K_SAVER_SOURCE, SAVER_AERIALS);
    }

    static void setSaverSource(Context c, int value) {
        put(c, K_SAVER_SOURCE, value);
    }

    static int saverTopic(Context c) {
        return getInt(c, K_SAVER_TOPIC, 0);
    }

    static void setSaverTopic(Context c, int value) {
        put(c, K_SAVER_TOPIC, value);
    }

    static int saverDelay(Context c) {
        return getInt(c, K_SAVER_DELAY, 5);
    }

    static void setSaverDelay(Context c, int value) {
        put(c, K_SAVER_DELAY, value);
    }

    /** Schoner auch starten, wenn eine andere App untätig vorne ist (W133). */
    static boolean saverInApps(Context c) {
        return getBool(c, K_SAVER_IN_APPS, true);
    }

    static void setSaverInApps(Context c, boolean value) {
        put(c, K_SAVER_IN_APPS, value);
    }

    static boolean saverClock(Context c) {
        return getBool(c, K_SAVER_CLOCK, true);
    }

    static void setSaverClock(Context c, boolean value) {
        put(c, K_SAVER_CLOCK, value);
    }

    static boolean saverLocation(Context c) {
        return getBool(c, K_SAVER_LOCATION, true);
    }

    static void setSaverLocation(Context c, boolean value) {
        put(c, K_SAVER_LOCATION, value);
    }

    /** Lichtstreifen über der fokussierten Kachel/Karte. */
    static boolean shine(Context c) {
        return getBool(c, K_SHINE, true);
    }

    /** Bewegung reduzieren (W83): Ein- und Auszoomen von Apps, Ordnern und Kontrollzentrum nur als Überblendung. */
    static boolean reduceMotion(Context c) {
        return getBool(c, K_REDUCE_MOTION, false);
    }

    static void setReduceMotion(Context c, boolean value) {
        put(c, K_REDUCE_MOTION, value);
    }

    static void setShine(Context c, boolean value) {
        put(c, K_SHINE, value);
    }

    /** Oberste Reihe als Favoriten-Leiste; nur sie füllt den Top Shelf (W14/W17). */
    static boolean favorites(Context c) {
        return getBool(c, K_FAVORITES, true);
    }

    static void setFavorites(Context c, boolean value) {
        put(c, K_FAVORITES, value);
    }

    static final int BG_MATCHING = 0;
    static final int BG_BLURRED = 1;
    static final int BG_CONTENT = 2;
    static final String[] BACKGROUND_NAMES = {"Matching color", "Blurred content", "Content"};

    /** Hintergrund der ganzen Startseite: passende Farbe, unscharfer oder scharfer Inhalt. */
    static int background(Context c) {
        return Math.max(0, Math.min(BACKGROUND_NAMES.length - 1, getInt(c, K_BACKGROUND, BG_BLURRED)));
    }

    static void setBackground(Context c, int value) {
        put(c, K_BACKGROUND, value);
    }

    /** Ein Schalter für alle Weichzeichner-Effekte (Hintergrund, Ordner, Fenster, App Switcher). */
    public static boolean blurEnabled(Context c) {
        return getBool(c, K_BLUR_ALL, true);
    }

    static void setBlurEnabled(Context c, boolean value) {
        put(c, K_BLUR_ALL, value);
    }

    /** Vollbild „USB-Speicher fehlt“ nach dem Aufwachen automatisch schließen (braucht adb-Freigabe). */
    static boolean hideUsbNotice(Context c) {
        return getBool(c, K_HIDE_USB_NOTICE, true);
    }

    static void setHideUsbNotice(Context c, boolean value) {
        put(c, K_HIDE_USB_NOTICE, value);
    }

    /** Uhrzeit in der Pille der Startseite (aus = nur das Profilbild). */
    static boolean showClock(Context c) {
        return getBool(c, K_SHOW_CLOCK, true);
    }

    static void setShowClock(Context c, boolean value) {
        put(c, K_SHOW_CLOCK, value);
    }

    /** Lichtstreifen über der fokussierten Top-Shelf-Karte (W13). */
    static boolean shineShelf(Context c) {
        return getBool(c, K_SHINE_SHELF, true);
    }

    static void setShineShelf(Context c, boolean value) {
        put(c, K_SHINE_SHELF, value);
    }

    /** Reihenfolge der Kontrollzentrum-Kacheln; fehlende IDs hinten anhängen, unbekannte weglassen. */
    static java.util.List<String> controlOrder(Context c) {
        java.util.List<String> out = new java.util.ArrayList<>();
        java.util.List<String> known = java.util.Arrays.asList(ControlItems.IDS);
        for (String id : ccGet(c, K_CC_ORDER).split(",")) {
            if (known.contains(id) && !out.contains(id)) out.add(id);
        }
        for (String id : ControlItems.IDS) {
            if (!out.contains(id)) out.add(id);
        }
        return out;
    }

    static void setControlOrder(Context c, java.util.List<String> order) {
        sp(c).edit().putString(ccKey(c, K_CC_ORDER), String.join(",", order)).apply();
    }

    static java.util.Set<String> controlHidden(Context c) {
        java.util.Set<String> out = new java.util.HashSet<>();
        for (String id : ccGet(c, K_CC_HIDDEN).split(",")) {
            if (!id.isEmpty()) out.add(id);
        }
        return out;
    }

    static void setControlHidden(Context c, java.util.Set<String> hidden) {
        sp(c).edit().putString(ccKey(c, K_CC_HIDDEN), String.join(",", hidden)).apply();
    }

    /** Kontrollzentrum je Profil (W): p0 behält die alten Schlüssel, andere Profile hängen ihre id an. */
    private static String ccKey(Context c, String base) {
        String id = Profiles.currentId(c);
        return "p0".equals(id) ? base : base + "_" + id;
    }

    /** Wert des Profils; hat es noch keinen eigenen, gilt der des ersten Profils. */
    private static String ccGet(Context c, String base) {
        String own = sp(c).getString(ccKey(c, base), null);
        return own != null ? own : sp(c).getString(base, "");
    }

    static void resetControl(Context c) {
        // Leerer Wert statt Entfernen: sonst übernähme ein weiteres Profil wieder die Kacheln des ersten.
        sp(c).edit().putString(ccKey(c, K_CC_ORDER), "").putString(ccKey(c, K_CC_HIDDEN), "").apply();
    }

    // ---- W22: eingebaute Kacheln am Ende der Startseite

    static boolean settingsTile(Context c) {
        return getBool(c, K_TILE_SETTINGS, true);
    }

    static void setSettingsTile(Context c, boolean value) {
        put(c, K_TILE_SETTINGS, value);
    }

    static boolean hiddenTile(Context c) {
        return getBool(c, K_TILE_HIDDEN, true);
    }

    static void setHiddenTile(Context c, boolean value) {
        put(c, K_TILE_HIDDEN, value);
    }

    // ---- W23: Zeitzone der eigenen Uhren (der TV läuft auf UTC)

    /** Hours the clocks run ahead of TV time (-12..12); default +1 = Germany in winter. */
    static int clockOffset(Context c) {
        return Math.max(-12, Math.min(12, getInt(c, K_CLOCK_OFFSET, 1)));
    }

    static void setClockOffset(Context c, int hours) {
        put(c, K_CLOCK_OFFSET, Math.max(-12, Math.min(12, hours)));
    }

    /** Zone of the own clocks: TV zone shifted by the chosen hours. */
    static java.util.TimeZone clockTimeZone(Context c) {
        int minutes = java.util.TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60000
                + clockOffset(c) * 60;
        int abs = Math.abs(minutes);
        return java.util.TimeZone.getTimeZone(String.format(java.util.Locale.US, "GMT%s%02d:%02d",
                minutes < 0 ? "-" : "+", abs / 60, abs % 60));
    }

    /** Uhr auf die gewählte Zeitzone stellen. */
    static void applyZone(Context c, android.widget.TextClock clock) {
        clock.setTimeZone(clockTimeZone(c).getID());
    }

    // ---- Erscheinungsbild: Dunkel, Hell oder Automatisch (tagsüber hell)

    static final String[] APPEARANCE_NAMES = {"Dark", "Light", "Automatic"};

    static int appearance(Context c) {
        int i = getInt(c, K_APPEARANCE, 0);
        return i >= 0 && i < APPEARANCE_NAMES.length ? i : 0;
    }

    static void setAppearance(Context c, int index) {
        put(c, K_APPEARANCE, index);
    }

    /** Hell bei „Light“, bei „Automatic“ von 7 bis 19 Uhr (Zeitzone wie die Uhren). */
    static boolean isLight(Context c) {
        int mode = appearance(c);
        if (mode != 2) return mode == 1;
        java.util.Calendar now = java.util.Calendar.getInstance(clockTimeZone(c));
        int hour = now.get(java.util.Calendar.HOUR_OF_DAY);
        return hour >= 7 && hour < 19;
    }

    // ---- Schrift (W115): Roboto oder eine mitgelieferte freie Schrift (Fonts.NAMES)

    static int font(Context c) {
        int i = getInt(c, K_FONT, 0);
        return i >= 0 && i < Fonts.NAMES.length ? i : 0;
    }

    static void setFont(Context c, int index) {
        put(c, K_FONT, index);
    }

    // ---- W21: Profil im Kontrollzentrum

    // Name und Bild gehören zum aktuellen Profil (W11).
    static String userName(Context c) {
        return Profiles.current(c).name;
    }

    static void setUserName(Context c, String name) {
        Profiles.update(c, Profiles.currentId(c), name, null);
    }

    /** Profilbilder: 0 = Anfangsbuchstabe, sonst Emoji. */
    static final String[] AVATARS = {"", e(0x1F98A), e(0x1F43C), e(0x1F42F), e(0x1F981), e(0x1F438), e(0x1F427),
            e(0x1F419), e(0x1F680), e(0x1F3AE),
            // W26: mehr Tiere (Reihenfolge oben bleibt, damit gewählte Bilder gleich bleiben).
            e(0x1F436), e(0x1F431), e(0x1F430), e(0x1F43B), e(0x1F428), e(0x1F42D), e(0x1F439), e(0x1F994),
            e(0x1F9A6), e(0x1F9A5), e(0x1F412), e(0x1F418), e(0x1F992), e(0x1F993), e(0x1F984), e(0x1F434),
            e(0x1F42E), e(0x1F437), e(0x1F411), e(0x1F414), e(0x1F986), e(0x1F989), e(0x1F99C), e(0x1F42C),
            e(0x1F433), e(0x1F988), e(0x1F422), e(0x1F996), e(0x1F41D), e(0x1F98B)};

    private static String e(int codePoint) {
        return new String(Character.toChars(codePoint));
    }

    /** Einrichtungsassistent schon gezeigt (W124). */
    static boolean setupDone(Context c) {
        return getBool(c, K_SETUP_DONE, false);
    }

    static void setSetupDone(Context c, boolean value) {
        put(c, K_SETUP_DONE, value);
    }

    /** Flächen als Glas mit Glanz und Lichtrand zeichnen (Nachbildung von Liquid Glass). */
    static boolean liquidGlass(Context c) {
        return getBool(c, K_LIQUID_GLASS, false);
    }

    static void setLiquidGlass(Context c, boolean value) {
        put(c, K_LIQUID_GLASS, value);
    }

    /** Deckkraft aller Glasflächen, 0..100; der Standard entspricht dem bisherigen Aussehen. */
    static int glassOpacity(Context c) {
        return Math.max(0, Math.min(100, getInt(c, K_GLASS_OPACITY, LiquidGlass.OPACITY_DEFAULT)));
    }

    static void setGlassOpacity(Context c, int value) {
        put(c, K_GLASS_OPACITY, Math.max(0, Math.min(100, value)));
    }

    // ---- Updates (Settings > General > Updates)
    private static final String K_UPD_INTERVAL = "update_interval";
    private static final String K_UPD_NOTIFY = "update_notify";
    private static final String K_UPD_LAST = "update_last_check";
    private static final String K_UPD_VERSION = "update_version";
    private static final String K_UPD_URL = "update_url";
    private static final String K_UPD_RELEASES = "update_releases";

    /** 0 Never, 1 Daily, 2 Weekly, 3 Monthly. */
    static int updateInterval(Context c) {
        return getInt(c, K_UPD_INTERVAL, 0);
    }

    static void setUpdateInterval(Context c, int value) {
        put(c, K_UPD_INTERVAL, value);
    }

    static boolean updateNotify(Context c) {
        return getBool(c, K_UPD_NOTIFY, true);
    }

    static void setUpdateNotify(Context c, boolean value) {
        put(c, K_UPD_NOTIFY, value);
    }

    static long updateLastCheck(Context c) {
        return sp(c).getLong(K_UPD_LAST, 0L);
    }

    static void setUpdateLastCheck(Context c, long value) {
        sp(c).edit().putLong(K_UPD_LAST, value).apply();
    }

    /** Newest release found by the last check ("" if none). */
    static String updateVersion(Context c) {
        return sp(c).getString(K_UPD_VERSION, "");
    }

    static void setUpdateVersion(Context c, String value) {
        sp(c).edit().putString(K_UPD_VERSION, value).apply();
    }

    static String updateUrl(Context c) {
        return sp(c).getString(K_UPD_URL, "");
    }

    static void setUpdateUrl(Context c, String value) {
        sp(c).edit().putString(K_UPD_URL, value).apply();
    }

    /** JSON list of the latest releases (tag, notes, apk) for the changelog. */
    static String updateReleases(Context c) {
        return sp(c).getString(K_UPD_RELEASES, "[]");
    }

    static void setUpdateReleases(Context c, String value) {
        sp(c).edit().putString(K_UPD_RELEASES, value).apply();
    }
}
