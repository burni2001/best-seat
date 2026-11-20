package tv.bernhard.launcher;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.media.tv.TvContract;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Inhalte einer App für den Top Shelf aus dem TV-Provider: zuerst Watch Next
 * („Continue watching“), dann die Programme ihres ersten sichtbaren Vorschaukanals.
 * Braucht READ_EPG_DATA und READ_TV_LISTINGS (per pm grant).
 */
final class ShelfContent {

    static final class Item {
        String title;
        String subtitle;
        String image;
        /** Breite durch Höhe des Bildes. */
        float aspect = 16f / 9f;
        String intentUri;
        String section;
    }

    private static final String TAG = "Launcher.Shelf";
    private static final int MAX_ITEMS = 12;
    private static final int MAX_WATCH_NEXT = 6;
    private static final long MAX_AGE_MS = 5 * 60_000;

    /** Alle Inhalte je Paket, einmal komplett gelesen (Filter in der Abfrage verbietet der Provider). */
    private static Map<String, List<Item>> all;
    private static long loadedAt;

    private ShelfContent() {}

    /** Zwischengespeicherte Inhalte, falls frisch genug, sonst null. */
    static synchronized List<Item> cached(String pkg) {
        if (all == null || System.currentTimeMillis() - loadedAt > MAX_AGE_MS) return null;
        List<Item> items = all.get(pkg);
        return items != null ? items : new ArrayList<>();
    }

    static synchronized void invalidate() {
        all = null;
    }

    /** Lädt (blockierend, im Hintergrund aufrufen). */
    static List<Item> load(Context context, String pkg) {
        List<Item> hit = cached(pkg);
        if (hit != null) return hit;
        long start = System.currentTimeMillis();
        Map<String, List<Item>> map = new HashMap<>();
        ContentResolver cr = context.getContentResolver();
        try {
            loadWatchNext(cr, map);
        } catch (RuntimeException e) {
            Log.w(TAG, "watch next failed: " + e);
        }
        try {
            loadPreview(cr, map);
        } catch (RuntimeException e) {
            Log.w(TAG, "preview programs failed: " + e);
        }
        synchronized (ShelfContent.class) {
            all = map;
            loadedAt = System.currentTimeMillis();
        }
        Log.i(TAG, "Loaded shelf content for " + map.size() + " apps in "
                + (System.currentTimeMillis() - start) + " ms");
        List<Item> items = map.get(pkg);
        return items != null ? items : new ArrayList<>();
    }

    private static final class Timed {
        final Item item;
        final long time;

        Timed(Item item, long time) {
            this.item = item;
            this.time = time;
        }
    }

    private static void loadWatchNext(ContentResolver cr, Map<String, List<Item>> out) {
        String[] cols = {
                TvContract.WatchNextPrograms.COLUMN_TITLE,
                TvContract.WatchNextPrograms.COLUMN_EPISODE_TITLE,
                TvContract.WatchNextPrograms.COLUMN_POSTER_ART_URI,
                TvContract.WatchNextPrograms.COLUMN_THUMBNAIL_URI,
                TvContract.WatchNextPrograms.COLUMN_POSTER_ART_ASPECT_RATIO,
                TvContract.WatchNextPrograms.COLUMN_INTENT_URI,
                TvContract.WatchNextPrograms.COLUMN_PACKAGE_NAME,
                TvContract.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS,
        };
        Map<String, List<Timed>> byPkg = new HashMap<>();
        try (Cursor c = cr.query(TvContract.WatchNextPrograms.CONTENT_URI, cols, null, null, null)) {
            while (c != null && c.moveToNext()) {
                Item item = read(c);
                if (item == null || c.getString(6) == null) continue;
                item.section = "Continue watching";
                List<Timed> list = byPkg.get(c.getString(6));
                if (list == null) byPkg.put(c.getString(6), list = new ArrayList<>());
                list.add(new Timed(item, c.isNull(7) ? 0 : c.getLong(7)));
            }
        }
        for (Map.Entry<String, List<Timed>> e : byPkg.entrySet()) {
            Collections.sort(e.getValue(), (a, b) -> Long.compare(b.time, a.time));
            List<Item> list = new ArrayList<>();
            for (Timed t : e.getValue()) {
                if (list.size() >= MAX_WATCH_NEXT) break;
                list.add(t.item);
            }
            out.put(e.getKey(), list);
        }
    }

