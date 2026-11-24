package tv.bestseat.launcher;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Profilbilder: Hintergrundfarben, alle Emojis zur Auswahl, runder Avatar. */
final class Avatars {

    private Avatars() {
    }

    static final String[] COLOR_NAMES = {"Gray", "Blue", "Indigo", "Purple", "Pink", "Red", "Orange", "Yellow",
            "Green", "Teal"};
    /** Je Farbe hell (oben links) und dunkel (unten rechts) für den Verlauf. */
    private static final int[][] COLORS = {
            {Color.rgb(70, 74, 84), Color.rgb(44, 46, 54)},
            {Color.rgb(90, 170, 255), Color.rgb(30, 100, 220)},
            {Color.rgb(120, 140, 255), Color.rgb(80, 70, 210)},
            {Color.rgb(190, 120, 255), Color.rgb(130, 60, 210)},
            {Color.rgb(255, 130, 190), Color.rgb(220, 60, 130)},
            {Color.rgb(255, 115, 105), Color.rgb(200, 45, 45)},
            {Color.rgb(255, 170, 80), Color.rgb(230, 110, 20)},
            {Color.rgb(255, 215, 90), Color.rgb(220, 160, 20)},
            {Color.rgb(110, 215, 120), Color.rgb(40, 150, 70)},
            {Color.rgb(90, 215, 210), Color.rgb(20, 140, 150)},
    };
    /** Ohne gewählte Farbe: Anfangsbuchstabe blau-lila (wie bisher), Emoji grau. */
    private static final int[] INITIAL_DEFAULT = {Color.rgb(120, 140, 255), Color.rgb(170, 90, 220)};

    static int colorCount() {
        return COLORS.length;
    }

    /** Runder Avatar: Anfangsbuchstabe (emoji leer) oder Emoji auf Farbverlauf; weißer Ring bei Fokus. */
    static void style(Context c, TextView v, String emoji, int color, boolean focused, String name, int sizeDp) {
        int[] colors = color >= 0 && color < COLORS.length ? COLORS[color]
                : emoji.isEmpty() ? INITIAL_DEFAULT : COLORS[0];
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, colors);
        bg.setShape(GradientDrawable.OVAL);
        if (focused) bg.setStroke(Ui.dp(c, 2), Color.WHITE);
        v.setBackground(bg);
        float size = sizeDp;
        if (emoji.isEmpty()) {
            String n = name == null || name.isEmpty() ? "?" : name;
            v.setText(n.substring(0, n.offsetByCodePoints(0, 1)).toUpperCase());
            v.setTextColor(Color.WHITE);
            v.setTypeface(Fonts.get(v.getContext(), Fonts.BOLD));
            v.setTextSize(TypedValue.COMPLEX_UNIT_SP, size * 0.42f);
        } else {
            v.setText(emoji);
            v.setTypeface(Fonts.get(v.getContext(), Fonts.REGULAR));
            v.setTextSize(TypedValue.COMPLEX_UNIT_SP, size * 0.48f);
        }
    }

    // ---------------------------------------------------------------- Alle Emojis

    /** Unicode-Blöcke in einer sinnvollen Reihenfolge: Gesichter, Leute, Tiere und Natur, Essen, Dinge, Symbole. */
    private static final int[][] RANGES = {
            {0x1F600, 0x1F64F}, {0x1F910, 0x1F9FF}, {0x1F466, 0x1F4AF}, {0x1F400, 0x1F465},
            {0x1F330, 0x1F37F}, {0x1FA70, 0x1FAFF}, {0x1F380, 0x1F3FF}, {0x1F4B0, 0x1F4FF},
            {0x1F680, 0x1F6FF}, {0x1F300, 0x1F32F}, {0x1F500, 0x1F5FF}, {0x2600, 0x27BF},
            {0x2B00, 0x2BFF}, {0x1F000, 0x1F2FF}, {0x2300, 0x23FF},
    };
    private static List<String> all;

    /** Alle Emojis, die der TV als Bild darstellen kann (Emoji_Presentation und Glyphe vorhanden). */
    static synchronized List<String> all() {
        if (all != null) return all;
        List<String> list = new ArrayList<>();
        Paint paint = new Paint();
        for (int[] r : RANGES) {
            for (int cp = r[0]; cp <= r[1]; cp++) {
                if (!android.icu.lang.UCharacter.hasBinaryProperty(cp,
                        android.icu.lang.UProperty.EMOJI_PRESENTATION)) continue;
                String s = new String(Character.toChars(cp));
                if (paint.hasGlyph(s) && !list.contains(s)) list.add(s);
            }
        }
        all = list;
        return list;
    }

    interface Pick {
        void picked(String emoji);
    }

    /** Overlay mit allen Emojis als Raster (12 je Reihe). OK wählt, Zurück schließt. */
    static Dialog chooser(Activity activity, String current, Pick pick) {
        Dialog dialog = new Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar);
        FrameLayout dim = new FrameLayout(activity);
        dim.setBackgroundColor(Color.argb(140, 0, 0, 0));
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(activity, 16);
        box.setPadding(p, Ui.dp(activity, 12), p, p);
        float radius = Ui.dp(activity, 24);
        // Fast deckend: Hinter dem dichten Raster stört durchscheinender Inhalt.
        box.setBackground(Ui.rounded(Ui.light ? Color.argb(245, 242, 242, 247) : Color.argb(245, 30, 30, 34), radius));
        box.setOutlineProvider(new RoundedOutline(radius));
        box.setClipToOutline(true);
        box.setElevation(Ui.dp(activity, 20));
        TextView title = Ui.text(activity, "Choose an emoji", 15, Ui.TEXT, true);
        title.setPadding(Ui.dp(activity, 6), 0, 0, Ui.dp(activity, 8));
        box.addView(title);

        List<String> items = all();
        GridView grid = new GridView(activity);
        grid.setNumColumns(12);
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setVerticalSpacing(Ui.dp(activity, 4));
        grid.setHorizontalSpacing(Ui.dp(activity, 4));
        grid.setVerticalScrollBarEnabled(false);
        grid.setVerticalFadingEdgeEnabled(true);
        grid.setFadingEdgeLength(Ui.dp(activity, 24));
        grid.setSelector(Ui.rounded(Ui.ROW_FOCUSED, Ui.dp(activity, 10)));
        grid.setDrawSelectorOnTop(false);
        grid.setAdapter(new EmojiAdapter(activity, items));
        grid.setOnItemClickListener((parent, view, position, id) -> {
            dialog.dismiss();
            pick.picked(items.get(position));
        });
        box.addView(grid, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                Ui.dp(activity, 380)));
        dim.addView(box, new FrameLayout.LayoutParams(Ui.dp(activity, 620), ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));
        dialog.setContentView(dim);
        int start = Math.max(0, items.indexOf(current));
        grid.post(() -> {
            grid.requestFocus();
            grid.setSelection(start);
        });
        return dialog;
    }

    private static final class EmojiAdapter extends BaseAdapter {
        private final Context context;
        private final List<String> items;

        EmojiAdapter(Context context, List<String> items) {
            this.context = context;
            this.items = items;
        }

        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public Object getItem(int position) {
            return items.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convert, ViewGroup parent) {
            TextView t = convert instanceof TextView ? (TextView) convert : new TextView(context);
            if (convert == null) {
                t.setGravity(Gravity.CENTER);
                t.setIncludeFontPadding(false);
                t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
                t.setLayoutParams(new GridView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        Ui.dp(context, 42)));
            }
            t.setText(items.get(position));
            return t;
        }
    }
}
