package tv.bestseat.launcher;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Profile wie bei tvOS (W11): jedes Profil hat Namen, Profilbild und ein eigenes Layout
 * (Kacheln, Ordner, ausgeblendete Apps). Die Einstellungen gelten für alle Profile.
 * Das erste Profil („p0“) nutzt weiter layout.json, weitere layout-&lt;id&gt;.json.
 */
final class Profiles {

    static final int MAX = 5;
    private static final String KEY_LIST = "profiles";
    private static final String KEY_CURRENT = "profile_current";

    static final class Profile {
        final String id;
        String name;
        int avatar;
        /** Frei gewähltes Emoji (seit 0.15.0, null = Bild aus Prefs.AVATARS[avatar]; "" = Anfangsbuchstabe). */
        String emoji;
        /** Hintergrundfarbe (Avatars.COLOR_NAMES), -1 = Standard. */
        int color = -1;

        Profile(String id, String name, int avatar) {
            this.id = id;
            this.name = name;
            this.avatar = avatar;
        }

        /** Emoji des Profilbilds, "" = Anfangsbuchstabe. */
        String picture() {
            if (emoji != null) return emoji;
            return avatar >= 0 && avatar < Prefs.AVATARS.length ? Prefs.AVATARS[avatar] : "";
        }
    }

    private Profiles() {
    }

    private static SharedPreferences sp(Context c) {
        return Prefs.settings(c);
    }

    static synchronized List<Profile> all(Context c) {
        List<Profile> list = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(sp(c).getString(KEY_LIST, "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Profile p = new Profile(o.getString("id"), o.optString("name", "User"), o.optInt("avatar", 0));
                if (o.has("emoji")) p.emoji = o.optString("emoji", "");
                p.color = o.optInt("color", -1);
                list.add(p);
            }
        } catch (Exception ignored) {
        }
        if (list.isEmpty()) {
            // Erstes Profil aus den bisherigen Einstellungen (Name und Bild aus W21).
            SharedPreferences p = sp(c);
            String name = p.getString("user_name", "").trim();
            list.add(new Profile("p0", name.isEmpty() ? "User" : name, p.getInt("user_avatar", 0)));
            save(c, list);
        }
        return list;
    }

    private static void save(Context c, List<Profile> list) {
        JSONArray a = new JSONArray();
        try {
            for (Profile p : list) {
                JSONObject o = new JSONObject().put("id", p.id).put("name", p.name).put("avatar", p.avatar)
                        .put("color", p.color);
                if (p.emoji != null) o.put("emoji", p.emoji);
                a.put(o);
            }
        } catch (Exception ignored) {
        }
        sp(c).edit().putString(KEY_LIST, a.toString()).commit();
    }

    static Profile current(Context c) {
        List<Profile> list = all(c);
        String id = sp(c).getString(KEY_CURRENT, "p0");
        for (Profile p : list) {
            if (p.id.equals(id)) return p;
        }
        return list.get(0);
    }

    static String currentId(Context c) {
        return current(c).id;
    }

    static File layoutFile(Context c, String id) {
        return new File(c.getFilesDir(), "p0".equals(id) ? "layout.json" : "layout-" + id + ".json");
    }

    static void update(Context c, String id, String name, Integer avatar) {
        List<Profile> list = all(c);
        for (Profile p : list) {
            if (!p.id.equals(id)) continue;
            if (name != null && !name.trim().isEmpty()) p.name = name.trim();
            if (avatar != null) {
                p.avatar = avatar;
                p.emoji = null;
            }
        }
        save(c, list);
    }

    /** Profilbild des Profils setzen: emoji (null = unverändert), Farbe (null = unverändert). */
    static void setPicture(Context c, String id, String emoji, Integer color) {
        List<Profile> list = all(c);
        for (Profile p : list) {
            if (!p.id.equals(id)) continue;
            if (emoji != null) p.emoji = emoji;
            if (color != null) p.color = color;
        }
        save(c, list);
    }

    /** Neues Profil, startet mit einer Kopie des Layouts des aktuellen Profils. Liefert die id. */
    static String add(Context c, String name) {
        List<Profile> list = all(c);
        int n = 1;
        String id;
        while (true) {
            id = "p" + n;
            boolean used = false;
            for (Profile p : list) used |= p.id.equals(id);
            if (!used) break;
            n++;
        }
        // Ein Tier, das noch niemand hat.
        int avatar = 1;
        for (int i = 10; i < Prefs.AVATARS.length; i++) {
            boolean taken = false;
            for (Profile p : list) taken |= p.avatar == i;
            if (!taken) {
                avatar = i;
                break;
            }
        }
        list.add(new Profile(id, name == null || name.trim().isEmpty() ? "User " + (n + 1) : name.trim(), avatar));
        save(c, list);
        copy(layoutFile(c, currentId(c)), layoutFile(c, id));
        return id;
    }

    /** Profil wechseln; die Startseite muss danach neu starten. */
    static void switchTo(Context c, String id) {
        sp(c).edit().putString(KEY_CURRENT, id).commit();
        LayoutStore.reset();
    }

    /** Profil löschen (nicht das letzte). Ist es das aktuelle, wird zum ersten gewechselt. */
    static void remove(Context c, String id) {
        List<Profile> list = all(c);
        if (list.size() <= 1) return;
        List<Profile> rest = new ArrayList<>();
        for (Profile p : list) {
            if (!p.id.equals(id)) rest.add(p);
        }
        save(c, rest);
        layoutFile(c, id).delete();
        if (id.equals(sp(c).getString(KEY_CURRENT, "p0"))) switchTo(c, rest.get(0).id);
    }

    private static void copy(File from, File to) {
        if (!from.exists()) return;
        try (FileInputStream in = new FileInputStream(from); FileOutputStream out = new FileOutputStream(to)) {
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
        } catch (Exception ignored) {
        }
    }
}
