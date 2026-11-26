package tv.bestseat.launcher;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.drawable.GradientDrawable;

/**
 * „Liquid Glass“ als Nachbildung (Option unter Look, L1–L5): kein Shader und keine Live-Verzerrung,
 * sondern auf jede Fläche gezeichnet: ein Glanz oben, ein Lichtrand mit Verlauf (hell oben links,
 * schwach in der Mitte, etwas heller unten rechts) und ein weicher innerer Lichtsaum, der die Lichtbrechung
 * am Rand andeutet. Kostet nur ein paar Verläufe pro Fläche, also keine Last für die GPU.
 * OLED: Die hellsten Stellen bleiben unter 45 % Deckkraft und nur wenige Pixel breit; ruhende Flächen
 * (Leiste, Pille) bekommen die schwächere Stufe.
 */
final class LiquidGlass {

    /** Gesetzt von Ui.apply aus Prefs.liquidGlass. */
    static boolean on;

    private static final Paint SHEEN = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint RIM = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint GLOW = new Paint(Paint.ANTI_ALIAS_FLAG);

    static {
        RIM.setStyle(Paint.Style.STROKE);
        GLOW.setStyle(Paint.Style.STROKE);
    }

    private LiquidGlass() {}

    static float density() {
        return android.content.res.Resources.getSystem().getDisplayMetrics().density;
    }

    /** Glas auf eine abgerundete Fläche. strength 1 = Fenster und Menüs, 0.6 = ruhende Flächen. */
    static void draw(Canvas c, RectF r, float radius, float density, float strength) {
        Path p = new Path();
        p.addRoundRect(r, radius, radius, Path.Direction.CW);
        draw(c, p, r, density, strength);
    }

    static void draw(Canvas c, Path shape, RectF r, float density, float strength) {
        draw(c, shape, r, density, strength, true);
    }

    /** Nur die Lichtkante, ohne Glanz und Tönung: für App-Kacheln (leichter Glaseffekt). */
    static void edge(Canvas c, RectF r, float radius, float density, float strength) {
        Path p = new Path();
        p.addRoundRect(r, radius, radius, Path.Direction.CW);
        draw(c, p, r, density, strength, false);
    }

