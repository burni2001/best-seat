package tv.bestseat.launcher;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

/**
 * Uhr-und-Profil-Pille oben rechts wie bei tvOS. Auf der Startseite: Uhrzeit und Profilbild.
 * Im Kontrollzentrum liegt sie an genau derselben Stelle und wächst nach links um das
 * Schalter-Symbol; Symbol und Profilbild sind dort wählbar (Kontrollzentrum / Profile).
 */
final class StatusPill extends LinearLayout {

    static final int TOP_DP = 24;
    static final int RIGHT_DP = 24;
    static final int HEIGHT_DP = 36;
    static final int AVATAR_DP = 28;
    private static final int BUTTON_DP = 30;

    private final Activity activity;
    private final TextClock time;
    final TextView avatar;
    /** Nur im Kontrollzentrum: Schalter-Symbol (sonst null). */
    final FrameLayout controls;
    private ControlIcons.Icon controlsIcon;
    /** Shown when a newer release exists and notifications are on; focusable only in the Control Center. */
    private final FrameLayout updateIcon;
    private String selected;
    private final GradientDrawable pillBg;
    private float depth;
    private boolean bare;

    // Deckkraft der Pille: Startseite und Kontrollzentrum. Im CC gleich wie auf der Startseite (Wunsch des Nutzers, 0.18.9).
    private static final int HOME_ALPHA_DARK = 110, CC_ALPHA_DARK = 110;
    private static final int HOME_ALPHA_LIGHT = 150, CC_ALPHA_LIGHT = 150;

    private static int pillColor(float depth) {
        int a = Ui.light ? Math.round(HOME_ALPHA_LIGHT + (CC_ALPHA_LIGHT - HOME_ALPHA_LIGHT) * depth)
                : Math.round(HOME_ALPHA_DARK + (CC_ALPHA_DARK - HOME_ALPHA_DARK) * depth);
        return Ui.light ? Color.argb(a, 255, 255, 255) : Color.argb(a, LiquidGlass.on ? LiquidGlass.darkLift() : 0, LiquidGlass.on ? LiquidGlass.darkLift() : 0, LiquidGlass.on ? LiquidGlass.darkLift() : 0);
    }

    /** 0 = Farbe der Startseite, 1 = Farbe im Kontrollzentrum. */
    void setDepth(float f) {
        depth = f;
        if (!bare && solid && pillBg instanceof LiquidGlass.Rounded) {
            ((LiquidGlass.Rounded) pillBg).solid(Ui.light ? Color.rgb(238, 240, 244) : Color.rgb(28, 30, 36));
            return;
        }
        pillBg.setColor(bare ? Color.TRANSPARENT : pillColor(f));
    }

    /** Glass without a backdrop (Control Center over another app): same strong tint as the rows, readable. */
    private boolean solid;

    void setSolid(boolean s) {
        solid = s;
        setDepth(depth);
    }

    /**
     * Ohne Hintergrund, solange die Pille der Startseite darunter sichtbar ist:
     * zwei halbtransparente Pillen übereinander ergäben kurz eine dunklere Farbe.
     */
    void setBare(boolean b) {
        bare = b;
        setDepth(depth);
    }

