package tv.bestseat.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/**
 * Schieberegler für eine Einstellungszeile (Standard 0..100): Schiene, gefüllter Teil, Knopf und Zahl.
 * Bedient wird er über die Zeile mit Links/Rechts; die Farben folgen dem Fokus der Zeile.
 */
final class Slider extends View {

    private static final int TRACK_DP = 120;
    private static final int NUMBER_DP = 34;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private int value;
    private final int min;
    private final int max;
    private final boolean signed;
    private boolean focused;

    Slider(Context context, int value) {
        this(context, value, 0, 100, false);
    }

    /** Range min..max; signed shows a leading + for positive values (hour offsets). */
    Slider(Context context, int value, int min, int max, boolean signed) {
        super(context);
        this.min = min;
        this.max = max;
        this.signed = signed;
        this.value = Math.max(min, Math.min(max, value));
        paint.setTypeface(Fonts.get(context, Fonts.MEDIUM));
        paint.setTextSize(Ui.dp(context, 14));
        paint.setTextAlign(Paint.Align.RIGHT);
        setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                Ui.dp(context, TRACK_DP + NUMBER_DP + 8), Ui.dp(context, 24)));
    }

    int value() {
        return value;
    }

    void setValue(int v) {
        value = Math.max(min, Math.min(max, v));
        invalidate();
    }

    void setFocusedLook(boolean f) {
        focused = f;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        float d = getResources().getDisplayMetrics().density;
        int ink = focused ? Ui.TEXT_ON_FOCUS : Ui.TEXT;
        float cy = getHeight() / 2f;
        float left = d * 7, right = d * (TRACK_DP - 7);
        float h = d * 4;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(70, Color.red(ink), Color.green(ink), Color.blue(ink)));
        rect.set(left, cy - h / 2, right, cy + h / 2);
        c.drawRoundRect(rect, h / 2, h / 2, paint);
        float x = left + (right - left) * (value - min) / (float) (max - min);
        paint.setColor(ink);
        rect.set(left, cy - h / 2, x, cy + h / 2);
        c.drawRoundRect(rect, h / 2, h / 2, paint);
        c.drawCircle(x, cy, d * 7, paint);
        float tx = getWidth();
        float ty = cy - (paint.descent() + paint.ascent()) / 2f;
        c.drawText((signed && value > 0 ? "+" : "") + value, tx, ty, paint);
    }
}
