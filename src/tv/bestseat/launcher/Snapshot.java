package tv.bestseat.launcher;

import android.graphics.Bitmap;

/**
 * Verkleinertes, weichgezeichnetes Abbild einer Ansicht (Hintergrund des Kontrollzentrums, W101).
 * Die Startseite zeichnet sich selbst, ohne adb und ohne Fensterunschärfe des Systems; das Ergebnis ist
 * klein (192×108) und wird beim Anzeigen hochskaliert, das glättet zusätzlich.
 */
final class Snapshot {

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
                box(r, w, h);
                box(g, w, h);
                box(b, w, h);
            }
            for (int i = 0; i < px.length; i++) px[i] = 0xff000000 | (r[i] << 16) | (g[i] << 8) | b[i];
            bmp.setPixels(px, 0, w, 0, 0, w, h);
            return bmp;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Waagrecht und senkrecht mitteln, am Rand wird der Randpixel wiederholt. */
    private static void box(int[] ch, int w, int h) {
        int[] tmp = new int[ch.length];
        int span = BLUR_RADIUS * 2 + 1;
        for (int y = 0; y < h; y++) {
            int row = y * w;
            for (int x = 0; x < w; x++) {
                int sum = 0;
                for (int k = -BLUR_RADIUS; k <= BLUR_RADIUS; k++) {
                    sum += ch[row + Math.max(0, Math.min(w - 1, x + k))];
                }
                tmp[row + x] = sum / span;
            }
        }
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                int sum = 0;
                for (int k = -BLUR_RADIUS; k <= BLUR_RADIUS; k++) {
                    sum += tmp[Math.max(0, Math.min(h - 1, y + k)) * w + x];
                }
                ch[y * w + x] = sum / span;
            }
        }
    }
}
