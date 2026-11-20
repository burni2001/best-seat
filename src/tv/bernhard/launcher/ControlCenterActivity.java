package tv.bernhard.launcher;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.text.TextUtils;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/**
 * Kontrollzentrum: Milchglas-Leiste rechts über der laufenden App (wie tvOS).
 * Kacheln für Schoner, Ambilight, Hue Sync, Bild, Ton, Eingänge, App Switcher, VPN, Einstellungen.
 * Schließt mit Zurück/Menü, nach jeder Aktion und nach 30 s ohne Taste (nichts bleibt lange stehen).
 */
public final class ControlCenterActivity extends Activity {

    static final String ACTION = "tv.bernhard.launcher.CONTROL_CENTER";
    private static final String TAG = "Launcher.Control";
    private static final long IDLE_CLOSE_MS = 30_000;
    // Maße exakt nach dem tvOS-Kontrollzentrum (Design/control center.png, 3024 px Bildbreite = 960 dp):
    // Kachel 150 px, Abstand 26 px, Rand 37 px, Kasten 754 px (quadratisch), Radien Kachel 37 px, Kasten 72 px.
    private static final float PANEL_DP = 238.75f;
    /** Kacheln je Reihe: höchstens 2 (B12), Kasten nur so hoch wie nötig (W16). */
    private static final int COLS = 2;
    private static final float GAP_DP = 8.25f;
    private static final float PANEL_RADIUS_DP = 23f;
    private static final float TILE_RADIUS_DP = 12f;
    private static final float FOCUS_SCALE = 1.05f;
    /** Weichgezeichnetes Abbild der Startseite, von HomeActivity vor dem Start gesetzt. */
    static android.graphics.Bitmap homeBlur;
    private static final float TILE_DP = 47.5f;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable idleClose = this::close;
    private FrameLayout root;
    private LinearLayout panel;
    private LinearLayout body;
    private StatusPill pill;
    /** Höhe des Kastens mit den Kacheln; die Profilseite übernimmt sie. */
    private int mainPanelHeight;
    /** Langes OK auf einem anderen Profil: nach dem Neuaufbau gleich bearbeiten. */
    private boolean editAfterSwitch;
    /** Profil gewechselt ohne Neustart: beim Schließen die Startseite neu laden. */
    private boolean profileSwitched;

    /** Feste Höhe in Pixeln oder 0 = so hoch wie der Inhalt. */
    private void setPanelHeight(int px) {
        ViewGroup.LayoutParams lp = panel.getLayoutParams();
        int h = px > 0 ? px : ViewGroup.LayoutParams.WRAP_CONTENT;
        if (lp != null && lp.height != h) {
            lp.height = h;
            panel.setLayoutParams(lp);
        }
    }
    private boolean closing;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.apply(this);
        root = new FrameLayout(this);
        // Abdunkeln von rechts nach links, die App links bleibt erkennbar. Eigene Ebene, damit nur sie
        // einblendet; die Pille steht vom ersten Bild an (Übergabe von der Startseite ohne Blinken).
        dim = new View(this);
        android.graphics.Bitmap blurred = homeBlur;
        homeBlur = null;
        // Wie tvOS: Der Bildschirm um das Kontrollzentrum bleibt unverändert, kein Abdunkeln mehr (B48).
        // Die Ebene bleibt als Klickfläche zum Schließen.
        dim.setBackgroundColor(Color.TRANSPARENT);
        root.addView(dim, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        root.setOnClickListener(v -> close());

        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 6);
        // Unten weniger: Die letzte Reihe bringt ihren Abstand (GAP_DP) schon mit, zusammen 11,75 dp wie oben.
        panel.setPadding(pad, pad, pad, Ui.dp(this, 3.5f));
        if (blurred != null && Prefs.blurEnabled(this)) {
            // Milchglas: nur was hinter dem Kasten liegt, weichgezeichnet (W102).
            panel.setBackground(new GlassDrawable(blurred, panel, Ui.dp(this, PANEL_RADIUS_DP),
                    Ui.light ? Color.argb(150, 238, 240, 244) : Color.argb(120, 28, 30, 36),
                    Ui.MATERIAL_STROKE, Math.max(1, Ui.dp(this, 0.5f))));
        } else {
            // tvOS-Material: dunkle Glasfläche mit feinem hellem Rand.
            android.graphics.drawable.GradientDrawable pbg = Ui.rounded(Ui.CONTROL_PANEL, Ui.dp(this, PANEL_RADIUS_DP));
            pbg.setStroke(Math.max(1, Ui.dp(this, 0.5f)), Ui.MATERIAL_STROKE);
            panel.setBackground(pbg);
        }
        panel.setElevation(Ui.dp(this, 30));
        // Children stay inside their own bounds, so the scrolling list never covers clock and date (B10)
        panel.setClipChildren(true);
        panel.setClipToPadding(true);

