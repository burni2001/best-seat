package tv.bestseat.launcher;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reihenfolge, Ausgeblendetes und bekannte Kacheln, gespeichert in files/layout.json.
 * Einträge werden nie verworfen, nur weil eine App oder ein Eingang gerade fehlt
 * (AT4K verliert so versteckte Eingänge nach dem Neustart, Issue #48). Gelöscht wird
 * nur bei echter Deinstallation oder auf Wunsch („Remove tile“).
 */
final class LayoutStore {

    static final class Known {
        String label;
        boolean external;
        boolean input;
    }

    private static final String TAG = "Launcher.Layout";
    private static LayoutStore instance;

    private final File file;
    final List<String> order = new ArrayList<>();
    final Set<String> hidden = new HashSet<>();
    /** Zählt Änderungen an „hidden“, damit die Startseite merkt, wenn All Apps etwas ändert. */
    volatile int version;
    final Map<String, Known> known = new LinkedHashMap<>();
    /** Ordner: Schlüssel „folder:N“ steht in order wie eine Kachel; Inhalt in der eigenen Reihenfolge. */
    final Map<String, Folder> folders = new LinkedHashMap<>();

    static final class Folder {
        String name;
        final List<String> keys = new ArrayList<>();
    }

    private LayoutStore(Context context) {
        // Jedes Profil hat sein eigenes Layout (W11).
        file = Profiles.layoutFile(context, Profiles.currentId(context));
        load();
    }

    static synchronized LayoutStore get(Context context) {
        if (instance == null) {
            instance = new LayoutStore(context.getApplicationContext());
        }
        return instance;
    }

    /** Nach dem Wiederherstellen einer Sicherung: beim nächsten get() neu laden. */
    static synchronized void reset() {
        instance = null;
    }

    private void load() {
        if (!file.exists()) {
            return;
        }
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] data = new byte[(int) file.length()];
            int read = 0;
            while (read < data.length) {
                int n = in.read(data, read, data.length - read);
                if (n < 0) break;
                read += n;
            }
            JSONObject root = new JSONObject(new String(data, 0, read, StandardCharsets.UTF_8));
            JSONArray o = root.optJSONArray("order");
            for (int i = 0; o != null && i < o.length(); i++) order.add(o.getString(i));
            JSONArray h = root.optJSONArray("hidden");
            for (int i = 0; h != null && i < h.length(); i++) hidden.add(h.getString(i));
            JSONObject f = root.optJSONObject("folders");
            if (f != null) {
                JSONArray names = f.names();
                for (int i = 0; names != null && i < names.length(); i++) {
                    String key = names.getString(i);
                    JSONObject e = f.getJSONObject(key);
                    Folder folder = new Folder();
                    folder.name = e.optString("name", "Folder");
                    JSONArray keys = e.optJSONArray("keys");
                    for (int j = 0; keys != null && j < keys.length(); j++) folder.keys.add(keys.getString(j));
                    folders.put(key, folder);
                }
            }
            JSONObject k = root.optJSONObject("known");
            if (k != null) {
                JSONArray names = k.names();
                for (int i = 0; names != null && i < names.length(); i++) {
                    String key = names.getString(i);
                    JSONObject e = k.getJSONObject(key);
                    Known known = new Known();
                    known.label = e.optString("label", key);
                    known.external = e.optBoolean("external");
                    known.input = e.optBoolean("input");
                    this.known.put(key, known);
                }
            }
        } catch (Exception e) {
            // Kaputte Datei: mit leerer Ordnung weiter, die alte bleibt als .bad liegen.
            Log.w(TAG, "layout.json unreadable, starting fresh", e);
            file.renameTo(new File(file.getPath() + ".bad"));
            order.clear();
            hidden.clear();
            known.clear();
            folders.clear();
        }
    }

    synchronized void save() {
        try {
            JSONObject root = new JSONObject();
            root.put("order", new JSONArray(order));
            root.put("hidden", new JSONArray(hidden));
            JSONObject k = new JSONObject();
            for (Map.Entry<String, Known> e : known.entrySet()) {
                JSONObject o = new JSONObject();
                o.put("label", e.getValue().label);
                if (e.getValue().external) o.put("external", true);
                if (e.getValue().input) o.put("input", true);
                k.put(e.getKey(), o);
            }
            root.put("known", k);
            JSONObject f = new JSONObject();
            for (Map.Entry<String, Folder> e : folders.entrySet()) {
                JSONObject o = new JSONObject();
                o.put("name", e.getValue().name);
                o.put("keys", new JSONArray(e.getValue().keys));
                f.put(e.getKey(), o);
            }
            root.put("folders", f);
            File tmp = new File(file.getPath() + ".tmp");
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                out.write(root.toString(1).getBytes(StandardCharsets.UTF_8));
                out.getFD().sync();
            }
            if (!tmp.renameTo(file)) {
                Log.w(TAG, "rename failed");
            }
        } catch (Exception e) {
            Log.w(TAG, "save failed", e);
        }
    }

    /** Merkt sich eine Kachel. Neue kommen ans Ende; Eingänge und die eigene App starten ausgeblendet. */
    synchronized boolean remember(Tile tile, String ownPackage) {
        boolean changed = false;
        Known k = known.get(tile.key);
        if (k == null) {
            k = new Known();
            known.put(tile.key, k);
            if (tile.kind == Tile.INPUT || ownPackage.equals(tile.pkg)) {
                hidden.add(tile.key);
            }
            changed = true;
        }
        if (!tile.label.equals(k.label) || k.external != tile.external || k.input != (tile.kind == Tile.INPUT)) {
            k.label = tile.label;
            k.external = tile.external;
            k.input = tile.kind == Tile.INPUT;
            changed = true;
        }
        if (!order.contains(tile.key)) {
            order.add(tile.key);
            changed = true;
        }
        return changed;
    }

    synchronized void forget(String key) {
        known.remove(key);
        order.remove(key);
        hidden.remove(key);
        save();
    }

    synchronized void setHidden(String key, boolean hide) {
        if (hide) hidden.add(key); else hidden.remove(key);
        version++;
        save();
    }

    /**
     * Neue Reihenfolge der sichtbaren Kacheln übernehmen. Versteckte und fehlende
     * behalten ihre Plätze, nur die sichtbaren Plätze werden neu belegt.
     */
    synchronized void reorderVisible(List<String> visible) {
        Set<String> set = new HashSet<>(visible);
        int next = 0;
        for (int i = 0; i < order.size(); i++) {
            if (set.contains(order.get(i)) && next < visible.size()) {
                order.set(i, visible.get(next++));
            }
        }
        save();
    }

    // ---------------------------------------------------------------- Ordner

    static boolean isFolder(String key) {
        return key != null && key.startsWith("folder:");
    }

    /** Ordner, in dem die Kachel liegt, sonst null. */
    synchronized String folderOf(String key) {
        for (Map.Entry<String, Folder> e : folders.entrySet()) {
            if (e.getValue().keys.contains(key)) return e.getKey();
        }
        return null;
    }

    /** Neuer Ordner an der Stelle der ersten Kachel; die Kachel wandert hinein. */
    synchronized String createFolder(String name, String firstKey) {
        int n = 1;
        while (folders.containsKey("folder:" + n)) n++;
        String key = "folder:" + n;
        Folder folder = new Folder();
        folder.name = name;
        folders.put(key, folder);
        int at = order.indexOf(firstKey);
        if (at < 0) order.add(key); else order.add(at, key);
        addToFolder(key, firstKey);
        return key;
    }

    synchronized void addToFolder(String folderKey, String key) {
        Folder folder = folders.get(folderKey);
        if (folder == null || isFolder(key)) return;
        String old = folderOf(key);
        if (old != null) folders.get(old).keys.remove(key);
        if (!folder.keys.contains(key)) folder.keys.add(key);
        if (old != null && !old.equals(folderKey)) dropIfEmpty(old);
        version++;
        save();
    }

    /** Kachel aus dem Ordner nehmen; sie steht danach direkt hinter dem Ordner auf der Startseite. */
    synchronized void removeFromFolder(String key) {
        String folderKey = folderOf(key);
        if (folderKey == null) return;
        folders.get(folderKey).keys.remove(key);
        order.remove(key);
        int at = order.indexOf(folderKey);
        order.add(at < 0 ? order.size() : at + 1, key);
        dropIfEmpty(folderKey);
        version++;
        save();
    }

    synchronized void renameFolder(String folderKey, String name) {
        Folder folder = folders.get(folderKey);
        if (folder == null) return;
        folder.name = name;
        version++;
        save();
    }

    /** Ordner auflösen: alle Kacheln kommen an seine Stelle zurück. */
    synchronized void removeFolder(String folderKey) {
        Folder folder = folders.remove(folderKey);
        if (folder == null) return;
        int at = order.indexOf(folderKey);
        order.remove(folderKey);
        if (at < 0) at = order.size();
        for (String key : folder.keys) {
            order.remove(key);
            order.add(Math.min(at++, order.size()), key);
        }
        version++;
        save();
    }

    private void dropIfEmpty(String folderKey) {
        Folder folder = folders.get(folderKey);
        if (folder != null && folder.keys.isEmpty()) {
            folders.remove(folderKey);
            order.remove(folderKey);
        }
    }
}
