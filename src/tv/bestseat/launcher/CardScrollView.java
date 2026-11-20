package tv.bestseat.launcher;

import android.content.Context;
import android.graphics.Rect;
import android.widget.HorizontalScrollView;

/** Waagrechte Reihe im Top Shelf, die nicht selbst zum Fokus scrollt (die Startseite richtet die Karte links aus). */
final class CardScrollView extends HorizontalScrollView {

    CardScrollView(Context context) {
        super(context);
        setHorizontalScrollBarEnabled(false);
        setOverScrollMode(OVER_SCROLL_NEVER);
        setFocusable(false);
    }

    @Override
    protected int computeScrollDeltaToGetChildRectOnScreen(Rect rect) {
        return 0;
    }
}
