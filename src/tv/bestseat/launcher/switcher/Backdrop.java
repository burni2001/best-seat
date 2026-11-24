package tv.bestseat.launcher.switcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;

import tv.bestseat.launcher.Snapshot;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Hintergründe von All Apps (Einstellung „Background“ im Menü):
 * Petrol: farblich nachgemachtes Milchglas wie ein geöffneter Ordner bei AT4K, sofort da.
 * Blurred: echtes Milchglas (auch hinter der Leiste des App Switchers, Schalter „Blur background“). Der TV kann Fenster dahinter nicht weichzeichnen
 * (Fenster-Blur ist im System aus), deshalb per adb ein Bildschirmfoto, selbst
 * weichgezeichnet und abgedunkelt. Kostet beim Öffnen rund 0,7 s (am 16.11. gemessen).
 */
public final class Backdrop {

    // ---- Petrol: Verlauf und Farbflecken, am 16.11. innen im AT4K-Ordner „Gaming“ gemessen.

    /** Oben fast schwarz, unten dunkles Petrol. */
    private static final int TOP = Color.rgb(12, 16, 18);
    private static final int BOTTOM = Color.rgb(16, 30, 36);

    /** Farbflecken: Farbe, Deckkraft, Mitte (Anteil von Breite und Höhe), Radius (Anteil der Breite). */
    private static final float[][] GLOWS = {
            {120, 50, 160, 0.13f, 0.20f, 0.45f, 0.28f}, // lila links
            {120, 130, 145, 0.12f, 0.72f, 0.48f, 0.25f}, // graublau rechts
            {40, 110, 130, 0.14f, 0.12f, 0.92f, 0.32f}, // petrol unten links
            {150, 90, 40, 0.07f, 0.48f, 0.12f, 0.26f}, // warm oben
            {60, 90, 150, 0.08f, 0.90f, 0.10f, 0.22f}, // blau oben rechts
    };

    // ---- Blurred: Bildschirmfoto.

    /** Verkleinerung des Bildschirmfotos, mittelt zugleich je 8×8 Pixel. */
    private static final int SCALE = 8;
    /** Radius des Weichzeichners im verkleinerten Bild (mal 8 = Pixel am TV). */
    private static final int BLUR_RADIUS = 4;
    private static final int BLUR_PASSES = 3;
    /** Helligkeit nach dem Abdunkeln, innen im AT4K-Ordner gemessen (ca. 37 %). */
    private static final float BRIGHTNESS = 0.40f;

    private Backdrop() {}

    /** Petrol-Verlauf mit schwachen, verwischten Farbflecken. */
    public static Drawable petrol(int screenWidth) {
        Drawable[] layers = new Drawable[GLOWS.length + 1];
        layers[0] = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[] {TOP, BOTTOM});
        for (int i = 0; i < GLOWS.length; i++) {
            float[] glow = GLOWS[i];
            int color = Color.argb(Math.round(glow[3] * 255),
                    Math.round(glow[0]), Math.round(glow[1]), Math.round(glow[2]));
            GradientDrawable spot = new GradientDrawable();
            spot.setGradientType(GradientDrawable.RADIAL_GRADIENT);
            spot.setColors(new int[] {color, Color.argb(0, Color.red(color), Color.green(color), Color.blue(color))});
            spot.setGradientCenter(glow[4], glow[5]);
            spot.setGradientRadius(glow[6] * screenWidth);
            layers[i + 1] = spot;
        }
        return new LayerDrawable(layers);
    }

    /** Bildschirmfoto holen und als kleines, unscharfes, dunkles Bild liefern. */
    public static Bitmap capture(Context context) throws Exception {
        return captureBoth(context, false)[1];
    }

    /**
     * Bildschirmfoto holen: [0] scharf in halber Größe (nur mit sharp, sonst null),
     * [1] klein, unscharf und dunkel.
     */
    public static Bitmap[] captureBoth(Context context, boolean sharp) throws Exception {
        byte[] raw;
        AdbClient.requireGranted(context);
        try (AdbSession adb = AdbSession.open(context, 3000)) {
            raw = adb.exec("screencap");
        }
        ByteBuffer header = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
        int width = header.getInt();
        int height = header.getInt();
        int format = header.getInt();
        // Format 1 = RGBA_8888. Der Kopf ist je nach Android-Version 12 oder 16 Byte lang.
        long pixelBytes = (long) width * height * 4;
        int offset = (int) (raw.length - pixelBytes);
        if (format != 1 || width <= 0 || height <= 0 || offset < 12 || offset > 16) {
            throw new IllegalStateException("Unexpected screencap " + width + "x" + height
                    + " format " + format + " length " + raw.length);
        }

        Bitmap clear = null;
        if (sharp) {
            int hw = width / 2;
            int hh = height / 2;
            int[] px = new int[hw * hh];
            for (int y = 0; y < hh; y++) {
                int p = offset + (y * 2 * width) * 4;
                for (int x = 0; x < hw; x++, p += 8) {
                    px[y * hw + x] = 0xff000000 | ((raw[p] & 0xff) << 16) | ((raw[p + 1] & 0xff) << 8)
                            | (raw[p + 2] & 0xff);
                }
            }
            clear = Bitmap.createBitmap(px, hw, hh, Bitmap.Config.ARGB_8888);
        }

        int w = width / SCALE;
        int h = height / SCALE;
        int[] r = new int[w * h];
        int[] g = new int[w * h];
        int[] b = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int sr = 0, sg = 0, sb = 0;
                for (int dy = 0; dy < SCALE; dy++) {
                    int p = offset + (((y * SCALE + dy) * width) + x * SCALE) * 4;
                    for (int dx = 0; dx < SCALE; dx++, p += 4) {
                        sr += raw[p] & 0xff;
                        sg += raw[p + 1] & 0xff;
                        sb += raw[p + 2] & 0xff;
                    }
                }
                int i = y * w + x;
                r[i] = sr / (SCALE * SCALE);
                g[i] = sg / (SCALE * SCALE);
                b[i] = sb / (SCALE * SCALE);
            }
        }
        // Mehrfacher Kastenfilter ergibt einen weichen, fast gaußschen Verlauf.
        for (int pass = 0; pass < BLUR_PASSES; pass++) {
            Snapshot.boxBlur(r, w, h, BLUR_RADIUS);
            Snapshot.boxBlur(g, w, h, BLUR_RADIUS);
            Snapshot.boxBlur(b, w, h, BLUR_RADIUS);
        }

        int[] pixels = new int[w * h];
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = 0xff000000
                    | (Math.round(r[i] * BRIGHTNESS) << 16)
                    | (Math.round(g[i] * BRIGHTNESS) << 8)
                    | Math.round(b[i] * BRIGHTNESS);
        }
        return new Bitmap[] {clear, Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)};
    }
}