        // Uhrzeit und Profilbild stehen jetzt in der Pille oben rechts (StatusPill), nicht mehr im Kasten.
        shown = new java.lang.ref.WeakReference<>(this);
        pill = new StatusPill(this, true);
        pill.avatar.setOnClickListener(v -> showProfile());
        pill.controls.setOnClickListener(v -> showMain());
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        // Clip so tiles never slide over the clock and date when the list scrolls (B10)
        scroll.setClipChildren(true);
        scroll.setClipToPadding(true);
        scroll.setVerticalFadingEdgeEnabled(true);
        scroll.setFadingEdgeLength(Ui.dp(this, 24));
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setClipChildren(false);
        body.setClipToPadding(false);
        body.setPadding(Ui.dp(this, 6), Ui.dp(this, 6), Ui.dp(this, 6), 0);
        scroll.addView(body);
        // Höhe nach Inhalt; passt er nicht, begrenzt der Kasten ihn auf den Bildschirm und die Liste scrollt.
        panel.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        // Kasten direkt unter der Pille, rechtsbündig mit ihr (wie tvOS).
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Ui.dp(this, PANEL_DP),
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END | Gravity.TOP);
        lp.topMargin = Ui.dp(this, StatusPill.TOP_DP + StatusPill.HEIGHT_DP + 8.5f);
        lp.rightMargin = Ui.dp(this, StatusPill.RIGHT_DP);
        lp.bottomMargin = Ui.dp(this, 20);
        root.addView(panel, lp);
        root.addView(pill, pill.frameParams());
        setContentView(root);

        showMain();
        // Fokus beim Öffnen auf dem Schalter-Symbol in der Pille (zwischen Uhr und Profilbild).
        pill.controls.requestFocus();
        pill.post(() -> pill.controls.requestFocus());
        // Pille wächst nach links um das Schalter-Symbol, der Kasten klappt darunter auf.
        // Erst wachsen, wenn die Pille der Startseite übergeben ist (handoff), sonst stehen zwei Uhren da.
        pill.collapseControls();
        pill.setBare(true);
        // Bis zur Übergabe scheint die Pille der Startseite durch; zwei übereinander sähen dichter aus.
        pill.setAlpha(0f);
        // Kasten quillt aus der Pille heraus (wie ein Trichter): erst schmal und flach direkt unter der
        // Pille, dann in voller Größe. Drehpunkt oben rechts, wo die Pille sitzt.
        panel.setPivotX(Ui.dp(this, PANEL_DP));
        panel.setPivotY(0);
        reduceMotion = Prefs.reduceMotion(this);
        if (!reduceMotion) {
            panel.setScaleX(FUNNEL_X);
            panel.setScaleY(FUNNEL_Y);
            panel.setTranslationY(-Ui.dp(this, FUNNEL_LIFT_DP));
        }
        panel.setAlpha(0f);
        dim.setAlpha(0f);
        // Abdunkeln erst nach der Übergabe (handoff): Sonst dunkelt es die Pille der Startseite darunter mit ab.
        android.animation.ObjectAnimator fadeIn = android.animation.ObjectAnimator.ofFloat(panel, "alpha", 0f, 1f);
        fadeIn.setDuration(Ui.ms(reduceMotion ? 260 : 160));
        fadeIn.start();
        panel.animate().scaleX(1f).scaleY(1f).translationY(0f).setInterpolator(new DecelerateInterpolator(1.8f))
                .setDuration(Ui.ms(480)).start();
        // Pille der Startseite erst ausblenden, wenn die eigene gezeichnet ist.
        // Das System blendet das durchsichtige Fenster selbst kurz ein (am TV gemessen gut 200 ms); bis es
        // ganz da ist, liegen beide Pillen genau übereinander. onEnterAnimationComplete kommt hier nicht.
        handler.postDelayed(handoff, 280);
    }

    /** W83: Kasten nur ein- und ausblenden, ohne Trichter. */
    private boolean reduceMotion;
    private static final float FUNNEL_X = 0.3f;
    private static final float FUNNEL_Y = 0.06f;
    private static final int FUNNEL_LIFT_DP = 28;
    private View dim;

    /** Das zuletzt geöffnete CC. Nur dieses steuert die Pille der Startseite (B50). */
    private static java.lang.ref.WeakReference<ControlCenterActivity> shown;

    /**
     * Ein altes CC wird von Android oft erst Sekunden später zerstört. Öffnet man inzwischen
     * ein neues, darf das alte die Pille der Startseite nicht wieder einblenden.
     */
    private void setHomePillHidden(boolean hidden) {
        if (shown != null && shown.get() == this) {
            hidingHomePill = hidden;
            HomeActivity.setPillHidden(hidden);
        }
    }

    private boolean hidingHomePill;

    /** Für HomeActivity.onResume: Pille der Startseite nicht einblenden, solange das CC sie vertritt. */
    static boolean hidesHomePill() {
        ControlCenterActivity cc = shown == null ? null : shown.get();
        return cc != null && cc.hidingHomePill && !cc.isDestroyed();
    }

    static final String EXTRA_CLOSE = "close";

    /**
     * Home bei offenem CC (W104): Android holt die Startseite sofort nach vorn. Das CC kommt ohne
     * Übergang zurück und schließt mit seiner Animation (Pille ← CC, rückwärts wie beim Öffnen).
     */
    static boolean closeAnimated(Activity from) {
        ControlCenterActivity cc = shown == null ? null : shown.get();
        // Auch während des Schließens: Der zweite Druck des Doppelklicks schiebt die Startseite erneut nach vorn.
        if (cc == null || cc.isFinishing() || cc.isDestroyed() || cc.overApp()) return false;
        cc.handler.removeCallbacks(cc.stopFinish);
        from.startActivity(new Intent(from, ControlCenterActivity.class).putExtra(EXTRA_CLOSE, true),
                android.app.ActivityOptions.makeCustomAnimation(from, 0, 0).toBundle());
        return true;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent.getBooleanExtra(EXTRA_CLOSE, false)) {
            overridePendingTransition(0, 0);
            close();
        }
    }

    private final Runnable stopFinish = this::finish;

    @Override
    protected void onDestroy() {
        super.onDestroy();
        setHomePillHidden(false);
        if (shown != null && shown.get() == this) shown = null;
    }

    private boolean handedOff;
    private final Runnable handoff = () -> {
        if (handedOff) return;
        handedOff = true;
        // Erst die eigene Pille zeigen, ein Bild später die der Startseite verstecken (nie eine Lücke).
        pill.setAlpha(1f);
        // Wachsen erst danach, sonst steht für ein Bild die zweite Uhr daneben.
        root.postOnAnimation(() -> root.postOnAnimation(() -> {
            setHomePillHidden(true);
            pill.setBare(false);
            root.postOnAnimation(pill::growControls);
        }));
        dim.animate().alpha(1f).setDuration(Ui.ms(300)).start();
    };

    @Override
    public void onEnterAnimationComplete() {
        super.onEnterAnimationComplete();
        handler.removeCallbacks(handoff);
        if (!closing) handoff.run();
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumedNow = true;
        poke();
    }

    /** Für Doppel-Home (W103): War das CC beim ersten Druck offen, schließt der zweite statt neu zu öffnen. */
    private static boolean resumedNow;
    private static long pausedAt;

    static boolean wasJustOpen() {
        return resumedNow || android.os.SystemClock.uptimeMillis() - pausedAt < 600;
    }

    @Override
    protected void onPause() {
        super.onPause();
        resumedNow = false;
        pausedAt = android.os.SystemClock.uptimeMillis();
        handler.removeCallbacks(idleClose);
    }

    @Override
    protected void onStart() {
        super.onStart();
        handler.removeCallbacks(stopFinish);
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Kurz warten: Home bei offenem CC holt es gleich zurück, um es animiert zu schließen (W104).
        handler.postDelayed(stopFinish, 500);
    }

    private void poke() {
        handler.removeCallbacks(idleClose);
        handler.postDelayed(idleClose, IDLE_CLOSE_MS);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        poke();
        int code = event.getKeyCode();
        if (movingId != null && handleMoveKey(event)) return true;
        if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_MENU) {
            if (event.getAction() == KeyEvent.ACTION_UP) {
                if (code == KeyEvent.KEYCODE_BACK && isProfileSub()) showProfile();
                else if (code == KeyEvent.KEYCODE_BACK && body.getTag() != null) showMain();
                else close();
            }
            return true;
        }
        // W90: Pfeil links vom Schalter-Symbol in der Pille auf die große Kachel (statt schließen).
        if (code == KeyEvent.KEYCODE_DPAD_LEFT && body.getTag() == null && pill.controls != null
                && pill.controls.isFocused() && sideColumn != null && sideColumn.getParent() instanceof ViewGroup) {
            View big = ((ViewGroup) sideColumn.getParent()).getChildAt(0);
            if (big != null && big.isFocusable()) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) big.requestFocus();
                return true;
            }
        }
        // Pfeil links ganz links: im Hauptteil schließen (Leiste kam von rechts), im Untermenü zurück.
        if (code == KeyEvent.KEYCODE_DPAD_LEFT && atLeftEdge()) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                if (isProfileSub()) showProfile();
                else if (body.getTag() != null) showMain();
                else close();
            }
            return true;
        }
        // W85: Am Ende in jeder Richtung (oben, unten, links, rechts) raus aus dem Kontrollzentrum.
        // Nur beim ersten Druck, nicht beim Halten: Durchlaufen einer Liste endet sonst ungewollt.
        int dir = arrowDirection(code);
        if (dir != 0 && !closing) {
            View focused = getCurrentFocus();
            View next = focused == null ? null : android.view.FocusFinder.getInstance()
                    .findNextFocus((ViewGroup) getWindow().getDecorView(), focused, dir);
            if (focused != null && !beyondEdge(focused, next, dir)) {
                if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) close();
                return true;
            }
            // W86: Hoch/runter in eine Reihe mit anderer Aufteilung: auf die bündige Kachel (rechte Kante
            // zuerst, dann linke), z. B. von Ambilight runter auf die kleine Kachel ganz rechts.
            if (next != null && (dir == View.FOCUS_DOWN || dir == View.FOCUS_UP)) {
                View aligned = alignedInRow(focused, next);
                if (aligned != null && aligned != next) {
                    if (event.getAction() == KeyEvent.ACTION_DOWN) aligned.requestFocus(dir);
                    return true;
                }
            }
        }
        return super.dispatchKeyEvent(event);
    }

    /**
     * Liegt next wirklich jenseits der Kante von from (nicht nur schräg daneben)? Android springt sonst z. B.
     * von der Kachel „Settings“ mit Pfeil rechts hoch zum Profilbild, weil es etwas weiter rechts endet.
     */
    private boolean beyondEdge(View from, View next, int dir) {
        if (next == null) return false;
        int[] a = new int[2];
        int[] b = new int[2];
        from.getLocationOnScreen(a);
        next.getLocationOnScreen(b);
        int tol = Ui.dp(this, 4);
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
    private View alignedInRow(View from, View next) {
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
            right = SIDE_COLUMN.equals(((View) from.getParent()).getTag());
            if (!right) return null;
        }
        int[] f = new int[2];
        from.getLocationOnScreen(f);
        int edge = right ? f[0] + from.getWidth() : f[0];
        int tol = Ui.dp(this, 4);
        int[] c = new int[2];
        for (int i = 0; i < row.getChildCount(); i++) {
            View v = row.getChildAt(i);
            if (!v.isFocusable() || v.getVisibility() != View.VISIBLE) continue;
            v.getLocationOnScreen(c);
            if (Math.abs((right ? c[0] + v.getWidth() : c[0]) - edge) <= tol) return v;
        }
        return null;
    }

    private static int arrowDirection(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP: return View.FOCUS_UP;
            case KeyEvent.KEYCODE_DPAD_DOWN: return View.FOCUS_DOWN;
            case KeyEvent.KEYCODE_DPAD_LEFT: return View.FOCUS_LEFT;
            case KeyEvent.KEYCODE_DPAD_RIGHT: return View.FOCUS_RIGHT;
            default: return 0;
        }
    }

    /** Fokus auf einer Kachel der linken Spalte bzw. auf einer Zeile eines Untermenüs. */
    private boolean atLeftEdge() {
        View focused = getCurrentFocus();
        if (focused == null) return false;
        if (body.getTag() != null && !String.valueOf(body.getTag()).startsWith("profile")) return true;
        // Die rechte Spalte neben der großen Kachel ist nicht der linke Rand.
        return focused.getParent() instanceof LinearLayout
                && !SIDE_COLUMN.equals(((LinearLayout) focused.getParent()).getTag())
                && ((LinearLayout) focused.getParent()).getChildAt(0) == focused;
    }

    /** Paket der App, über der das Kontrollzentrum liegt (Doppel-Home aus einer App), sonst nicht gesetzt. */
    static final String EXTRA_OVER_APP = "over_app";

    private boolean overApp() {
        return getIntent() != null && getIntent().getStringExtra(EXTRA_OVER_APP) != null;
    }

    private void close() {
        if (closing) return;
        closing = true;
        handler.removeCallbacks(handoff);
        if (profileSwitched) {
            // Startseite mit dem Layout des neuen Profils aufbauen.
            startActivity(new Intent(this, HomeActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            finish();
            overridePendingTransition(0, 0);
            return;
        }
        // Rückwärts: Kasten fließt in die Pille zurück, das Schalter-Symbol zieht sich ein.
        long d = Ui.ms(300);
        panel.animate().cancel();
        if (!reduceMotion) {
            panel.animate().scaleX(FUNNEL_X).scaleY(FUNNEL_Y).translationY(-Ui.dp(this, FUNNEL_LIFT_DP))
                    .setInterpolator(new android.view.animation.AccelerateInterpolator(1.6f)).setDuration(d).start();
        }
        android.animation.ObjectAnimator fadeOut = android.animation.ObjectAnimator.ofFloat(panel, "alpha",
                panel.getAlpha(), 0f);
        fadeOut.setStartDelay(reduceMotion ? 0 : d * 2 / 3);
        fadeOut.setDuration(reduceMotion ? d : d / 3);
        fadeOut.start();
        pill.shrinkControls(d);
        // Über einer App gibt es keine Pille der Startseite, in die diese zurückkehrt: ausblenden.
        if (overApp()) pill.animate().alpha(0f).setDuration(d).start();
        dim.animate().alpha(0f).setDuration(d).withEndAction(() -> {
            // Pille der Startseite zurück, bevor die eigene verschwindet: kein Blinken.
            setHomePillHidden(false);
            pill.setBare(true);
            root.post(() -> {
                finish();
                overridePendingTransition(0, 0);
            });
        }).start();
    }

    // ---------------------------------------------------------------- Inhalt

    private void showMain() {
        showMain(null);
    }

    /** Hauptseite aufbauen; focusId = Kachel, die den Fokus bekommt (sonst die erste). */
    private void showMain(String focusId) {
        setPanelHeight(0);
        if (wiggleAnim != null) {
            wiggleAnim.cancel();
            wiggleAnim = null;
        }
        body.removeAllViews();
        body.setTag(null);
        pill.setSelected("controls");
        icons.clear();
        tiles.clear();
        boolean vpn = vpnConnected();
        curRow = null;
        sideColumn = null;
        cellIndex = 0;
        View first = null;
        // Reihenfolge und Sichtbarkeit aus den Einstellungen (W12).
        java.util.Set<String> hidden = Prefs.controlHidden(this);
        // tvOS: große Kachel mit zwei daneben, darunter eine Reihe mit zwei normalen Kacheln, ganz unten
        // kleine quadratische Symbol-Kacheln (vier je Reihe). Ab der sechsten Kachel also klein.
        smallFrom = 5;
        for (String id : Prefs.controlOrder(this)) {
            if (hidden.contains(id)) continue;
            buildingId = id;
            View v = addItem(id, vpn);
            v.setTag(id);
            v.setOnLongClickListener(x -> {
                if (movingId == null) tileMenu(id);
                return true;
            });
            if (id.equals(movingId)) wiggle(v);
            if (first == null || id.equals(focusId)) first = v;
        }
        // Weniger als drei Kacheln: rechte Spalte bleibt leer, die große Kachel behält ihre Höhe.
        fillRow();
        if (first != null) first.requestFocus();
        setActive(ControlItems.VPN, vpn);
        queryHueState(0);
    }

    // ---------------------------------------------------------------- Zustand (blaues Symbol)

    private static final ComponentName HUE_RECEIVER =
            new ComponentName("tv.bernhard.huesync", "tv.bernhard.huesync.ControlReceiver");
    private final java.util.Map<String, ControlIcons.Icon> icons = new java.util.HashMap<>();
    private final java.util.Map<String, View> tiles = new java.util.HashMap<>();
    private final java.util.Set<String> active = new java.util.HashSet<>();

    private int iconColor(String id, boolean focused) {
        if (active.contains(id)) return Ui.ACCENT;
        return focused ? Ui.TEXT_ON_FOCUS : Ui.TEXT;
    }

    private void setActive(String id, boolean on) {
        if (on) active.add(id);
        else active.remove(id);
        ControlIcons.Icon icon = icons.get(id);
        View tile = tiles.get(id);
        if (icon != null) icon.setColor(iconColor(id, tile != null && tile.isFocused()));
    }

    /** Ambilight / Hue Sync im Hintergrund schalten (Hue-Sync-App) und den Zustand nachfragen. */
    private void toggleHue(String id, String action) {
        try {
            sendBroadcast(new Intent(action).setComponent(HUE_RECEIVER));
        } catch (RuntimeException e) {
            Toast.makeText(this, "Hue Sync app not available", Toast.LENGTH_SHORT).show();
            return;
        }
        // Sofort umfärben, die Antwort der App bestätigt oder korrigiert es.
        setActive(id, !active.contains(id));
        queryHueState(ControlItems.AMBILIGHT.equals(id) ? 2500 : 700);
        queryHueState(5000);
    }

    /** Zustand von Hue Sync und Ambilight bei der Hue-Sync-App abfragen (ab Hue Sync 1.9.7). */
    private void queryHueState(long delayMs) {
        if (!tiles.containsKey(ControlItems.AMBILIGHT) && !tiles.containsKey(ControlItems.HUE_SYNC)) return;
        handler.postDelayed(() -> {
            if (isFinishing()) return;
            try {
                sendOrderedBroadcast(new Intent("tv.bernhard.huesync.STATE").setComponent(HUE_RECEIVER), null,
                        new HueStateReceiver(this), handler, 0, null, null);
            } catch (RuntimeException ignored) {
            }
        }, delayMs);
    }

    private static final class HueStateReceiver extends android.content.BroadcastReceiver {
        private final java.lang.ref.WeakReference<ControlCenterActivity> owner;

        HueStateReceiver(ControlCenterActivity a) {
            owner = new java.lang.ref.WeakReference<>(a);
        }

        @Override
        public void onReceive(android.content.Context context, Intent intent) {
            ControlCenterActivity a = owner.get();
            String data = getResultData();
            if (a == null || a.isFinishing() || data == null) return;
            for (String part : data.split(";")) {
                String[] kv = part.split("=");
                if (kv.length != 2 || "?".equals(kv[1])) continue;
                if ("sync".equals(kv[0])) a.setActive(ControlItems.HUE_SYNC, "1".equals(kv[1]));
                if ("ambilight".equals(kv[0])) a.setActive(ControlItems.AMBILIGHT, "1".equals(kv[1]));
            }
        }
    }

    // ---------------------------------------------------------------- Sortieren / Ausblenden (W25)

    private String movingId;
    private android.animation.ObjectAnimator wiggleAnim;

    /** Langes OK auf einer Kachel: Verschieben oder Ausblenden, wie auf der Startseite. */
    private void tileMenu(String id) {
        body.removeAllViews();
        body.setTag("tile");
        TextView q = Ui.text(this, ControlItems.title(id), 15, Ui.TEXT, true);
        q.setPadding(Ui.dp(this, 4), 0, 0, Ui.dp(this, 4));
        body.addView(q);
        View move = wideRow("Edit Control Center", () -> {
            movingId = id;
            showMain(id);
        });
        wideRow("Hide from Control Center", () -> hideTile(id));
        wideRow("Cancel", () -> showMain(id));
        move.requestFocus();
    }

    private void hideTile(String id) {
        java.util.Set<String> h = Prefs.controlHidden(this);
        if (h.size() >= ControlItems.IDS.length - 1) {
            Toast.makeText(this, "Keep at least one tile", Toast.LENGTH_SHORT).show();
            return;
        }
        if (id.equals(ControlItems.HOME) && !Prefs.settingsTile(this)) {
            Toast.makeText(this, "Needed while the Settings tile is hidden", Toast.LENGTH_SHORT).show();
            return;
        }
        h.add(id);
        Prefs.setControlHidden(this, h);
        Toast.makeText(this, "Bring it back in Settings, Control Center", Toast.LENGTH_SHORT).show();
        showMain();
    }

    /** Im Verschiebemodus: Pfeile schieben die Kachel auf den Platz der Nachbarkachel, OK oder Zurück beenden. */
    private boolean handleMoveKey(KeyEvent event) {
        int code = event.getKeyCode();
        int dir;
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT: dir = View.FOCUS_LEFT; break;
            case KeyEvent.KEYCODE_DPAD_RIGHT: dir = View.FOCUS_RIGHT; break;
            case KeyEvent.KEYCODE_DPAD_UP: dir = View.FOCUS_UP; break;
            case KeyEvent.KEYCODE_DPAD_DOWN: dir = View.FOCUS_DOWN; break;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_BACK:
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    String id = movingId;
                    movingId = null;
                    showMain(id);
                }
                return true;
            default:
                return false;
        }
        if (event.getAction() != KeyEvent.ACTION_DOWN) return true;
        // Getauscht wird mit der Kachel, die auf dem Bildschirm in Pfeilrichtung liegt (große Kachel,
        // Seitenspalte, normale und kleine Reihen sind unterschiedlich breit; Indexrechnung passte nicht).
        View cur = body.findViewWithTag(movingId);
        View next = cur == null ? null
                : android.view.FocusFinder.getInstance().findNextFocus(body, cur, dir);
        if (next == null || !(next.getTag() instanceof String) || movingId.equals(next.getTag())) return true;
        String other = (String) next.getTag();
        java.util.List<String> order = Prefs.controlOrder(this);
        int a = order.indexOf(movingId);
        int b = order.indexOf(other);
        if (a < 0 || b < 0) return true;
        // Einfügen statt Tauschen (wie auf der Startseite): Die Kachel nimmt den Platz der Zielkachel ein,
        // die dazwischen rücken um eins. Tauschen warf bei ungleich großen Kacheln die andere weit weg.
        order.remove(a);
        order.add(b, movingId);
        Prefs.setControlOrder(this, order);
        showMain(movingId);
        return true;
    }

    private void wiggle(View v) {
        if (wiggleAnim != null) wiggleAnim.cancel();
        wiggleAnim = android.animation.ObjectAnimator.ofFloat(v, "rotation", -1.5f, 1.5f);
        wiggleAnim.setDuration(Ui.ms(260));
        wiggleAnim.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        wiggleAnim.setRepeatMode(android.animation.ValueAnimator.REVERSE);
        wiggleAnim.start();
    }

    private View addItem(String id, boolean vpn) {
        switch (id) {
            case ControlItems.SCREENSAVER:
                return cell("Screensaver", "Start now", () -> launch(new Intent(this, ScreensaverActivity.class)));
            case ControlItems.AMBILIGHT:
                // Schalten per Broadcast: die Leiste bleibt offen, das Symbol wird blau, wenn an.
                return cell("Ambilight", "On / off", () -> toggleHue(ControlItems.AMBILIGHT, "tv.bernhard.huesync.AMBILIGHT"));
            case ControlItems.HUE_SYNC:
                return cell("Hue Sync", "On / off", () -> toggleHue(ControlItems.HUE_SYNC, "tv.bernhard.huesync.TOGGLE"));
            case ControlItems.INPUTS:
                return cell("Inputs", "HDMI and devices", this::showInputs);
            case ControlItems.PICTURE:
                return cell("Picture", "Picture settings", () -> launch(PictureSettingsActivity.fresh(new Intent("android.settings.PICTURE_ACTIVITY"))));
            case ControlItems.SOUND:
                return cell("Sound", "Sound and output", () -> launch(PictureSettingsActivity.fresh(new Intent("android.settings.SOUND_SETTINGS"))));
            case ControlItems.APP_SWITCHER:
                // Eigener App Switcher im Launcher (seit 0.6.0, vorher tv.bernhard.appswitcher).
                return cell("App Switcher", "Recent apps", () -> launch(new Intent(this,
                        tv.bernhard.launcher.switcher.SwitcherActivity.class)));
            case ControlItems.VPN:
                return cell(vpn ? "VPN on" : "VPN off", "", vpn ? this::confirmVpnOff : this::openVpnApp);
            case ControlItems.ALL_APPS:
                return cell("All Apps", "Every app in one list", () -> launch(new Intent(this,
                        tv.bernhard.launcher.allapps.AllAppsActivity.class)));
            case ControlItems.RESTART:
                // Neustart-Dialog von Philips (exportiert, fragt selbst nach), W24.
                return cell("Restart", "Restart the TV", () -> launch(new Intent("org.droidtv.intent.action.CONFIRM_REBOOT")
                        .setComponent(new ComponentName("org.droidtv.tvsystemui", "org.droidtv.tvsystemui.RCRebootActivity"))));
            case ControlItems.SETTINGS:
                return cell("TV Settings", "Android settings", () -> launch(PictureSettingsActivity.fresh(new Intent("android.settings.SETTINGS"))));
            default:
                return cell("Settings", "Launcher settings", () -> launch(new Intent(this, SettingsActivity.class)));
        }
    }

    private void showInputs() {
        setPanelHeight(0);
        body.removeAllViews();
        body.setTag("inputs");
        body.addView(Ui.caption(this, "Inputs"), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        List<Tile> inputs = AppRepository.inputs(this);
        View first = null;
        for (Tile t : inputs) {
            View v = wideRow(t.label, () -> launch(t.intent));
            if (first == null) first = v;
        }
        if (first == null) {
            body.addView(Ui.dimText(this, "No inputs found"));
            first = wideRow("Back", this::showMain);
        }
        first.requestFocus();
    }

    // ---------------------------------------------------------------- Profil (W21)

    /** Profilbild in der Pille neu zeichnen. */
    private void styleHeaderAvatar(boolean focused) {
        pill.refreshAvatar();
    }

    private boolean isProfileSub() {
        Object t = body.getTag();
        return t != null && String.valueOf(t).startsWith("profile_");
    }

    /** Profile wie tvOS (W11): alle Profile als Kreise, Haken am aktiven, „+“ zum Hinzufügen. */
    private void showProfile() {
        // Profilseite genauso groß wie die Kachelseite (Höhe merken, solange sie noch zu sehen ist).
        if (body.getTag() == null && panel.getHeight() > 0) mainPanelHeight = panel.getHeight();
        setPanelHeight(mainPanelHeight);
        body.removeAllViews();
        body.setTag("profile");
        pill.setSelected("profile");
        java.util.List<Profiles.Profile> list = Profiles.all(this);
        String currentId = Profiles.currentId(this);
        // tvOS: große Kreise, zwei je Reihe, Name darunter.
        LinearLayout[] rowRef = new LinearLayout[1];
        java.util.function.Supplier<LinearLayout> nextRow = () -> {
            if (rowRef[0] == null || rowRef[0].getChildCount() == 2) {
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_HORIZONTAL);
                // Platzhalter rechts, damit eine einzelne Zelle unter der linken steht.
                row.setTag("profiles");
                row.setClipChildren(false);
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                rlp.topMargin = Ui.dp(this, body.getChildCount() == 0 ? 6 : 14);
                body.addView(row, rlp);
                rowRef[0] = row;
            }
            return rowRef[0];
        };
        View focus = null;
        for (Profiles.Profile pr : list) {
            boolean active = pr.id.equals(currentId);
            View cell = profileCell(nextRow.get(), pr, pr.name, active);
            // OK wechselt, langes OK bearbeitet (wie tvOS; ein anderes Profil wird dafür erst aktiv).
            cell.setOnClickListener(v -> {
                if (!active) switchProfile(pr.id);
            });
            cell.setOnLongClickListener(v -> {
                if (active) {
                    showEditProfile();
                } else {
                    Profiles.switchTo(this, pr.id);
                    profileSwitched = true;
                    pill.refreshAvatar();
                    editAfterSwitch = true;
                    showProfile();
                }
                return true;
            });
            if (active) focus = cell;
        }
        if (list.size() < Profiles.MAX) {
            View add = profileCell(nextRow.get(), null, "Add Profile", false);
            add.setOnClickListener(v -> askProfileName("New profile", "", name -> {
                String id = Profiles.add(this, name);
                switchProfile(id);
            }));
        }
        // Letzte Reihe mit nur einer Zelle: unsichtbarer Platzhalter, damit sie links bündig steht.
        if (rowRef[0] != null && rowRef[0].getChildCount() == 1) {
            rowRef[0].addView(new View(this), new LinearLayout.LayoutParams(Ui.dp(this, 120), 1));
        }
        if (focus != null) focus.requestFocus();
        if (editAfterSwitch) {
            editAfterSwitch = false;
            showEditProfile();
        }
    }

    private static final int PROFILE_DP = 76;

    /** Ein Profil als großer Kreis mit Namen darunter, aktives mit grünem Haken; profile null = „+“. */
    private View profileCell(LinearLayout row, Profiles.Profile profile, String label, boolean active) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        cell.setFocusable(true);
        cell.setClickable(true);
        cell.setClipChildren(false);
        int size = Ui.dp(this, PROFILE_DP);
        FrameLayout holder = new FrameLayout(this);
        holder.setClipChildren(false);
        TextView circle = new TextView(this);
        circle.setGravity(Gravity.CENTER);
        circle.setIncludeFontPadding(false);
        holder.addView(circle, new FrameLayout.LayoutParams(size, size));
        if (active) {
            // Grüner Haken unten rechts mit Rand in Kastenfarbe.
            TextView badge = new TextView(this);
            badge.setText("\u2713");
            badge.setGravity(Gravity.CENTER);
            badge.setIncludeFontPadding(false);
            badge.setTextColor(Color.WHITE);
            badge.setTypeface(Typeface.DEFAULT_BOLD);
            badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(Color.rgb(52, 199, 89));
            g.setStroke(Ui.dp(this, 2), Ui.light ? Color.rgb(236, 236, 240) : Color.rgb(44, 44, 48));
            badge.setBackground(g);
            int b = Ui.dp(this, 24);
            FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(b, b, Gravity.END | Gravity.BOTTOM);
            holder.addView(badge, blp);
        }
        cell.addView(holder, new LinearLayout.LayoutParams(size, size));
        TextView text = Ui.text(this, label, 12, active ? Ui.TEXT : Ui.TEXT_DIM, active);
        text.setGravity(Gravity.CENTER);
        text.setSingleLine(true);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = Ui.dp(this, 8);
        cell.addView(text, tlp);
        Runnable style = () -> {
            boolean has = cell.isFocused();
            if (profile == null) {
                GradientDrawable bg = new GradientDrawable();
                bg.setShape(GradientDrawable.OVAL);
                bg.setColor(Ui.light ? Color.rgb(205, 208, 216) : Color.rgb(72, 74, 82));
                if (has) bg.setStroke(Ui.dp(this, 2), Ui.light ? Color.rgb(22, 24, 28) : Color.WHITE);
                circle.setBackground(bg);
                circle.setText("+");
                circle.setTypeface(Typeface.DEFAULT);
                circle.setTextColor(Ui.TEXT);
                circle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 34);
            } else {
                Avatars.style(this, circle, profile.picture(), profile.color, has, profile.name, PROFILE_DP);
            }
        };
        style.run();
        cell.setOnFocusChangeListener((v, has) -> {
            style.run();
            holder.animate().scaleX(has ? 1.1f : 1f).scaleY(has ? 1.1f : 1f).setDuration(Ui.ms(140)).start();
            text.setTextColor(has || active ? Ui.TEXT : Ui.TEXT_DIM);
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(this, 120),
                ViewGroup.LayoutParams.WRAP_CONTENT);
        row.addView(cell, lp);
        return cell;
    }

    /** Anderes Profil: Startseite mit dessen Layout neu starten. */
    private void switchProfile(String id) {
        Profiles.switchTo(this, id);
        startActivity(new Intent(this, HomeActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        finish();
        overridePendingTransition(0, 0);
    }

    /** Aktuelles Profil bearbeiten: Profilbild, Name, Entfernen. */
    private void showEditProfile() {
        setPanelHeight(0);
        body.removeAllViews();
        body.setTag("profile_edit");
        TextView name = Ui.text(this, Prefs.userName(this), 15, Ui.TEXT, true);
        name.setPadding(Ui.dp(this, 4), 0, 0, Ui.dp(this, 2));
        body.addView(name);
        TextView hint = Ui.dimText(this, "Choose your profile picture");
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        hint.setPadding(Ui.dp(this, 4), 0, 0, Ui.dp(this, 10));
        body.addView(hint);
        Profiles.Profile me = Profiles.current(this);
        String current = me.picture();
        // Kleine Auswahl wie bisher (Anfangsbuchstabe und Tiere), das aktuelle Bild immer dabei,
        // am Ende „…“ für alle Emojis im Overlay.
        java.util.List<String> quick = new java.util.ArrayList<>();
        for (int i = 0; i < 9; i++) quick.add(Prefs.AVATARS[i]);
        if (!quick.contains(current)) quick.set(8, current);
        View focus = null;
        LinearLayout row = null;
        int size = Ui.dp(this, 40);
        for (int i = 0; i <= quick.size(); i++) {
            if (i % 5 == 0) row = avatarRow();
            TextView a = avatarCell(row, i % 5 > 0);
            if (i == quick.size()) {
                // Alle Emojis
                Runnable st = () -> {
                    GradientDrawable bg = new GradientDrawable();
                    bg.setShape(GradientDrawable.OVAL);
                    bg.setColor(Ui.light ? Color.rgb(205, 208, 216) : Color.rgb(60, 62, 70));
                    if (a.isFocused()) bg.setStroke(Ui.dp(this, 2), Ui.light ? Color.rgb(22, 24, 28) : Color.WHITE);
                    a.setBackground(bg);
                };
                a.setText("\u2026");
                a.setTextColor(Ui.TEXT);
                a.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
                st.run();
                a.setOnFocusChangeListener((v, has) -> {
                    st.run();
                    v.animate().scaleX(has ? 1.15f : 1f).scaleY(has ? 1.15f : 1f).setDuration(Ui.ms(140)).start();
                });
                a.setOnClickListener(v -> showAllEmojis(current));
                continue;
            }
            final String emoji = quick.get(i);
            Avatars.style(this, a, emoji, me.color, false, me.name, 40);
            a.setOnFocusChangeListener((v, has) -> {
                Avatars.style(this, a, emoji, Profiles.current(this).color, has, me.name, 40);
                v.animate().scaleX(has ? 1.15f : 1f).scaleY(has ? 1.15f : 1f).setDuration(Ui.ms(140)).start();
            });
            a.setOnClickListener(v -> {
                Profiles.setPicture(this, me.id, emoji, null);
                styleHeaderAvatar(false);
                showProfile();
            });
            if (emoji.equals(current) && focus == null) focus = a;
        }
        // Hintergrundfarbe: das eigene Bild auf jeder Farbe, die gewählte mit dünnem Ring.
        TextView colorTitle = Ui.dimText(this, "Background");
        colorTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        colorTitle.setPadding(Ui.dp(this, 4), Ui.dp(this, 4), 0, Ui.dp(this, 8));
        body.addView(colorTitle);
        for (int i = 0; i < Avatars.colorCount(); i++) {
            if (i % 5 == 0) row = avatarRow();
            TextView a = avatarCell(row, i % 5 > 0);
            final int color = i;
            boolean chosen = color == me.color || (me.color < 0 && color == 0 && !current.isEmpty());
            Runnable st = () -> {
                Avatars.style(this, a, current, color, a.isFocused(), me.name, 40);
                if (chosen && !a.isFocused() && a.getBackground() instanceof GradientDrawable) {
                    ((GradientDrawable) a.getBackground()).setStroke(Ui.dp(this, 2), Color.argb(150, 255, 255, 255));
                }
            };
            st.run();
            a.setOnFocusChangeListener((v, has) -> {
                st.run();
                v.animate().scaleX(has ? 1.15f : 1f).scaleY(has ? 1.15f : 1f).setDuration(Ui.ms(140)).start();
            });
            a.setOnClickListener(v -> {
                Profiles.setPicture(this, me.id, null, color);
                styleHeaderAvatar(false);
                showEditProfile();
                focusColor(color);
            });
        }
        wideRow("Change name", () -> askProfileName("Profile name", Prefs.userName(this), n -> {
            Prefs.setUserName(this, n);
            styleHeaderAvatar(false);
            showEditProfile();
        }));
        if (Profiles.all(this).size() > 1) wideRow("Remove profile", this::confirmRemoveProfile);
        if (focus != null) focus.requestFocus();
    }

    private LinearLayout avatarRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.bottomMargin = Ui.dp(this, 10);
        body.addView(row, rlp);
        return row;
    }

    private TextView avatarCell(LinearLayout row, boolean gap) {
        TextView a = new TextView(this);
        a.setGravity(Gravity.CENTER);
        a.setIncludeFontPadding(false);
        a.setFocusable(true);
        a.setClickable(true);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 40));
        if (gap) alp.leftMargin = Ui.dp(this, 12);
        row.addView(a, alp);
        return a;
    }

    /** Nach dem Wählen einer Farbe den Fokus auf derselben Farbe lassen. */
    private void focusColor(int color) {
        int seen = 0;
        for (int i = 0; i < body.getChildCount(); i++) {
            View child = body.getChildAt(i);
            if (child instanceof TextView && "Background".contentEquals(((TextView) child).getText())) {
                seen = 1;
                continue;
            }
            if (seen > 0 && child instanceof LinearLayout && ((LinearLayout) child).getChildCount() > 0
                    && ((LinearLayout) child).getChildAt(0) instanceof TextView) {
                LinearLayout row = (LinearLayout) child;
                if (color < row.getChildCount()) {
                    row.getChildAt(color).requestFocus();
                    return;
                }
                color -= row.getChildCount();
            }
        }
    }

    /** Overlay mit allen Emojis; die Leiste bleibt offen, solange es offen ist. */
    private void showAllEmojis(String current) {
        handler.removeCallbacks(idleClose);
        String id = Profiles.currentId(this);
        android.app.Dialog d = Avatars.chooser(this, current, emoji -> {
            Profiles.setPicture(this, id, emoji, null);
            styleHeaderAvatar(false);
            showEditProfile();
        });
        d.setOnDismissListener(x -> poke());
        d.show();
    }

    private void confirmRemoveProfile() {
        body.removeAllViews();
        body.setTag("profile_remove");
        TextView q = Ui.text(this, "Remove " + Prefs.userName(this) + "?", 15, Ui.TEXT, true);
        q.setPadding(Ui.dp(this, 4), 0, 0, Ui.dp(this, 4));
        body.addView(q);
        TextView info = Ui.dimText(this, "Its tiles and folders will be deleted.");
        info.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        info.setPadding(Ui.dp(this, 4), 0, 0, Ui.dp(this, 6));
        body.addView(info);
        View keep = wideRow("Keep", this::showEditProfile);
        wideRow("Remove", () -> {
            String id = Profiles.currentId(this);
            Profiles.remove(this, id);
            switchProfile(Profiles.currentId(this));
        });
        keep.requestFocus();
    }

    private interface NameCallback {
        void done(String name);
    }

    private void askProfileName(String title, String preset, NameCallback callback) {
        handler.removeCallbacks(idleClose);
        Dialogs.Panel p = Dialogs.askText(this, title, preset, n -> {
            if (!n.isEmpty()) callback.done(n);
        });
        p.dialog.setOnDismissListener(d -> poke());
    }

    private void confirmVpnOff() {
        body.removeAllViews();
        body.setTag("vpn");
        TextView q = Ui.text(this, "Disconnect the VPN?", 15, Ui.TEXT, true);
        q.setPadding(Ui.dp(this, 4), 0, 0, Ui.dp(this, 12));
        body.addView(q);
        View cancel = wideRow("Keep connected", this::showMain);
        wideRow("Disconnect", () -> launch(new Intent("tv.bernhard.tvhelper.VPN_DISCONNECT")
                .setPackage("tv.bernhard.tvhelper")));
        cancel.requestFocus();
    }

    private void openVpnApp() {
        Intent i = getPackageManager().getLeanbackLaunchIntentForPackage("com.surfshark.vpnclient.android");
        if (i == null) i = getPackageManager().getLaunchIntentForPackage("com.surfshark.vpnclient.android");
        if (i == null) {
            Toast.makeText(this, "VPN app not installed", Toast.LENGTH_SHORT).show();
            return;
        }
        launch(i);
    }

    private void launch(Intent intent) {
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            finish();
            overridePendingTransition(0, 0);
        } catch (RuntimeException e) {
            Log.w(TAG, "launch failed: " + intent, e);
            Toast.makeText(this, "Not available", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean vpnConnected() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            for (Network n : cm.getAllNetworks()) {
                NetworkCapabilities caps = cm.getNetworkCapabilities(n);
                if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return true;
            }
        } catch (RuntimeException ignored) {
        }
        return false;
    }

    // ---------------------------------------------------------------- Bausteine

    private LinearLayout curRow;
    /** Laufende Nummer der Kachel auf der Hauptseite; 0 = große Kachel links oben (W29). */
    private int cellIndex;
    /** Ab diesem Index kleine Symbol-Kacheln (vier in einer Reihe). */
    private int smallFrom = Integer.MAX_VALUE;
    private static final int SMALL_COUNT = 4;
    /** Rechte Spalte neben der großen Kachel (Kacheln 1 und 2). */
    private LinearLayout sideColumn;
    /** ID der Kachel, die gerade gebaut wird (für ihr Symbol). */
    private String buildingId;

    /**
     * Nächste Kachel. Wie bei tvOS: die erste ist doppelt so hoch, rechts daneben stehen
     * die nächsten zwei übereinander, danach Reihen mit COLS Kacheln.
     */
    private View cell(String title, String sub, Runnable action) {
        int index = cellIndex++;
        if (body.getTag() == null && index == 0) {
            LinearLayout top = pairRow();
            View big = button(title, action, true);
            top.addView(big, new LinearLayout.LayoutParams(0, Ui.dp(this, 2 * TILE_DP + GAP_DP), 1f));
            sideColumn = new LinearLayout(this);
            sideColumn.setOrientation(LinearLayout.VERTICAL);
            sideColumn.setClipChildren(false);
            sideColumn.setTag(SIDE_COLUMN);
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0, Ui.dp(this, 2 * TILE_DP + GAP_DP), 1f);
            slp.leftMargin = Ui.dp(this, GAP_DP);
            top.addView(sideColumn, slp);
            curRow = null;
            return big;
        }
        if (body.getTag() == null && index <= 2 && sideColumn != null) {
            View b = button(title, action, false);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    Ui.dp(this, TILE_DP));
            if (index == 2) lp.topMargin = Ui.dp(this, GAP_DP);
            sideColumn.addView(b, lp);
            return b;
        }
        if (body.getTag() == null && index >= smallFrom) {
            if ((index - smallFrom) % SMALL_COUNT == 0) curRow = pairRow();
            View b = smallButton(title, action);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(this, SMALL_DP), 1f);
            if (curRow.getChildCount() > 0) lp.leftMargin = Ui.dp(this, GAP_DP);
            curRow.addView(b, lp);
            return b;
        }
        if (curRow == null || curRow.getChildCount() >= COLS) curRow = pairRow();
        View b = button(title, action, false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(this, TILE_DP), 1f);
        if (curRow.getChildCount() > 0) lp.leftMargin = Ui.dp(this, GAP_DP);
        curRow.addView(b, lp);
        return b;
    }

    private static final String SIDE_COLUMN = "side";
    private static final float SMALL_DP = TILE_DP;

    /** Kleine quadratische Kachel nur mit Symbol (tvOS-Kontrollzentrum, unterste Reihe). */
    private View smallButton(String title, Runnable action) {
        FrameLayout b = new FrameLayout(this);
        b.setFocusable(true);
        b.setClickable(true);
        b.setContentDescription(title);
        float r = Ui.dp(this, TILE_RADIUS_DP);
        b.setBackground(Ui.rounded(Ui.ROW, r));
        Ui.evenShadow(b);
        final String id = buildingId;
        ControlIcons.Icon icon = new ControlIcons.Icon(id, iconColor(id, false));
        if (id != null) {
            icons.put(id, icon);
            tiles.put(id, b);
        }
        android.widget.ImageView iv = new android.widget.ImageView(this);
        iv.setImageDrawable(icon);
        int is = Ui.dp(this, 22);
        b.addView(iv, new FrameLayout.LayoutParams(is, is, Gravity.CENTER));
        b.setOnFocusChangeListener((v, has) -> {
            v.setBackground(Ui.rounded(has ? Ui.ROW_FOCUSED : Ui.ROW, r));
            icon.setColor(iconColor(id, has));
            v.animate().scaleX(has ? FOCUS_SCALE : 1f).scaleY(has ? FOCUS_SCALE : 1f)
                    .translationZ(has ? Ui.dp(this, 10) : 0).setDuration(Ui.ms(140)).start();
        });
        b.setOnClickListener(v -> action.run());
        return b;
    }

    /** Letzte Reihe mit leeren Plätzen auffüllen, damit die Kacheln gleich breit bleiben. */
    private void fillRow() {
        // Reihe der kleinen Kacheln auf vier auffüllen, sonst auf zwei.
        int target = cellIndex > smallFrom ? SMALL_COUNT : COLS;
        while (curRow != null && curRow.getChildCount() < target) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, 1, 1f);
            lp.leftMargin = Ui.dp(this, GAP_DP);
            curRow.addView(new View(this), lp);
        }
    }

    private LinearLayout pairRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Ui.dp(this, GAP_DP);
        body.addView(row, lp);
        return row;
    }

    /**
     * Kachel mit Symbol und Titel (W31). Normal: Symbol links neben dem Titel. Groß: Symbol oben,
     * Titel unten links (wie die große Kachel im tvOS-Kontrollzentrum).
     */
    private View button(String title, Runnable action, boolean big) {
        LinearLayout b = new LinearLayout(this);
        b.setOrientation(big ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        // Große Kachel: Symbol groß in der Mitte, Titel darunter (Wunsch des Nutzers).
        b.setGravity(big ? Gravity.CENTER : Gravity.CENTER_VERTICAL | Gravity.START);
        int p = Ui.dp(this, 12);
        b.setPadding(p, big ? Ui.dp(this, 6) : 0, p, big ? Ui.dp(this, 6) : 0);
        b.setFocusable(true);
        b.setClickable(true);
        float r = Ui.dp(this, TILE_RADIUS_DP);
        b.setBackground(Ui.rounded(Ui.ROW, r));
        Ui.evenShadow(b);
        final String id = buildingId;
        ControlIcons.Icon icon = new ControlIcons.Icon(id, iconColor(id, false));
        if (id != null) {
            icons.put(id, icon);
            tiles.put(id, b);
        }
        android.widget.ImageView iv = new android.widget.ImageView(this);
        iv.setImageDrawable(icon);
        int is = Ui.dp(this, big ? 48 : 22);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(is, is);
        if (big) {
            ilp.bottomMargin = Ui.dp(this, 4);
        } else {
            ilp.rightMargin = Ui.dp(this, 8);
        }
        b.addView(iv, ilp);
        TextView t = Ui.text(this, title, big ? 13 : 12, Ui.TEXT, true);
        marquee(t);
        if (big) t.setGravity(Gravity.CENTER_HORIZONTAL);
        b.addView(t, big ? new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT) : new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        b.setOnFocusChangeListener((v, has) -> {
            v.setBackground(Ui.rounded(has ? Ui.ROW_FOCUSED : Ui.ROW, r));
            t.setTextColor(has ? Ui.TEXT_ON_FOCUS : Ui.TEXT);
            icon.setColor(iconColor(id, has));
            // Zu langer Text läuft auf der fokussierten Kachel durch (B12).
            t.setSelected(has);
            // Leicht vergrößert, aber mit Luft zu den Nachbarn (B19).
            v.animate().scaleX(has ? FOCUS_SCALE : 1f).scaleY(has ? FOCUS_SCALE : 1f)
                    .translationZ(has ? Ui.dp(this, 10) : 0).setDuration(Ui.ms(140)).start();
        });
        b.setOnClickListener(v -> action.run());
        return b;
    }

    private static void marquee(TextView v) {
        v.setSingleLine(true);
        v.setEllipsize(TextUtils.TruncateAt.MARQUEE);
        v.setMarqueeRepeatLimit(-1);
        v.setHorizontalFadingEdgeEnabled(true);
    }

    private View wideRow(String label, Runnable action) {
        LinearLayout row = Ui.row(this);
        row.setMinimumHeight(Ui.dp(this, 42));
        row.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 14), 0);
        row.addView(Ui.text(this, label, 14, Ui.TEXT, true), Ui.fill());
        row.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, GAP_DP);
        body.addView(row, lp);
        return row;
    }
}
