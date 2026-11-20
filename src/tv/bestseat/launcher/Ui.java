package tv.bestseat.launcher;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

/** Farben und Bausteine. Hell oder dunkel je nach Einstellung „Appearance“ (Ui.apply in onCreate). */
final class Ui {

    static int BACKGROUND;
    static int PANEL;
    static int ROW;
    static int ROW_FOCUSED;
    static int TEXT;
    static int TEXT_ON_FOCUS;
    static int TEXT_DIM;
    static int TEXT_DIM_FOCUSED;
    static int TRACK_OFF;
    /** Kasten des Kontrollzentrums. */
    static int CONTROL_PANEL;
    /** Milchglas-Leiste der Favoriten und ihr Rand. */
    static int DOCK;
    static int DOCK_STROKE;
    /** Übergang vom Top Shelf zum Hintergrund der Startseite. */
    static int SHADE_END;
    static boolean light;
    /** tvOS-Material (Kontextmenü, Hinweisfenster): Fläche, feiner Rand, Fokusfläche. */
    static int MATERIAL;
    static int MATERIAL_STROKE;
    static int MENU_FOCUSED;
    static int BUTTON;
    /** Ordnerkachel-Fläche und Pille des Ordnernamens. */
    static int FOLDER_TILE;
    static int FOLDER_PILL;
    static int FOLDER_TITLE;

    static {
        setLight(false);
    }

    static void setLight(boolean value) {
        light = value;
        if (value) {
            BACKGROUND = Color.rgb(226, 228, 233);
            PANEL = Color.argb(240, 246, 247, 250);
            ROW = Color.argb(200, 214, 217, 224);
            ROW_FOCUSED = Color.WHITE;
            TEXT = Color.rgb(22, 24, 28);
            TEXT_ON_FOCUS = Color.rgb(16, 18, 22);
            TEXT_DIM = Color.rgb(96, 100, 110);
            TEXT_DIM_FOCUSED = Color.rgb(96, 100, 110);
            TRACK_OFF = Color.rgb(170, 174, 182);
            CONTROL_PANEL = Color.argb(242, 238, 240, 244);
            DOCK = Color.argb(128, 254, 253, 255);
            DOCK_STROKE = Color.argb(41, 255, 255, 255);
            SHADE_END = Color.argb(245, 226, 228, 233);
            MATERIAL = Color.argb(225, 250, 250, 252);
            MATERIAL_STROKE = Color.argb(41, 255, 255, 255);
            MENU_FOCUSED = Color.WHITE;
            BUTTON = Color.rgb(229, 229, 234);
            // tvOS-Screenshot hell: Ordner (233,234,235) über Hintergrund (146,157,161) = Weiß 80 %.
            FOLDER_TILE = Color.argb(204, 255, 255, 255);
            FOLDER_PILL = Color.argb(115, 255, 255, 255);
            FOLDER_TITLE = Color.argb(153, 0, 0, 0);
        } else {
            BACKGROUND = Color.rgb(10, 11, 14);
            PANEL = Color.argb(235, 34, 36, 42);
            ROW = Color.argb(150, 60, 62, 70);
            ROW_FOCUSED = Color.rgb(236, 238, 242);
            TEXT = Color.WHITE;
            TEXT_ON_FOCUS = Color.rgb(16, 18, 22);
            TEXT_DIM = Color.rgb(160, 164, 172);
            TEXT_DIM_FOCUSED = Color.rgb(80, 84, 92);
            TRACK_OFF = Color.rgb(96, 98, 106);
            CONTROL_PANEL = Color.argb(238, 30, 32, 38);
            DOCK = Color.argb(128, 30, 30, 30);
            DOCK_STROKE = Color.argb(26, 255, 255, 255);
            SHADE_END = Color.argb(245, 8, 9, 12);
            MATERIAL = Color.argb(225, 30, 30, 30);
            MATERIAL_STROKE = Color.argb(26, 255, 255, 255);
            MENU_FOCUSED = Color.argb(242, 255, 255, 255);
            BUTTON = Color.rgb(44, 44, 46);
            // tvOS-Screenshot dunkel: Ordner (98,91,82) über Hintergrund (118,72,19) = Grau 90 zu 59 %.
            FOLDER_TILE = Color.argb(150, 90, 90, 92);
            FOLDER_PILL = Color.argb(115, 0, 0, 0);
            FOLDER_TITLE = Color.argb(128, 255, 255, 255);
        }
    }

    /** Design für diese Seite festlegen; liefert, ob es hell ist. */
    static boolean apply(android.content.Context context) {
        setLight(Prefs.isLight(context));
        return light;
    }
    static final int ACCENT = Color.rgb(74, 144, 255);

    static final int TILE_RADIUS_DP = 12;

    /** Alle Animationen etwas langsamer und weicher (Wunsch des Nutzers): Dauer × 1,3. */
    static final float ANIM_SCALE = 1.3f;

    static long ms(long base) {
        return Math.round(base * ANIM_SCALE);
    }

    private Ui() {}

