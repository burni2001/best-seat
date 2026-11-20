package tv.bernhard.launcher;

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

    TileView(Context context, Tile tile, int width, int height) {
        super(context);
        this.tile = tile;
        setFocusable(true);
        setClickable(true);
        setLongClickable(true);
        setContentDescription(tile.label);
        // Radius wie tvOS: 30 bei 250 Breite (12 %).
        float radius = width * 0.12f;
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

    void updateAvailability() {
        // Fehlende USB-Apps: kein Symbol, dafür blass und grau (Wunsch des Nutzers).
        setAlpha(tile.available ? 1f : 0.32f);
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

    /** Fokus-Animation; dx/dy = Richtung der letzten Pfeiltaste (-1, 0, 1). */
    void focus(boolean focused, int dx, int dy) {
        float dp = getResources().getDisplayMetrics().density;
        animate().cancel();
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
