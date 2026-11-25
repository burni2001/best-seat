package tv.bestseat.launcher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.os.storage.StorageManager;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Lädt alle Kacheln: Apps, TV-Eingänge und bekannte, gerade fehlende Stick-Apps. */
public final class AppRepository {

    private static final String TAG = "Launcher.Apps";
    static final String PLAY_TV = "org.droidtv.playtv";

    private AppRepository() {}

    /** Alle Kacheln in gespeicherter Reihenfolge, auch ausgeblendete (Feld hidden im Store). */
    static List<Tile> load(Context context) {
        long start = System.currentTimeMillis();
        PackageManager pm = context.getPackageManager();
        LayoutStore store = LayoutStore.get(context);
        boolean nonTv = Prefs.nonTvApps(context);

        Map<String, ResolveInfo> byPackage = new LinkedHashMap<>();
        collect(pm, Intent.CATEGORY_LEANBACK_LAUNCHER, byPackage);
        if (nonTv) {
            collect(pm, Intent.CATEGORY_LAUNCHER, byPackage);
        }

        Map<String, Tile> tiles = new HashMap<>();
        File cacheDir = new File(context.getCacheDir(), "banners");
        cacheDir.mkdirs();
        for (ResolveInfo info : byPackage.values()) {
            ActivityInfo activity = info.activityInfo;
            ApplicationInfo app = activity.applicationInfo;
            Tile tile = new Tile(Tile.appKey(activity.packageName), Tile.APP,
                    displayLabel(activity.packageName, String.valueOf(info.loadLabel(pm)).trim()));
            tile.pkg = activity.packageName;
            tile.component = new ComponentName(activity.packageName, activity.name);
            tile.intent = new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                    .setComponent(tile.component)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            tile.external = app.storageUuid != null && !StorageManager.UUID_DEFAULT.equals(app.storageUuid);
            Drawable banner = activity.loadBanner(pm);
            if (banner == null) banner = app.loadBanner(pm);
            tile.icon = info.loadIcon(pm);
            Drawable own = TileImages.load(context, tile.pkg);
            if (own != null) {
                // Eigenes Kachelbild des Nutzers (W126).
                tile.banner = own;
            } else if (banner != null) {
                tile.banner = cacheBanner(pm, cacheDir, tile.pkg, banner);
            } else {
                tile.color = backgroundFor(tile.icon);
            }
            tiles.put(tile.key, tile);
        }

        loadInputs(context, tiles);

        boolean changed = false;
        for (Tile tile : tiles.values()) {
            changed |= store.remember(tile, context.getPackageName());
        }
        if (changed) {
            store.save();
        }

        // Bekannte Stick-Apps, die gerade fehlen (USB-Speicher nach dem Aufwachen noch nicht da).
        for (Map.Entry<String, LayoutStore.Known> e : store.known.entrySet()) {
            if (tiles.containsKey(e.getKey()) || !e.getValue().external) continue;
            String pkg = e.getKey().substring(4);
            Tile ghost = new Tile(e.getKey(), Tile.APP, e.getValue().label);
            ghost.pkg = pkg;
            ghost.external = true;
            ghost.available = false;
            File cached = new File(cacheDir, pkg + ".png");
            ghost.banner = TileImages.load(context, pkg);
            if (ghost.banner == null && cached.exists()) {
                Bitmap bmp = BitmapFactory.decodeFile(cached.getPath());
                if (bmp != null) ghost.banner = new BitmapDrawable(context.getResources(), bmp);
            }
            if (ghost.banner == null) ghost.color = Ui.TILE_FALLBACK;
            tiles.put(ghost.key, ghost);
        }

        List<Tile> result = new ArrayList<>();
        for (String key : new ArrayList<>(store.order)) {
            Tile t = tiles.get(key);
            if (t != null) result.add(t);
        }
        Log.i(TAG, "Loaded " + result.size() + " tiles in " + (System.currentTimeMillis() - start) + " ms");
        return result;
    }

