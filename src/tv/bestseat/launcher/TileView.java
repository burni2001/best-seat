package tv.bestseat.launcher;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Kachel mit Fokus-Zoom, Schatten und Parallax: Beim Fokuswechsel kippt sie kurz in
 * Richtung der gedrückten Pfeiltaste und ein Glanz läuft darüber (die Fernbedienung
 * hat kein Touchpad, daher dieses „Wippen“ statt Dauer-Parallax).
 */
final class TileView extends FrameLayout {

    /** tvOS: 250×150 → 306×184 bei Fokus. */
    static final float FOCUS_SCALE = 1.224f;

    final Tile tile;
    private final View gloss;
    private ObjectAnimator wiggle;
    private final float radius;
    /** Beim Abbild für den Start-Zoom: ohne Lichtkante, sonst steht ein Schein auf der Fläche in Kachelfarbe. */
    private boolean bare;
    /** Glass: Lichtkante an (nur mit Fokus) und Stand des Lichtlaufs 0..1 (-1 = keiner). */
    private boolean edgeOn;
    private float sweep = -1f;
    private ValueAnimator sweepAnim;

    private void glassEdge(boolean focused) {
        if (!LiquidGlass.on || tile.kind == Tile.FOLDER) return;
        if (sweepAnim != null) sweepAnim.cancel();
        sweepAnim = null;
        edgeOn = focused;
        sweep = -1f;
        if (focused && !Prefs.reduceMotion(getContext())) {
            // Licht läuft unten von links nach rechts und gleichzeitig oben von rechts nach links.
            ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
            a.setDuration(Ui.ms(320)); // so schnell wie der Fokus-Zoom: nur am Rand wahrnehmbar
            a.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
            a.addUpdateListener(an -> {
                sweep = (float) an.getAnimatedValue();
                invalidate();
            });
            a.addListener(new AnimEnd(() -> {
                sweep = -1f;
                invalidate();
            }));
            sweepAnim = a;
            a.start();
        }
        invalidate();
    }

    /** Kachel ohne Lichtkante und Glanzstreifen auf canvas zeichnen (Bild für den Start-Zoom). */
    void drawBare(android.graphics.Canvas canvas) {
        float glossAlpha = gloss.getAlpha();
        bare = true;
        gloss.setAlpha(0f);
        try {
            draw(canvas);
        } finally {
            bare = false;
            gloss.setAlpha(glossAlpha);
        }
    }
    private final android.graphics.RectF edgeBox = new android.graphics.RectF();

