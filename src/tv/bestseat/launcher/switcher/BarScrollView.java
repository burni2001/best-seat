package tv.bestseat.launcher.switcher;

import android.content.Context;
import android.graphics.Rect;
import android.widget.HorizontalScrollView;

/**
 * Leiste des App Switchers. Scrollt beim Fokuswechsel nicht selbst, das macht
 * SwitcherActivity.scrollToTile() immer weich und bis zum Rand. Android würde
 * sonst nur bis zur Kachel scrollen, und ein weiterer Druck nach außen schöbe
 * den Rest ruckartig nach.
 */
final class BarScrollView extends HorizontalScrollView {

    BarScrollView(Context context) {
        super(context);
    }

    @Override
    protected int computeScrollDeltaToGetChildRectOnScreen(Rect rect) {
        return 0;
    }
}
