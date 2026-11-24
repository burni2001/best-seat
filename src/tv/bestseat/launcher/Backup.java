package tv.bestseat.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import tv.bestseat.launcher.switcher.AdbClient;

/**
 * Layout-Sicherung (W34): Kacheln, Ordner, ausgeblendete Apps und alle Einstellungen in einer Datei
 * im Download-Ordner des TV (interner Speicher). Die Datei übersteht eine Neuinstallation; gelesen
 * und geschrieben wird über die eigene adb-Verbindung, weil die App sonst keinen Zugriff hätte.
 */
final class Backup {

    static final String DIR = "/sdcard/Download/BestSeat";
    /** Ordner bis 0.18.11 (alter App-Name); wird beim ersten Zugriff umbenannt. */
    private static final String OLD_DIR = "/sdcard/Download/AppleTVHome";
    private static final String MIGRATE = "{ [ -d " + OLD_DIR + " ] && [ ! -e " + DIR + " ] && mv " + OLD_DIR + " " + DIR + "; true; }; ";
    static final String FILE = DIR + "/backup.json";
    private static final String PREVIOUS = DIR + "/backup-previous.json";
    /** Eigene Bilder (W110): HD-Banner und Kachelbilder, als Dateien neben backup.json. */
    private static final String IMAGES = DIR + "/images";
    private static final String TILE_IMAGES = "tile-images";
    /** Gesicherte Einstellungsdateien (SharedPreferences). */
    private static final String[] PREF_FILES = {"settings", "allapps", "blacklist"};
    private static final int TIMEOUT_MS = 20000;

    private Backup() {
    }

