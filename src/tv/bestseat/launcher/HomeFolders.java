package tv.bestseat.launcher;

import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Ordner auf der Startseite: Ordnerkachel, geöffneter Ordner und die Menüs dazu. */
final class HomeFolders {

    private final HomeActivity home;

    HomeFolders(HomeActivity home) {
        this.home = home;
    }

    /** Kachel für einen Ordner mit seinen sichtbaren Apps; leer = keine Kachel. */
    Tile tile(String key, java.util.Map<String, Tile> byKey) {
        LayoutStore.Folder f = home.store.folders.get(key);
        if (f == null) return null;
        List<Tile> members = new ArrayList<>();
        for (String k : f.keys) {
            Tile t = byKey.get(k);
            if (t != null && !home.store.hidden.contains(k)) members.add(t);
        }
        if (members.isEmpty()) return null;
        Tile folder = new Tile(key, Tile.FOLDER, f.name);
        folder.members = members;
        folder.banner = new FolderDrawable(members);
        folder.color = Color.TRANSPARENT;
        return folder;
    }

    /** Geöffneter Ordner: großer Kasten mit Titel und den Apps als Kacheln. */
    void open(String folderKey) {
        LayoutStore.Folder f = home.store.folders.get(folderKey);
        if (f == null) return;
        movingKey = null;
        // Kompakt wie tvOS: 3 Kacheln nebeneinander, Kasten fest für 3×3 (W33, Sammelmeldung 10).
        int cols = 3;
        int tileDp = 116;
        int gapDp = 24;
        int boxDp = cols * tileDp + (cols - 1) * gapDp + 2 * 18 + 2 * 16;
        int rowDp = 14 + Math.round(tileDp * 3f / 5f) + 10 + 18;
        View origin = null;
        for (TileView tv : home.tileViews) {
            if (tv.tile.key.equals(folderKey)) origin = tv;
        }
        Dialogs.Panel p = Dialogs.folderPanel(home, f.name, boxDp, 3 * rowDp + 12, snapshot(), origin);
        p.keyHook = (d, code, ev) -> moveKey(p, folderKey, code, ev);
        View first = fill(p, folderKey, null);
        home.show(p);
        if (first != null) first.requestFocus();
    }

    // Verschieben im Ordner (W131): App wackelt, Pfeile tauschen, OK oder Zurück beendet.
    private String movingKey;
    private final List<TileView> folderViews = new ArrayList<>();
    private static final int COLS = 3;

