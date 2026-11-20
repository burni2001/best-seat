package tv.bernhard.launcher;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Quellen für den Bildschirmschoner: Aerial-Videos und Online-Fotos (Subreddit). Blockierend, im Hintergrund aufrufen. */
final class SaverSources {

    static final class Aerial {
        String url;
        String name;
        String timeOfDay;
        /** Sekunde im Video → Beschreibung der Stelle (wie tvOS unten links). */
        final java.util.TreeMap<Integer, String> places = new java.util.TreeMap<>();
    }

    private static final String TAG = "Launcher.Saver";
    /** Liste der Aerials (Hersteller-Server), gepflegt vom Open-Source-Projekt Aerial Views; dieselbe nutzt AT4K. */
    private static final String AERIAL_JSON =
            "https://cdn.jsdelivr.net/gh/theothernt/AerialViews@1.8.3/app/src/main/res/raw/tvos26.json";
    private static final String AERIAL_STRINGS =
            "https://cdn.jsdelivr.net/gh/theothernt/AerialViews@1.8.3/app/src/main/res/raw/tvos26_strings.json";
    private static final long AERIAL_MAX_AGE_MS = 7L * 24 * 3600_000;
    private static final String USER_AGENT = "BestSeat/1.0 (Android TV launcher; personal use)";

    /** Themen für „Online photos“: Anzeigename, Subreddit, Wikimedia-Commons-Kategorie (Ersatz, falls Reddit sperrt). */
    static final String[][] PHOTO_TOPICS = {
            {"Landscapes", "EarthPorn", "Featured_pictures_of_landscapes"},
            {"Mountains", "MountainPorn", "Featured_pictures_of_mountains"},
            {"Cities", "CityPorn", "Featured_pictures_of_cityscapes"},
            {"Coasts", "BeachPorn", "Featured_pictures_of_coasts"},
            {"Space", "spaceporn", "Featured_pictures_of_astronomy"},
    };
    private static final long PHOTO_LIST_MAX_AGE_MS = 6L * 3600_000;
    private static final int PHOTO_CACHE_FILES = 60;

    private SaverSources() {}

    /** Datei aus dem Netz, eine Woche intern zwischengespeichert; ohne Netz die alte Kopie. */
    private static String cachedText(Context context, String url, String name) {
        File cache = new File(context.getFilesDir(), name);
        if (!cache.exists() || System.currentTimeMillis() - cache.lastModified() > AERIAL_MAX_AGE_MS) {
            try {
                byte[] data = download(url);
                if (data != null && data.length > 1000) {
                    try (FileOutputStream f = new FileOutputStream(cache)) {
                        f.write(data);
                    }
                    return new String(data, StandardCharsets.UTF_8);
                }
            } catch (Exception e) {
                Log.w(TAG, name + " download failed: " + e);
            }
        }
        try {
            if (cache.exists()) return new String(readAll(new FileInputStream(cache)), StandardCharsets.UTF_8);
        } catch (Exception e) {
            Log.w(TAG, name + " unreadable: " + e);
        }
        return null;
    }