    StatusPill(Activity activity, boolean withControls) {
        super(activity);
        this.activity = activity;
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setClipChildren(false);
        setClipToPadding(false);
        setPadding(dp(14), 0, dp(4), 0);
        // Bei Liquid Glass mit Glanz und Lichtrand, schwächer, weil die Pille ruht.
        pillBg = LiquidGlass.on ? new LiquidGlass.Rounded(LiquidGlass.density(), 0.6f) : new GradientDrawable();
        pillBg.setColor(pillColor(0f));
        pillBg.setCornerRadius(dp(HEIGHT_DP) / 2f);
        setBackground(pillBg);

        time = new TextClock(activity);
        time.setFormat24Hour("HH:mm");
        time.setFormat12Hour("h:mm");
        Prefs.applyZone(activity, time);
        time.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        time.setTypeface(Fonts.get(time.getContext(), Fonts.MEDIUM));
        time.setTextColor(Ui.light ? Color.argb(200, 0, 0, 0) : Color.argb(210, 255, 255, 255));
        time.setIncludeFontPadding(false);
        LayoutParams tlp = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.rightMargin = dp(8);
        addView(time, tlp);

        updateIcon = new FrameLayout(activity);
        ImageView uv = new ImageView(activity);
        uv.setImageDrawable(new ControlIcons.Icon("update", Ui.TEXT).strokeWidth(1.8f));
        int up = dp(6);
        uv.setPadding(up, up, up, up);
        updateIcon.addView(uv, new FrameLayout.LayoutParams(dp(BUTTON_DP), dp(BUTTON_DP)));
        LayoutParams ulp = new LayoutParams(dp(BUTTON_DP), dp(BUTTON_DP));
        ulp.rightMargin = dp(4);
        addView(updateIcon, ulp);
        if (withControls) {
            updateIcon.setFocusable(true);
            updateIcon.setClickable(true);
            updateIcon.setOnFocusChangeListener((v, has) ->
                    v.animate().scaleX(has ? 1.15f : 1f).scaleY(has ? 1.15f : 1f).setDuration(Ui.ms(140)).start());
            updateIcon.setOnClickListener(v -> activity.startActivity(new Intent(activity, SettingsActivity.class)
                    .putExtra(SettingsActivity.EXTRA_PAGE, "updates")));
        }
        refreshUpdate();

        if (withControls) {
            controls = new FrameLayout(activity);
            controls.setFocusable(true);
            controls.setClickable(true);
            ImageView iv = new ImageView(activity);
            // Dünn wie zuvor, nicht kräftig wie die Kacheln (Wunsch des Nutzers).
            controlsIcon = new ControlIcons.Icon("controlcenter", Ui.TEXT).strokeWidth(1.8f);
            iv.setImageDrawable(controlsIcon);
            int p = dp(6);
            iv.setPadding(p, p, p, p);
            controls.addView(iv, new FrameLayout.LayoutParams(dp(BUTTON_DP), dp(BUTTON_DP)));
            LayoutParams clp = new LayoutParams(dp(BUTTON_DP), dp(BUTTON_DP));
            clp.rightMargin = dp(6);
            addView(controls, clp);
            controls.setOnFocusChangeListener((v, has) -> {
                styleControls();
                v.animate().scaleX(has ? 1.15f : 1f).scaleY(has ? 1.15f : 1f).setDuration(Ui.ms(140)).start();
            });
        } else {
            controls = null;
        }

        avatar = new TextView(activity);
        avatar.setGravity(Gravity.CENTER);
        avatar.setIncludeFontPadding(false);
        avatar.setFocusable(withControls);
        avatar.setClickable(withControls);
        addView(avatar, new LayoutParams(dp(AVATAR_DP), dp(AVATAR_DP)));
        if (withControls) {
            avatar.setOnFocusChangeListener((v, has) -> {
                refreshAvatar();
                v.animate().scaleX(has ? 1.15f : 1f).scaleY(has ? 1.15f : 1f).setDuration(Ui.ms(140)).start();
            });
        }
        refreshAvatar();
        styleControls();
        refreshClock();
    }

    /** Update icon: only with a newer release found and "Notify about updates" on. */
    void refreshUpdate() {
        boolean show = Prefs.updateNotify(activity) && UpdateChecker.available(activity);
        updateIcon.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    /** Startseite: Uhrzeit ausblendbar (nur Profilbild). Im Kontrollzentrum bleibt sie immer. */
    void refreshClock() {
        if (controls != null) return;
        boolean show = Prefs.showClock(activity);
        time.setVisibility(show ? View.VISIBLE : View.GONE);
        setPadding(dp(show ? 14 : 4), 0, dp(4), 0);
    }

    /** Lage: oben rechts, gleiche Werte auf der Startseite und im Kontrollzentrum. */
    FrameLayout.LayoutParams frameParams() {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(HEIGHT_DP), Gravity.TOP | Gravity.END);
        lp.topMargin = dp(TOP_DP);
        lp.rightMargin = dp(RIGHT_DP);
        return lp;
    }

