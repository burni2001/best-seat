package tv.bestseat.launcher;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/**
 * Kontrollzentrum: Milchglas-Leiste rechts über der laufenden App (wie tvOS).
 * Kacheln für Schoner, Ambilight, Ambience (Hue Sync), Bild, Ton, Eingänge, App Switcher, VPN, Einstellungen.
 * Schließt mit Zurück/Menü, nach jeder Aktion und nach 30 s ohne Taste (nichts bleibt lange stehen).
 */
public final class ControlCenterActivity extends Activity {

    static final String ACTION = "tv.bestseat.launcher.CONTROL_CENTER";
    private static final String TAG = "Launcher.Control";
    private static final long IDLE_CLOSE_MS = 30_000;
    // Maße exakt nach dem tvOS-Kontrollzentrum (Design/control center.png, 3024 px Bildbreite = 960 dp):
    // Kachel 150 px, Abstand 26 px, Rand 37 px, Kasten 754 px (quadratisch), Radien Kachel 37 px, Kasten 72 px.
    private static final float PANEL_DP = 238.75f;
    private static final float PANEL_RADIUS_DP = 23f;
    /** Weichgezeichnetes Abbild der Startseite, von HomeActivity vor dem Start gesetzt. */
    static android.graphics.Bitmap homeBlur;

    final Handler handler = new Handler(Looper.getMainLooper());
    final Runnable idleClose = this::close;
    private FrameLayout root;
    LinearLayout panel;
    LinearLayout body;
    StatusPill pill;
    private final CcProfilePage profile = new CcProfilePage(this);
    private final CcTiles cells = new CcTiles(this);
    /** Höhe des Kastens mit den Kacheln; die Profilseite übernimmt sie. */
    int mainPanelHeight;
    /** Profil gewechselt ohne Neustart: beim Schließen die Startseite neu laden. */
    boolean profileSwitched;

