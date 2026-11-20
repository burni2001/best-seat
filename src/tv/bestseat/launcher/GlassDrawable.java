package tv.bestseat.launcher;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;

/**
 * Milchglas für den Kasten des Kontrollzentrums (W102): nur der Teil der Startseite, der hinter dem Kasten
 * liegt, weichgezeichnet, darüber eine halbdurchsichtige Tönung und der feine helle Rand wie bisher.
 * Das Abbild ist bildschirmgroß gedacht (klein gerechnet); der Ausschnitt folgt der Lage des Kastens.
 */
final class GlassDrawable extends Drawable {

    private final Bitmap shot;
    private final View host;
    private final float radius;
    private final Paint image = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint tint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private final RectF box = new RectF();
    private final Rect src = new Rect();

    GlassDrawable(Bitmap shot, View host, float radius, int tintColor, int strokeColor, float strokeWidth) {
        this.shot = shot;
        this.host = host;
        this.radius = radius;
        tint.setColor(tintColor);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setColor(strokeColor);
        stroke.setStrokeWidth(strokeWidth);
    }

    @Override
    public void draw(Canvas canvas) {
        Rect b = getBounds();
        box.set(b);
        clip.reset();
        clip.addRoundRect(box, radius, radius, Path.Direction.CW);
        View root = host.getRootView();
        int sw = root != null ? root.getWidth() : 0;
        int sh = root != null ? root.getHeight() : 0;
        canvas.save();
        canvas.clipPath(clip);
        if (sw > 0 && sh > 0) {
            float sx = shot.getWidth() / (float) sw;
            float sy = shot.getHeight() / (float) sh;
            int left = host.getLeft(), top = host.getTop();
            src.set(Math.round(left * sx), Math.round(top * sy),
                    Math.round((left + b.width()) * sx), Math.round((top + b.height()) * sy));
            canvas.drawBitmap(shot, src, box, image);
        }
        canvas.drawRect(box, tint);
        canvas.restore();
        float h = stroke.getStrokeWidth() / 2f;
        box.inset(h, h);
        canvas.drawRoundRect(box, radius, radius, stroke);
    }

    @Override
    public void getOutline(android.graphics.Outline outline) {
        outline.setRoundRect(getBounds(), radius);
    }

    @Override
    public void setAlpha(int alpha) {
        image.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        image.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