    private static void loadPreview(ContentResolver cr, Map<String, List<Item>> out) {
        // Je App der erste sichtbare Vorschaukanal (kleinste ID), sonst der erste überhaupt.
        Map<String, Long> channel = new HashMap<>();
        Map<String, Boolean> channelBrowsable = new HashMap<>();
        Map<Long, String> channelName = new HashMap<>();
        try (Cursor c = cr.query(TvContract.Channels.CONTENT_URI,
                new String[] {TvContract.Channels._ID, TvContract.Channels.COLUMN_DISPLAY_NAME,
                        TvContract.Channels.COLUMN_BROWSABLE, TvContract.Channels.COLUMN_TYPE,
                        TvContract.Channels.COLUMN_PACKAGE_NAME}, null, null, null)) {
            while (c != null && c.moveToNext()) {
                if (!TvContract.Channels.TYPE_PREVIEW.equals(c.getString(3))) continue;
                String pkg = c.getString(4);
                if (pkg == null) continue;
                long id = c.getLong(0);
                boolean browsable = c.getInt(2) == 1;
                channelName.put(id, c.getString(1));
                Long have = channel.get(pkg);
                Boolean haveBrowsable = channelBrowsable.get(pkg);
                boolean better = have == null
                        || (browsable && !Boolean.TRUE.equals(haveBrowsable))
                        || (browsable == Boolean.TRUE.equals(haveBrowsable) && id < have);
                if (better) {
                    channel.put(pkg, id);
                    channelBrowsable.put(pkg, browsable);
                }
            }
        }
        if (channel.isEmpty()) return;
        Map<Long, String> pkgOfChannel = new HashMap<>();
        for (Map.Entry<String, Long> e : channel.entrySet()) pkgOfChannel.put(e.getValue(), e.getKey());

        String[] cols = {
                TvContract.PreviewPrograms.COLUMN_TITLE,
                TvContract.PreviewPrograms.COLUMN_EPISODE_TITLE,
                TvContract.PreviewPrograms.COLUMN_POSTER_ART_URI,
                TvContract.PreviewPrograms.COLUMN_THUMBNAIL_URI,
                TvContract.PreviewPrograms.COLUMN_POSTER_ART_ASPECT_RATIO,
                TvContract.PreviewPrograms.COLUMN_INTENT_URI,
                TvContract.PreviewPrograms.COLUMN_CHANNEL_ID,
                TvContract.PreviewPrograms.COLUMN_BROWSABLE,
                TvContract.PreviewPrograms.COLUMN_WEIGHT,
        };
        Map<String, List<Timed>> byPkg = new HashMap<>();
        try (Cursor c = cr.query(TvContract.PreviewPrograms.CONTENT_URI, cols, null, null, null)) {
            while (c != null && c.moveToNext()) {
                long ch = c.getLong(6);
                String pkg = pkgOfChannel.get(ch);
                if (pkg == null || (!c.isNull(7) && c.getInt(7) == 0)) continue;
                Item item = read(c);
                if (item == null) continue;
                String name = channelName.get(ch);
                item.section = name != null ? name : "";
                List<Timed> list = byPkg.get(pkg);
                if (list == null) byPkg.put(pkg, list = new ArrayList<>());
                list.add(new Timed(item, c.isNull(8) ? 0 : c.getLong(8)));
            }
        }
        for (Map.Entry<String, List<Timed>> e : byPkg.entrySet()) {
            // Höheres Gewicht zuerst, sonst Reihenfolge des Providers.
            List<Timed> sorted = new ArrayList<>(e.getValue());
            Collections.sort(sorted, (a, b) -> Long.compare(b.time, a.time));
            List<Item> list = out.get(e.getKey());
            if (list == null) out.put(e.getKey(), list = new ArrayList<>());
            for (Timed t : sorted) {
                if (list.size() >= MAX_ITEMS) break;
                list.add(t.item);
            }
        }
    }

    private static Item read(Cursor c) {
        Item item = new Item();
        item.title = c.getString(0);
        item.subtitle = c.getString(1);
        String poster = c.getString(2);
        String thumb = c.getString(3);
        item.aspect = aspect(c.isNull(4) ? -1 : c.getInt(4));
        // Querformat bevorzugt (Thumbnail ist meist 16:9), Hochformat nur, wenn nichts anderes da ist.
        item.image = poster != null ? poster : thumb;
        if (thumb != null && item.aspect < 1f) {
            item.image = thumb;
            item.aspect = 16f / 9f;
        }
        item.intentUri = c.getString(5);
        if (item.image == null && item.title == null) return null;
        return item;
    }

    private static float aspect(int code) {
        switch (code) {
            case TvContract.PreviewPrograms.ASPECT_RATIO_3_2: return 3f / 2f;
            case TvContract.PreviewPrograms.ASPECT_RATIO_4_3: return 4f / 3f;
            case TvContract.PreviewPrograms.ASPECT_RATIO_1_1: return 1f;
            case TvContract.PreviewPrograms.ASPECT_RATIO_2_3: return 2f / 3f;
            case 5: // ASPECT_RATIO_MOVIE_POSTER (nicht im SDK-Jar)
            return 1f / 1.441f;
            default: return 16f / 9f;
        }
    }
}