    TileView(Context context, Tile tile, int width, int height) {
        super(context);
        this.tile = tile;
        setFocusable(true);
        setClickable(true);
        setLongClickable(true);
        setContentDescription(tile.label);
        // Radius wie tvOS: 30 bei 250 Breite (12 %).
        radius = width * 0.12f;
        setOutlineProvider(new RoundedOutline(radius));
        setClipToOutline(true);
        setElevation(Ui.dp(context, 2));
        if (Ui.light && tile.kind != Tile.FOLDER) {
            // Helles Design: Der gerichtete Schatten reichte weit nach unten; schwächer, weicher.
            setOutlineSpotShadowColor(android.graphics.Color.argb(110, 0, 0, 0));
        }
        if (tile.kind == Tile.FOLDER) {
            // Ordnerfläche ist halbtransparent: ein Schatten würde als dunkles Rechteck durchscheinen.
            setOutlineAmbientShadowColor(android.graphics.Color.TRANSPARENT);
            setOutlineSpotShadowColor(android.graphics.Color.TRANSPARENT);
        }
        setCameraDistance(9000 * getResources().getDisplayMetrics().density);

        if (tile.banner != null) {
            ImageView image = new ImageView(context);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setImageDrawable(tile.banner);
            addView(image, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        } else {
            setBackground(fallbackBackground(tile.color));
            LinearLayout inner = new LinearLayout(context);
            inner.setOrientation(LinearLayout.VERTICAL);
            inner.setGravity(Gravity.CENTER);
            // Settings-Kachel: nur ein großes Zahnrad, ohne Schrift (W87); der Name steht beim Fokus darunter.
            boolean bigIcon = tile.kind == Tile.SETTINGS;
            if (tile.icon != null) {
                ImageView icon = new ImageView(context);
                icon.setImageDrawable(tile.icon);
                int s = Math.round(height * (bigIcon ? 0.62f : 0.46f));
                inner.addView(icon, new LinearLayout.LayoutParams(s, s));
            }
            if (!bigIcon) {
                TextView label = Ui.text(context, tile.label, 11, Color.argb(230, 255, 255, 255), true);
                label.setGravity(Gravity.CENTER);
                label.setPadding(Ui.dp(context, 6), Ui.dp(context, 3), Ui.dp(context, 6), 0);
                inner.addView(label);
            }
            addView(inner, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        }


        // Diagonales Lichtband: breiter, weicher Schein (Wunsch des Nutzers: Streifen viel breiter),
        // um 28° gedreht, wandert quer über die Kachel. Die Kachel schneidet ihn an den Ecken ab.
        gloss = new View(context);
        gloss.setBackground(glossDrawable());
        gloss.setAlpha(0f);
        gloss.setRotation(28f);
        LayoutParams glp = new LayoutParams(Math.round(width * 1.1f), Math.round(height * 3f),
                Gravity.CENTER_VERTICAL | Gravity.START);
        addView(gloss, glp);

        updateAvailability();
        setLayoutParams(new LinearLayout.LayoutParams(width, height));
    }

    @Override
    protected void dispatchDraw(android.graphics.Canvas canvas) {
        super.dispatchDraw(canvas);
        // Glass: leichte Lichtkante auf App-Kacheln (Ordner haben ihr eigenes Glas).
        // Nur auf der fokussierten Kachel (Wunsch des Nutzers), beim Fokus mit einem Lichtlauf entlang der Kanten.
        if (LiquidGlass.on && tile.kind != Tile.FOLDER && !bare && edgeOn) {
            edgeBox.set(0, 0, getWidth(), getHeight());
            // Die ruhende Kante kommt erst mit dem Ende des Lichtlaufs: in dessen zweiter Hälfte eingeblendet.
            float rest = sweep < 0f ? 1f : Math.max(0f, (sweep - 0.5f) * 2f);
            if (rest > 0f) {
                LiquidGlass.edge(canvas, edgeBox, radius, LiquidGlass.density(), 0.35f * rest);
                LiquidGlass.glint(canvas, edgeBox, radius, rest);
            }
            if (sweep >= 0f) LiquidGlass.sweep(canvas, edgeBox, radius, LiquidGlass.density(), sweep);
        }
    }

    /** Weicher, breiter Lichtschein: langsam ansteigend, kurze helle Mitte. */
    static GradientDrawable glossDrawable() {
        return new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[] {Color.TRANSPARENT, Color.argb(10, 255, 255, 255), Color.argb(26, 255, 255, 255),
                        Color.argb(48, 255, 255, 255), Color.argb(26, 255, 255, 255), Color.argb(10, 255, 255, 255),
                        Color.TRANSPARENT});
    }

    private GradientDrawable fallbackBackground(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[2] = Math.min(1f, hsv[2] + 0.12f);
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {Color.HSVToColor(hsv), color});
        return d;
    }

    /** Pulsieren der grauen Stick-Kacheln, solange der Stick fehlt (W130). */
    private android.animation.ValueAnimator pulse;
    private final Runnable stopPulse = this::stopPulse;

    void updateAvailability() {
        // Fehlende USB-Apps: kein Symbol, dafür blass und grau (Wunsch des Nutzers).
        stopPulse();
        setAlpha(tile.available ? 1f : GHOST_ALPHA);
        if (!tile.available) {
            // Kurz nach dem Erscheinen der Startseite pulsieren sie langsam: man sieht, dass noch etwas kommt.
            long left = HomeActivity.ghostPulseLeft();
            if (left > 0 && !Prefs.reduceMotion(getContext())) {
                pulse = android.animation.ValueAnimator.ofFloat(GHOST_ALPHA, 0.62f);
                pulse.setDuration(Ui.ms(900));
                pulse.setRepeatMode(android.animation.ValueAnimator.REVERSE);
                pulse.setRepeatCount(android.animation.ValueAnimator.INFINITE);
                pulse.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
                pulse.addUpdateListener(a -> setAlpha((float) a.getAnimatedValue()));
                pulse.start();
                postDelayed(stopPulse, left);
            }
        }
        if (tile.available) {
            setLayerType(LAYER_TYPE_NONE, null);
        } else {
            android.graphics.ColorMatrix cm = new android.graphics.ColorMatrix();
            cm.setSaturation(0f);
            android.graphics.Paint grey = new android.graphics.Paint();
            grey.setColorFilter(new android.graphics.ColorMatrixColorFilter(cm));
            setLayerType(LAYER_TYPE_HARDWARE, grey);
        }
    }

