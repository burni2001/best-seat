package tv.bestseat.launcher;

import android.graphics.Bitmap;

/**
 * Verkleinertes, weichgezeichnetes Abbild einer Ansicht (Hintergrund des Kontrollzentrums, W101).
 * Die Startseite zeichnet sich selbst, ohne adb und ohne Fensterunschärfe des Systems; das Ergebnis ist
 * klein (192×108) und wird beim Anzeigen hochskaliert, das glättet zusätzlich.
 */
public final class Snapshot {

    private static final int BLUR_RADIUS = 3;
    private static final int BLUR_PASSES = 3;

    private Snapshot() {}

    /** Kleines Bild in sich weichzeichnen (3 Kastenfilter-Durchgänge, wirkt fast gaußsch). */
    static Bitmap blur(Bitmap bmp) {
        try {
            int w = bmp.getWidth(), h = bmp.getHeight();
            int[] px = new int[w * h];
            bmp.getPixels(px, 0, w, 0, 0, w, h);
            int[] r = new int[px.length], g = new int[px.length], b = new int[px.length];
            for (int i = 0; i < px.length; i++) {
                r[i] = (px[i] >> 16) & 0xff;
                g[i] = (px[i] >> 8) & 0xff;
                b[i] = px[i] & 0xff;
            }
            for (int pass = 0; pass < BLUR_PASSES; pass++) {
                boxBlur(r, w, h, BLUR_RADIUS);
                boxBlur(g, w, h, BLUR_RADIUS);
                boxBlur(b, w, h, BLUR_RADIUS);
            }
            for (int i = 0; i < px.length; i++) px[i] = 0xff000000 | (r[i] << 16) | (g[i] << 8) | b[i];
            bmp.setPixels(px, 0, w, 0, 0, w, h);
            return bmp;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * Ein Kanal, waagrecht und senkrecht über radius Pixel gemittelt, am Rand wird der Randpixel
     * wiederholt. Auch für den Hintergrund des App Switchers und von All Apps (Backdrop).
     */
    public static void boxBlur(int[] ch, int w, int h, int radius) {
        int[] tmp = new int[ch.length];
        int span = radius * 2 + 1;
        for (int y = 0; y < h; y++) {
            int row = y * w;
            for (int x = 0; x < w; x++) {
                int sum = 0;
                for (int k = -radius; k <= radius; k++) {
                    sum += ch[row + Math.max(0, Math.min(w - 1, x + k))];
                }
                tmp[row + x] = sum / span;
            }
        }
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                int sum = 0;
                for (int k = -radius; k <= radius; k++) {
                    sum += tmp[Math.max(0, Math.min(h - 1, y + k)) * w + x];
                }
                ch[y * w + x] = sum / span;
            }
        }
    }
}
