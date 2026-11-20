package tv.bestseat.launcher;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.animation.DecelerateInterpolator;

/**
 * Fläche der Favoriten-Leiste wie bei tvOS: Hat die Kachel ganz links oder ganz rechts den Fokus,
 * dehnt sich die Leiste auf dieser Seite um so viel aus, wie die Kachel wächst, und die Ecken
 * dieser Seite runden sich passend zur vergrößerten Kachel (gleicher Abstand rundum).
 * Gezeichnet wird dafür über die eigenen Grenzen hinaus (Eltern schneiden nicht ab).
 */
final class DockDrawable extends Drawable {

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float radius;
    private final float maxExtend;
    private final float focusRadius;
    private float left;
    private float right;
    private ValueAnimator leftAnim;
    private ValueAnimator rightAnim;

    /**
     * @param radius      Eckenradius ohne Fokus am Rand
     * @param maxExtend   so weit wächst die Kachel am Rand mit Fokus über ihren Platz hinaus
     * @param focusRadius Eckenradius der Seite, deren Kachel den Fokus hat
     */
    DockDrawable(int color, int strokeColor, float strokeWidth, float radius, float maxExtend, float focusRadius) {
        fill.setColor(color);
        stroke.setColor(strokeColor);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(strokeWidth);
        this.radius = radius;
        this.maxExtend = maxExtend;
        this.focusRadius = focusRadius;
    }

    /** Fokus am linken / rechten Rand (oder keiner), weich animiert wie die Kachel. */
    void setEdges(boolean leftFocused, boolean rightFocused) {
        leftAnim = animate(leftAnim, left, leftFocused ? 1f : 0f, true);
        rightAnim = animate(rightAnim, right, rightFocused ? 1f : 0f, false);
    }

    private ValueAnimator animate(ValueAnimator old, float from, float to, boolean isLeft) {
        if (old != null) old.cancel();
        if (from == to) return null;
        ValueAnimator a = ValueAnimator.ofFloat(from, to);
        a.setDuration(Ui.ms(to > from ? 300 : 180));
        a.setInterpolator(new DecelerateInterpolator());
        a.addUpdateListener(v -> {
            if (isLeft) left = (float) v.getAnimatedValue();
            else right = (float) v.getAnimatedValue();
            invalidateSelf();
        });
        a.start();
        return a;
    }

    @Override
    public void draw(Canvas c) {
        Rect b = getBounds();
        float half = stroke.getStrokeWidth() / 2f;
        RectF r = new RectF(b.left - maxExtend * left + half, b.top + half,
                b.right + maxExtend * right - half, b.bottom - half);
        float rl = radius + (focusRadius - radius) * left;
        float rr = radius + (focusRadius - radius) * right;
        Path p = new Path();
        p.addRoundRect(r, new float[] {rl, rl, rr, rr, rr, rr, rl, rl}, Path.Direction.CW);
        c.drawPath(p, fill);
        c.drawPath(p, stroke);
    }

    @Override
    public void setAlpha(int alpha) {
        fill.setAlpha(alpha);
        stroke.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter filter) {
        fill.setColorFilter(filter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
