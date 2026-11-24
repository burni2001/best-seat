package tv.bestseat.launcher;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import java.util.List;

/** Ordnerkachel wie bei tvOS: Milchglasfläche mit bis zu neun Mini-Kacheln (3×3). */
final class FolderDrawable extends Drawable {

    private static final int COLS = 3;
    private static final int ROWS = 3;

    private final List<Tile> members;
    /** Eigene Kopien der Banner/Symbole: Grenzen setzen darf die Kacheln im Ordner nicht stören. */
    private final Drawable[] banners;
    private final Drawable[] icons;
    private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cellPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    FolderDrawable(List<Tile> members) {
        this.members = members;
        int n = Math.min(COLS * ROWS, members.size());
        banners = new Drawable[n];
        icons = new Drawable[n];
        for (int i = 0; i < n; i++) {
            banners[i] = copy(members.get(i).banner);
            icons[i] = copy(members.get(i).icon);
        }
        bg.setColor(Ui.FOLDER_TILE);
    }

    @Override
    public void draw(Canvas c) {
        Rect b = getBounds();
        c.drawRect(b, bg);
        // Maße wie die tvOS-Ordnerkachel: 3×3 Mini-Kacheln 58×35 in 250×150, Schritt 70 × 44,
        // Rand links 26 und oben 14, Radius 7 – alles relativ zur Kachelgröße.
        float sx = b.width() / 250f;
        float sy = b.height() / 150f;
        float cw = 58 * sx;
        float ch = 35 * sy;
        float r = (Ui.light ? 8 : 7) * sx;
        // Oben immer 14 von 150 (tvOS): mit Fokus (1,224-fach) liegt die erste Reihe dann genau
        // auf der Oberkante der Nachbarkacheln (0,112 / 1,224 × 150 ≈ 14). Nicht nach Reihenzahl verschieben.
        float top = 14;
        for (int i = 0; i < banners.length; i++) {
            Tile t = members.get(i);
            float x = b.left + (26 + (i % COLS) * 70) * sx;
            float y = b.top + (top + (i / COLS) * 44) * sy;
            RectF cell = new RectF(x, y, x + cw, y + ch);
            c.save();
            Path clip = new Path();
            clip.addRoundRect(cell, r, r, Path.Direction.CW);
            c.clipPath(clip);
            Drawable banner = banners[i];
            Drawable icon = icons[i];
            if (banner != null) {
                // Banner (16:9) mittig auf die 5:3-Zelle zuschneiden, nicht verzerren.
                float bw = cw;
                float bh = cw * 9f / 16f;
                if (bh < ch) {
                    bh = ch;
                    bw = ch * 16f / 9f;
                }
                float cx = cell.centerX();
                float cy = cell.centerY();
                banner.setBounds(Math.round(cx - bw / 2), Math.round(cy - bh / 2), Math.round(cx + bw / 2),
                        Math.round(cy + bh / 2));
                banner.draw(c);
            } else {
                cellPaint.setColor(t.color != 0 ? t.color : Color.DKGRAY);
                c.drawRect(cell, cellPaint);
                if (icon != null) {
                    float s = ch * 0.6f;
                    float cx = cell.centerX();
                    float cy = cell.centerY();
                    icon.setBounds(Math.round(cx - s / 2), Math.round(cy - s / 2), Math.round(cx + s / 2),
                            Math.round(cy + s / 2));
                    icon.draw(c);
                }
            }
            c.restore();
        }
    }

    private static Drawable copy(Drawable d) {
        if (d == null) return null;
        Drawable.ConstantState state = d.getConstantState();
        return state != null ? state.newDrawable().mutate() : d;
    }

    @Override
    public void setAlpha(int alpha) {
        bg.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter filter) {
        bg.setColorFilter(filter);
    }

    @SuppressWarnings("deprecation") // Pflicht-Methode von Drawable, nur noch formal veraltet.
    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
