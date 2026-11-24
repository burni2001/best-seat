package tv.bestseat.launcher;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Kacheln der Hauptseite im Kontrollzentrum: große Kachel mit Seitenspalte, Zweierreihen und kleine
 * Symbol-Kacheln, dazu das blaue Symbol für eingeschaltete Funktionen.
 */
final class CcTiles {

    /** Markiert die rechte Spalte neben der großen Kachel. */
    static final String SIDE_COLUMN = "side";

    private final ControlCenterActivity cc;

    CcTiles(ControlCenterActivity cc) {
        this.cc = cc;
    }

    /** Neuer Aufbau der Hauptseite; ab Kachel smallFrom kleine Symbol-Kacheln. */
    void reset(int smallFrom) {
        icons.clear();
        tiles.clear();
        curRow = null;
        sideColumn = null;
        cellIndex = 0;
        this.smallFrom = smallFrom;
    }

    /** ID der Kachel, die als Nächstes gebaut wird. */
    void building(String id) {
        buildingId = id;
    }

    boolean isActive(String id) {
        return active.contains(id);
    }

    boolean has(String id) {
        return tiles.containsKey(id);
    }

    LinearLayout sideColumn() {
        return sideColumn;
    }

    // Maße wie im Kontrollzentrum (siehe ControlCenterActivity).
    /** Kacheln je Reihe: höchstens 2 (B12), Kasten nur so hoch wie nötig (W16). */
    private static final int COLS = 2;
    static final float GAP_DP = 8.25f;
    private static final float TILE_RADIUS_DP = 12f;
    /** Große Kachel bei Glass: deutlich runder als die 12 dp ohne Glass. */
    private static final float BIG_GLASS_RADIUS_DP = 24f;
    private static final float FOCUS_SCALE = 1.05f;
    /** Fokus-Anhebung: klein genug, dass der Schatten in den Rand des Kastens passt (12 dp). */
    private static final float FOCUS_Z_DP = 5f;
    private static final float TILE_DP = 47.5f;

    private final java.util.Map<String, ControlIcons.Icon> icons = new java.util.HashMap<>();
    private final java.util.Map<String, View> tiles = new java.util.HashMap<>();
    private final java.util.Set<String> active = new java.util.HashSet<>();

    private int iconColor(String id, boolean focused) {
        if (active.contains(id)) return Ui.ACCENT;
        return focused ? Ui.TEXT_ON_FOCUS : Ui.TEXT;
    }

    void setActive(String id, boolean on) {
        if (on) active.add(id);
        else active.remove(id);
        ControlIcons.Icon icon = icons.get(id);
        View tile = tiles.get(id);
        if (icon != null) icon.setColor(iconColor(id, tile != null && tile.isFocused()));
    }

    private LinearLayout curRow;
    /** Laufende Nummer der Kachel auf der Hauptseite; 0 = große Kachel links oben (W29). */
    private int cellIndex;
    /** Ab diesem Index kleine Symbol-Kacheln (vier in einer Reihe). */
    private int smallFrom = Integer.MAX_VALUE;
    private static final int SMALL_COUNT = 4;
    /** Rechte Spalte neben der großen Kachel (Kacheln 1 und 2). */
    private LinearLayout sideColumn;
    /** ID der Kachel, die gerade gebaut wird (für ihr Symbol). */
    private String buildingId;

