package tv.bestseat.launcher;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 4K-Test: links eine SurfaceView mit Puffer in Panel-Auflösung (eigene Ebene, HWC),
 * rechts dasselbe Muster als normale View (1080p-Oberfläche, wird verkleinert).
 * Ist die linke Ebene echt 4K, ist die kleine Schrift links lesbar und die Streifen
 * sind scharf; rechts ist beides grauer Brei.
 */
public final class FourKTestActivity extends Activity implements SurfaceHolder.Callback {

    private static final String TAG = "Launcher.4K";
    private int bufW;
    private int bufH;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Hälfte des Panels: logisch 960×1080 dp-Pixel, physisch 1920×2160.
        int logicalW = getResources().getDisplayMetrics().widthPixels / 2;
        int logicalH = getResources().getDisplayMetrics().heightPixels;
        int physW = 3840;
        try {
            physW = getDisplay().getMode().getPhysicalWidth();
        } catch (RuntimeException ignored) {
        }
        float factor = physW / (float) getResources().getDisplayMetrics().widthPixels;
        bufW = Math.round(logicalW * factor);
        bufH = Math.round(logicalH * factor);
        Log.i(TAG, "logical half " + logicalW + "x" + logicalH + ", buffer " + bufW + "x" + bufH);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setBackgroundColor(Color.BLACK);

        FrameLayout left = new FrameLayout(this);
        SurfaceView surface = new SurfaceView(this);
        surface.getHolder().setFixedSize(bufW, bufH);
        surface.getHolder().addCallback(this);
        left.addView(surface, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        left.addView(label("4K LAYER (" + bufW + "×" + bufH + ")"), labelParams());
        root.addView(left, new LinearLayout.LayoutParams(logicalW, logicalH));

        FrameLayout right = new FrameLayout(this);
        Bitmap bmp = Bitmap.createBitmap(bufW, bufH, Bitmap.Config.ARGB_8888);
        draw(new Canvas(bmp), bufW, bufH);
        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.FIT_XY);
        image.setImageBitmap(bmp);
        right.addView(image, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        right.addView(label("NORMAL UI (" + logicalW + "×" + logicalH + ")"), labelParams());
        root.addView(right, new LinearLayout.LayoutParams(logicalW, logicalH));
        setContentView(root);
    }

    private TextView label(String text) {
        TextView t = Ui.text(this, text, 18, Color.WHITE, true);
        t.setBackgroundColor(Color.argb(200, 200, 30, 30));
        t.setPadding(Ui.dp(this, 10), Ui.dp(this, 4), Ui.dp(this, 10), Ui.dp(this, 4));
        return t;
    }

    private static FrameLayout.LayoutParams labelParams() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
    }

    /** Muster in Puffer-Pixeln: Streifen, kleine Schrift, Schachbrett. */
    static void draw(Canvas c, int w, int h) {
        c.drawColor(Color.WHITE);
        Paint black = new Paint();
        black.setColor(Color.BLACK);
        black.setAntiAlias(false);
        int top = h / 10;
        int third = (h - top) / 3;
        // 1-Pixel-Streifen senkrecht (links) und waagrecht (rechts)
        for (int x = 0; x < w / 2; x += 2) c.drawRect(x, top, x + 1, top + third, black);
        for (int y = top; y < top + third; y += 2) c.drawRect(w / 2f, y, w, y + 1, black);
        // Kleine Schrift in mehreren Größen
        Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        text.setColor(Color.BLACK);
        int y = top + third + 30;
        int[] sizes = {12, 16, 20, 26, 34};
        for (int s : sizes) {
            text.setTextSize(s);
            y += s + 14;
            c.drawText(s + " px: If you can read this line, this half is sharp 4K. 0123456789", 30, y, text);
        }
        // Schachbrett 1 px
        int cbTop = top + 2 * third;
        for (int yy = cbTop; yy < h; yy++) {
            for (int xx = (yy & 1); xx < w; xx += 2) c.drawPoint(xx, yy, black);
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        paint(holder);
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Log.i(TAG, "surface " + width + "x" + height);
        paint(holder);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
    }

    private void paint(SurfaceHolder holder) {
        Canvas c = holder.lockCanvas();
        if (c == null) return;
        try {
            draw(c, c.getWidth(), c.getHeight());
        } finally {
            holder.unlockCanvasAndPost(c);
        }
    }
}
