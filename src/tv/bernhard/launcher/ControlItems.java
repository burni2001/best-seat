package tv.bernhard.launcher;

/** Kacheln des Kontrollzentrums: feste IDs, Reihenfolge und Sichtbarkeit stehen in den Prefs (W12). */
final class ControlItems {
    static final String SCREENSAVER = "screensaver";
    static final String AMBILIGHT = "ambilight";
    static final String HUE_SYNC = "huesync";
    static final String INPUTS = "inputs";
    static final String PICTURE = "picture";
    static final String SOUND = "sound";
    static final String APP_SWITCHER = "appswitcher";
    static final String VPN = "vpn";
    static final String SETTINGS = "settings";
    static final String HOME = "home";
    static final String RESTART = "restart";
    static final String ALL_APPS = "allapps";

    static final String[] IDS = {SCREENSAVER, AMBILIGHT, HUE_SYNC, INPUTS, PICTURE, SOUND, APP_SWITCHER, VPN,
            SETTINGS, HOME, RESTART, ALL_APPS};

    private ControlItems() {
    }

    static String title(String id) {
        switch (id) {
            case SCREENSAVER: return "Screensaver";
            case AMBILIGHT: return "Ambilight";
            case HUE_SYNC: return "Hue Sync";
            case INPUTS: return "Inputs";
            case PICTURE: return "Picture";
            case SOUND: return "Sound";
            case APP_SWITCHER: return "App Switcher";
            case VPN: return "VPN";
            case SETTINGS: return "TV Settings";
            case HOME: return "Settings";
            case RESTART: return "Restart";
            case ALL_APPS: return "All Apps";
            default: return id;
        }
    }
}
