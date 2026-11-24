package tv.bestseat.launcher;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

/** Farben und Bausteine. Hell oder dunkel je nach Einstellung „Appearance“ (Ui.apply in onCreate). */
final class Ui {

    /**
     * Fensterübergang setzen. overridePendingTransition ist ab Android 14 veraltet; der Ersatz
     * (overrideActivityTransition) fehlt aber vor Android 14, daher bewusst hier an einer Stelle.
     */
    @SuppressWarnings("deprecation")
    static void transition(Activity a, int enter, int exit) {
        a.overridePendingTransition(enter, exit);
    }

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
    /** Ersatzfarbe für Kacheln ohne Banner und ohne erkennbare Symbolfarbe. */
    static final int TILE_FALLBACK = Color.rgb(48, 52, 60);
    /** Fläche der eingebauten Kacheln „Hidden apps“ und „Settings“. */
    static final int BUILTIN_TILE = Color.rgb(44, 46, 54);

    /** Schatten der Namen unter Kacheln: hell sehr dezent, dunkel kräftiger. */
    static int labelShadow() {
        return light ? Color.argb(70, 0, 0, 0) : Color.argb(150, 0, 0, 0);
    }
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
        LiquidGlass.on = Prefs.liquidGlass(context);
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

    /** Zu langer Text läuft durch, solange die View ausgewählt ist (setSelected(true), z. B. bei Fokus). */
    static void marquee(TextView v) {
        v.setSingleLine(true);
        v.setEllipsize(TextUtils.TruncateAt.MARQUEE);
        v.setMarqueeRepeatLimit(-1);
        v.setHorizontalFadingEdgeEnabled(true);
    }

    static TextView text(Context context, CharSequence value, int sp, int color, boolean bold) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setTextColor(color);
        // Wie tvOS: Medium für Beschriftungen, Bold nur für große Titel.
        if (bold && sp < 22) {
            view.setTypeface(Fonts.get(view.getContext(), Fonts.MEDIUM));
        } else {
            view.setTypeface(Fonts.get(view.getContext(), bold ? Fonts.BOLD : Fonts.REGULAR));
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
        GradientDrawable d = LiquidGlass.on ? new LiquidGlass.Rounded(LiquidGlass.density(), 1f) : new GradientDrawable();
        // Glass: durchsichtiger, der Hintergrund scheint durch (Wunsch des Nutzers).
        if (LiquidGlass.on) color = Color.argb(light ? 135 : 125, Color.red(color), Color.green(color), Color.blue(color));
        d.setColor(color);
        d.setCornerRadius(radiusPx);
        d.setStroke(Math.max(1, dp(context, 0.5f)), MATERIAL_STROKE);
        return d;
    }

    /** Wie rounded(), bei Glass mit Glanz und Lichtrand (strength 1 = Fenster, 0.6 = ruhende Flächen). */
    static GradientDrawable roundedGlass(int color, float radiusPx, float strength) {
        GradientDrawable d = LiquidGlass.on ? new LiquidGlass.Rounded(LiquidGlass.density(), strength)
                : new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radiusPx);
        return d;
    }

    /**
     * Hintergrund für Kacheln und Zeilen: bei Glass ist jede Kachel selbst aus Glas (durchsichtig getönt,
     * mit Lichtkante), fokussiert weiß mit leichter Kante. Sonst wie rounded().
     */
    static GradientDrawable tileBg(int color, float radiusPx) {
        if (!LiquidGlass.on) return rounded(color, radiusPx);
        if (color == ROW) return roundedGlass(glassTint(), radiusPx, 1f);
        return roundedGlass(color, radiusPx, 0.45f);
    }

    /** Tönung einer Glas-Kachel (über dem unscharfen Hintergrund, falls vorhanden). */
    static int glassTint() {
        return light ? Color.argb(105, 255, 255, 255) : Color.argb(46, 255, 255, 255);
    }

    /** Wie evenShadow, bei Glass ohne Schatten: Er schiene durch die durchsichtige Kachel als dunkler Fleck. */
    static void tileShadow(android.view.View v) {
        if (!LiquidGlass.on) {
            evenShadow(v);
            return;
        }
        if (android.os.Build.VERSION.SDK_INT < 28) return;
        v.setOutlineSpotShadowColor(Color.TRANSPARENT);
        v.setOutlineAmbientShadowColor(Color.TRANSPARENT);
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
        row.setBackground(tileBg(ROW, r));
        tileShadow(row);
        row.setOnFocusChangeListener((v, has) -> {
            v.setBackground(tileBg(has ? ROW_FOCUSED : ROW, r));
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
        pill.setBackground(tileBg(ROW, r));
        pill.setOnFocusChangeListener((v, has) -> {
            v.setBackground(tileBg(has ? ROW_FOCUSED : ROW, r));
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
