package tv.bestseat.launcher;

import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

/** Pfeilnavigation im Kontrollzentrum: Kanten erkennen und bündige Kacheln finden (W85, W86). */
final class CcFocus {

    private CcFocus() {
    }

    /**
     * Liegt next wirklich jenseits der Kante von from (nicht nur schräg daneben)? Android springt sonst z. B.
     * von der Kachel „Settings“ mit Pfeil rechts hoch zum Profilbild, weil es etwas weiter rechts endet.
     */
    static boolean beyondEdge(View from, View next, int dir) {
        if (next == null) return false;
        int[] a = new int[2];
        int[] b = new int[2];
        from.getLocationOnScreen(a);
        next.getLocationOnScreen(b);
        int tol = Ui.dp(from.getContext(), 4);
        switch (dir) {
            case View.FOCUS_RIGHT: return b[0] >= a[0] + from.getWidth() - tol;
            case View.FOCUS_LEFT: return b[0] + next.getWidth() <= a[0] + tol;
            case View.FOCUS_DOWN: return b[1] >= a[1] + from.getHeight() - tol;
            default: return b[1] + next.getHeight() <= a[1] + tol;
        }
    }

    /**
     * Kachel in der Reihe von next, die mit from bündig ist: Steht from rechts in seiner Reihe (oder in der
     * rechten Spalte), die mit gleicher rechter Kante, steht es links, die mit gleicher linker Kante.
     */
    static View alignedInRow(View from, View next) {
        // Nur waagrechte Reihen (nicht die senkrechte Spalte neben der großen Kachel).
        if (!(next.getParent() instanceof LinearLayout)
                || ((LinearLayout) next.getParent()).getOrientation() != LinearLayout.HORIZONTAL) return null;
        ViewGroup row = (ViewGroup) next.getParent();
        if (row == from.getParent()) return null;
        boolean right;
        if (from.getParent() instanceof LinearLayout
                && ((LinearLayout) from.getParent()).getOrientation() == LinearLayout.HORIZONTAL) {
            ViewGroup own = (ViewGroup) from.getParent();
            int index = own.indexOfChild(from);
            if (own.getChildCount() < 2) return null;
            if (index == own.getChildCount() - 1) right = true;
            else if (index == 0) right = false;
            else return null;
        } else {
            right = CcTiles.SIDE_COLUMN.equals(((View) from.getParent()).getTag());
            if (!right) return null;
        }
        int[] f = new int[2];
        from.getLocationOnScreen(f);
        int edge = right ? f[0] + from.getWidth() : f[0];
        int tol = Ui.dp(from.getContext(), 4);
        int[] c = new int[2];
        for (int i = 0; i < row.getChildCount(); i++) {
            View v = row.getChildAt(i);
            if (!v.isFocusable() || v.getVisibility() != View.VISIBLE) continue;
            v.getLocationOnScreen(c);
            if (Math.abs((right ? c[0] + v.getWidth() : c[0]) - edge) <= tol) return v;
        }
        return null;
    }

    static int arrowDirection(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP: return View.FOCUS_UP;
            case KeyEvent.KEYCODE_DPAD_DOWN: return View.FOCUS_DOWN;
            case KeyEvent.KEYCODE_DPAD_LEFT: return View.FOCUS_LEFT;
            case KeyEvent.KEYCODE_DPAD_RIGHT: return View.FOCUS_RIGHT;
            default: return 0;
        }
    }
}
