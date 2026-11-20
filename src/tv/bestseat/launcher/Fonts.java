package tv.bestseat.launcher;

import android.content.Context;
import android.graphics.Typeface;
import android.util.Log;
import android.util.SparseArray;

/**
 * Schrift des Launchers: Roboto (System) oder eine mitgelieferte freie Schrift (SIL Open Font License,
 * Lizenztexte in docs/fonts). Die mitgelieferten sind variable Schriften; die Stärke kommt über die
 * Achse wght, deshalb überall Fonts.get(context, Stärke) statt Typeface.create.
 */
public final class Fonts {

    static final String[] NAMES = {"Roboto", "Inter", "Manrope", "Plus Jakarta Sans", "Outfit", "Urbanist"};
    private static final String[] FILES = {null, "fonts/Inter.ttf", "fonts/Manrope.ttf",
            "fonts/PlusJakartaSans.ttf", "fonts/Outfit.ttf", "fonts/Urbanist.ttf"};

    public static final int THIN = 100;
    public static final int LIGHT = 300;
    public static final int REGULAR = 400;
    public static final int MEDIUM = 500;
    public static final int BOLD = 700;

    private static final int[] BOOST = {0, 0, 100, 100, 0, 100};

    private static final SparseArray<Typeface> CACHE = new SparseArray<>();

    private Fonts() {
    }

    /** Schrift in der gewählten Familie und Stärke (100 bis 900). */
    public static Typeface get(Context context, int weight) {
        return get(context, Prefs.font(context), weight);
    }

    /** Schrift einer bestimmten Familie (Index in NAMES), z. B. für die Vorschau in der Auswahl. */
    static Typeface get(Context context, int font, int weight) {
        // Manrope, Plus Jakarta Sans und Urbanist zeichnen dieselbe Stärke dünner: eine Stufe kräftiger,
        // damit alle auf Distanz ähnlich wirken.
        weight = Math.min(900, weight + BOOST[font]);
        int key = font * 1000 + weight;
        Typeface cached = CACHE.get(key);
        if (cached != null) return cached;
        Typeface t = null;
        if (FILES[font] != null) {
            try {
                t = new Typeface.Builder(context.getAssets(), FILES[font])
                        .setFontVariationSettings("'wght' " + weight)
                        .build();
            } catch (RuntimeException e) {
                Log.w("Launcher.Fonts", "font failed: " + FILES[font], e);
            }
        }
        if (t == null) t = roboto(weight);
        CACHE.put(key, t);
        return t;
    }

    private static Typeface roboto(int weight) {
        if (weight <= 200) return Typeface.create("sans-serif-thin", Typeface.NORMAL);
        if (weight <= 300) return Typeface.create("sans-serif-light", Typeface.NORMAL);
        if (weight <= 400) return Typeface.create("sans-serif", Typeface.NORMAL);
        if (weight <= 600) return Typeface.create("sans-serif-medium", Typeface.NORMAL);
        return Typeface.create("sans-serif", Typeface.BOLD);
    }
}