    /** Feste Höhe in Pixeln oder 0 = so hoch wie der Inhalt. */
    void setPanelHeight(int px) {
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
        // Per Intent auf der Startseite: die Startseite öffnet das CC neu, mit Milchglas-Hintergrund.
        if (homeBlur == null && ACTION.equals(getIntent().getAction()) && HomeActivity.openControlCenterFromIntent()) {
            finish();
            Ui.transition(this, 0, 0);
            return;
        }
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
        // Abstand liegt in der Liste (body), nicht im Kasten: So reicht die Liste bis an den Rand und die
        // Schatten der Kacheln werden nicht 6 dp hinter den Kacheln abgeschnitten.
        panel.setPadding(0, 0, 0, 0);
        if (blurred != null && Prefs.blurEnabled(this)) {
            // Milchglas: nur was hinter dem Kasten liegt, weichgezeichnet (W102).
            panel.setBackground(new GlassDrawable(blurred, panel, Ui.dp(this, PANEL_RADIUS_DP),
                    Ui.light ? Color.argb(LiquidGlass.on ? 60 : 150, 238, 240, 244)
                            : Color.argb(LiquidGlass.on ? 40 : 120, 28, 30, 36),
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
        panel.setClipToPadding(false);

        // Uhrzeit und Profilbild stehen jetzt in der Pille oben rechts (StatusPill), nicht mehr im Kasten.
        shown = new java.lang.ref.WeakReference<>(this);
        pill = new StatusPill(this, true);
        pill.avatar.setOnClickListener(v -> profile.showProfile());
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
        body.setPadding(pad + Ui.dp(this, 6), pad + Ui.dp(this, 6), pad + Ui.dp(this, 6), Ui.dp(this, 3.5f));
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
        // Wechsel: Kommt der Intent erneut, während das CC offen ist (z. B. Doppel-HOME in tvQuickActions,
        // das HOME selbst abfängt), schließt er es wie der zweite Doppelklick (W103).
        if (intent.getBooleanExtra(EXTRA_CLOSE, false) || (ACTION.equals(intent.getAction()) && wasJustOpen())) {
            // wasJustOpen statt resumedNow: Android pausiert die Aktivität vor onNewIntent.
            Ui.transition(this, 0, 0);
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
            root.postOnAnimation(() -> {
                pill.growControls();
                if (!overApp()) HomeActivity.shiftVpn(-pill.controlsExtraPx(), Ui.ms(420), true);
            });
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

    void poke() {
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
                if (code == KeyEvent.KEYCODE_BACK && profile.isProfileSub()) profile.showProfile();
                else if (code == KeyEvent.KEYCODE_BACK && body.getTag() != null) showMain();
                else close();
            }
            return true;
        }
        // W90: Pfeil links vom Schalter-Symbol in der Pille auf die große Kachel (statt schließen).
        if (code == KeyEvent.KEYCODE_DPAD_LEFT && body.getTag() == null && pill.controls != null
                && pill.controls.isFocused() && cells.sideColumn() != null && cells.sideColumn().getParent() instanceof ViewGroup) {
            View big = ((ViewGroup) cells.sideColumn().getParent()).getChildAt(0);
            if (big != null && big.isFocusable()) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) big.requestFocus();
                return true;
            }
        }
        // Pfeil links ganz links: im Hauptteil schließen (Leiste kam von rechts), im Untermenü zurück.
        if (code == KeyEvent.KEYCODE_DPAD_LEFT && atLeftEdge()) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                if (profile.isProfileSub()) profile.showProfile();
                else if (body.getTag() != null) showMain();
                else close();
            }
            return true;
        }
        // W85: Am Ende in jeder Richtung (oben, unten, links, rechts) raus aus dem Kontrollzentrum.
        // Nur beim ersten Druck, nicht beim Halten: Durchlaufen einer Liste endet sonst ungewollt.
        int dir = CcFocus.arrowDirection(code);
        if (dir != 0 && !closing) {
            View focused = getCurrentFocus();
            View next = focused == null ? null : android.view.FocusFinder.getInstance()
                    .findNextFocus((ViewGroup) getWindow().getDecorView(), focused, dir);
            if (focused != null && !CcFocus.beyondEdge(focused, next, dir)) {
                if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) close();
                return true;
            }
            // W86: Hoch/runter in eine Reihe mit anderer Aufteilung: auf die bündige Kachel (rechte Kante
            // zuerst, dann linke), z. B. von Ambilight runter auf die kleine Kachel ganz rechts.
            if (next != null && (dir == View.FOCUS_DOWN || dir == View.FOCUS_UP)) {
                View aligned = CcFocus.alignedInRow(focused, next);
                if (aligned != null && aligned != next) {
                    if (event.getAction() == KeyEvent.ACTION_DOWN) aligned.requestFocus(dir);
                    return true;
                }
            }
        }
        return super.dispatchKeyEvent(event);
    }

    /** Fokus auf einer Kachel der linken Spalte bzw. auf einer Zeile eines Untermenüs. */
    private boolean atLeftEdge() {
        View focused = getCurrentFocus();
        if (focused == null) return false;
        if (body.getTag() != null && !String.valueOf(body.getTag()).startsWith("profile")) return true;
        // Die rechte Spalte neben der großen Kachel ist nicht der linke Rand.
        return focused.getParent() instanceof LinearLayout
                && !CcTiles.SIDE_COLUMN.equals(((LinearLayout) focused.getParent()).getTag())
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
            Ui.transition(this, 0, 0);
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
        if (!overApp()) HomeActivity.shiftVpn(0f, d, false);
        // Über einer App gibt es keine Pille der Startseite, in die diese zurückkehrt: ausblenden.
        if (overApp()) pill.animate().alpha(0f).setDuration(d).start();
        dim.animate().alpha(0f).setDuration(d).withEndAction(() -> {
            // Pille der Startseite zurück, bevor die eigene verschwindet: kein Blinken.
            setHomePillHidden(false);
            pill.setBare(true);
            root.post(() -> {
                finish();
                Ui.transition(this, 0, 0);
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
        boolean vpn = vpnConnected();
        View first = null;
        // Reihenfolge und Sichtbarkeit aus den Einstellungen (W12).
        java.util.Set<String> hidden = Prefs.controlHidden(this);
        // tvOS: große Kachel mit zwei daneben, darunter eine Reihe mit zwei normalen Kacheln, ganz unten
        // kleine quadratische Symbol-Kacheln (vier je Reihe). Ab der sechsten Kachel also klein.
        cells.reset(5);
        for (String id : Prefs.controlOrder(this)) {
            if (hidden.contains(id)) continue;
            cells.building(id);
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
        cells.fillRow();
        if (first != null) first.requestFocus();
        cells.setActive(ControlItems.VPN, vpn);
        queryHueState(0);
    }

    // ---------------------------------------------------------------- Zustand (blaues Symbol)

    private static final ComponentName HUE_RECEIVER =
            new ComponentName(Companions.HUESYNC, Companions.HUESYNC + ".ControlReceiver");
    /** Ambilight / Hue Sync im Hintergrund schalten (App Ambience) und den Zustand nachfragen. */
    private void toggleHue(String id, String action) {
        try {
            sendBroadcast(new Intent(action).setComponent(HUE_RECEIVER));
        } catch (RuntimeException e) {
            Toast.makeText(this, "Ambience app not available", Toast.LENGTH_SHORT).show();
            return;
        }
        // Sofort umfärben, die Antwort der App bestätigt oder korrigiert es.
        cells.setActive(id, !cells.isActive(id));
        queryHueState(ControlItems.AMBILIGHT.equals(id) ? 2500 : 700);
        queryHueState(5000);
    }

    /** Zustand von Hue Sync und Ambilight bei der App Ambience abfragen (ab Hue Sync 1.9.7). */
    private void queryHueState(long delayMs) {
        if (!cells.has(ControlItems.AMBILIGHT) && !cells.has(ControlItems.HUE_SYNC)) return;
        handler.postDelayed(() -> {
            if (isFinishing()) return;
            try {
                sendOrderedBroadcast(new Intent(Companions.HUESYNC + ".STATE").setComponent(HUE_RECEIVER), null,
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
                if ("sync".equals(kv[0])) a.cells.setActive(ControlItems.HUE_SYNC, "1".equals(kv[1]));
                if ("ambilight".equals(kv[0])) a.cells.setActive(ControlItems.AMBILIGHT, "1".equals(kv[1]));
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
                return cells.cell("Screensaver", "Start now", () -> launch(new Intent(this, ScreensaverActivity.class)));
            case ControlItems.AMBILIGHT:
                // Schalten per Broadcast: die Leiste bleibt offen, das Symbol wird blau, wenn an.
                return cells.cell("Ambilight", "On / off", () -> toggleHue(ControlItems.AMBILIGHT, Companions.HUESYNC + ".AMBILIGHT"));
            case ControlItems.HUE_SYNC:
                return cells.cell("Ambience", "On / off", () -> toggleHue(ControlItems.HUE_SYNC, Companions.HUESYNC + ".TOGGLE"));
            case ControlItems.INPUTS:
                return cells.cell("Inputs", "HDMI and devices", this::showInputs);
            case ControlItems.PICTURE:
                return cells.cell("Picture", "Picture settings", () -> launch(PictureSettingsActivity.fresh(new Intent("android.settings.PICTURE_ACTIVITY"))));
            case ControlItems.SOUND:
                return cells.cell("Sound", "Sound and output", () -> launch(PictureSettingsActivity.fresh(new Intent("android.settings.SOUND_SETTINGS"))));
            case ControlItems.APP_SWITCHER:
                // Eigener App Switcher im Launcher (seit 0.6.0, vorher eigene App).
                return cells.cell("App Switcher", "Recent apps", () -> launch(new Intent(this,
                        tv.bestseat.launcher.switcher.SwitcherActivity.class)));
            case ControlItems.VPN:
                return cells.cell(vpn ? "VPN on" : "VPN off", "", vpn ? this::confirmVpnOff : this::openVpnApp);
            case ControlItems.ALL_APPS:
                return cells.cell("All Apps", "Every app in one list", () -> launch(new Intent(this,
                        tv.bestseat.launcher.allapps.AllAppsActivity.class)));
            case ControlItems.RESTART:
                // Neustart-Dialog von Philips (exportiert, fragt selbst nach), W24.
                return cells.cell("Restart", "Restart the TV", () -> launch(new Intent("org.droidtv.intent.action.CONFIRM_REBOOT")
                        .setComponent(new ComponentName("org.droidtv.tvsystemui", "org.droidtv.tvsystemui.RCRebootActivity"))));
            case ControlItems.SETTINGS:
                return cells.cell("TV Settings", "Android settings", () -> launch(PictureSettingsActivity.fresh(new Intent("android.settings.SETTINGS"))));
            default:
                return cells.cell("Settings", "Launcher settings", () -> launch(new Intent(this, SettingsActivity.class)));
        }
    }

    private void showInputs() {
        // Inputs so groß wie die Kachelseite (wie Profil), nicht auf die Zeilen geschrumpft.
        if (body.getTag() == null && panel.getHeight() > 0) mainPanelHeight = panel.getHeight();
        setPanelHeight(mainPanelHeight);
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

    private void confirmVpnOff() {
        body.removeAllViews();
        body.setTag("vpn");
        TextView q = Ui.text(this, "Disconnect the VPN?", 15, Ui.TEXT, true);
        q.setPadding(Ui.dp(this, 4), 0, 0, Ui.dp(this, 12));
        body.addView(q);
        View cancel = wideRow("Keep connected", this::showMain);
        wideRow("Disconnect", () -> launch(new Intent(Companions.TVHELPER + ".VPN_DISCONNECT")
                .setPackage(Companions.TVHELPER)));
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
            Ui.transition(this, 0, 0);
        } catch (RuntimeException e) {
            Log.w(TAG, "launch failed: " + intent, e);
            Toast.makeText(this, "Not available", Toast.LENGTH_SHORT).show();
        }
    }

    @SuppressWarnings("deprecation") // getAllNetworks: der Ersatz per Callback lohnt für eine Momentabfrage nicht.
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

    View wideRow(String label, Runnable action) {
        LinearLayout row = Ui.row(this);
        row.setMinimumHeight(Ui.dp(this, 42));
        row.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 14), 0);
        row.addView(Ui.text(this, label, 14, Ui.TEXT, true), Ui.fill());
        row.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, CcTiles.GAP_DP);
        // Platz an den Seiten, damit die vergrößerte Zeile mit Fokus samt Schatten nicht am Rand anstößt.
        lp.leftMargin = Ui.dp(this, 8);
        lp.rightMargin = Ui.dp(this, 8);
        body.addView(row, lp);
        return row;
    }
}