    private static final float GHOST_ALPHA = 0.32f;

    private void stopPulse() {
        removeCallbacks(stopPulse);
        if (pulse == null) return;
        pulse.cancel();
        pulse = null;
        if (!tile.available) setAlpha(GHOST_ALPHA);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        removeCallbacks(stopPulse);
        if (pulse != null) {
            pulse.cancel();
            pulse = null;
        }
    }

    /** Fokus-Animation; dx/dy = Richtung der letzten Pfeiltaste (-1, 0, 1). */
    void focus(boolean focused, int dx, int dy) {
        float dp = getResources().getDisplayMetrics().density;
        animate().cancel();
        glassEdge(focused);
        if (focused) {
            setRotationY(dx * 9f);
            setRotationX(-dy * 9f);
            // Fokus: Schatten deutlicher (höher und im hellen Design dunkler).
            if (Ui.light && tile.kind != Tile.FOLDER) setOutlineSpotShadowColor(android.graphics.Color.argb(190, 0, 0, 0));
            animate().scaleX(FOCUS_SCALE).scaleY(FOCUS_SCALE).translationZ(24 * dp)
                    .rotationX(0f).rotationY(0f)
                    .setInterpolator(new OvershootInterpolator(1.0f)).setDuration(Ui.ms(320)).start();
            if (!Prefs.shine(getContext())) return;
            // Wegen der Schräglage muss das Band ein Stück weiter außen starten und enden.
            float w = getWidth();
            float band = gloss.getLayoutParams().width;
            float extra = getHeight() * 0.6f;
            float left = -band - extra;
            float right = w + extra;
            gloss.setTranslationX(dx < 0 ? right : left);
            gloss.setAlpha(1f);
            gloss.animate().cancel();
            gloss.animate().translationX(dx < 0 ? left : right)
                    .setInterpolator(new DecelerateInterpolator()).setDuration(Ui.ms(900))
                    .withEndAction(() -> gloss.setAlpha(0f)).start();
        } else {
            if (Ui.light && tile.kind != Tile.FOLDER) setOutlineSpotShadowColor(android.graphics.Color.argb(110, 0, 0, 0));
            animate().scaleX(1f).scaleY(1f).translationZ(0f).rotationX(0f).rotationY(0f)
                    .setInterpolator(new DecelerateInterpolator()).setDuration(Ui.ms(180)).start();
            gloss.animate().cancel();
            gloss.setAlpha(0f);
        }
    }

    /** OK gedrückt: Kachel gibt nach (push-in); losgelassen: federt auf Fokusgröße zurück. */
    void press(boolean down) {
        if (!isFocused()) return;
        animate().cancel();
        if (down) {
            animate().scaleX(FOCUS_SCALE * 0.93f).scaleY(FOCUS_SCALE * 0.93f)
                    .setInterpolator(new DecelerateInterpolator(1.5f)).setDuration(Ui.ms(90)).start();
        } else {
            animate().scaleX(FOCUS_SCALE).scaleY(FOCUS_SCALE)
                    .setInterpolator(new OvershootInterpolator(2f)).setDuration(Ui.ms(260)).start();
        }
    }

    /** Wackeln im Verschiebe-Modus (wie tvOS), ruhig: kleiner Ausschlag, langsamer (Wunsch des Nutzers). */
    void setWiggle(boolean on) {
        if (on && wiggle == null) {
            wiggle = ObjectAnimator.ofFloat(this, "rotation", -1.3f, 1.3f);
            wiggle.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
            wiggle.setDuration(Ui.ms(280));
            wiggle.setRepeatMode(ValueAnimator.REVERSE);
            wiggle.setRepeatCount(ValueAnimator.INFINITE);
            wiggle.start();
        } else if (!on && wiggle != null) {
            wiggle.cancel();
            wiggle = null;
            setRotation(0f);
        }
    }
}
