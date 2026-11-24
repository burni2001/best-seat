package tv.bestseat.launcher;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.DisplayMetrics;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;

/**
 * Banner für den großen Top Shelf der Startseite: eigene HD-Banner des Nutzers, sonst das größte Banner,
 * das die App mitliefert; dazu die Randfarbe einfarbiger Banner und der Export per Intent.
 */
final class HomeBanners {

    private static final String TAG = "Launcher.Home";
    /** Eigene, hochskalierte Banner: <Paketname>.png in diesem Ordner ersetzt das Banner der App im Top Shelf. */
    static final String HD_BANNER_DIR = "banners-hd";

    private final Context context;
    private final Map<String, Drawable> heroBanners = new HashMap<>();
    private final Set<Drawable> hdDrawables = Collections.newSetFromMap(new IdentityHashMap<>());

    HomeBanners(Context context) {
        this.context = context;
    }

    /** Zwischenspeicher leeren (Refresh von Hand). */
    void clear() {
        heroBanners.clear();
    }

    /** true: eigenes HD-Banner des Nutzers. */
    boolean isHd(Drawable d) {
        return hdDrawables.contains(d);
    }

    /**
     * Banner für den großen Top Shelf in der höchsten Auflösung, die die App mitliefert (xxxhdpi),
     * statt der Kachelgröße (320×180 px), die auf 846×476 px hochgezogen unscharf wirkt.
     */
    Drawable hero(Tile tile) {
        if (tile.pkg == null) return tile.banner;
        Drawable cached = heroBanners.get(tile.key);
        if (cached != null) return cached;
        Drawable hd = hdBanner(tile.pkg);
        if (hd != null) {
            heroBanners.put(tile.key, hd);
            hdDrawables.add(hd);
            return hd;
        }
        Drawable best = largest(context, tile);
        heroBanners.put(tile.key, best);
        return best;
    }

    /** Hochskaliertes Banner des Nutzers, falls vorhanden (banners-hd/<Paket>.png im App-Ordner). */
    private Drawable hdBanner(String pkg) {
        try {
            File dir = context.getExternalFilesDir(HD_BANNER_DIR);
            if (dir == null) return null;
            File f = new File(dir, pkg + ".png");
            if (!f.isFile()) return null;
            Bitmap b = BitmapFactory.decodeFile(f.getPath());
            if (b == null) return null;
            BitmapDrawable d = new BitmapDrawable(context.getResources(), b);
            // Pixelgröße als Eigengröße (nicht nach Dichte umrechnen): steuert die Anzeigebreite.
            d.setTargetDensity(context.getResources().getDisplayMetrics().densityDpi);
            return d;
        } catch (RuntimeException | OutOfMemoryError e) {
            Log.w(TAG, "hd banner failed: " + pkg, e);
            return null;
        }
    }

    /** Größtes Banner der App aus ihren Ressourcen (höchste Dichte), ohne Nutzer-Ersatz. */
    static Drawable largest(Context c, Tile tile) {
        Drawable best = tile.banner;
        try {
            PackageManager pm = c.getPackageManager();
            int res = 0;
            if (tile.component != null) res = pm.getActivityInfo(tile.component, 0).banner;
            ApplicationInfo ai = pm.getApplicationInfo(tile.pkg, 0);
            if (res == 0) res = ai.banner;
            if (res != 0) {
                Drawable d = pm.getResourcesForApplication(ai).getDrawableForDensity(res,
                        DisplayMetrics.DENSITY_XXXHIGH, null);
                if (d != null && d.getIntrinsicWidth() > (best != null ? best.getIntrinsicWidth() : 0)) best = d;
            }
        } catch (Exception ignored) {
        }
        return best;
    }

    /**
     * Randfarbe eines kleinen Banners (unter 600 px breit), wenn alle vier Ecken fast gleich sind,
     * sonst 0 (dann wird das Banner wie bisher auf die Karte gezogen).
     */
    int flatEdgeColor(Drawable d) {
        if (!(d instanceof BitmapDrawable)) return 0;
        Bitmap b = ((BitmapDrawable) d).getBitmap();
        // Eigene HD-Banner dürfen groß sein, sonst nur kleine Banner (große füllen die Karte).
        if (b == null || (b.getWidth() >= 600 && !hdDrawables.contains(d))) return 0;
        int w = b.getWidth() - 3;
        int h = b.getHeight() - 3;
        int[] c = {b.getPixel(2, 2), b.getPixel(w, 2), b.getPixel(2, h), b.getPixel(w, h)};
        for (int x : c) {
            if (Color.alpha(x) < 250) return 0;
            if (Math.abs(Color.red(x) - Color.red(c[0])) + Math.abs(Color.green(x) - Color.green(c[0]))
                    + Math.abs(Color.blue(x) - Color.blue(c[0])) > 30) return 0;
        }
        return c[0];
    }

    /**
     * Exportiert für alle Apps ohne Top-Shelf-Inhalte das größte Banner als PNG in
     * banner-export/ (App-Ordner im internen Speicher) samt Liste; per adb abholen.
     */
    void export(List<Tile> allTiles, ExecutorService worker) {
        final List<Tile> tiles = new ArrayList<>();
        for (Tile t : allTiles) {
            if (t.members != null) tiles.addAll(t.members);
            else tiles.add(t);
        }
        final Context app = context.getApplicationContext();
        worker.execute(() -> {
            File dir = app.getExternalFilesDir("banner-export");
            if (dir == null) return;
            File[] old = dir.listFiles();
            if (old != null) for (File f : old) f.delete();
            StringBuilder list = new StringBuilder("package\tname\twidth\theight\tflat\n");
            int n = 0;
            for (Tile t : tiles) {
                if (t.kind != Tile.APP || t.pkg == null) continue;
                List<ShelfContent.Item> items = ShelfContent.load(app, t.pkg);
                if (items != null && !items.isEmpty()) continue;
                Drawable d = largest(context, t);
                if (d == null || d.getIntrinsicWidth() <= 0 || d.getIntrinsicHeight() <= 0) continue;
                try {
                    Bitmap b;
                    if (d instanceof BitmapDrawable && ((BitmapDrawable) d).getBitmap() != null) {
                        // Originalpixel, nicht die auf den Bildschirm umgerechnete Größe.
                        b = ((BitmapDrawable) d).getBitmap();
                    } else {
                        int dw = d.getIntrinsicWidth() * 2, dh = d.getIntrinsicHeight() * 2;
                        b = Bitmap.createBitmap(dw, dh, Bitmap.Config.ARGB_8888);
                        Canvas c = new Canvas(b);
                        Rect old2 = new Rect(d.getBounds());
                        d.setBounds(0, 0, dw, dh);
                        d.draw(c);
                        d.setBounds(old2);
                    }
                    int w = b.getWidth(), h = b.getHeight();
                    try (FileOutputStream out = new FileOutputStream(new File(dir, t.pkg + ".png"))) {
                        b.compress(Bitmap.CompressFormat.PNG, 100, out);
                    }
                    boolean flat = flatEdgeColor(d) != 0;
                    list.append(t.pkg).append('\t').append(t.label).append('\t').append(w).append('\t')
                            .append(h).append('\t').append(flat ? "yes" : "no").append('\n');
                    n++;
                } catch (Exception | OutOfMemoryError e) {
                    Log.w(TAG, "export failed: " + t.pkg, e);
                }
            }
            try (FileOutputStream out = new FileOutputStream(new File(dir, "list.tsv"))) {
                out.write(list.toString().getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                Log.w(TAG, "export list failed", e);
            }
            Log.i(TAG, "Exported " + n + " banners to " + dir);
        });
    }
}
