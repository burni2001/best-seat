package tv.bernhard.launcher.allapps;

import android.content.Context;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;

/**
 * ScrollView für das Raster. Eine normale ScrollView scrollt beim Fokuswechsel nur
 * so weit, bis die Kachel sichtbar ist. Der Rand über der ersten bzw. unter der
 * letzten Reihe bliebe dann verdeckt. Hier geht das Ziel in der ersten Reihe gleich
 * ganz nach oben und in der letzten ganz nach unten, in einer einzigen weichen
 * Bewegung (kein zweites Nachscrollen).
 */
final class EdgeScrollView extends ScrollView {

    EdgeScrollView(Context context) {
        super(context);
    }

    @Override
    protected int computeScrollDeltaToGetChildRectOnScreen(Rect rect) {
        int delta = super.computeScrollDeltaToGetChildRectOnScreen(rect);
        if (getChildCount() == 0 || !(getChildAt(0) instanceof ViewGroup)) {
            return delta;
        }
        ViewGroup grid = (ViewGroup) getChildAt(0);
        if (grid.getChildCount() == 0) {
            return delta;
        }
        // rect liegt in Koordinaten des Inhalts, das Raster beginnt dort bei 0.
        View first = grid.getChildAt(0);
        View last = grid.getChildAt(grid.getChildCount() - 1);
        if (rect.top < first.getBottom()) {
            return -getScrollY();
        }
        if (rect.bottom > last.getTop()) {
            int maxScroll = Math.max(0, grid.getHeight() - getHeight());
            return maxScroll - getScrollY();
        }
        return delta;
    }
}
