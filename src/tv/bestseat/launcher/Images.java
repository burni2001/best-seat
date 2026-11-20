package tv.bestseat.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Lädt Bilder für den Top Shelf (http(s), content://, android.resource://), verkleinert
 * auf die Zielbreite, Zwischenspeicher im RAM (30 Bilder) und auf dem internen Speicher
 * (cache/shelf, höchstens 300 Dateien). Nie auf den USB-Stick.
 */
final class Images {

    interface Callback {
        void onImage(Bitmap bitmap);
    }

    private static final String TAG = "Launcher.Images";
    private static final int MAX_DISK_FILES = 300;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService POOL = Executors.newFixedThreadPool(3);
    // Nach Anzahl begrenzt: Eine LruCache-Unterklasse mit sizeOf() bringt d8 (build-tools 34) zum Absturz.
    private static final LruCache<String, Bitmap> MEMORY = new LruCache<>(30);

    private Images() {}

    static Bitmap memory(String uri) {
        return uri == null ? null : MEMORY.get(uri);
    }

    /** Bild in die ImageView laden; ein späterer Aufruf für dieselbe View gewinnt. */
    static void into(Context context, String uri, int maxWidth, ImageView view) {
        view.setTag(uri);
        load(context, uri, maxWidth, bmp -> {
            if (uri.equals(view.getTag()) && bmp != null) {
                view.setImageBitmap(bmp);
                view.setAlpha(0f);
                view.animate().alpha(1f).setDuration(Ui.ms(180)).start();
            }
        });
    }

    static void load(Context context, String uri, int maxWidth, Callback callback) {
        if (uri == null) {
            callback.onImage(null);
            return;
        }
        Bitmap hit = MEMORY.get(uri);
        if (hit != null) {
            callback.onImage(hit);
            return;
        }
        final Context app = context.getApplicationContext();
        POOL.execute(() -> {
            Bitmap bmp = null;
            try {
                byte[] data = fetch(app, uri);
                if (data != null) bmp = decode(data, maxWidth);
            } catch (Exception e) {
                Log.w(TAG, "image failed: " + uri + " " + e);
            }
            if (bmp != null) MEMORY.put(uri, bmp);
            final Bitmap result = bmp;
            MAIN.post(() -> callback.onImage(result));
        });
    }

    private static byte[] fetch(Context context, String uri) throws Exception {
        boolean web = uri.startsWith("http://") || uri.startsWith("https://");
        File dir = new File(context.getCacheDir(), "shelf");
        File file = new File(dir, Integer.toHexString(uri.hashCode()) + "_" + uri.length());
        if (web && file.exists()) {
            file.setLastModified(System.currentTimeMillis());
            return readAll(new java.io.FileInputStream(file));
        }
        byte[] data;
        if (web) {
            HttpURLConnection con = (HttpURLConnection) new URL(uri).openConnection();
            con.setConnectTimeout(6000);
            con.setReadTimeout(10000);
            con.setInstanceFollowRedirects(true);
            try {
                if (con.getResponseCode() / 100 != 2) return null;
                data = readAll(con.getInputStream());
            } finally {
                con.disconnect();
            }
            dir.mkdirs();
            try (FileOutputStream out = new FileOutputStream(file)) {
                out.write(data);
            }
            trim(dir);
        } else {
            data = readAll(context.getContentResolver().openInputStream(Uri.parse(uri)));
        }
        return data;
    }

    private static byte[] readAll(InputStream in) throws Exception {
        if (in == null) return null;
        try (InputStream s = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = s.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }

    private static Bitmap decode(byte[] data, int maxWidth) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, o);
        int sample = 1;
        while (o.outWidth / (sample * 2) >= maxWidth) sample *= 2;
        BitmapFactory.Options d = new BitmapFactory.Options();
        d.inSampleSize = sample;
        d.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeByteArray(data, 0, data.length, d);
    }

    private static void trim(File dir) {
        File[] files = dir.listFiles();
        if (files == null || files.length <= MAX_DISK_FILES) return;
        Arrays.sort(files, Comparator.comparingLong(File::lastModified));
        for (int i = 0; i < files.length - MAX_DISK_FILES; i++) {
            files[i].delete();
        }
    }
}
