package tv.bestseat.launcher;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;

/** Ende der Einfrier-Animation melden (eigene Klasse statt anonymer Klasse, d8 verträgt keine). */
final class FreezeEnd extends AnimatorListenerAdapter {
    private final TopShelf home;
    private final ValueAnimator anim;

    FreezeEnd(TopShelf home, ValueAnimator anim) {
        this.home = home;
        this.anim = anim;
    }

    @Override
    public void onAnimationEnd(Animator animation) {
        home.freezeAnimEnded(anim);
    }
}