    static List<Aerial> aerials(Context context, boolean uhd) {
        List<Aerial> out = new ArrayList<>();
        String json = cachedText(context, AERIAL_JSON, "aerials.json");
        if (json == null) return out;
        JSONObject strings = new JSONObject();
        try {
            String text = cachedText(context, AERIAL_STRINGS, "aerial_strings.json");
            if (text != null) strings = new JSONObject(text);
        } catch (Exception e) {
            Log.w(TAG, "aerial names unreadable: " + e);
        }
        try {
            JSONArray assets = new JSONObject(json).getJSONArray("assets");
            for (int i = 0; i < assets.length(); i++) {
                JSONObject a = assets.getJSONObject(i);
                if (!a.optBoolean("includeInShuffle", true)) continue;
                String url = uhd ? a.optString("url-4K-SDR", "") : "";
                if (url.isEmpty()) url = a.optString("url-1080-SDR", "");
                if (url.isEmpty()) url = a.optString("url-1080-H264", "");
                if (url.isEmpty()) continue;
                Aerial aerial = new Aerial();
                aerial.url = url;
                aerial.name = strings.optString(a.optString("localizedNameKey", "-"), "");
                if (aerial.name.isEmpty()) aerial.name = nameFromShot(a.optString("shotID", ""), a.optString("scene", ""));
                aerial.name = aerial.name.replace('\u00a0', ' ');
                JSONObject poi = a.optJSONObject("pointsOfInterest");
                if (poi != null) {
                    java.util.Iterator<String> keys = poi.keys();
                    while (keys.hasNext()) {
                        String k = keys.next();
                        String text = strings.optString(poi.optString(k), "");
                        try {
                            if (!text.isEmpty()) aerial.places.put(Integer.parseInt(k), text.replace('\u00a0', ' '));
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
                aerial.timeOfDay = a.optString("timeOfDay", "day");
                out.add(aerial);
            }
        } catch (Exception e) {
            Log.w(TAG, "aerial list unreadable: " + e);
        }
        Collections.shuffle(out);
        Log.i(TAG, out.size() + " aerials (" + (uhd ? "4K" : "1080p") + ")");
        return out;
    }

    /** „GMT026_363A_103NC_E1027_KOREA_JAPAN_NIGHT“ → „Korea Japan Night“; reine Codes → Szene. */
    static String nameFromShot(String shot, String scene) {
        StringBuilder b = new StringBuilder();
        for (String part : shot.split("_")) {
            if (part.isEmpty() || part.matches(".*\\d.*") || part.length() < 2) continue;
            if (b.length() > 0) b.append(' ');
            b.append(part.charAt(0)).append(part.substring(1).toLowerCase(Locale.ROOT));
        }
        if (b.length() > 0) return b.toString();
        if (scene.isEmpty()) return "";
        return scene.substring(0, 1).toUpperCase(Locale.ROOT) + scene.substring(1);
    }

    /** Fotoliste eines Themas: erst Reddit (Top des Monats), sonst Wikimedia Commons. 6 h zwischengespeichert. */
    static List<String> photos(Context context, int topic) {
        String[] t = PHOTO_TOPICS[Math.max(0, Math.min(topic, PHOTO_TOPICS.length - 1))];
        File cache = new File(context.getFilesDir(), "photos2-" + t[1] + ".txt");
        List<String> out = new ArrayList<>();
        if (cache.exists() && System.currentTimeMillis() - cache.lastModified() < PHOTO_LIST_MAX_AGE_MS) {
            try {
                for (String line : new String(readAll(new FileInputStream(cache)), StandardCharsets.UTF_8).split("\n")) {
                    if (!line.isEmpty()) out.add(line);
                }
            } catch (Exception ignored) {
            }
        }
        if (out.size() < 5) {
            out = reddit(t[1]);
            if (out.size() < 5) out = commons(t[2]);
            if (out.size() >= 5) {
                try (FileOutputStream f = new FileOutputStream(cache)) {
                    f.write(String.join("\n", out).getBytes(StandardCharsets.UTF_8));
                } catch (Exception ignored) {
                }
            }
        }
        Collections.shuffle(out);
        Log.i(TAG, out.size() + " photos for " + t[0]);
        return out;
    }

    /** Querformat-Fotos (mindestens Full HD) aus den Top-Beiträgen des Monats. */
    private static List<String> reddit(String subreddit) {
        List<String> out = new ArrayList<>();
        try {
            byte[] data = download("https://www.reddit.com/r/" + subreddit + "/top.json?t=month&limit=80&raw_json=1");
            if (data == null) return out;
            JSONArray children = new JSONObject(new String(data, StandardCharsets.UTF_8))
                    .getJSONObject("data").getJSONArray("children");
            for (int i = 0; i < children.length(); i++) {
                JSONObject d = children.getJSONObject(i).getJSONObject("data");
                if (d.optBoolean("over_18")) continue;
                String url = d.optString("url", "");
                if (!url.matches("https://i\\.redd\\.it/.+\\.(jpg|jpeg|png)")) continue;
                JSONObject preview = d.optJSONObject("preview");
                if (preview == null) continue;
                JSONObject src = preview.getJSONArray("images").getJSONObject(0).getJSONObject("source");
                int w = src.optInt("width");
                int h = src.optInt("height");
                if (w < 1920 || w < h * 1.3) continue;
                out.add(url);
            }
        } catch (Exception e) {
            Log.w(TAG, "photos from r/" + subreddit + " failed: " + e);
        }
        Log.i(TAG, out.size() + " photos from r/" + subreddit);
        return out;
    }

    /** Ausgezeichnete Bilder einer Commons-Kategorie, als 3840-px-Vorschau, ab zufälligem Buchstaben. */
    private static List<String> commons(String category) {
        List<String> out = new ArrayList<>();
        String[] prefixes = {String.valueOf((char) ('A' + new java.util.Random().nextInt(26))), ""};
        for (String prefix : prefixes) {
            try {
                byte[] data = download("https://commons.wikimedia.org/w/api.php?action=query&format=json&formatversion=2"
                        + "&generator=categorymembers&gcmtype=file&gcmlimit=50&gcmtitle=Category:" + category
                        + (prefix.isEmpty() ? "" : "&gcmstartsortkeyprefix=" + prefix)
                        + "&prop=imageinfo&iiprop=url%7Csize&iiurlwidth=3840");
                if (data == null) continue;
                JSONObject query = new JSONObject(new String(data, StandardCharsets.UTF_8)).optJSONObject("query");
                if (query == null) continue;
                JSONArray pages = query.getJSONArray("pages");
                for (int i = 0; i < pages.length(); i++) {
                    JSONObject info = pages.getJSONObject(i).getJSONArray("imageinfo").getJSONObject(0);
                    int w = info.optInt("width");
                    int h = info.optInt("height");
                    // Vorschau-Links tragen Statistik-Parameter (?utm_…); für Prüfung und Cache weglassen.
                    String url = info.optString("thumburl", "").replaceFirst("\\?.*$", "");
                    // Nur Bilder ab 3840 px: dann liefert Commons eine Vorschau vom Thumb-Server (echtes 4K);
                    // Originale von upload.wikimedia.org drosselt Commons sofort (HTTP 429).
                    if (w < 3840 || h == 0 || w < h * 1.3 || w > h * 2.1 || url.isEmpty()) continue;
                    if (!url.matches("(?i).+\\.(jpe?g|png)$")) continue;
                    out.add(url);
                }
            } catch (Exception e) {
                Log.w(TAG, "commons " + category + " failed: " + e);
            }
            if (out.size() >= 10) break;
        }
        Log.i(TAG, out.size() + " photos from commons " + category);
        return out;
    }

    /** Foto in den internen Zwischenspeicher (cache/saver) laden; höchstens 60 Dateien. */
    static File photoFile(Context context, String url) throws Exception {
        File dir = new File(context.getCacheDir(), "saver");
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IllegalStateException("no cache dir");
        File f = new File(dir, Integer.toHexString(url.hashCode()) + ".img");
        if (f.length() > 0) {
            f.setLastModified(System.currentTimeMillis());
            return f;
        }
        byte[] data = download(url);
        if (data == null || data.length < 10_000) throw new IllegalStateException("download failed");
        File tmp = new File(dir, f.getName() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(data);
        }
        if (!tmp.renameTo(f)) throw new IllegalStateException("rename failed");
        File[] files = dir.listFiles();
        if (files != null && files.length > PHOTO_CACHE_FILES) {
            java.util.Arrays.sort(files, (x, y) -> Long.compare(x.lastModified(), y.lastModified()));
            for (int i = 0; i < files.length - PHOTO_CACHE_FILES; i++) files[i].delete();
        }
        return f;
    }

    static byte[] download(String url) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(8000);
        con.setReadTimeout(20000);
        con.setRequestProperty("User-Agent", USER_AGENT);
        try {
            if (con.getResponseCode() / 100 != 2) {
                Log.w(TAG, "HTTP " + con.getResponseCode() + " for " + url);
                return null;
            }
            return readAll(con.getInputStream());
        } finally {
            con.disconnect();
        }
    }

    private static byte[] readAll(InputStream in) throws Exception {
        try (InputStream s = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[32768];
            int n;
            while ((n = s.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }
}
