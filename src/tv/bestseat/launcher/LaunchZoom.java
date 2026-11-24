package tv.bestseat.launcher;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

/** Zoom der Kachel beim Start einer App und zurück in die Kachel bei der Rückkehr (W82). */
final class LaunchZoom {

    private static final String TAG = "Launcher.Home";

    private final HomeActivity home;

    LaunchZoom(HomeActivity home) {
        this.home = home;
    }

    /** Kein Kachelbild für den nächsten Start (kein Zoom). */
    void clear() {
        zoomBmp = null;
    }

    boolean hasCapture() {
        return zoomBmp != null;
    }

    /**
     * Start-Animation wie tvOS: Die Kachel fliegt sofort auf den Zuschauer zu und füllt den Schirm,
     * während die App im Hintergrund startet (je nach App 1–2 s bis zum ersten Bild, vom USB-Stick
     * länger). Sobald das App-Fenster da ist, blendet es darüber ein.
     */
    private android.graphics.Bitmap zoomBmp;
    private android.graphics.RectF zoomRect;
    private float zoomRadius;
    private View zoomOverlay;
    private final Runnable zoomTimeout = this::timedOut;

    /**
     * Kein App-Fenster gekommen (Start fehlgeschlagen o. ä.): Startseite wieder zeigen. Auch, wenn die
     * Startseite nie gestoppt wurde: Die App ist dann durchsichtig (z. B. TV-Einstellungen als
     * Seitenpanel), onStart kommt nie, und die Fläche bliebe sonst stehen (B39).
     */
    private void timedOut() {
        if (home.hasWindowFocus() || home.visibleSince != 0) remove(true);
    }

