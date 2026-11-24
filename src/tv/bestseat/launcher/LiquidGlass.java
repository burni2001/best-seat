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
     * Wie die Vorlagen des Nutzers: kein gleich breiter Innenrahmen, sondern Licht, das nur an den
     * Kanten oben links und (schwächer) unten rechts auf das Glas fällt und an den Seiten fast verschwindet.
     * Dazu ein weicher Saum nach innen an denselben Stellen und ein schwacher Glanz oben.
     */
    private static void draw(Canvas c, Path shape, RectF r, float density, float strength, boolean body) {
        if (r.width() <= 0 || r.height() <= 0) return;
        boolean light = Ui.light;
        float s = Math.max(0f, Math.min(1f, strength));
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
                + "uniform shader img; uniform float2 center; uniform float2 half; uniform float radius;"
                + "uniform float zoom; uniform float band; uniform float bend;"
                + "half4 main(float2 p) {"
                + "  float2 q = p - center;"
                + "  float2 b = half - radius;"
                + "  float2 d = abs(q) - b;"
                + "  float dist = length(max(d, 0.0)) + min(max(d.x, d.y), 0.0) - radius;"
                + "  float t = clamp(1.0 + dist / band, 0.0, 1.0);"
                + "  float2 n = max(d.x, d.y) > 0.0 ? normalize(max(d, 0.0)) * sign(q)"
                + "      : (d.x > d.y ? float2(sign(q.x), 0.0) : float2(0.0, sign(q.y)));"
                + "  return img.eval(center + q / zoom + n * (t * t * band * bend));"
                + "}";
        private static final float ZOOM = 1.18f;
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
                src.setLocalMatrix(map);
                s.setInputShader("img", src);
                s.setFloatUniform("center", box.centerX(), box.centerY());
                s.setFloatUniform("half", box.width() / 2f, box.height() / 2f);
                s.setFloatUniform("radius", radius);
                s.setFloatUniform("zoom", ZOOM);
                s.setFloatUniform("band", Math.min(16f * density, Math.min(box.width(), box.height()) / 2f));
                s.setFloatUniform("bend", 0.9f);
                image.setShader(s);
                canvas.drawPath(clip, image);
                image.setShader(null);
                return true;
            } catch (RuntimeException e) {
                shader = null;
                image.setShader(null);
                return false;
            }
        }
    }
}
