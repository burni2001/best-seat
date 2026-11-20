package tv.bestseat.launcher;

import android.content.Context;
import android.content.SharedPreferences;

/** Einstellungen der Oberfläche. */
final class Prefs {

    static final int[] COLUMN_CHOICES = {5, 6, 7, 8};

    static final int SAVER_AERIALS = 0;
    static final int SAVER_PHOTOS = 1;
    static final int SAVER_CLOCK = 2;
    static final String[] SAVER_NAMES = {"Aerial videos", "Online photos", "Clock"};
    /** Minuten bis zum Schoner, 0 = aus. */
    static final int[] SAVER_DELAYS = {1, 2, 3, 4, 5, 0};

    private Prefs() {}

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("settings", Context.MODE_PRIVATE);
    }

    static int columns(Context c) {
        return sp(c).getInt("columns", 6);
    }

    static void setColumns(Context c, int value) {
        sp(c).edit().putInt("columns", value).apply();
    }

    static boolean nonTvApps(Context c) {
        return sp(c).getBoolean("non_tv_apps", false);
    }

    static void setNonTvApps(Context c, boolean value) {
        sp(c).edit().putBoolean("non_tv_apps", value).apply();
    }

    static boolean showVpn(Context c) {
        return sp(c).getBoolean("show_vpn", true);
    }

    static void setShowVpn(Context c, boolean value) {
        sp(c).edit().putBoolean("show_vpn", value).apply();
    }

    static int saverSource(Context c) {
        return sp(c).getInt("saver_source", SAVER_AERIALS);
    }

    static void setSaverSource(Context c, int value) {
        sp(c).edit().putInt("saver_source", value).apply();
    }

    static int saverTopic(Context c) {
        return sp(c).getInt("saver_topic", 0);
    }

    static void setSaverTopic(Context c, int value) {
        sp(c).edit().putInt("saver_topic", value).apply();
    }

    static int saverDelay(Context c) {
        return sp(c).getInt("saver_delay", 5);
    }

    static void setSaverDelay(Context c, int value) {
        sp(c).edit().putInt("saver_delay", value).apply();
    }

    static boolean saverClock(Context c) {
        return sp(c).getBoolean("saver_clock", true);
    }

    static void setSaverClock(Context c, boolean value) {
        sp(c).edit().putBoolean("saver_clock", value).apply();
    }

    static boolean saverLocation(Context c) {
        return sp(c).getBoolean("saver_location", true);
    }

    static void setSaverLocation(Context c, boolean value) {
        sp(c).edit().putBoolean("saver_location", value).apply();
    }

    /** Lichtstreifen über der fokussierten Kachel/Karte. */
    static boolean shine(Context c) {
        return sp(c).getBoolean("shine", true);
    }

    /** Bewegung reduzieren (W83): Ein- und Auszoomen von Apps, Ordnern und Kontrollzentrum nur als Überblendung. */
    static boolean reduceMotion(Context c) {
        return sp(c).getBoolean("reduce_motion", false);
    }

    static void setReduceMotion(Context c, boolean value) {
        sp(c).edit().putBoolean("reduce_motion", value).apply();
    }

    static void setShine(Context c, boolean value) {
        sp(c).edit().putBoolean("shine", value).apply();
    }

    /** Oberste Reihe als Favoriten-Leiste; nur sie füllt den Top Shelf (W14/W17). */
    static boolean favorites(Context c) {
        return sp(c).getBoolean("favorites", true);
    }

    static void setFavorites(Context c, boolean value) {
        sp(c).edit().putBoolean("favorites", value).apply();
    }

    static final int BG_MATCHING = 0;
    static final int BG_BLURRED = 1;
    static final int BG_CONTENT = 2;
    static final String[] BACKGROUND_NAMES = {"Matching color", "Blurred content", "Content"};

    /** Hintergrund der ganzen Startseite: passende Farbe, unscharfer oder scharfer Inhalt. */
    static int background(Context c) {
        return Math.max(0, Math.min(BACKGROUND_NAMES.length - 1, sp(c).getInt("background", BG_BLURRED)));
    }

    static void setBackground(Context c, int value) {
        sp(c).edit().putInt("background", value).apply();
    }

    /** Ein Schalter für alle Weichzeichner-Effekte (Hintergrund, Ordner, Fenster, App Switcher). */
    static boolean blurEnabled(Context c) {
        return sp(c).getBoolean("blur_all", true);
    }

    static void setBlurEnabled(Context c, boolean value) {
        sp(c).edit().putBoolean("blur_all", value).apply();
    }

    /** Vollbild „USB-Speicher fehlt“ nach dem Aufwachen automatisch schließen (braucht adb-Freigabe). */
    static boolean hideUsbNotice(Context c) {
        return sp(c).getBoolean("hide_usb_notice", true);
    }

    static void setHideUsbNotice(Context c, boolean value) {
        sp(c).edit().putBoolean("hide_usb_notice", value).apply();
    }

    /** Uhrzeit in der Pille der Startseite (aus = nur das Profilbild). */
    static boolean showClock(Context c) {
        return sp(c).getBoolean("show_clock", true);
    }

    static void setShowClock(Context c, boolean value) {
        sp(c).edit().putBoolean("show_clock", value).apply();
    }

    /** Lichtstreifen über der fokussierten Top-Shelf-Karte (W13). */
    static boolean shineShelf(Context c) {
        return sp(c).getBoolean("shine_shelf", true);
    }

    static void setShineShelf(Context c, boolean value) {
        sp(c).edit().putBoolean("shine_shelf", value).apply();
    }

    /** Reihenfolge der Kontrollzentrum-Kacheln; fehlende IDs hinten anhängen, unbekannte weglassen. */
    static java.util.List<String> controlOrder(Context c) {
        java.util.List<String> out = new java.util.ArrayList<>();
        java.util.List<String> known = java.util.Arrays.asList(ControlItems.IDS);
        for (String id : ccGet(c, "cc_order").split(",")) {
            if (known.contains(id) && !out.contains(id)) out.add(id);
        }
        for (String id : ControlItems.IDS) {
            if (!out.contains(id)) out.add(id);
        }
        return out;
    }

    static void setControlOrder(Context c, java.util.List<String> order) {
        sp(c).edit().putString(ccKey(c, "cc_order"), String.join(",", order)).apply();
    }

    static java.util.Set<String> controlHidden(Context c) {
        java.util.Set<String> out = new java.util.HashSet<>();
        for (String id : ccGet(c, "cc_hidden").split(",")) {
            if (!id.isEmpty()) out.add(id);
        }
        return out;
    }

    static void setControlHidden(Context c, java.util.Set<String> hidden) {
        sp(c).edit().putString(ccKey(c, "cc_hidden"), String.join(",", hidden)).apply();
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
        sp(c).edit().putString(ccKey(c, "cc_order"), "").putString(ccKey(c, "cc_hidden"), "").apply();
    }

    // ---- W22: eingebaute Kacheln am Ende der Startseite

    static boolean settingsTile(Context c) {
        return sp(c).getBoolean("tile_settings", true);
    }

    static void setSettingsTile(Context c, boolean value) {
        sp(c).edit().putBoolean("tile_settings", value).apply();
    }

    static boolean hiddenTile(Context c) {
        return sp(c).getBoolean("tile_hidden", true);
    }

    static void setHiddenTile(Context c, boolean value) {
        sp(c).edit().putBoolean("tile_hidden", value).apply();
    }

    // ---- W23: Zeitzone der eigenen Uhren (der TV läuft auf UTC)

    static final String[] ZONE_IDS = {"Europe/Berlin", ""};
    static final String[] ZONE_NAMES = {"Germany", "TV time"};

    static int clockZone(Context c) {
        int i = sp(c).getInt("clock_zone", 0);
        return i >= 0 && i < ZONE_IDS.length ? i : 0;
    }

    static void setClockZone(Context c, int index) {
        sp(c).edit().putInt("clock_zone", index).apply();
    }

    /** Uhr auf die gewählte Zeitzone stellen; leer = Zeitzone des TVs. */
    static void applyZone(Context c, android.widget.TextClock clock) {
        String id = ZONE_IDS[clockZone(c)];
        clock.setTimeZone(id.isEmpty() ? null : id);
    }

    // ---- Erscheinungsbild: Dunkel, Hell oder Automatisch (tagsüber hell)

    static final String[] APPEARANCE_NAMES = {"Dark", "Light", "Automatic"};

    static int appearance(Context c) {
        int i = sp(c).getInt("appearance", 0);
        return i >= 0 && i < APPEARANCE_NAMES.length ? i : 0;
    }

    static void setAppearance(Context c, int index) {
        sp(c).edit().putInt("appearance", index).apply();
    }

    /** Hell bei „Light“, bei „Automatic“ von 7 bis 19 Uhr (Zeitzone wie die Uhren). */
    static boolean isLight(Context c) {
        int mode = appearance(c);
        if (mode != 2) return mode == 1;
        String id = ZONE_IDS[clockZone(c)];
        java.util.Calendar now = java.util.Calendar.getInstance(id.isEmpty()
                ? java.util.TimeZone.getDefault() : java.util.TimeZone.getTimeZone(id));
        int hour = now.get(java.util.Calendar.HOUR_OF_DAY);
        return hour >= 7 && hour < 19;
    }

    // ---- Schrift (W115): Roboto oder eine mitgelieferte freie Schrift (Fonts.NAMES)

    static int font(Context c) {
        int i = sp(c).getInt("font", 0);
        return i >= 0 && i < Fonts.NAMES.length ? i : 0;
    }

    static void setFont(Context c, int index) {
        sp(c).edit().putInt("font", index).apply();
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

    static int userAvatar(Context c) {
        int i = Profiles.current(c).avatar;
        return i >= 0 && i < AVATARS.length ? i : 0;
    }

    static void setUserAvatar(Context c, int index) {
        Profiles.update(c, Profiles.currentId(c), null, index);
    }
}