    /** Sicherung schreiben; die vorige bleibt als backup-previous.json. Wirft bei Fehlern. */
    static void save(Context context) throws Exception {
        JSONObject root = new JSONObject();
        root.put("format", 1);
        root.put("created", System.currentTimeMillis());
        try {
            root.put("app", context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName);
        } catch (Exception ignored) {
        }
        // Layouts aller Profile (layout.json, layout-<id>.json).
        JSONObject layouts = new JSONObject();
        File[] files = context.getFilesDir().listFiles();
        if (files != null) {
            for (File f : files) {
                String n = f.getName();
                if (n.equals("layout.json") || (n.startsWith("layout-") && n.endsWith(".json"))) {
                    layouts.put(n, new JSONObject(read(f)));
                }
            }
        }
        root.put("layouts", layouts);
        JSONObject prefs = new JSONObject();
        for (String name : PREF_FILES) {
            prefs.put(name, prefsToJson(context.getSharedPreferences(name, Context.MODE_PRIVATE)));
        }
        root.put("prefs", prefs);

        String b64 = Base64.encodeToString(root.toString().getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
        String cmd = MIGRATE + "mkdir -p " + DIR + " && { [ -f " + FILE + " ] && mv -f " + FILE + " " + PREVIOUS
                + "; true; } && echo " + b64 + " | base64 -d > " + FILE + " && echo SAVED";
        String out = AdbClient.shell(context, cmd, TIMEOUT_MS);
        if (!out.endsWith("SAVED")) throw new Exception(out.isEmpty() ? "Could not write the backup" : out);
        saveImages(context);
    }

    /**
     * Bilder nach Download/BestSeat/images kopieren. HD-Banner liegen schon im externen Ordner
     * (Android/data, internes Laufwerk) und gehen direkt; Kachelbilder liegen im App-Datenordner, den adb
     * nicht lesen kann, und werden dafür kurz in den externen Ordner gelegt.
     */
    private static void saveImages(Context context) throws Exception {
        File ext = context.getExternalFilesDir(null);
        if (ext == null) throw new Exception("No external folder for images");
        File stage = new File(ext, "backup-tile-images");
        deleteTree(stage);
        stage.mkdirs();
        File[] files = new File(context.getFilesDir(), TILE_IMAGES).listFiles();
        if (files != null) for (File f : files) if (f.isFile()) copy(f, new File(stage, f.getName()));
        String hd = new File(ext, HomeBanners.HD_BANNER_DIR).getPath();
        String out = AdbClient.shell(context, "rm -rf " + IMAGES + " && mkdir -p " + IMAGES + "/" + TILE_IMAGES
                + " " + IMAGES + "/" + HomeBanners.HD_BANNER_DIR
                + " && cp -r " + stage.getPath() + "/. " + IMAGES + "/" + TILE_IMAGES + "/"
                + " && { [ -d " + hd + " ] && cp -r " + hd + "/. " + IMAGES + "/" + HomeBanners.HD_BANNER_DIR + "/; true; }"
                + " && echo COPIED", TIMEOUT_MS).trim();
        deleteTree(stage);
        if (!out.endsWith("COPIED")) throw new Exception("Could not copy the images: " + out);
    }

    /** Bilder aus Download/BestSeat/images zurückholen; fehlt der Ordner (alte Sicherung), bleibt alles, wie es ist. */
    private static void restoreImages(Context context) throws Exception {
        File ext = context.getExternalFilesDir(null);
        if (ext == null) return;
        File stage = new File(ext, "restore-tile-images");
        deleteTree(stage);
        String hd = new File(ext, HomeBanners.HD_BANNER_DIR).getPath();
        String out = AdbClient.shell(context, "[ -d " + IMAGES + " ] || { echo NONE; exit 0; }; "
                + "rm -rf " + hd + " " + stage.getPath() + " && mkdir -p " + hd + " " + stage.getPath()
                + " && cp -r " + IMAGES + "/" + HomeBanners.HD_BANNER_DIR + "/. " + hd + "/"
                + " && cp -r " + IMAGES + "/" + TILE_IMAGES + "/. " + stage.getPath() + "/"
                + " && echo COPIED", TIMEOUT_MS).trim();
        if (out.endsWith("NONE")) return;
        if (!out.endsWith("COPIED")) throw new Exception("Could not read the images: " + out);
        File target = new File(context.getFilesDir(), TILE_IMAGES);
        deleteTree(target);
        File[] files = stage.listFiles();
        if (files != null) {
            for (File f : files) if (f.isFile() && f.getName().endsWith(".png")) copy(f, new File(target, f.getName()));
        }
        deleteTree(stage);
        TileImages.version++;
    }

    private static void copy(File from, File to) throws Exception {
        to.getParentFile().mkdirs();
        try (java.io.FileInputStream in = new java.io.FileInputStream(from);
                FileOutputStream out = new FileOutputStream(to)) {
            byte[] b = new byte[65536];
            int n;
            while ((n = in.read(b)) > 0) out.write(b, 0, n);
        }
    }

    private static void deleteTree(File f) {
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteTree(k);
        f.delete();
    }

    /** Zeit der letzten Sicherung oder null, wenn keine da ist. */
    static String lastBackup(Context context) {
        try {
            String out = AdbClient.shell(context, MIGRATE + "stat -c %Y " + FILE + " 2>/dev/null", TIMEOUT_MS).trim();
            if (out.isEmpty() || !out.matches("\\d+")) return null;
            SimpleDateFormat f = new SimpleDateFormat("d MMM yyyy, HH:mm", Locale.ENGLISH);
            // Gleiche Zeitzone wie die Uhren (der TV läuft auf UTC).
            String zone = Prefs.ZONE_IDS[Prefs.clockZone(context)];
            if (!zone.isEmpty()) f.setTimeZone(java.util.TimeZone.getTimeZone(zone));
            return f.format(new Date(Long.parseLong(out) * 1000L));
        } catch (Exception e) {
            return null;
        }
    }

    /** Sicherung einspielen. Danach muss die App neu starten. Wirft bei Fehlern. */
    static void restore(Context context) throws Exception {
        String text = AdbClient.shell(context, MIGRATE + "cat " + FILE + " 2>/dev/null", TIMEOUT_MS);
        if (!text.startsWith("{")) throw new Exception("No backup found");
        JSONObject root = new JSONObject(text);
        if (root.optInt("format") != 1) throw new Exception("Unknown backup format");

        JSONObject layouts = root.optJSONObject("layouts");
        if (layouts == null && root.optJSONObject("layout") != null) {
            // Sicherungen von 0.9.0: nur ein Layout.
            layouts = new JSONObject().put("layout.json", root.getJSONObject("layout"));
        }
        if (layouts != null) {
            Iterator<String> names = layouts.keys();
            while (names.hasNext()) {
                String n = names.next();
                if (!n.matches("layout(-[a-z0-9]+)?\\.json")) continue;
                try (FileOutputStream out = new FileOutputStream(new File(context.getFilesDir(), n))) {
                    out.write(layouts.getJSONObject(n).toString().getBytes(StandardCharsets.UTF_8));
                }
            }
            LayoutStore.reset();
        }
        JSONObject prefs = root.optJSONObject("prefs");
        if (prefs != null) {
            for (String name : PREF_FILES) {
                JSONObject p = prefs.optJSONObject(name);
                if (p != null) jsonToPrefs(p, context.getSharedPreferences(name, Context.MODE_PRIVATE));
            }
        }
        restoreImages(context);
    }

    // ---------------------------------------------------------------- Hilfen

    private static JSONObject prefsToJson(SharedPreferences prefs) throws Exception {
        JSONObject o = new JSONObject();
        for (Map.Entry<String, ?> e : prefs.getAll().entrySet()) {
            Object v = e.getValue();
            JSONObject item = new JSONObject();
            if (v instanceof Boolean) {
                item.put("t", "b");
            } else if (v instanceof Integer) {
                item.put("t", "i");
            } else if (v instanceof Long) {
                item.put("t", "l");
            } else if (v instanceof Float) {
                item.put("t", "f");
                v = ((Float) v).doubleValue();
            } else if (v instanceof Set) {
                item.put("t", "s");
                JSONArray a = new JSONArray();
                for (Object s : (Set<?>) v) a.put(String.valueOf(s));
                v = a;
            } else {
                item.put("t", "t");
                v = String.valueOf(v);
            }
            item.put("v", v);
            o.put(e.getKey(), item);
        }
        return o;
    }

    private static void jsonToPrefs(JSONObject o, SharedPreferences prefs) throws Exception {
        SharedPreferences.Editor ed = prefs.edit().clear();
        Iterator<String> keys = o.keys();
        while (keys.hasNext()) {
            String k = keys.next();
            JSONObject item = o.getJSONObject(k);
            switch (item.getString("t")) {
                case "b": ed.putBoolean(k, item.getBoolean("v")); break;
                case "i": ed.putInt(k, item.getInt("v")); break;
                case "l": ed.putLong(k, item.getLong("v")); break;
                case "f": ed.putFloat(k, (float) item.getDouble("v")); break;
                case "s": {
                    JSONArray a = item.getJSONArray("v");
                    Set<String> set = new HashSet<>();
                    for (int i = 0; i < a.length(); i++) set.add(a.getString(i));
                    ed.putStringSet(k, set);
                    break;
                }
                default: ed.putString(k, item.getString("v"));
            }
        }
        ed.commit();
    }

    private static String read(File f) throws Exception {
        byte[] data = new byte[(int) f.length()];
        try (java.io.FileInputStream in = new java.io.FileInputStream(f)) {
            int n = 0;
            while (n < data.length) {
                int r = in.read(data, n, data.length - n);
                if (r < 0) break;
                n += r;
            }
        }
        return new String(data, StandardCharsets.UTF_8);
    }
}