    /**
     * Nächste Kachel. Wie bei tvOS: die erste ist doppelt so hoch, rechts daneben stehen
     * die nächsten zwei übereinander, danach Reihen mit COLS Kacheln.
     */
    View cell(String title, String sub, Runnable action) {
        int index = cellIndex++;
        if (cc.body.getTag() == null && index == 0) {
            LinearLayout top = pairRow();
            View big = button(title, action, true);
            top.addView(big, new LinearLayout.LayoutParams(0, Ui.dp(cc, 2 * TILE_DP + GAP_DP), 1f));
            sideColumn = new LinearLayout(cc);
            sideColumn.setOrientation(LinearLayout.VERTICAL);
            sideColumn.setClipChildren(false);
            sideColumn.setTag(SIDE_COLUMN);
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0, Ui.dp(cc, 2 * TILE_DP + GAP_DP), 1f);
            slp.leftMargin = Ui.dp(cc, GAP_DP);
            top.addView(sideColumn, slp);
            curRow = null;
            return big;
        }
        if (cc.body.getTag() == null && index <= 2 && sideColumn != null) {
            View b = button(title, action, false);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    Ui.dp(cc, TILE_DP));
            if (index == 2) lp.topMargin = Ui.dp(cc, GAP_DP);
            sideColumn.addView(b, lp);
            return b;
        }
        if (cc.body.getTag() == null && index >= smallFrom) {
            if ((index - smallFrom) % SMALL_COUNT == 0) curRow = pairRow();
            View b = smallButton(title, action);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(cc, SMALL_DP), 1f);
            if (curRow.getChildCount() > 0) lp.leftMargin = Ui.dp(cc, GAP_DP);
            curRow.addView(b, lp);
            return b;
        }
        if (curRow == null || curRow.getChildCount() >= COLS) curRow = pairRow();
        View b = button(title, action, false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(cc, TILE_DP), 1f);
        if (curRow.getChildCount() > 0) lp.leftMargin = Ui.dp(cc, GAP_DP);
        curRow.addView(b, lp);
        return b;
    }

    private static final float SMALL_DP = TILE_DP;

    /** Kleine quadratische Kachel nur mit Symbol (tvOS-Kontrollzentrum, unterste Reihe). */
    private View smallButton(String title, Runnable action) {
        FrameLayout b = new FrameLayout(cc);
        b.setFocusable(true);
        b.setClickable(true);
        b.setContentDescription(title);
        // Glass wie tvOS 26: kleine Kacheln rund.
        float r = LiquidGlass.on ? Ui.dp(cc, 999) : Ui.dp(cc, TILE_RADIUS_DP);
        b.setBackground(bg(false, r, b));
        Ui.tileShadow(b);
        final String id = buildingId;
        ControlIcons.Icon icon = new ControlIcons.Icon(id, iconColor(id, false));
        if (id != null) {
            icons.put(id, icon);
            tiles.put(id, b);
        }
        android.widget.ImageView iv = new android.widget.ImageView(cc);
        iv.setImageDrawable(icon);
        int is = Ui.dp(cc, 22);
        b.addView(iv, new FrameLayout.LayoutParams(is, is, Gravity.CENTER));
        b.setOnFocusChangeListener((v, has) -> {
            v.setBackground(bg(has, r, v));
            icon.setColor(iconColor(id, has));
            v.animate().scaleX(has ? FOCUS_SCALE : 1f).scaleY(has ? FOCUS_SCALE : 1f)
                    .translationZ(has ? Ui.dp(cc, FOCUS_Z_DP) : 0).setDuration(Ui.ms(140)).start();
        });
        b.setOnClickListener(v -> action.run());
        return b;
    }

    /** Letzte Reihe mit leeren Plätzen auffüllen, damit die Kacheln gleich breit bleiben. */
    void fillRow() {
        // Reihe der kleinen Kacheln auf vier auffüllen, sonst auf zwei.
        int target = cellIndex > smallFrom ? SMALL_COUNT : COLS;
        while (curRow != null && curRow.getChildCount() < target) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, 1, 1f);
            lp.leftMargin = Ui.dp(cc, GAP_DP);
            curRow.addView(new View(cc), lp);
        }
    }

    private LinearLayout pairRow() {
        LinearLayout row = new LinearLayout(cc);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Ui.dp(cc, GAP_DP);
        cc.body.addView(row, lp);
        return row;
    }

    /**
     * Kachel mit Symbol und Titel (W31). Normal: Symbol links neben dem Titel. Groß: Symbol oben,
     * Titel unten links (wie die große Kachel im tvOS-Kontrollzentrum).
     */
    private View button(String title, Runnable action, boolean big) {
        LinearLayout b = new LinearLayout(cc);
        b.setOrientation(big ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        // Große Kachel: Symbol groß in der Mitte, Titel darunter (Wunsch des Nutzers).
        b.setGravity(big ? Gravity.CENTER : Gravity.CENTER_VERTICAL | Gravity.START);
        int p = Ui.dp(cc, 12);
        b.setPadding(p, big ? Ui.dp(cc, 6) : 0, p, big ? Ui.dp(cc, 6) : 0);
        b.setFocusable(true);
        b.setClickable(true);
        // Glass wie tvOS 26: halbe Kacheln als Pille, die große stärker gerundet.
        float r = !LiquidGlass.on ? Ui.dp(cc, TILE_RADIUS_DP) : big ? Ui.dp(cc, BIG_GLASS_RADIUS_DP) : Ui.dp(cc, 999);
        b.setBackground(bg(false, r, b));
        Ui.tileShadow(b);
        final String id = buildingId;
        ControlIcons.Icon icon = new ControlIcons.Icon(id, iconColor(id, false));
        if (id != null) {
            icons.put(id, icon);
            tiles.put(id, b);
        }
        android.widget.ImageView iv = new android.widget.ImageView(cc);
        iv.setImageDrawable(icon);
        int is = Ui.dp(cc, big ? 48 : 22);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(is, is);
        if (big) {
            ilp.bottomMargin = Ui.dp(cc, 4);
        } else {
            ilp.rightMargin = Ui.dp(cc, 8);
        }
        b.addView(iv, ilp);
        TextView t = Ui.text(cc, title, big ? 13 : 12, Ui.TEXT, true);
        Ui.marquee(t);
        if (big) t.setGravity(Gravity.CENTER_HORIZONTAL);
        b.addView(t, big ? new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT) : new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        b.setOnFocusChangeListener((v, has) -> {
            v.setBackground(bg(has, r, v));
            t.setTextColor(has ? Ui.TEXT_ON_FOCUS : Ui.TEXT);
            icon.setColor(iconColor(id, has));
            // Zu langer Text läuft auf der fokussierten Kachel durch (B12).
            t.setSelected(has);
            // Leicht vergrößert, aber mit Luft zu den Nachbarn (B19).
            v.animate().scaleX(has ? FOCUS_SCALE : 1f).scaleY(has ? FOCUS_SCALE : 1f)
                    .translationZ(has ? Ui.dp(cc, FOCUS_Z_DP) : 0).setDuration(Ui.ms(140)).start();
        });
        b.setOnClickListener(v -> action.run());
        return b;
    }

    /** Kachelfläche; bei Glass mit dem unscharfen Hintergrund darunter (Milchglas), fokussiert weiß. */
    private android.graphics.drawable.Drawable bg(boolean focused, float r, View host) {
        if (!focused && LiquidGlass.on && cc.glassBackdrop != null) {
            LiquidGlass.Rounded d = new LiquidGlass.Rounded(LiquidGlass.density(), 1f).backdrop(cc.glassBackdrop, host);
            d.setColor(Ui.glassTint());
            d.setCornerRadius(r);
            return d;
        }
        return Ui.tileBg(focused ? Ui.ROW_FOCUSED : Ui.ROW, r);
    }
}