    static int dp(Context context, float value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                context.getResources().getDisplayMetrics()));
    }

    static TextView text(Context context, CharSequence value, int sp, int color, boolean bold) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setTextColor(color);
        // Wie tvOS: Medium für Beschriftungen, Bold nur für große Titel.
        if (bold && sp < 22) {
            view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        } else {
            view.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        }
        view.setMaxLines(1);
        view.setEllipsize(TextUtils.TruncateAt.END);
        return view;
    }

    static TextView caption(Context context, String value) {
        TextView view = text(context, value.toUpperCase(), 12, TEXT_DIM, true);
        view.setLetterSpacing(0.12f);
        return view;
    }

    /** tvOS-Material: Fläche mit feinem hellem Rand. */
    static GradientDrawable material(Context context, float radiusPx) {
        // Der TV meldet Fensterunschärfe als aktiv, zeichnet sie aber nicht: Die Kästen dahinter
        // schimmerten scharf durch. Darum deckend statt Milchglas.
        int color = Color.rgb(Color.red(MATERIAL), Color.green(MATERIAL), Color.blue(MATERIAL));
        GradientDrawable d = rounded(color, radiusPx);
        d.setStroke(Math.max(1, dp(context, 0.5f)), MATERIAL_STROKE);
        return d;
    }

    static GradientDrawable rounded(int color, float radiusPx) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radiusPx);
        return d;
    }

    /**
     * Im hellen Design ein Schatten rundherum statt nur nach unten (B21): ohne gerichteten Schatten,
     * nur der weiche Umgebungsschatten.
     */
    static void evenShadow(android.view.View v) {
        if (android.os.Build.VERSION.SDK_INT < 28) return;
        v.setOutlineSpotShadowColor(light ? Color.TRANSPARENT : Color.BLACK);
        v.setOutlineAmbientShadowColor(light ? Color.argb(255, 60, 64, 80) : Color.BLACK);
    }

    /** Fokussierbare Zeile wie in tvOS: mit Fokus hell mit dunkler Schrift, leicht größer. */
    static LinearLayout row(Context context) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(context, 20), 0, dp(context, 20), 0);
        row.setMinimumHeight(dp(context, 52));
        row.setFocusable(true);
        row.setClickable(true);
        // tvOS-Listen: Radius 16 px; Fokus weiß 90 % mit dunkler Schrift.
        float r = dp(context, 9);
        row.setBackground(rounded(ROW, r));
        evenShadow(row);
        row.setOnFocusChangeListener((v, has) -> {
            v.setBackground(rounded(has ? ROW_FOCUSED : ROW, r));
            tint((LinearLayout) v, has);
            v.animate().scaleX(has ? 1.03f : 1f).scaleY(has ? 1.03f : 1f).translationZ(has ? dp(context, 8) : 0)
                    .setDuration(140).start();
        });
        return row;
    }

    /** Schrift aller Texte einer Zeile an den Fokus anpassen (hell/dunkel), gedimmte bleiben gedimmt. */
    static void tint(LinearLayout row, boolean focused) {
        for (int i = 0; i < row.getChildCount(); i++) {
            if (row.getChildAt(i) instanceof TextView && !(row.getChildAt(i) instanceof Switch)) {
                TextView t = (TextView) row.getChildAt(i);
                boolean dim = Boolean.TRUE.equals(t.getTag());
                t.setTextColor(focused ? (dim ? TEXT_DIM_FOCUSED : TEXT_ON_FOCUS) : (dim ? TEXT_DIM : TEXT));
            }
        }
    }

    static TextView dimText(Context context, CharSequence value) {
        TextView t = text(context, value, 16, TEXT_DIM, false);
        t.setTag(Boolean.TRUE);
        return t;
    }

    /** Runder Knopf. */
    static TextView pill(Context context, String label) {
        TextView pill = text(context, label, 15, TEXT, true);
        pill.setGravity(Gravity.CENTER);
        pill.setPadding(dp(context, 20), 0, dp(context, 20), 0);
        pill.setMinHeight(dp(context, 38));
        pill.setFocusable(true);
        pill.setClickable(true);
        float r = dp(context, 19);
        pill.setBackground(rounded(ROW, r));
        pill.setOnFocusChangeListener((v, has) -> {
            v.setBackground(rounded(has ? ROW_FOCUSED : ROW, r));
            ((TextView) v).setTextColor(has ? TEXT_ON_FOCUS : TEXT);
            v.animate().scaleX(has ? 1.08f : 1f).scaleY(has ? 1.08f : 1f).setDuration(120).start();
        });
        return pill;
    }

    /** Schalter als reine Anzeige, umgeschaltet wird über die Zeile. */
    static Switch toggle(Context context) {
        Switch toggle = new Switch(context);
        toggle.setFocusable(false);
        toggle.setClickable(false);
        int[][] states = {{android.R.attr.state_checked}, {}};
        toggle.setThumbTintList(new ColorStateList(states, new int[] {Color.WHITE, Color.rgb(170, 170, 178)}));
        toggle.setTrackTintList(new ColorStateList(states, new int[] {ACCENT, TRACK_OFF}));
        return toggle;
    }

    static LinearLayout.LayoutParams fill() {
        return new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
    }
}