    void refreshAvatar() {
        Profiles.Profile p = Profiles.current(activity);
        Avatars.style(activity, avatar, p.picture(), p.color, avatar.isFocused(), p.name, AVATAR_DP);
    }

    /** Welche Seite offen ist ("controls" oder "profile"): dunkler Kreis hinter dem Symbol. */
    void setSelected(String which) {
        selected = which;
        styleControls();
    }

    private void styleControls() {
        if (controls == null) return;
        boolean focused = controls.isFocused();
        int bg = focused ? Ui.ROW_FOCUSED : "controls".equals(selected)
                ? (Ui.light ? Color.argb(45, 0, 0, 0) : Color.argb(140, 96, 98, 104)) : Color.TRANSPARENT;
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(bg);
        controls.setBackground(d);
        controlsIcon.setColor(focused ? Ui.TEXT_ON_FOCUS : Ui.TEXT);
    }

    /** Vor dem Wachsen: Schalter-Symbol zusammengeklappt, die Pille gleicht genau der der Startseite. */
    void collapseControls() {
        if (controls == null) return;
        LayoutParams lp = (LayoutParams) controls.getLayoutParams();
        lp.width = 1;
        lp.rightMargin = 0;
        controls.setLayoutParams(lp);
        controls.setAlpha(0f);
    }

    /** Um so viel wird die Pille beim Öffnen des Kontrollzentrums nach links breiter. */
    int controlsExtraPx() {
        return dp(BUTTON_DP) + dp(6);
    }

    /** Kontrollzentrum öffnet: Schalter-Symbol wächst von 0 auf volle Breite, die Pille dehnt sich nach links. */
    void growControls() {
        if (controls == null) return;
        LayoutParams lp = (LayoutParams) controls.getLayoutParams();
        int full = dp(BUTTON_DP);
        int fullMargin = dp(6);
        // Mindestens 1 px: Eine Ansicht ohne Größe kann den Fokus nicht halten.
        lp.width = 1;
        lp.rightMargin = 0;
        controls.setLayoutParams(lp);
        controls.setAlpha(0f);
        ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
        a.setDuration(Ui.ms(420));
        a.setInterpolator(new DecelerateInterpolator(2f));
        a.addUpdateListener(v -> {
            float f = (float) v.getAnimatedValue();
            lp.width = Math.max(1, Math.round(full * f));
            lp.rightMargin = Math.round(fullMargin * f);
            controls.setLayoutParams(lp);
            controls.setAlpha(f);
        });
        a.start();
        animateDepth(1f, Ui.ms(650));
    }

    /** Kontrollzentrum schließt: Schalter-Symbol schrumpft, die Pille zieht sich nach rechts zusammen. */
    void shrinkControls(long duration) {
        if (controls == null) return;
        LayoutParams lp = (LayoutParams) controls.getLayoutParams();
        int full = Math.max(1, lp.width);
        int fullMargin = lp.rightMargin;
        ValueAnimator a = ValueAnimator.ofFloat(1f, 0f);
        a.setDuration(duration);
        a.setInterpolator(new android.view.animation.AccelerateInterpolator(1.5f));
        a.addUpdateListener(v -> {
            float f = (float) v.getAnimatedValue();
            lp.width = Math.max(1, Math.round(full * f));
            lp.rightMargin = Math.round(fullMargin * f);
            controls.setLayoutParams(lp);
            controls.setAlpha(f);
        });
        a.start();
        animateDepth(0f, duration);
    }

    private ValueAnimator depthAnim;

    /** Weicher Farbübergang der Pille, eigenständig neben dem Wachsen und Schrumpfen. */
    private void animateDepth(float to, long duration) {
        if (depthAnim != null) depthAnim.cancel();
        depthAnim = ValueAnimator.ofFloat(depth, to);
        depthAnim.setDuration(duration);
        depthAnim.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
        depthAnim.addUpdateListener(v -> setDepth((float) v.getAnimatedValue()));
        depthAnim.start();
    }

    private int dp(float v) {
        return Ui.dp(activity, v);
    }
}
