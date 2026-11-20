package tv.bernhard.launcher.switcher;

import android.animation.LayoutTransition;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


/**
 * App Switcher wie bei tvOS: vorne groß der aktuelle Bildschirm als Karte, die zuletzt genutzten
 * Apps gestaffelt links dahinter, über jeder Karte ein kleines App-Symbol. Links = ältere App,
 * rechts = neuere. Laufende Apps haben einen weißen Punkt neben dem Namen. OK startet, Pfeil hoch
 * beendet die App (Force Quit, Karte gleitet nach oben weg), Pfeil runter öffnet die App-Info, Zurück schließt.
 */
public class SwitcherActivity extends Activity {

    private static final int MAX_APPS = 8;

    /** Wie lange höchstens auf das Bildschirmfoto für den unscharfen Hintergrund gewartet wird. */
    private static final int BLUR_WAIT_MS = 1500;


    /** Verhindert, dass mehrere Force-Quit-Befehle gleichzeitig laufen. */
    private boolean quitting;

    /** Ob die Leiste von der Startseite aus geöffnet wurde. */
    private volatile boolean fromHome;

    /** Ob adb erreichbar war und damit bekannt ist, welche Apps laufen. */
    private boolean runningKnown;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Globaler Schalter in den Einstellungen: aus heißt, die Taste tut nichts.
        if (!SwitcherPrefs.isEnabled(this)) {
            finish();
            return;
        }

        if (!RecentApps.hasUsageAccess(this)) {
            Toast.makeText(this, "App Switcher: usage access missing", Toast.LENGTH_LONG).show();
            RecentApps.openUsageAccessSettings(this);
            finish();
            return;
        }

