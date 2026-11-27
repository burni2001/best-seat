package tv.bestseat.launcher;

import android.graphics.Outline;
import android.view.View;
import android.view.ViewOutlineProvider;

/** Rundet die Ecken einer View ab, zusammen mit setClipToOutline(true). */
public final class RoundedOutline extends ViewOutlineProvider {

    private final float radius;
    private float shadowAlpha = 1f;

    public RoundedOutline(float radius) {
        this.radius = radius;
    }

    @Override
    public void getOutline(View view, Outline outline) {
        outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
        outline.setAlpha(shadowAlpha);
    }

    /** Strength of the shadow this outline casts (0..1); the caller invalidates the outline. */
    void shadowAlpha(float alpha) {
        shadowAlpha = alpha;
    }

    float currentShadowAlpha() {
        return shadowAlpha;
    }
}
