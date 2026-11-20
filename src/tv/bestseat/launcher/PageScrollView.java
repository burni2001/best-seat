package tv.bestseat.launcher;

import android.content.Context;
import android.graphics.Rect;
import android.widget.ScrollView;

/** ScrollView, die nicht selbst zum Fokus scrollt; die Startseite steuert die Lage selbst. */
final class PageScrollView extends ScrollView {

    interface OnScroll {
        void onScroll(int y);
    }

    OnScroll listener;

    PageScrollView(Context context) {
        super(context);
        setVerticalScrollBarEnabled(false);
        setOverScrollMode(OVER_SCROLL_NEVER);
        setClipChildren(false);
        setFocusable(false);
    }

    private android.animation.ValueAnimator anim;

    /**
     * Weich zur Lage y scrollen (statt smoothScrollTo mit festen 250 ms, Wunsch des Nutzers: zu schnell).
     * Dauer nach Strecke, sanft an- und auslaufend wie tvOS.
     */
    void animateTo(int y) {
        int from = getScrollY();
        int max = Math.max(0, getChildCount() == 0 ? 0 : getChildAt(0).getHeight() - getHeight());
        int to = Math.max(0, Math.min(y, max));
        if (anim != null) anim.cancel();
        if (from == to) return;
        float dp = getResources().getDisplayMetrics().density;
        float distDp = Math.abs(to - from) / dp;
        long ms = Ui.ms(Math.round(Math.max(300, Math.min(580, 260 + distDp * 0.9f))));
        anim = android.animation.ValueAnimator.ofInt(from, to);
        anim.setDuration(ms);
        anim.setInterpolator(new android.view.animation.PathInterpolator(0.3f, 0f, 0.2f, 1f));
        anim.addUpdateListener(a -> scrollTo(0, (int) a.getAnimatedValue()));
        anim.start();
    }

    @Override
    protected int computeScrollDeltaToGetChildRectOnScreen(Rect rect) {
        return 0;
    }

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);
        if (listener != null) listener.onScroll(t);
    }
}