    /**
     * Ganz leichter, weicher Lichtschein im oberen linken Viertel (fokussierte App-Kachel), wie Licht,
     * das von oben links auf das Glas fällt. strength 0..1 blendet ihn ein.
     */
    static void glint(Canvas c, RectF r, float radius, float strength) {
        if (r.width() <= 0 || r.height() <= 0 || strength <= 0f) return;
        int a = Math.round((Ui.light ? 30 : 24) * Math.min(1f, strength));
        float cx = r.left + r.width() * 0.27f, cy = r.top + r.height() * 0.24f;
        float rad = Math.max(r.width(), r.height()) * 0.5f;
        SHEEN.setShader(new android.graphics.RadialGradient(cx, cy, rad,
                new int[] {Color.argb(a, 255, 255, 255), Color.argb(Math.round(a * 0.45f), 255, 255, 255),
                        Color.TRANSPARENT},
                new float[] {0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        Path shape = new Path();
        shape.addRoundRect(r, radius, radius, Path.Direction.CW);
        c.save();
        c.clipPath(shape);
        c.drawRect(r, SHEEN);
        c.restore();
    }

    /**
     * Lichtlauf über die Kante (Fokus einer App-Kachel): ein heller Fleck läuft unten von links nach rechts,
     * gleichzeitig einer oben von rechts nach links. phase 0..1, blendet am Anfang und Ende weich ein und aus.
     */
    static void sweep(Canvas c, RectF r, float radius, float density, float phase) {
        if (r.width() <= 0 || r.height() <= 0) return;
        int a = Math.round((Ui.light ? 80 : 74) * (float) Math.sin(Math.PI * Math.max(0f, Math.min(1f, phase))));
        if (a <= 0) return;
        Path shape = new Path();
        shape.addRoundRect(r, radius, radius, Path.Direction.CW);
        float spot = Math.max(r.width(), r.height()) * 0.4f;
        float[][] spots = {{r.left + r.width() * phase, r.bottom}, {r.right - r.width() * phase, r.top}};
        c.save();
        c.clipPath(shape);
        for (float[] s : spots) {
            // Nicht nur ein Strich auf der Kante: Der hängt am Kontrast zum Hintergrund daneben und verschwand
            // je nach Modus (Wunsch des Nutzers). Dazu ein weiches Band nach innen, das auf der Kachel selbst liegt.
            float[] bands = {28f, 14f};
            float[] bandAlpha = {0.22f, 0.32f};
            for (int i = 0; i < bands.length; i++) {
                GLOW.setStrokeWidth(bands[i] * density);
                GLOW.setShader(new android.graphics.RadialGradient(s[0], s[1], spot,
                        Color.argb(Math.round(a * bandAlpha[i]), 255, 255, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP));
                c.drawPath(shape, GLOW);
            }
            RIM.setStrokeWidth(Math.max(1f, 1.6f * density) * 2f);
            RIM.setShader(new android.graphics.RadialGradient(s[0], s[1], spot,
                    Color.argb(a, 255, 255, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP));
            c.drawPath(shape, RIM);
        }
        c.restore();
    }

    /**
     * Wie die Vorlagen des Nutzers: kein gleich breiter Innenrahmen, sondern Licht, das nur an den
     * Kanten oben links und (schwächer) unten rechts auf das Glas fällt und an den Seiten fast verschwindet.
     * Dazu ein weicher Saum nach innen an denselben Stellen und ein schwacher Glanz oben.
     */
    private static void draw(Canvas c, Path shape, RectF r, float density, float strength, boolean body) {
        if (r.width() <= 0 || r.height() <= 0) return;
        boolean light = Ui.light;
        float s = Math.max(0f, Math.min(1f, strength)) * 0.7f; // highlights toned down
        // Neutral weiß: Der Blaustich wirkte im Dunkeln zu kräftig (Wunsch des Nutzers); Farbe kommt nur vom Hintergrund.
        int tint = Color.WHITE;
        c.save();
        c.clipPath(shape);
        if (body) {
            // Glanz oben: schwach, damit die Fläche gewölbt wirkt.
            int sheenTop = Color.argb(Math.round((light ? 70 : 38) * s), Color.red(tint), Color.green(tint), Color.blue(tint));
            SHEEN.setShader(new LinearGradient(0, r.top, 0, r.top + r.height() * 0.45f,
                    sheenTop, Color.TRANSPARENT, Shader.TileMode.CLAMP));
            c.drawRect(r, SHEEN);
        }
        // Weicher Saum nach innen, nur wo das Licht einfällt: mehrere Striche, außen schmal und hell,
        // innen breit und schwach. Die Hälfte jedes Strichs liegt außerhalb und wird abgeschnitten.
        float cx = r.centerX(), cy = r.centerY();
        float[] widths = body ? new float[] {14f, 8f, 4f} : new float[] {6f, 3f};
        int[] alphas = body ? new int[] {light ? 30 : 22, light ? 40 : 30, light ? 55 : 42} : new int[] {26, 40};
        for (int i = 0; i < widths.length; i++) {
            GLOW.setStrokeWidth(widths[i] * density);
            GLOW.setShader(lightSweep(cx, cy, Math.round(alphas[i] * s), tint));
            c.drawPath(shape, GLOW);
        }
        // Lichtkante: dünn und hell oben links, schwächer unten rechts, an den Seiten fast weg.
        float rimW = Math.max(1f, 1.3f * density);
        RIM.setStrokeWidth(rimW * 2f);
        RIM.setShader(lightSweep(cx, cy, Math.round((light ? 230 : 215) * s), Color.WHITE));
        c.drawPath(shape, RIM);
        c.restore();
    }

    /**
     * Verlauf rund um die Mitte (0° = rechts, im Uhrzeigersinn): hell bei 225° (oben links),
     * halb so hell bei 45° (unten rechts), dazwischen fast durchsichtig.
     */
    private static SweepGradient lightSweep(float cx, float cy, int alpha, int rgb) {
        int r = Color.red(rgb), g = Color.green(rgb), b = Color.blue(rgb);
        int lo = Color.argb(Math.round(alpha * 0.06f), r, g, b);
        return new SweepGradient(cx, cy,
                new int[] {Color.argb(Math.round(alpha * 0.18f), r, g, b), Color.argb(Math.round(alpha * 0.5f), r, g, b),
                        lo, lo, Color.argb(Math.round(alpha * 0.35f), r, g, b), Color.argb(alpha, r, g, b),
                        Color.argb(Math.round(alpha * 0.35f), r, g, b), lo, Color.argb(Math.round(alpha * 0.18f), r, g, b)},
                new float[] {0f, 0.125f, 0.27f, 0.47f, 0.55f, 0.625f, 0.70f, 0.85f, 1f});
    }

    /** Abgerundete Fläche (wie Ui.rounded), bei Liquid Glass mit Glanz und Lichtrand. */
    /** Regler „Glass opacity“ (0..100). Standard 40 = bisheriges Aussehen. */
    static final int OPACITY_DEFAULT = 40;
    static int opacity = OPACITY_DEFAULT;

    /**
     * Deckkraft einer Glasfläche nach dem Regler. 0 ist nicht ganz durchsichtig (8 % der bisherigen
     * Deckkraft, Wunsch des Nutzers), 100 fast deckend. Deckende Farben (Fokus weiß) bleiben unverändert.
     */
    static int alpha(int a) {
        if (!on || a >= 255) return a;
        int v = Math.max(0, Math.min(100, opacity));
        float f = v <= OPACITY_DEFAULT ? 0.6f + 0.4f * v / OPACITY_DEFAULT
                : 1f + 1.5f * (v - OPACITY_DEFAULT) / (100f - OPACITY_DEFAULT);
        return Math.max(1, Math.min(Ui.light ? 245 : 212, Math.round(a * f))); // dark stays slightly see-through at 100
    }

    /**
     * Deckkraft des unscharfen Bildes unter dem Glas (Lupe). Bis zum Standard voll, darunter wird es
     * ausgeblendet, sonst bliebe das Glas trotz Regler milchig (CC über die Pfeiltaste, Menüs, Ordner).
     */
    static int backdropAlpha() {
        int v = Math.max(0, Math.min(100, opacity));
        if (v >= OPACITY_DEFAULT) return 255;
        return Math.round(255 * (0.85f + 0.15f * v / OPACITY_DEFAULT));
    }

    /**
     * Dark glass turns from black to dark grey above the default opacity (0..95 added to each channel, 45 at opacity 0 so the thin tint is grey too),
     * because a nearly opaque black tint looks like a hole, not like glass.
     */
    static int darkLift() {
        int v = Math.max(0, Math.min(100, opacity));
        return v <= OPACITY_DEFAULT ? Math.round(45f * (OPACITY_DEFAULT - v) / OPACITY_DEFAULT)
                : Math.round(95f * (v - OPACITY_DEFAULT) / (100f - OPACITY_DEFAULT));
    }

    /** Wie alpha(int), auf eine Farbe angewandt. */
    static int color(int c) {
        return Color.argb(alpha(Color.alpha(c)), Color.red(c), Color.green(c), Color.blue(c));
    }

    static final class Rounded extends GradientDrawable {
        private final float density;
        private final float strength;
        private final RectF box = new RectF();
        private final Lens lens = new Lens();
        private android.graphics.Bitmap backdrop;
        private android.view.View host;

        Rounded(float density, float strength) {
            this.density = density;
            this.strength = strength;
        }

        /**
         * Milchglas: das weichgezeichnete Abbild des Bildschirms (bildschirmgroß gedacht, klein gerechnet),
         * genau der Ausschnitt hinter host, unter die Tönung gelegt.
         */
        /** Jede Tönung einer Glasfläche folgt dem Regler „Glass opacity“. */
        @Override
        public void setColor(int argb) {
            super.setColor(LiquidGlass.color(argb));
        }

        /**
         * Kräftige Tönung ohne Hintergrundbild (Kontrollzentrum über einer fremden App, deren Bild der
         * Launcher nicht sieht): folgt dem Regler nur zwischen 150 und 230, damit die Fläche lesbar bleibt.
         */
        Rounded solid(int rgb) {
            int v = Math.max(0, Math.min(100, opacity));
            if (Color.red(rgb) < 100) { // dark base: lift towards grey at high opacity
                int lift = darkLift();
                rgb = Color.rgb(Color.red(rgb) + lift, Color.green(rgb) + lift, Color.blue(rgb) + lift);
            }
            super.setColor((Math.round(150 + (Ui.light ? 80 : 60) * v / 100f) << 24) | (rgb & 0xFFFFFF));
            return this;
        }

        Rounded backdrop(android.graphics.Bitmap shot, android.view.View host) {
            this.backdrop = shot;
            this.host = host;
            return this;
        }

        @Override
        public void draw(Canvas canvas) {
            box.set(getBounds());
            float radius = getCornerRadius();
            radius = Math.min(radius, Math.min(box.width(), box.height()) / 2f);
            if (on && backdrop != null && host != null) lens.draw(canvas, box, radius, density, backdrop, host);
            super.draw(canvas);
            if (!on) return;
            LiquidGlass.draw(canvas, box, radius, density, strength);
        }
    }

    /**
     * Glas mit Hintergrund: zeigt den Ausschnitt eines bildschirmgroß gedachten (klein gerechneten)
     * Abbilds, der genau hinter host liegt. Ab Android 13 durch eine Lupe (AGSL): Mitte leicht vergrößert,
     * am Rand zieht das Glas den Hintergrund von außen herein wie eine gewölbte, lichtbrechende Kante.
     */
    static final class Lens {
        private static final String LENS = ""
                + "uniform shader img; uniform float2 center; uniform float2 halfSize; uniform float radius;"
                + "uniform float zoom; uniform float band; uniform float bend; uniform float chroma; uniform float rim;"
                + "half4 main(float2 p) {"
                + "  float2 q = p - center;"
                + "  float2 b = halfSize - radius;"
                + "  float2 d = abs(q) - b;"
                // Inside the straight part both distances are negative. A hard max() / axis pick makes the normal
                // flip along the diagonal of every corner (a visible crack on large boxes), so blend both axes
                // smoothly (soft max) instead.
                + "  float m = max(d.x, d.y);"
                + "  float k = 0.25 * band;"
                + "  float2 w = exp((d - m) / k);"
                + "  float soft = m + k * log(w.x + w.y);"
                + "  float dist = (m > 0.0 ? length(max(d, 0.0)) + min(m, 0.0) : soft) - radius;"
                + "  float t = clamp(1.0 + dist / band, 0.0, 1.0);"
                + "  float2 n = m > 0.0 ? normalize(max(d, 0.0)) * sign(q) : normalize(w) * sign(q);"
                // S curve with a slight overshoot: the picture is squeezed and folds back near the edge.
                // The offset points INTO the glass and is clamped to the box: the edge only shows what lies under
                // the glass itself, never colours of the neighbours around it.
                + "  float disp = min(band * bend * t * t * (1.0 + 0.3 * sin(6.2832 * t)), 0.9 * min(halfSize.x, halfSize.y));"
                + "  float2 base = center + q / zoom;"
                + "  float2 lo = center - halfSize + 1.0;"
                + "  float2 hi = center + halfSize - 1.0;"
                // Red, green and blue read at slightly different offsets: a thin colour fringe at the edge.
                + "  half4 g = img.eval(clamp(base - n * disp, lo, hi));"
                + "  half r = img.eval(clamp(base - n * disp * (1.0 + chroma), lo, hi)).r;"
                + "  half bl = img.eval(clamp(base - n * disp * (1.0 - chroma), lo, hi)).b;"
                + "  half3 col = half3(r, g.g, bl);"
                // Light edge that fades towards the middle.
                + "  float edge = pow(t, 8.0) * rim;"
                + "  col = col + half3(edge);"
                + "  return half4(col, g.a);"
                + "}";
        // Lupe vor allem am Rand (Wunsch des Nutzers): Mitte nur leicht vergrößert, breites, stark gebogenes Randband.
        private static final float ZOOM = 1.06f;
        private static final float BAND_DP = 34f;
        private static final float BEND = 1.9f;
        private static final float CHROMA = 0.05f; // colour fringe
        private static final float RIM = 0.16f; // light edge
        private final Path clip = new Path();
        private final int[] loc = new int[2];
        private final Paint image = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.Matrix map = new android.graphics.Matrix();
        private Object shader;

        void draw(Canvas canvas, RectF box, float radius, float density, android.graphics.Bitmap backdrop,
                android.view.View host) {
            android.view.View root = host.getRootView();
            if (!(host.getParent() instanceof android.view.View) || root == null || root.getWidth() <= 0) return;
            // Lage ohne die eigene Vergrößerung von host: Die skaliert das Glas mit, wie eine Linse.
            android.view.View parent = (android.view.View) host.getParent();
            parent.getLocationInWindow(loc);
            float sx = 1f, sy = 1f;
            for (android.view.ViewParent p = (android.view.ViewParent) parent; p instanceof android.view.View; p = p.getParent()) {
                sx *= ((android.view.View) p).getScaleX();
                sy *= ((android.view.View) p).getScaleY();
            }
            if (sx <= 0f || sy <= 0f) return;
            float x = loc[0] + (host.getLeft() - parent.getScrollX()) * sx;
            float y = loc[1] + (host.getTop() - parent.getScrollY()) * sy;
            clip.reset();
            clip.addRoundRect(box, radius, radius, Path.Direction.CW);
            map.setScale(root.getWidth() / (float) backdrop.getWidth() / sx,
                    root.getHeight() / (float) backdrop.getHeight() / sy);
            map.postTranslate(-x / sx, -y / sy);
            // Regler „Glass opacity“ gilt auch für das unscharfe Bild unter dem Glas.
            image.setAlpha(backdropAlpha());
            if (android.os.Build.VERSION.SDK_INT >= 33 && drawLens(canvas, box, radius, density, backdrop)) return;
            canvas.save();
            canvas.clipPath(clip);
            canvas.concat(map);
            canvas.drawBitmap(backdrop, 0, 0, image);
            canvas.restore();
        }

        @android.annotation.TargetApi(33)
        private boolean drawLens(Canvas canvas, RectF box, float radius, float density, android.graphics.Bitmap backdrop) {
            try {
                if (shader == null) shader = new android.graphics.RuntimeShader(LENS);
                android.graphics.RuntimeShader s = (android.graphics.RuntimeShader) shader;
                android.graphics.BitmapShader src = new android.graphics.BitmapShader(backdrop,
                        Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                src.setFilterMode(android.graphics.BitmapShader.FILTER_MODE_LINEAR); // smooth, not stair-stepped, when magnified
                src.setLocalMatrix(map);
                s.setInputShader("img", src);
                s.setFloatUniform("center", box.centerX(), box.centerY());
                s.setFloatUniform("halfSize", box.width() / 2f, box.height() / 2f);
                s.setFloatUniform("radius", radius);
                s.setFloatUniform("zoom", ZOOM);
                s.setFloatUniform("band", Math.min(BAND_DP * density, Math.min(box.width(), box.height()) / 2f));
                s.setFloatUniform("bend", BEND);
                s.setFloatUniform("chroma", CHROMA);
                s.setFloatUniform("rim", RIM);
                image.setShader(s);
                canvas.drawPath(clip, image);
                image.setShader(null);
                return true;
            } catch (RuntimeException e) {
                android.util.Log.w("Launcher", "lens shader failed", e);
                shader = null;
                image.setShader(null);
                return false;
            }
        }
    }
}