    /** Kacheln des Ordners (neu) einsetzen; liefert die Kachel für den Fokus. */
    private View fill(Dialogs.Panel p, String folderKey, String focusKey) {
        LayoutStore.Folder f = home.store.folders.get(folderKey);
        p.content.removeAllViews();
        folderViews.clear();
        if (f == null) return null;
        java.util.Map<String, Tile> byKey = new java.util.HashMap<>();
        for (Tile t : home.allTiles) byKey.put(t.key, t);
        int cols = COLS;
        int tileDp = 116;
        int gapDp = 24;
        int gap = Ui.dp(home, gapDp);
        int w = Ui.dp(home, tileDp);
        int h = Math.round(w * 3f / 5f);
        LinearLayout row = null;
        View first = null;
        View focus = null;
        int i = 0;
        for (String k : f.keys) {
            Tile t = byKey.get(k);
            if (t == null || home.store.hidden.contains(k)) continue;
            if (i % cols == 0) {
                row = new LinearLayout(home);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setClipChildren(false);
                row.setClipToPadding(false);
                LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                rp.topMargin = Ui.dp(home, 14);
                p.content.addView(row, rp);
            }
            LinearLayout cell = new LinearLayout(home);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setClipChildren(false);
            TileView tv = new TileView(home, t, w, h);
            cell.addView(tv);
            TextView label = Ui.text(home, t.label, 13, Color.WHITE, true);
            label.setShadowLayer(Ui.dp(home, 8), 0, Ui.dp(home, 1), Ui.labelShadow());
            label.setGravity(Gravity.CENTER);
            label.setAlpha(0f);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = Math.round(h * (TileView.FOCUS_SCALE - 1f) / 2f) + Ui.dp(home, 4);
            label.setTranslationY(Ui.dp(home, 2));
            label.setTypeface(Fonts.get(label.getContext(), Fonts.MEDIUM));
            Ui.marquee(label);
            cell.addView(label, lp);
            tv.setOnFocusChangeListener((v, has) -> {
                tv.focus(has, 0, 0);
                label.animate().alpha(has ? 1f : 0f).setDuration(Ui.ms(has ? 220 : 100)).start();
                label.setSelected(has);
            });
            tv.setOnClickListener(v -> {
                if (movingKey != null) return;
                home.zoom.capture(tv);
                p.dialog.dismiss();
                home.open(t);
            });
            tv.setOnLongClickListener(v -> {
                if (movingKey == null) appMenu(p, folderKey, t);
                return true;
            });
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i % cols != 0) cp.leftMargin = gap;
            row.addView(cell, cp);
            folderViews.add(tv);
            tv.setWiggle(t.key.equals(movingKey));
            if (first == null) first = tv;
            if (t.key.equals(focusKey)) focus = tv;
            i++;
        }
        return focus != null ? focus : first;
    }

    private void startMoving(Dialogs.Panel p, String folderKey, String key) {
        movingKey = key;
        for (TileView tv : folderViews) {
            tv.setWiggle(tv.tile.key.equals(key));
            if (tv.tile.key.equals(key)) tv.requestFocus();
        }
        android.widget.Toast.makeText(home, "Use the arrows to move, OK to finish",
                android.widget.Toast.LENGTH_SHORT).show();
    }

    private boolean moveKey(Dialogs.Panel p, String folderKey, int code, android.view.KeyEvent ev) {
        if (movingKey == null) return false;
        boolean down = ev.getAction() == android.view.KeyEvent.ACTION_DOWN;
        int delta = 0;
        switch (code) {
            case android.view.KeyEvent.KEYCODE_DPAD_LEFT: delta = -1; break;
            case android.view.KeyEvent.KEYCODE_DPAD_RIGHT: delta = 1; break;
            case android.view.KeyEvent.KEYCODE_DPAD_UP: delta = -COLS; break;
            case android.view.KeyEvent.KEYCODE_DPAD_DOWN: delta = COLS; break;
            case android.view.KeyEvent.KEYCODE_DPAD_CENTER:
            case android.view.KeyEvent.KEYCODE_ENTER:
            case android.view.KeyEvent.KEYCODE_BACK:
                if (!down) {
                    String key = movingKey;
                    movingKey = null;
                    for (TileView tv : folderViews) tv.setWiggle(false);
                    home.store.save();
                    home.focusedKey = folderKey;
                    for (TileView tv : folderViews) if (tv.tile.key.equals(key)) tv.requestFocus();
                }
                return true;
            default:
                return false;
        }
        if (!down) return true;
        List<String> keys = new ArrayList<>();
        for (TileView tv : folderViews) keys.add(tv.tile.key);
        int from = keys.indexOf(movingKey);
        if (from < 0) return true;
        int to = Math.max(0, Math.min(keys.size() - 1, from + delta));
        if (to == from) return true;
        keys.remove(from);
        keys.add(to, movingKey);
        home.store.reorderFolder(folderKey, keys);
        View focus = fill(p, folderKey, movingKey);
        if (focus != null) focus.requestFocus();
        return true;
    }

    /** Verkleinertes Bild der Startseite als Hintergrund für den geöffneten Ordner. */
    private android.graphics.Bitmap snapshot() {
        try {
            View root = home.getWindow().getDecorView();
            int w = root.getWidth() / 4;
            int h = root.getHeight() / 4;
            if (w <= 0 || h <= 0) return null;
            android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(w, h,
                    android.graphics.Bitmap.Config.ARGB_8888);
            android.graphics.Canvas c = new android.graphics.Canvas(bmp);
            c.scale(0.25f, 0.25f);
            root.draw(c);
            return bmp;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Langes OK auf einer App im Ordner. */
    private void appMenu(Dialogs.Panel folderPanel, String folderKey, Tile tile) {
        Dialogs.Panel p = Dialogs.menu(home, null).anchor(folderPanel.dialog.getCurrentFocus());
        LinearLayout first = Dialogs.actionRow(home, p, "Edit Folder", () -> startMoving(folderPanel, folderKey, tile.key));
        Dialogs.actionRow(home, p, "Remove from Folder", () -> {
            folderPanel.dialog.dismiss();
            home.store.removeFromFolder(tile.key);
            home.focusedKey = tile.key;
            home.buildGrid();
        });
        Dialogs.actionRow(home, p, "Move to \u2026", () -> {
            folderPanel.dialog.dismiss();
            choose(tile);
        });
        Dialogs.actionRow(home, p, "Hide from Home Screen", () -> {
            String folder = home.store.folderOf(tile.key);
            folderPanel.dialog.dismiss();
            home.store.setHidden(tile.key, true);
            home.focusedKey = folder;
            home.buildGrid();
        });
        if (tile.kind == Tile.APP && tile.pkg != null) {
            Dialogs.actionRow(home, p, "Change Image \u2026", () -> {
                folderPanel.dialog.dismiss();
                home.changeImage(tile);
            });
        }
        if (tile.kind == Tile.APP && tile.pkg != null && tile.available) {
            Dialogs.actionRow(home, p, "App Info", () -> home.openAppInfo(tile.pkg));
        }
        home.showChild(p);
        first.requestFocus();
    }

    /** Ordner wählen oder neu anlegen. */
    void choose(Tile tile) {
        Dialogs.Panel p = Dialogs.menu(home, null).anchor(home.currentTileView);
        String current = home.store.folderOf(tile.key);
        LinearLayout first = Dialogs.actionRow(home, p, "New Folder", "\u2295", false, () -> askName("New Folder", "Folder", name -> {
            String key = home.store.createFolder(name, tile.key);
            home.focusedKey = key;
            home.buildGrid();
        }));
        for (java.util.Map.Entry<String, LayoutStore.Folder> e : home.store.folders.entrySet()) {
            if (e.getKey().equals(current)) continue;
            String key = e.getKey();
            Dialogs.actionRow(home, p, e.getValue().name, () -> {
                home.store.addToFolder(key, tile.key);
                home.focusedKey = key;
                home.buildGrid();
            });
        }
        home.show(p);
        first.requestFocus();
    }

    /** Langes OK auf einem Ordner. */
    void menu(Tile folder) {
        Dialogs.Panel p = Dialogs.menu(home, null).anchor(home.currentTileView);
        LinearLayout first = Dialogs.actionRow(home, p, "Edit Home Screen", () -> home.startMoving(folder.key));
        Dialogs.actionRow(home, p, "Rename", () -> askName("Rename folder", folder.label, name -> {
            home.store.renameFolder(folder.key, name);
            home.focusedKey = folder.key;
            home.buildGrid();
        }));
        Dialogs.actionRow(home, p, "Remove Folder", () -> {
            List<Tile> members = folder.members;
            home.store.removeFolder(folder.key);
            home.focusedKey = members != null && !members.isEmpty() ? members.get(0).key : null;
            home.buildGrid();
        });
        home.show(p);
        first.requestFocus();
    }

    private interface NameCallback {
        void done(String name);
    }

    private void askName(String title, String preset, NameCallback callback) {
        Dialogs.askText(home, title, preset, callback::done);
    }
}
