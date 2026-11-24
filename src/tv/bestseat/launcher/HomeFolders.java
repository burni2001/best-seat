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
        java.util.Map<String, Tile> byKey = new java.util.HashMap<>();
        for (Tile t : home.allTiles) byKey.put(t.key, t);
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
        int gap = Ui.dp(home, gapDp);
        int w = Ui.dp(home, tileDp);
        int h = Math.round(w * 3f / 5f);
        LinearLayout row = null;
        View first = null;
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
                home.zoom.capture(tv);
                p.dialog.dismiss();
                home.open(t);
            });
            tv.setOnLongClickListener(v -> {
                appMenu(p, t);
                return true;
            });
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i % cols != 0) cp.leftMargin = gap;
            row.addView(cell, cp);
            if (first == null) first = tv;
            i++;
        }
        home.show(p);
        if (first != null) first.requestFocus();
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
    private void appMenu(Dialogs.Panel folderPanel, Tile tile) {
        Dialogs.Panel p = Dialogs.menu(home, null).anchor(folderPanel.dialog.getCurrentFocus());
        LinearLayout first = Dialogs.actionRow(home, p, "Remove from Folder", () -> {
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
        if (tile.kind == Tile.APP && tile.pkg != null && tile.available) {
            Dialogs.actionRow(home, p, "App Info", () -> home.openAppInfo(tile.pkg));
        }
        p.dialog.show();
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
