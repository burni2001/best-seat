package tv.bestseat.launcher;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

/** Karte im Top Shelf: Bild des Inhalts, Titel als Notlösung, wenn das Bild fehlt. */
final class ShelfCard extends FrameLayout {

    static final float FOCUS_SCALE = 1.08f;

    final ShelfContent.Item item;
    final ImageView image;
    private final TextView fallback;
    private final View gloss;

    ShelfCard(Context context, ShelfContent.Item item, int width, int height) {
        super(context);
        this.item = item;
        setFocusable(true);
        setClickable(true);
        float radius = Ui.dp(context, 12); // tvOS: Radius 24 px
        setOutlineProvider(new RoundedOutline(radius));
        setClipToOutline(true);
        setElevation(Ui.dp(context, 4));
        setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {Color.rgb(52, 56, 66), Color.rgb(30, 32, 38)}));
        setCameraDistance(9000 * getResources().getDisplayMetrics().density);

        fallback = Ui.text(context, item.title != null ? item.title : "", 15, Ui.TEXT, true);
        fallback.setMaxLines(3);
        // Titel nur, wenn das Bild fehlt; beim Laden und Einblenden nicht durchscheinen lassen.
        if (item.image != null) fallback.setVisibility(INVISIBLE);
        fallback.setGravity(Gravity.CENTER);
        fallback.setPadding(Ui.dp(context, 12), 0, Ui.dp(context, 12), 0);
        addView(fallback, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        image = new ImageView(context);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        addView(image, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        gloss = new View(context);
        gloss.setBackground(TileView.glossDrawable());
        gloss.setAlpha(0f);
        gloss.setRotation(28f);
        addView(gloss, new LayoutParams(Math.round(width * 1.1f), Math.round(height * 3f),
                Gravity.CENTER_VERTICAL | Gravity.START));

        setLayoutParams(new android.widget.LinearLayout.LayoutParams(width, height));
    }

    /** Bild ließ sich nicht laden: Titel zeigen. */
    void showFallback() {
        fallback.setVisibility(VISIBLE);
    }

    void focus(boolean focused, int dx) {
        animate().cancel();
        float dp = getResources().getDisplayMetrics().density;
        if (focused) {
            setRotationY(dx * 7f);
            animate().scaleX(FOCUS_SCALE).scaleY(FOCUS_SCALE).translationZ(16 * dp).rotationY(0f)
                    .setInterpolator(new OvershootInterpolator(1.2f)).setDuration(Ui.ms(320)).start();
            if (!Prefs.shineShelf(getContext())) return;
            float band = gloss.getLayoutParams().width;
            float extra = getHeight() * 0.6f;
            float left = -band - extra;
            float right = getWidth() + extra;
            gloss.setTranslationX(dx < 0 ? right : left);
            gloss.setAlpha(1f);
            gloss.animate().cancel();
            gloss.animate().translationX(dx < 0 ? left : right).setInterpolator(new DecelerateInterpolator())
                    .setDuration(Ui.ms(950)).withEndAction(() -> gloss.setAlpha(0f)).start();
        } else {
            animate().scaleX(1f).scaleY(1f).translationZ(0f).rotationY(0f)
                    .setInterpolator(new DecelerateInterpolator()).setDuration(Ui.ms(180)).start();
            gloss.animate().cancel();
            gloss.setAlpha(0f);
        }
    }
}
