package tv.bernhard.launcher;

import android.graphics.Outline;
import android.view.View;
import android.view.ViewOutlineProvider;

/** Abgerundete Kontur mit veränderlichem Radius (für die Start-Animation). */
final class RoundOutline extends ViewOutlineProvider {
    private final float[] radius;

    RoundOutline(float[] radius) {
        this.radius = radius;
    }

    @Override
    public void getOutline(View view, Outline outline) {
        outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), Math.max(0f, radius[0]));
    }
}
