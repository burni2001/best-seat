package tv.bestseat.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import java.io.File;
import java.io.FileOutputStream;

/**
 * Eigene Kachelbilder des Nutzers (W126): <Paket>.png im internen Ordner tile-images.
 * Ersetzt das Banner der App auf der Kachel. Nie auf den USB-Stick.
 */
final class TileImages {

    private static final String DIR = "tile-images";
    /** Größte gespeicherte Breite; Kacheln sind deutlich kleiner. */
    private static final int MAX_WIDTH = 960;

    private TileImages() {}

    static File file(Context context, String pkg) {
        return new File(new File(context.getFilesDir(), DIR), pkg + ".png");
    }

    static boolean has(Context context, String pkg) {
        return pkg != null && file(context, pkg).exists();
    }

    /** Eigenes Bild als Drawable, sonst null. */
    static Drawable load(Context context, String pkg) {
        if (!has(context, pkg)) return null;
        Bitmap bmp = BitmapFactory.decodeFile(file(context, pkg).getPath());
        return bmp == null ? null : new BitmapDrawable(context.getResources(), bmp);
    }

    /** Bilddaten (PNG, JPEG, WebP …) prüfen, verkleinern und speichern. false, wenn es kein Bild ist. */
    static boolean save(Context context, String pkg, byte[] data) {
        if (data == null || data.length == 0) return false;
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false;
        int sample = 1;
        while (bounds.outWidth / (sample * 2) >= MAX_WIDTH) sample *= 2;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inSampleSize = sample;
        Bitmap bmp = BitmapFactory.decodeByteArray(data, 0, data.length, o);
        if (bmp == null) return false;
        if (bmp.getWidth() > MAX_WIDTH) {
            int h = Math.max(1, Math.round(bmp.getHeight() * (MAX_WIDTH / (float) bmp.getWidth())));
            bmp = Bitmap.createScaledBitmap(bmp, MAX_WIDTH, h, true);
        }
        File f = file(context, pkg);
        f.getParentFile().mkdirs();
        try (FileOutputStream out = new FileOutputStream(f)) {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
            return true;
        } catch (Exception e) {
            f.delete();
            return false;
        }
    }

    static void remove(Context context, String pkg) {
        file(context, pkg).delete();
    }
}
