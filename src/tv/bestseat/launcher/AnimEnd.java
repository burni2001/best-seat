package tv.bestseat.launcher;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;

/** Ende einer Animation melden (eigene Klasse: d8 im Build kann keine anonymen Klassen). */
final class AnimEnd extends AnimatorListenerAdapter {
    private final Runnable end;

    AnimEnd(Runnable end) {
        this.end = end;
    }

    @Override
    public void onAnimationEnd(Animator animation) {
        end.run();
    }
}
