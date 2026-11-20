package tv.bernhard.launcher;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;

/** Ende einer Animation (eigene Klasse: d8 im Build kann keine anonymen Klassen). */
final class ZoomEnd extends AnimatorListenerAdapter {
    private final Runnable end;

    ZoomEnd(Runnable end) {
        this.end = end;
    }

    @Override
    public void onAnimationEnd(Animator animation) {
        end.run();
    }
}