    private static void collect(PackageManager pm, String category, Map<String, ResolveInfo> out) {
        for (ResolveInfo info : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category), 0)) {
            if (!out.containsKey(info.activityInfo.packageName)) {
                out.put(info.activityInfo.packageName, info);
            }
        }
    }

    /** Nur die TV-Eingänge (für das Kontrollzentrum). */
    static List<Tile> inputs(Context context) {
        Map<String, Tile> out = new LinkedHashMap<>();
        loadInputs(context, out);
        List<Tile> list = new ArrayList<>(out.values());
        // Zuletzt genutzte zuerst (Wunsch des Nutzers); nie genutzte in der Reihenfolge des TVs dahinter.
        android.content.SharedPreferences used = context.getSharedPreferences(INPUTS_USED, Context.MODE_PRIVATE);
        java.util.Collections.sort(list, (a, b) -> Long.compare(used.getLong(b.key, 0), used.getLong(a.key, 0)));
        return list;
    }

    private static final String INPUTS_USED = "inputs_used";

    /** Eingang wurde über Best Seat gestartet: für die Sortierung merken. */
    static void noteInputUsed(Context context, Tile tile) {
        if (tile == null || tile.kind != Tile.INPUT) return;
        context.getSharedPreferences(INPUTS_USED, Context.MODE_PRIVATE).edit()
                .putLong(tile.key, System.currentTimeMillis()).apply();
    }

    /** HDMI-Anschlüsse und CEC-Geräte. Start per VIEW auf den Durchleitungs-Kanal (wie die Quellenwahl). */
    private static void loadInputs(Context context, Map<String, Tile> out) {
        try {
            TvInputManager tim = (TvInputManager) context.getSystemService(Context.TV_INPUT_SERVICE);
            if (tim == null) return;
            ComponentName playTv = resolvePlayTv(context);
            for (TvInputInfo info : tim.getTvInputList()) {
                if (!info.isPassthroughInput() || info.isHidden(context)) continue;
                // Über HDMI-CEC angemeldete Geräte erscheinen als eigene Eingänge unter einem HDMI-Port
                // (z. B. HDMI100004); nur die echten Anschlüsse zeigen (der TV hat 4).
                if (info.getParentId() != null) continue;
                CharSequence label = info.loadCustomLabel(context);
                if (label == null) label = info.loadLabel(context);
                Tile tile = new Tile(Tile.inputKey(info.getId()), Tile.INPUT, String.valueOf(label));
                tile.intent = new Intent(Intent.ACTION_VIEW,
                        TvContract.buildChannelUriForPassthroughInput(info.getId()))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                if (playTv != null) tile.intent.setComponent(playTv);
                tile.pkg = playTv != null ? playTv.getPackageName() : null;
                tile.icon = info.loadIcon(context);
                tile.color = Color.rgb(40, 44, 54);
                out.put(tile.key, tile);
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "TV inputs unavailable", e);
        }
    }

    private static ComponentName resolvePlayTv(Context context) {
        Intent probe = new Intent(Intent.ACTION_VIEW,
                TvContract.buildChannelUriForPassthroughInput("probe")).setPackage(PLAY_TV);
        ResolveInfo ri = context.getPackageManager().resolveActivity(probe, 0);
        return ri == null ? null : new ComponentName(ri.activityInfo.packageName, ri.activityInfo.name);
    }

    /**
     * Banner als PNG zwischenspeichern, damit Stick-Apps ihre Kachel auch dann zeigen,
     * wenn der Stick noch fehlt. Neu geschrieben, wenn die App aktualisiert wurde.
     */
    private static Drawable cacheBanner(PackageManager pm, File dir, String pkg, Drawable banner) {
        try {
            long updated = 0;
            try {
                PackageInfo pi = pm.getPackageInfo(pkg, 0);
                updated = pi.lastUpdateTime;
            } catch (PackageManager.NameNotFoundException ignored) {
            }
            File file = new File(dir, pkg + ".png");
            if (!file.exists() || file.lastModified() < updated) {
                int w = Math.max(1, Math.min(640, banner.getIntrinsicWidth() > 0 ? banner.getIntrinsicWidth() : 320));
                int h = Math.max(1, Math.min(360, banner.getIntrinsicHeight() > 0 ? banner.getIntrinsicHeight() : 180));
                Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                Canvas c = new Canvas(bmp);
                Drawable copy = banner.getConstantState() != null ? banner.getConstantState().newDrawable() : banner;
                copy.setBounds(0, 0, w, h);
                copy.draw(c);
                try (FileOutputStream out = new FileOutputStream(file)) {
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
                }
                bmp.recycle();
            }
        } catch (Exception e) {
            Log.w(TAG, "banner cache failed for " + pkg, e);
        }
        return banner;
    }

    /** Dunkle Fläche aus der mittleren Farbe des Symbols. */
    static int backgroundFor(Drawable icon) {
        if (icon == null) return Ui.TILE_FALLBACK;
        try {
            Bitmap bmp = Bitmap.createBitmap(12, 12, Bitmap.Config.ARGB_8888);
            Drawable d = icon.getConstantState() != null ? icon.getConstantState().newDrawable() : icon;
            d.setBounds(0, 0, 12, 12);
            d.draw(new Canvas(bmp));
            long r = 0, g = 0, b = 0, n = 0;
            for (int y = 0; y < 12; y++) {
                for (int x = 0; x < 12; x++) {
                    int p = bmp.getPixel(x, y);
                    if (Color.alpha(p) < 128) continue;
                    r += Color.red(p); g += Color.green(p); b += Color.blue(p); n++;
                }
            }
            bmp.recycle();
            if (n == 0) return Ui.TILE_FALLBACK;
            float[] hsv = new float[3];
            Color.RGBToHSV((int) (r / n), (int) (g / n), (int) (b / n), hsv);
            hsv[1] = Math.min(hsv[1], 0.6f);
            hsv[2] = 0.32f;
            return Color.HSVToColor(hsv);
        } catch (RuntimeException e) {
            return Ui.TILE_FALLBACK;
        }
    }

    /**
     * Eigene Namen für Apps, deren Systemname missverständlich ist (W88): Die TV-Einstellungen heißen nur
     * „Settings“, wie die Einstellungen des Launchers. Die App selbst bleibt unverändert.
     */
    public static String displayLabel(String pkg, String label) {
        if ("com.android.tv.settings".equals(pkg)) return "TV Settings";
        return label;
    }
}
