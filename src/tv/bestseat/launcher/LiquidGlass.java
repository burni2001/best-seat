package tv.bestseat.launcher;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
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
    private static final Paint CYAN = new Paint(Paint.ANTI_ALIAS_FLAG);

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
        if (r.width() <= 0 || r.height() <= 0) return;
        boolean light = Ui.light;
        float s = Math.max(0f, Math.min(1f, strength));

        // Glanz: oberes Drittel leicht aufgehellt, als wäre die Fläche gewölbt.
        // Dunkel: leichter Cyan-Schimmer wie die tvOS-Gläser (#1cbbff, aus dem Design System des Nutzers).
        int sheenTop = light ? Color.argb(Math.round(140 * s), 255, 255, 255)
                : Color.argb(Math.round(72 * s), 160, 222, 255);
        SHEEN.setShader(new LinearGradient(0, r.top, 0, r.top + r.height() * 0.5f,
                sheenTop, Color.TRANSPARENT, Shader.TileMode.CLAMP));
        c.save();
        c.clipPath(shape);
        c.drawRect(r, SHEEN);
        if (!light) {
            CYAN.setColor(Color.argb(Math.round(20 * s), 28, 187, 255));
            c.drawRect(r, CYAN);
        }

        // Innerer Lichtsaum: breit und schwach, deutet die Brechung am Rand an.
        float glowW = 9f * density;
        GLOW.setStrokeWidth(glowW);
        int glowA = Math.round((light ? 110 : 62) * s);
        GLOW.setShader(new LinearGradient(r.left, r.top, r.right, r.bottom,
                light ? new int[] {Color.argb(glowA, 255, 255, 255), Color.argb(glowA / 4, 255, 255, 255),
                        Color.argb(glowA / 2, 255, 255, 255)}
                        : new int[] {Color.argb(glowA, 170, 228, 255), Color.argb(glowA / 4, 120, 210, 255),
                        Color.argb(glowA / 2, 170, 228, 255)},
                new float[] {0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        c.drawPath(shape, GLOW);

        // Reflex unten: schwacher Lichtsaum am unteren Rand, wie durchs Glas gefallenes Licht.
        int lowA = Math.round((light ? 70 : 40) * s);
        SHEEN.setShader(new LinearGradient(0, r.bottom, 0, r.bottom - r.height() * 0.22f,
                light ? Color.argb(lowA, 255, 255, 255) : Color.argb(lowA, 170, 228, 255),
                Color.TRANSPARENT, Shader.TileMode.CLAMP));
        c.drawRect(r, SHEEN);
        c.restore();

        // Lichtrand: dünn, hell oben links, fast weg in der Mitte, wieder heller unten rechts.
        float rimW = Math.max(1f, 1.5f * density);
        // Doppelt so breit und innen beschnitten: sichtbar bleibt die innere Hälfte, auch an runden Ecken.
        RIM.setStrokeWidth(rimW * 2f);
        int hi = Math.round((light ? 210 : 200) * s);
        RIM.setShader(new LinearGradient(r.left, r.top, r.right, r.bottom,
                new int[] {Color.argb(hi, 255, 255, 255), Color.argb(Math.round(14 * s), 255, 255, 255),
                        Color.argb(Math.round(hi * 0.55f), 255, 255, 255)},
                new float[] {0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        c.save();
        c.clipPath(shape);
        c.drawPath(shape, RIM);
        c.restore();
    }

    /** Abgerundete Fläche (wie Ui.rounded), bei Liquid Glass mit Glanz und Lichtrand. */
    static final class Rounded extends GradientDrawable {
        private final float density;
        private final float strength;
        private final RectF box = new RectF();

        Rounded(float density, float strength) {
            this.density = density;
            this.strength = strength;
        }

        @Override
        public void draw(Canvas canvas) {
            super.draw(canvas);
            if (!on) return;
            box.set(getBounds());
            float radius = getCornerRadius();
            radius = Math.min(radius, Math.min(box.width(), box.height()) / 2f);
            LiquidGlass.draw(canvas, box, radius, density, strength);
        }
    }
}