    /** Bild und sichtbare Lage der Kachel merken (vor dem Schließen eines Ordners). */
    void capture(View v) {
        zoomBmp = null;
        int w = v.getWidth(), h = v.getHeight();
        if (w <= 0 || h <= 0) return;
        try {
            android.graphics.Bitmap b = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888);
            v.draw(new android.graphics.Canvas(b));
            int[] loc = new int[2];
            v.getLocationOnScreen(loc);
            zoomRect = new android.graphics.RectF(loc[0], loc[1], loc[0] + w * v.getScaleX(), loc[1] + h * v.getScaleY());
            zoomRadius = w * 0.12f * v.getScaleX();
            zoomBmp = b;
        } catch (RuntimeException | OutOfMemoryError e) {
            zoomBmp = null;
        }
    }

    /** Start einer App: die gemerkte Kachel zoomt auf den ganzen Schirm. */
    void start() {
        zoomAnim(false);
    }

    /**
     * reverse = false: Kachel zoomt beim Start auf den ganzen Schirm (bleibt bis zum App-Fenster).
     * reverse = true: Rückkehr aus der App, genau umgekehrt: vom ganzen Schirm zurück in die Kachel,
     * danach weg (W82). Kostet wenig: ein Kachelbild, zwei Views, ein Animator.
     */
    private void zoomAnim(boolean reverse) {
        android.graphics.Bitmap bmp = zoomBmp;
        android.graphics.RectF r = zoomRect;
        zoomBmp = null;
        remove(false);
        if (bmp == null || r == null || home.rootView == null) return;
        final int sw = home.rootView.getWidth(), sh = home.rootView.getHeight();
        if (sw <= 0 || sh <= 0) return;
        int[] rl = new int[2];
        home.rootView.getLocationOnScreen(rl);
        // Fläche in der Hintergrundfarbe der Kachel wächst auf den ganzen Schirm; das Kachelbild selbst
        // wird höchstens doppelt so groß und landet in der Mitte (Wunsch des Nutzers).
        FrameLayout box = new FrameLayout(home);
        box.setClipChildren(false);
        final View fill = new View(home);
        fill.setBackgroundColor(edgeColor(bmp));
        fill.setPivotX(0f);
        fill.setPivotY(0f);
        final float[] radius = {zoomRadius};
        fill.setOutlineProvider(new RoundOutline(radius));
        fill.setClipToOutline(true);
        box.addView(fill, new FrameLayout.LayoutParams(sw, sh));
        final int iw = Math.round(r.width()), ih = Math.round(r.height());
        final ImageView iv = new ImageView(home);
        iv.setImageBitmap(bmp);
        iv.setScaleType(ImageView.ScaleType.FIT_XY);
        final float[] iRadius = {zoomRadius};
        iv.setOutlineProvider(new RoundOutline(iRadius));
        iv.setClipToOutline(true);
        box.addView(iv, new FrameLayout.LayoutParams(iw, ih));
        home.rootView.addView(box, new FrameLayout.LayoutParams(sw, sh));
        zoomOverlay = box;

        final float x0 = r.left - rl[0], y0 = r.top - rl[1];
        final float s0x = r.width() / sw, s0y = r.height() / sh;
        final float rad0 = zoomRadius;
        final float maxScale = 2f;
        // Bild: Mitte wandert von der Kachel in die Bildschirmmitte, Größe von 1x auf 2x (Pivot Mitte).
        final float cx0 = x0 + iw / 2f, cy0 = y0 + ih / 2f;
        iv.setTranslationX(x0);
        iv.setTranslationY(y0);
        final android.view.animation.Interpolator curve =
                new android.view.animation.PathInterpolator(0.3f, 0f, 0.15f, 1f);
        ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
        a.setDuration(Ui.ms(420));
        // Umgekehrt: am Anfang schnell, sanft in der Kachel landen.
        a.setInterpolator(t -> reverse ? 1f - curve.getInterpolation(t) : curve.getInterpolation(t));
        a.addUpdateListener(an -> {
            float f = (float) an.getAnimatedValue();
            float sx = s0x + (1f - s0x) * f, sy = s0y + (1f - s0y) * f;
            fill.setScaleX(sx);
            fill.setScaleY(sy);
            fill.setTranslationX(x0 * (1f - f));
            fill.setTranslationY(y0 * (1f - f));
            radius[0] = rad0 * (1f - f) / Math.max(0.01f, sx);
            fill.invalidateOutline();
            float is = 1f + (maxScale - 1f) * f;
            float cx = cx0 + (sw / 2f - cx0) * f, cy = cy0 + (sh / 2f - cy0) * f;
            iv.setScaleX(is);
            iv.setScaleY(is);
            iv.setTranslationX(cx - iw / 2f);
            iv.setTranslationY(cy - ih / 2f);
        });
        if (reverse) {
            iv.setScaleX(maxScale);
            iv.setScaleY(maxScale);
            iv.setTranslationX(sw / 2f - iw / 2f);
            iv.setTranslationY(sh / 2f - ih / 2f);
            a.addListener(new AnimEnd(() -> {
                if (zoomOverlay == box) remove(false);
            }));
            // Erst schrumpfen, wenn die Startseite wirklich zu sehen ist (Fensterfokus); bis dahin steht
            // die volle Fläche. Apps zeigen beim Beenden oft noch kurz Schwarz.
            pendingZoomBack = a;
            zoomBackRunning = true;
            home.handler.removeCallbacks(startZoomBack);
            home.handler.postDelayed(startZoomBack, 1500);
            if (home.hasWindowFocus()) home.handler.post(startZoomBack);
            return;
        }
        a.start();
        home.handler.removeCallbacks(zoomTimeout);
        home.handler.postDelayed(zoomTimeout, 6000);
    }

    /**
     * Rückkehr auf die Startseite: Wurde gerade eine App verlassen, deren Kachel (oder deren Ordner)
     * sichtbar ist, schrumpft sie umgekehrt zur Start-Animation in die Kachel zurück.
     */
    private ValueAnimator pendingZoomBack;
    private boolean zoomBackRunning;
    private final Runnable startZoomBack = () -> {
        ValueAnimator a = pendingZoomBack;
        pendingZoomBack = null;
        if (a != null) a.start();
    };

    void maybeZoomBack() {
        if (Prefs.reduceMotion(home) || !home.pageRevealed || home.rootView == null) return;
        String pkg = appLeftByHome(home, System.currentTimeMillis(), true);
        Log.i(TAG, "Zoom back: " + pkg);
        if (pkg == null) return;
        TileView target = null;
        for (TileView tv : home.tileViews) {
            Tile t = tv.tile;
            if (pkg.equals(t.pkg)) target = tv;
            if (target == null && t.members != null) {
                for (Tile m : t.members) if (pkg.equals(m.pkg)) target = tv;
            }
            if (target != null) break;
        }
        // Nicht isShown(): Bis onResume ist das Fenster der gestoppten Seite noch unsichtbar geschaltet.
        if (target == null || target.getVisibility() != View.VISIBLE || !target.isAttachedToWindow()) {
            Log.i(TAG, "zoom back: no tile " + (target == null ? "found" : "shown"));
            return;
        }
        int[] loc = new int[2];
        target.getLocationOnScreen(loc);
        int sh = home.rootView.getHeight();
        if (loc[1] + target.getHeight() <= 0 || loc[1] >= sh) {
            Log.i(TAG, "zoom back: tile off screen " + loc[1]);
            return;
        }
        capture(target);
        zoomAnim(true);
    }

    /** Hintergrundfarbe der Kachel: Mittel aus Pixeln knapp innerhalb der vier Ränder. */
    private static int edgeColor(android.graphics.Bitmap b) {
        int w = b.getWidth(), h = b.getHeight();
        int in = Math.max(2, Math.round(Math.min(w, h) * 0.04f));
        int[][] pts = {
            {in, h / 2}, {w - 1 - in, h / 2}, {w / 2, in}, {w / 2, h - 1 - in},
            {in, h / 4}, {w - 1 - in, h / 4}, {in, h * 3 / 4}, {w - 1 - in, h * 3 / 4},
            {w / 4, in}, {w * 3 / 4, in}, {w / 4, h - 1 - in}, {w * 3 / 4, h - 1 - in},
        };
        long rr = 0, gg = 0, bb = 0;
        int n = 0;
        for (int[] p : pts) {
            int c = b.getPixel(p[0], p[1]);
            if (android.graphics.Color.alpha(c) < 200) continue;
            rr += android.graphics.Color.red(c);
            gg += android.graphics.Color.green(c);
            bb += android.graphics.Color.blue(c);
            n++;
        }
        if (n == 0) return Color.BLACK;
        return Color.rgb((int) (rr / n), (int) (gg / n), (int) (bb / n));
    }

    /** Fensterfokus der Startseite geändert. */
    void onWindowFocus(boolean hasFocus) {
        if (hasFocus && pendingZoomBack != null) {
            home.handler.removeCallbacks(startZoomBack);
            startZoomBack.run();
        } else if (hasFocus && zoomOverlay != null && !zoomBackRunning) {
            // Zurück auf der Startseite, die Start-Fläche steht noch (durchsichtige App): weg damit (B39).
            remove(true);
        }
    }

    void remove(boolean animated) {
        home.handler.removeCallbacks(zoomTimeout);
        home.handler.removeCallbacks(startZoomBack);
        pendingZoomBack = null;
        zoomBackRunning = false;
        final View iv = zoomOverlay;
        zoomOverlay = null;
        if (iv == null || home.rootView == null) return;
        if (animated) {
            iv.animate().alpha(0f).setDuration(Ui.ms(250)).withEndAction(() -> home.rootView.removeView(iv)).start();
        } else {
            home.rootView.removeView(iv);
        }
    }

    /** stillResumed: auch eine App gelten lassen, deren Pause Android noch nicht eingetragen hat (onStart). */
    static String appLeftByHome(Context c, long pressWall, boolean stillResumed) {
        try {
            android.app.usage.UsageStatsManager usm =
                    (android.app.usage.UsageStatsManager) c.getSystemService(Context.USAGE_STATS_SERVICE);
            android.app.usage.UsageEvents events = usm.queryEvents(pressWall - 60_000, System.currentTimeMillis());
            android.app.usage.UsageEvents.Event e = new android.app.usage.UsageEvents.Event();
            String own = c.getPackageName();
            String lastPkg = null;
            long lastPause = 0;
            String resumedPkg = null;
            while (events.hasNextEvent()) {
                events.getNextEvent(e);
                if (own.equals(e.getPackageName())) continue;
                if (e.getEventType() == android.app.usage.UsageEvents.Event.ACTIVITY_PAUSED) {
                    lastPkg = e.getPackageName();
                    lastPause = e.getTimeStamp();
                    resumedPkg = null;
                } else if (e.getEventType() == android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED) {
                    // Wieder vorne: (noch) nicht verlassen.
                    lastPkg = null;
                    resumedPkg = e.getPackageName();
                }
            }
            if (stillResumed && resumedPkg != null) return resumedPkg;
            if (lastPkg == null || Math.abs(pressWall - lastPause) > 1500) return null;
            return lastPkg;
        } catch (RuntimeException ex) {
            Log.w(TAG, "usage stats", ex);
            return null;
        }
    }
}
