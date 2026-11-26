package tv.bestseat.launcher;

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
    static final String QUICK = "quicksettings"; // TCL only

    static final String[] IDS = {SCREENSAVER, AMBILIGHT, HUE_SYNC, INPUTS, PICTURE, SOUND, APP_SWITCHER, VPN,
            SETTINGS, HOME, RESTART, ALL_APPS, QUICK};

    private ControlItems() {
    }

    static String title(String id) {
        switch (id) {
            case SCREENSAVER: return "Screensaver";
            case AMBILIGHT: return "Ambilight";
            case HUE_SYNC: return "Ambience";
            case INPUTS: return "Sources";
            case PICTURE: return "Picture";
            case SOUND: return "Sound";
            case APP_SWITCHER: return "App Switcher";
            case VPN: return "VPN";
            case SETTINGS: return "TV Settings";
            case HOME: return "Settings";
            case RESTART: return "Restart";
            case ALL_APPS: return "All Apps";
            case QUICK: return "Quick Settings";
            default: return id;
        }
    }

    static final String TCL_SETTINGS = "com.android.tv.settings";
    static final String TCL_SHOW_WINDOW = "com.tcl.settings.SHOW_WINDOW";

    /** False for tiles whose target does not exist on this TV (Philips-only tiles, TCL-only tile). */
    static boolean available(android.content.Context c, String id) {
        switch (id) {
            case RESTART:
                return installed(c, "org.droidtv.tvsystemui");
            case AMBILIGHT:
            case HUE_SYNC:
                return installed(c, Companions.HUESYNC);
            case QUICK:
                return tclMenu(c);
            default:
                return true;
        }
    }

    static boolean installed(android.content.Context c, String pkg) {
        try {
            c.getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (android.content.pm.PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /** The TCL service that opens the picture, sound and quick menus exists. */
    static boolean tclMenu(android.content.Context c) {
        return c.getPackageManager().resolveService(
                new android.content.Intent(TCL_SHOW_WINDOW).setPackage(TCL_SETTINGS), 0) != null;
    }
}