        // Welche Apps wirklich laufen, verrät nur adb. Die Abfrage läuft im
        // Hintergrund, die Leiste erscheint, sobald das Ergebnis da ist.
        // Unscharfer Hintergrund: Bildschirmfoto gleichzeitig mit der Abfrage.
        // Die Leiste ist noch nicht zu sehen, das Foto zeigt also die App dahinter.
        long start = SystemClock.uptimeMillis();
        // Scharfes Foto für die Karte des aktuellen Bildschirms (tvOS), unscharfes für den Hintergrund.
        Bitmap[] backdrop = new Bitmap[2];
        Thread captureThread = new Thread(() -> {
            try {
                Bitmap[] both = Backdrop.captureBoth(this, true);
                backdrop[0] = both[0];
                backdrop[1] = both[1];
            } catch (Exception e) {
                Log.w("LauncherSwitcher", "No screenshot: " + e);
            }
        });
        captureThread.start();
        new Thread(() -> {
            Set<String> running = null;
            try {
                running = runningProcesses();
            } catch (Exception ignored) {
                // Ohne adb bleibt die Liste ungefiltert.
            }
            long psDone = SystemClock.uptimeMillis();
            if (captureThread != null) {
                try {
                    captureThread.join(Math.max(1, BLUR_WAIT_MS - (psDone - start)));
                } catch (InterruptedException ignored) {
                    // Dann eben ohne Foto.
                }
            }
            Bitmap shot = backdrop[0];
            Bitmap blurred = SwitcherPrefs.isBlurEnabled(this) && SwitcherPrefs.allBlur(this) ? backdrop[1] : null;
            Log.i("LauncherSwitcher", "ps " + (psDone - start) + " ms, ready "
                    + (SystemClock.uptimeMillis() - start) + " ms, blur " + (blurred != null));
            List<String> apps = collectApps();
            // Der TV beendet Apps im Hintergrund wegen Speichermangel oft schon
            // nach ein, zwei Minuten. Solche Apps bleiben in der Leiste, werden
            // aber ohne weißen Punkt. Ohne adb ist der Zustand unbekannt, dann gilt alles als laufend.
            Set<String> stopped = new HashSet<>();
            if (running != null) {
                for (String pkg : apps) {
                    if (!isRunning(pkg, running)) {
                        stopped.add(pkg);
                    }
                }
            }
            boolean unknown = running == null;
            runOnUiThread(() -> show(apps, stopped, unknown, shot, blurred));
        }).start();
    }

    private List<String> collectApps() {
        String home = RecentApps.homePackage(this);
        List<String> recent = RecentApps.recentPackages(this);
        // Kommt man von der Startseite, gibt es keine aktuelle App zu überspringen.
        // Aus All Apps oder den Einstellungen heraus gilt dasselbe, das eigene
        // Paket steht nicht in der Liste.
        fromHome = (!recent.isEmpty() && recent.get(0).equals(home))
                || RecentApps.ownUiInFront(this);
        List<String> apps = new ArrayList<>();
        for (String pkg : recent) {
            if (pkg.equals(home)) {
                continue;
            }
            apps.add(pkg);
            if (apps.size() == MAX_APPS) {
                break;
            }
        }
        return apps;
    }

    /** Namen aller laufenden Prozesse, z. B. „com.example“ oder „com.example:service“. */
    private Set<String> runningProcesses() throws Exception {
        AdbClient.requireGranted(this);
        String output = AdbClient.shell(this, "ps -A -o NAME", 5000);
        return new HashSet<>(Arrays.asList(output.split("\\s+")));
    }

    private static boolean isRunning(String pkg, Set<String> processes) {
        if (processes.contains(pkg)) {
            return true;
        }
        String prefix = pkg + ":";
        for (String name : processes) {
            if (name.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- Karten (tvOS)

    /** Kartengröße und Lage wie im tvOS-Screenshot des Nutzers: 1440×810 px, x 240, mittig. */
    private static final int CARD_W_DP = 720;
    private static final int CARD_H_DP = 405;
    private static final int CARD_X_DP = 120;
    private static final int CARD_RADIUS_DP = 12;
    /** Platz über der Karte für App-Symbol und Namen. */
    private static final int HEADER_DP = 46;
    /** So weit schaut jede ältere Karte links hinter der vorderen hervor. */
    private static final int STACK_STEP_DP = 50;
    /** So weit schaut die nächstneuere Karte rechts herein. */
    private static final int PEEK_RIGHT_DP = 45;
    /** Vorderste Karte beim Öffnen: füllt den Bildschirm und schrumpft dann auf ihre Größe. */
    private static final float ZOOM_START = 1920f / 1440f;

    private static final class Card {
        /** null = Startseite. */
        String pkg;
        CharSequence label;
        boolean stopped;
        FrameLayout unit;
        View shade;
        View caption;
    }

    private final List<Card> cards = new ArrayList<>();
    private FrameLayout stack;
    private int focus;
    private boolean opened;

    private void show(List<String> apps, Set<String> stopped, boolean unknown, Bitmap shot, Bitmap blurred) {
        if (isFinishing()) {
            return;
        }
        if (apps.isEmpty() && !fromHome) {
            Toast.makeText(this, "No recent apps", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        runningKnown = !unknown;
        if (unknown) {
            Toast.makeText(this, "adb not reachable, running state unknown",
                    Toast.LENGTH_SHORT).show();
        }
        setContentView(buildLayout(apps, stopped, shot, blurred));
    }

    private View buildLayout(List<String> apps, Set<String> stopped, Bitmap shot, Bitmap blurred) {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        if (blurred != null) {
            ImageView bg = new ImageView(this);
            BitmapDrawable drawable = new BitmapDrawable(getResources(), blurred);
            drawable.setFilterBitmap(true);
            bg.setImageDrawable(drawable);
            bg.setScaleType(ImageView.ScaleType.CENTER_CROP);
            bg.setAlpha(0.6f);
            root.addView(bg, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
        }
        root.setFocusable(View.NOT_FOCUSABLE);
        stack = new FrameLayout(this);
        stack.setClipChildren(false);
        root.setClipChildren(false);
        root.addView(stack, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        // Vorne: der Bildschirm, von dem man kommt (Startseite oder die aktuelle App).
        int first = 0;
        Card front = new Card();
        if (fromHome) {
            front.label = "Home";
        } else {
            front.pkg = apps.get(0);
            front.stopped = stopped.contains(front.pkg);
            first = 1;
        }
        if (addCard(front, shot)) {
            cards.add(front);
        }
        for (int i = first; i < apps.size(); i++) {
            Card c = new Card();
            c.pkg = apps.get(i);
            c.stopped = stopped.contains(c.pkg);
            if (addCard(c, null)) {
                cards.add(c);
            }
        }
        if (cards.size() < 2 && fromHome) {
            Toast.makeText(this, "No recent apps", Toast.LENGTH_SHORT).show();
            finish();
            return root;
        }
        // Wie bisher: Fokus auf der vorigen App, damit OK sofort zu ihr wechselt.
        // Erst zeigt die vorderste Karte den ganzen Bildschirm und schrumpft, dann gleitet der Stapel.
        focus = 0;
        place(false);
        stack.setPivotX(dp(CARD_X_DP + CARD_W_DP / 2));
        stack.setPivotY(screenHeight() / 2f);
        stack.setScaleX(ZOOM_START);
        stack.setScaleY(ZOOM_START);
        stack.post(() -> stack.animate().scaleX(1f).scaleY(1f).setDuration(338)
                .setInterpolator(new DecelerateInterpolator()).withEndAction(() -> {
                    opened = true;
                    if (focus == 0 && cards.size() > 1) {
                        focus = 1;
                    }
                    place(true);
                }).start());
        return root;
    }

    private int screenHeight() {
        return getResources().getDisplayMetrics().heightPixels;
    }

    private int screenWidth() {
        return getResources().getDisplayMetrics().widthPixels;
    }

    /** Baut die Karte samt Kopfzeile und hängt sie in den Stapel. false, wenn die App fehlt. */
    private boolean addCard(Card c, Bitmap shot) {
        PackageManager pm = getPackageManager();
        ApplicationInfo info = null;
        if (c.pkg != null) {
            try {
                info = pm.getApplicationInfo(c.pkg, 0);
                c.label = pm.getApplicationLabel(info);
            } catch (PackageManager.NameNotFoundException e) {
                return false;
            }
        }

        FrameLayout unit = new FrameLayout(this);
        unit.setClipChildren(false);
        c.unit = unit;

        // Karte
        FrameLayout card = new FrameLayout(this);
        card.setOutlineProvider(new tv.bernhard.launcher.RoundedOutline(dp(CARD_RADIUS_DP)));
        card.setClipToOutline(true);
        card.setElevation(dp(14));
        card.setBackgroundColor(Color.rgb(28, 28, 30));
        if (shot != null) {
            ImageView img = new ImageView(this);
            img.setImageBitmap(shot);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            card.addView(img, matchParent());
        } else if (info != null) {
            Drawable bgBanner = pm.getApplicationBanner(info);
            if (bgBanner != null) {
                // Unscharfes, dunkles Banner als Fläche, scharf in der Mitte (keine echten Vorschaubilder).
                ImageView bg = new ImageView(this);
                bg.setImageDrawable(bgBanner);
                bg.setScaleType(ImageView.ScaleType.CENTER_CROP);
                bg.setAlpha(0.55f);
                if (android.os.Build.VERSION.SDK_INT >= 31 && SwitcherPrefs.allBlur(this)) {
                    bg.setRenderEffect(android.graphics.RenderEffect.createBlurEffect(dp(40), dp(40),
                            android.graphics.Shader.TileMode.CLAMP));
                }
                card.addView(bg, matchParent());
            }
            ImageView mid = new ImageView(this);
            Drawable banner = pm.getApplicationBanner(info);
            FrameLayout.LayoutParams mp;
            if (banner != null) {
                mid.setImageDrawable(banner);
                mid.setScaleType(ImageView.ScaleType.CENTER_CROP);
                mid.setOutlineProvider(new tv.bernhard.launcher.RoundedOutline(dp(12)));
                mid.setClipToOutline(true);
                mid.setElevation(dp(10));
                mp = new FrameLayout.LayoutParams(dp(320), dp(180), Gravity.CENTER);
            } else {
                mid.setImageDrawable(pm.getApplicationIcon(info));
                mid.setScaleType(ImageView.ScaleType.FIT_CENTER);
                mp = new FrameLayout.LayoutParams(dp(140), dp(140), Gravity.CENTER);
            }
            card.addView(mid, mp);
        } else {
            TextView t = new TextView(this);
            t.setText(c.label);
            t.setTextColor(Color.WHITE);
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 34);
            t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            t.setGravity(Gravity.CENTER);
            card.addView(t, matchParent());
        }
        View shade = new View(this);
        shade.setBackgroundColor(Color.BLACK);
        shade.setAlpha(0f);
        card.addView(shade, matchParent());
        c.shade = shade;
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(dp(CARD_W_DP), dp(CARD_H_DP));
        cp.topMargin = dp(HEADER_DP);
        unit.addView(card, cp);

        // Kopfzeile: kleines App-Symbol (5:3) wie bei tvOS, daneben Name und Punkt nur bei der vorderen Karte.
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView mini = new ImageView(this);
        mini.setOutlineProvider(new tv.bernhard.launcher.RoundedOutline(dp(5)));
        mini.setClipToOutline(true);
        Drawable miniBanner = info != null ? pm.getApplicationBanner(info) : null;
        if (miniBanner != null) {
            mini.setImageDrawable(miniBanner);
            mini.setScaleType(ImageView.ScaleType.CENTER_CROP);
        } else if (info != null) {
            mini.setImageDrawable(pm.getApplicationIcon(info));
            mini.setScaleType(ImageView.ScaleType.FIT_CENTER);
            mini.setPadding(dp(4), dp(3), dp(4), dp(3));
            mini.setBackgroundColor(Color.rgb(58, 58, 62));
        } else {
            mini.setImageDrawable(getApplicationInfo().loadBanner(pm));
            mini.setScaleType(ImageView.ScaleType.CENTER_CROP);
            mini.setBackgroundColor(Color.rgb(58, 58, 62));
        }
        header.addView(mini, new LinearLayout.LayoutParams(dp(50), dp(30)));
        LinearLayout caption = new LinearLayout(this);
        caption.setOrientation(LinearLayout.HORIZONTAL);
        caption.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = new TextView(this);
        name.setText(c.label);
        name.setTextColor(Color.WHITE);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        name.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        name.setMaxLines(1);
        name.setEllipsize(TextUtils.TruncateAt.END);
        caption.addView(name);
        View dot = new View(this);
        GradientDrawable dotShape = new GradientDrawable();
        dotShape.setShape(GradientDrawable.OVAL);
        dotShape.setColor(Color.WHITE);
        dot.setBackground(dotShape);
        dot.setVisibility(c.pkg != null && !c.stopped && runningKnown ? View.VISIBLE : View.GONE);
        LinearLayout.LayoutParams dp6 = new LinearLayout.LayoutParams(dp(6), dp(6));
        dp6.leftMargin = dp(8);
        caption.addView(dot, dp6);
        caption.setAlpha(0f);
        c.caption = caption;
        LinearLayout.LayoutParams capp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        capp.leftMargin = dp(12);
        header.addView(caption, capp);
        FrameLayout.LayoutParams hp = new FrameLayout.LayoutParams(dp(CARD_W_DP), dp(HEADER_DP - 10));
        unit.addView(header, hp);

        FrameLayout.LayoutParams up = new FrameLayout.LayoutParams(dp(CARD_W_DP), dp(CARD_H_DP + HEADER_DP));
        up.topMargin = (screenHeight() - dp(CARD_H_DP)) / 2 - dp(HEADER_DP);
        unit.setPivotX(0);
        unit.setPivotY(dp(HEADER_DP + CARD_H_DP / 2));
        stack.addView(unit, up);
        return true;
    }

    private static FrameLayout.LayoutParams matchParent() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    /** Karten nach dem Fokus anordnen: ältere links gestaffelt dahinter, neuere rechts am Rand. */
    private void place(boolean animate) {
        for (int i = 0; i < cards.size(); i++) {
            Card c = cards.get(i);
            int d = i - focus;
            float tx;
            float scale = 1f;
            float shade = 0f;
            float alpha = 1f;
            float z;
            if (d == 0) {
                tx = dp(CARD_X_DP);
                z = dp(20);
            } else if (d > 0) {
                int k = Math.min(d, 4);
                tx = dp(CARD_X_DP) - dp(STACK_STEP_DP) * Math.min(d, 3);
                scale = 1f - 0.05f * k;
                shade = Math.min(0.6f, 0.22f * k);
                alpha = d > 3 ? 0f : 1f;
                z = dp(20) - d * dp(2);
            } else {
                tx = d == -1 ? screenWidth() - dp(PEEK_RIGHT_DP) : screenWidth() + dp(20);
                shade = 0.35f;
                z = dp(20) - d * dp(2);
            }
            View u = c.unit;
            u.setTranslationZ(z);
            float cap = d == 0 ? 1f : 0f;
            if (animate) {
                u.animate().translationX(tx).scaleX(scale).scaleY(scale).alpha(alpha)
                        .setInterpolator(new DecelerateInterpolator()).setDuration(364).start();
                c.shade.animate().alpha(shade).setDuration(364).start();
                c.caption.animate().alpha(cap).setDuration(260).start();
            } else {
                u.setTranslationX(tx);
                u.setScaleX(scale);
                u.setScaleY(scale);
                u.setAlpha(alpha);
                c.shade.setAlpha(shade);
                c.caption.setAlpha(cap);
            }
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (cards.isEmpty() || event.getAction() != KeyEvent.ACTION_DOWN) {
            return super.dispatchKeyEvent(event);
        }
        int key = event.getKeyCode();
        boolean first = event.getRepeatCount() == 0;
        switch (key) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (focus < cards.size() - 1) {
                    focus++;
                    opened = true;
                    place(true);
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (focus > 0) {
                    focus--;
                    opened = true;
                    place(true);
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                if (first) {
                    open(currentCard());
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (first && currentCard().pkg != null) {
                    Card c = currentCard();
                    forceQuit(c, c.pkg, c.label, c.stopped);
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (first && currentCard().pkg != null) {
                    openAppInfo(currentCard().pkg);
                }
                return true;
            default:
                return super.dispatchKeyEvent(event);
        }
    }

    private Card currentCard() {
        // Vor dem Ende der Öffnungsanimation gilt schon die vorige App.
        if (!opened && focus == 0 && cards.size() > 1) {
            focus = 1;
        }
        return cards.get(focus);
    }

    /** Vorderste Karte: einfach schließen (der Bildschirm liegt dahinter), sonst App starten. */
    private void open(Card c) {
        if (cards.indexOf(c) == 0 || c.pkg == null) {
            finish();
            return;
        }
        Intent intent = RecentApps.launchIntent(this, c.pkg);
        if (intent != null) {
            startActivity(intent);
        }
        finish();
    }

    private void forceQuit(Card tile, String pkg, CharSequence label, boolean stopped) {
        if (quitting) {
            return;
        }
        quitting = true;
        if (!AdbClient.hasKey(this)) {
            Toast.makeText(this, "Confirm “Allow USB debugging” on the TV (Always allow)",
                    Toast.LENGTH_LONG).show();
        }
        new Thread(() -> {
            String error = null;
            try {
                AdbClient.shell(this, "am force-stop " + pkg, 60000);
            } catch (Exception e) {
                error = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            }
            String finalError = error;
            runOnUiThread(() -> {
                quitting = false;
                if (finalError != null) {
                    Toast.makeText(this, "Force quit failed: " + finalError,
                            Toast.LENGTH_LONG).show();
                    return;
                }
                // Bis zum nächsten Start nicht mehr in der Leiste zeigen.
                RecentApps.markForceQuit(this, pkg);
                Toast.makeText(this, label + (stopped ? " removed" : " closed"),
                        Toast.LENGTH_SHORT).show();
                removeTile(tile);
            });
        }).start();
    }

    /** Karte gleitet nach oben weg (wie Wischen nach oben bei tvOS), danach rückt der Stapel nach. */
    private void removeTile(Card c) {
        int index = cards.indexOf(c);
        if (index < 0) {
            return;
        }
        cards.remove(index);
        View u = c.unit;
        u.animate().translationY(-screenHeight() * 0.7f).alpha(0f).setDuration(325)
                .setInterpolator(new AccelerateInterpolator())
                .withEndAction(() -> stack.removeView(u)).start();
        if (cards.isEmpty()) {
            u.postDelayed(this::finish, 260);
            return;
        }
        focus = Math.min(index, cards.size() - 1);
        place(true);
    }

    private void openAppInfo(String pkg) {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", pkg, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "App info not available", Toast.LENGTH_SHORT).show();
            return;
        }
        finish();
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics()));
    }
}
