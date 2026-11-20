package tv.bestseat.launcher.allapps;

import tv.bestseat.launcher.Fonts;
import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
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
import android.view.Window;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

import tv.bestseat.launcher.LauncherBridge;
import tv.bestseat.launcher.RoundedOutline;
import tv.bestseat.launcher.switcher.Backdrop;
import java.util.function.Supplier;

/**
 * Seite mit allen startbaren Apps als Raster. OK startet die App, langes OK
 * öffnet die App-Info. Pfeil links in der ersten Spalte (oder die Menü-Taste)
 * blendet am linken Rand das Menü mit Filtern, Einstellungen und About ein.
 * Seit App Switcher 1.8.0 Teil des App Switchers (vorher eigene App).
 */
public class AllAppsActivity extends Activity {

    private static final int COLUMNS = 6;

    // Eckenradien wie im AT4K-Launcher (aus dem App Switcher übernommen).
    /** App-Banner, entspricht den Kacheln von AT4K. */
    private static final int BANNER_RADIUS_DP = 15;
    /** Hintergrund der Menüzeile mit Fokus, entspricht der Fokus-Markierung von AT4K. */
    private static final int FOCUS_RADIUS_DP = 18;

    // Raster wie die App-Liste von AT4K (am TV per Screenshot gemessen, 1920×1080,
    // 2 px pro dp): Kachel 247×140 px, Abstand 44 px, Zeilenraster 240 px.
    private static final float TILE_WIDTH_DP = 123.5f;
    private static final float TILE_HEIGHT_DP = 70f;
    private static final float GAP_DP = 22f;
    private static final float ROW_PITCH_DP = 120f;
    /** Vergrößerung der Kachel mit Fokus (AT4K: 289×163 statt 247×140 px). */
    private static final float FOCUS_SCALE = 1.165f;
    /** Abstand des Namens unter der unvergrößerten Kachel. */
    private static final float LABEL_TOP_DP = 80f;
    private static final int MENU_WIDTH_DP = 300;

    private static final int BACKGROUND = Color.rgb(10, 10, 12);
    private static final int PANEL = Color.rgb(30, 30, 34);
    private static final int FOCUS = Color.rgb(70, 70, 80);
    private static final int SECONDARY = Color.rgb(160, 160, 170);

    private ScrollView gridScroller;
    private LinearLayout grid;
    private LinearLayout menu;
    private boolean menuOpen;
    /** Ob sich im Menü ein Filter geändert hat, dann wird beim Schließen neu geladen. */
    private boolean menuChanged;

    /** Paket der Kachel mit Fokus, damit er nach dem Neuladen an derselben Stelle steht. */
    private String focusedPkg;
    private View focusedTile;

    /** Wie lange höchstens auf das Bildschirmfoto für den Hintergrund „Blurred“ gewartet wird. */
    private static final int BLUR_TIMEOUT_MS = 1500;

    private FrameLayout root;
    private boolean revealed;

    /** Schrift beim Aufbau. Die Seite bleibt als eigene Aufgabe liegen; nach einem Wechsel neu aufbauen. */
    private int builtFont;

    @Override
    protected void onResume() {
        super.onResume();
        if (Fonts.current(this) != builtFont) recreate();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        builtFont = Fonts.current(this);
        String background = AllAppsPrefs.background(this);
        boolean blurred = AllAppsPrefs.BG_BLURRED.equals(background);
        if (!blurred) {
            // Das Fenster ist laut Theme durchsichtig, das braucht nur „Blurred“.
            makeOpaque();
            getWindow().getDecorView().setBackgroundColor(BACKGROUND);
        }
        setContentView(buildRoot());
        if (blurred) {
            // Erst unsichtbar, damit das Bildschirmfoto den Bildschirm dahinter zeigt.
            root.setAlpha(0f);
            loadBlurred();
        } else {
            applyBackground(background);
        }
        load();
    }

    /** Hintergrund „Black“ oder „Petrol“ (Backdrop), sofort ohne Wartezeit. */
    private void applyBackground(String background) {
        if (AllAppsPrefs.BG_PETROL.equals(background)) {
            root.setBackground(Backdrop.petrol(getResources().getDisplayMetrics().widthPixels));
        } else {
            root.setBackgroundColor(BACKGROUND);
        }
    }

    /**
     * Hintergrund „Blurred“: Bildschirm dahinter unscharf und abgedunkelt, per
     * Bildschirmfoto (Backdrop.capture, ca. 0,7 s). Klappt das nicht rechtzeitig,
     * bleibt es beim dunklen Hintergrund.
     */
    private void loadBlurred() {
        new Thread(() -> {
            Bitmap backdrop = null;
            long start = SystemClock.uptimeMillis();
            try {
                backdrop = Backdrop.capture(this);
                Log.i("AllAppsBackdrop", "Blurred backdrop in " + (SystemClock.uptimeMillis() - start) + " ms");
            } catch (Exception e) {
                Log.w("AllAppsBackdrop", "No blurred backdrop: " + e);
            }
            Bitmap result = backdrop;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (result != null) {
                    BitmapDrawable drawable = new BitmapDrawable(getResources(), result);
                    drawable.setFilterBitmap(true);
                    root.setBackground(drawable);
                } else if (!revealed) {
                    root.setBackgroundColor(BACKGROUND);
                }
                reveal();
            });
        }).start();
        root.postDelayed(() -> {
            if (!revealed) {
                root.setBackgroundColor(BACKGROUND);
                reveal();
            }
        }, BLUR_TIMEOUT_MS);
    }

    private void reveal() {
        if (revealed) {
            return;
        }
        revealed = true;
        // Danach undurchsichtig, damit die App dahinter nicht weiter mitläuft.
        root.animate().alpha(1f).setDuration(195).withEndAction(this::makeOpaque).start();
    }

    private void makeOpaque() {
        if (Build.VERSION.SDK_INT >= 30) {
            setTranslucent(false);
        }
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        // Inzwischen installierte oder entfernte Apps berücksichtigen.
        load();
    }

    /** Liest die Apps im Hintergrund, Banner laden dauert bei vielen Apps spürbar. */
    private void load() {
        new Thread(() -> {
            List<AppList.Entry> apps = AppList.load(this);
            runOnUiThread(() -> {
                if (!isFinishing()) {
                    fillGrid(apps);
                }
            });
        }).start();
    }

    // ---------------------------------------------------------------- Aufbau

    private View buildRoot() {
        // Hintergrund setzt onCreate() je nach Einstellung „Background“.
        root = new FrameLayout(this);

        // Scrollt in der ersten/letzten Reihe gleich bis an den Rand (EdgeScrollView).
        gridScroller = new EdgeScrollView(this);
        gridScroller.setVerticalScrollBarEnabled(false);
        gridScroller.setClipChildren(false);

        grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setGravity(Gravity.CENTER_HORIZONTAL);
        // Oben derselbe Rand wie links bei AT4K (54 dp).
        grid.setPadding(0, dp(54), 0, dp(30));
        grid.setClipChildren(false);
        grid.setClipToPadding(false);
        gridScroller.addView(grid);
        root.addView(gridScroller);

        root.addView(buildMenu(), new FrameLayout.LayoutParams(
                dp(MENU_WIDTH_DP), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START));
        return root;
    }

    private void fillGrid(List<AppList.Entry> apps) {
        grid.removeAllViews();
        focusedTile = null;
        View toFocus = null;
        LinearLayout row = null;
        for (int i = 0; i < apps.size(); i++) {
            if (i % COLUMNS == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setClipChildren(false);
                row.setClipToPadding(false);
                // Volle Zeilenbreite, damit auch die letzte, kürzere Zeile links beginnt.
                grid.addView(row, new LinearLayout.LayoutParams(
                        Math.round(COLUMNS * dpf(TILE_WIDTH_DP) + (COLUMNS - 1) * dpf(GAP_DP)),
                        Math.round(dpf(ROW_PITCH_DP))));
            }
            AppList.Entry app = apps.get(i);
            View tile = buildTile(app, i % COLUMNS == 0);
            LinearLayout.LayoutParams tileParams = new LinearLayout.LayoutParams(
                    Math.round(dpf(TILE_WIDTH_DP)), ViewGroup.LayoutParams.MATCH_PARENT);
            if (i % COLUMNS != 0) {
                tileParams.leftMargin = Math.round(dpf(GAP_DP));
            }
            row.addView(tile, tileParams);
            // Fokus auf die zuletzt markierte App, sonst auf die erste.
            if (toFocus == null || app.pkg.equals(focusedPkg)) {
                toFocus = tile;
            }
        }
        if (apps.isEmpty()) {
            Toast.makeText(this, "No apps found", Toast.LENGTH_SHORT).show();
            return;
        }
        focusedTile = toFocus;
        if (!menuOpen) {
            View first = toFocus;
            grid.post(first::requestFocus);
        }
    }

    private View buildTile(AppList.Entry app, boolean firstColumn) {
        // Wie bei AT4K: kein Fokus-Hintergrund, die Kachel wird größer, bekommt
        // einen Schatten, und nur dann erscheint der Name darunter.
        FrameLayout tile = new FrameLayout(this);
        tile.setFocusable(true);
        tile.setClickable(true);
        tile.setLongClickable(true);
        // Name und vergrößerte Kachel dürfen über den eigenen Platz hinausragen.
        tile.setClipChildren(false);
        tile.setClipToPadding(false);

        ImageView image = new ImageView(this);
        if (app.banner != null) {
            image.setImageDrawable(app.banner);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        } else {
            // Ohne TV-Banner: App-Symbol mittig auf dunkler Fläche.
            image.setImageDrawable(app.icon);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            image.setPadding(dp(14), dp(10), dp(14), dp(10));
            image.setBackgroundColor(Color.rgb(55, 55, 62));
        }
        image.setOutlineProvider(new RoundedOutline(dp(BANNER_RADIUS_DP)));
        image.setClipToOutline(true);
        tile.addView(image, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.round(dpf(TILE_HEIGHT_DP)), Gravity.TOP));

        TextView text = new TextView(this);
        text.setText(app.label);
        text.setTextColor(Color.WHITE);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        // Schrift wie bei AT4K: Roboto fett mit etwas Laufweite.
        text.setTypeface(Fonts.get(text.getContext(), Fonts.BOLD));
        text.setLetterSpacing(0.02f);
        text.setGravity(Gravity.CENTER_HORIZONTAL);
        text.setMaxLines(1);
        text.setEllipsize(TextUtils.TruncateAt.END);
        text.setIncludeFontPadding(false);
        text.setVisibility(View.INVISIBLE);
        // Breiter als die Kachel (bis in die Abstände), mittig darunter.
        int labelWidth = Math.round(dpf(TILE_WIDTH_DP + GAP_DP));
        FrameLayout.LayoutParams textParams = new FrameLayout.LayoutParams(
                labelWidth, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        textParams.topMargin = Math.round(dpf(LABEL_TOP_DP));
        int overhang = (labelWidth - Math.round(dpf(TILE_WIDTH_DP))) / 2;
        textParams.leftMargin = -overhang;
        textParams.rightMargin = -overhang;
        tile.addView(text, textParams);

        tile.setOnFocusChangeListener((v, hasFocus) -> {
            float scale = hasFocus ? FOCUS_SCALE : 1f;
            image.animate().scaleX(scale).scaleY(scale).translationZ(hasFocus ? dp(10) : 0)
                    .setDuration(195).start();
            text.setVisibility(hasFocus ? View.VISIBLE : View.INVISIBLE);
            if (hasFocus) {
                focusedPkg = app.pkg;
                focusedTile = v;
            }
        });

        tile.setOnClickListener(v -> launch(app));
        tile.setOnLongClickListener(v -> {
            showAppActions(app);
            return true;
        });
        if (firstColumn) {
            // Pfeil links am linken Rand öffnet das Menü.
            tile.setOnKeyListener((v, keyCode, event) -> {
                if (keyCode != KeyEvent.KEYCODE_DPAD_LEFT) {
                    return false;
                }
                if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                    openMenu();
                }
                return true;
            });
        }
        return tile;
    }

    // ------------------------------------------------------------------ Menü

    private View buildMenu() {
        ScrollView scroller = new ScrollView(this);
        scroller.setBackgroundColor(PANEL);
        scroller.setVerticalScrollBarEnabled(false);
        scroller.setVisibility(View.INVISIBLE);
        scroller.setElevation(dp(8));

        menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setPadding(dp(16), dp(32), dp(16), dp(32));
        scroller.addView(menu);

        TextView title = new TextView(this);
        title.setText("All Apps");
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        title.setTypeface(Fonts.get(title.getContext(), Fonts.BOLD));
        title.setPadding(dp(16), 0, 0, dp(8));
        menu.addView(title);

        addSection("Filter");
        addToggle("Show system apps", AllAppsPrefs.SHOW_SYSTEM, true);
        addToggle("Show non-TV apps", AllAppsPrefs.SHOW_NON_TV, true);

        addSection("Settings");
        addRow("Sort by", () -> AllAppsPrefs.get(this, AllAppsPrefs.SORT_BY_INSTALL) ? "Newest" : "Name", v -> {
            AllAppsPrefs.toggle(this, AllAppsPrefs.SORT_BY_INSTALL);
            menuChanged = true;
        });
        addToggle("Close when leaving", AllAppsPrefs.CLOSE_WHEN_LEAVING, false);
        // „Blurred“ braucht den Bildschirm dahinter und wirkt erst beim nächsten Öffnen.
        addRow("Background", () -> backgroundLabel(AllAppsPrefs.background(this)), v -> {
            String next = AllAppsPrefs.nextBackground(this);
            if (!AllAppsPrefs.BG_BLURRED.equals(next)) {
                applyBackground(next);
            }
        });

        addSection("");
        addRow("About", null, v -> showAbout());
        return scroller;
    }

    private static String backgroundLabel(String background) {
        switch (background) {
            case AllAppsPrefs.BG_BLACK:
                return "Black";
            case AllAppsPrefs.BG_BLURRED:
                return "Blurred";
            default:
                return "Petrol";
        }
    }

    private void addSection(String name) {
        TextView label = new TextView(this);
        label.setText(name.toUpperCase());
        label.setTextColor(SECONDARY);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        label.setTypeface(Fonts.get(label.getContext(), Fonts.BOLD));
        label.setLetterSpacing(0.08f);
        label.setPadding(dp(16), dp(20), 0, dp(6));
        menu.addView(label);
    }

    /**
     * Zeile mit Schalter (wie in den Einstellungen des App Switchers), OK schaltet um.
     * reload: Raster beim Schließen neu laden.
     */
    private void addToggle(String name, String key, boolean reload) {
        LinearLayout row = menuRow(name);
        Switch toggle = LauncherBridge.toggle(this);
        toggle.setChecked(AllAppsPrefs.get(this, key));
        row.addView(toggle);
        row.setOnClickListener(v -> {
            AllAppsPrefs.toggle(this, key);
            toggle.setChecked(AllAppsPrefs.get(this, key));
            if (reload) {
                menuChanged = true;
            }
        });
    }

    /** Zeile im Menü. value liefert den angezeigten Wert rechts, null für keinen. */
    private void addRow(String name, Supplier<String> value, View.OnClickListener action) {
        LinearLayout row = menuRow(name);
        TextView valueView = new TextView(this);
        valueView.setTextColor(SECONDARY);
        valueView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        valueView.setTypeface(Fonts.get(valueView.getContext(), Fonts.BOLD));
        if (value != null) {
            valueView.setText(value.get());
            row.addView(valueView);
        }
        row.setOnClickListener(v -> {
            action.onClick(v);
            if (value != null) {
                valueView.setText(value.get());
            }
        });
    }

    /** Leere Menüzeile mit Namen links, Fokus-Hintergrund und Pfeil rechts zum Schließen. */
    private LinearLayout menuRow(String name) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), 0, dp(16), 0);
        // Gleich hoch, ob mit Schalter oder mit Text rechts.
        row.setMinimumHeight(dp(50));
        row.setFocusable(true);
        row.setClickable(true);
        GradientDrawable normal = rounded(Color.TRANSPARENT, FOCUS_RADIUS_DP);
        GradientDrawable focused = rounded(FOCUS, FOCUS_RADIUS_DP);
        row.setBackground(normal);
        row.setOnFocusChangeListener((v, hasFocus) -> v.setBackground(hasFocus ? focused : normal));

        TextView text = new TextView(this);
        text.setText(name);
        text.setTextColor(Color.WHITE);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        text.setTypeface(Fonts.get(text.getContext(), Fonts.BOLD));
        text.setLetterSpacing(0.02f);
        row.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        // Pfeil rechts schließt das Menü wieder.
        row.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode != KeyEvent.KEYCODE_DPAD_RIGHT) {
                return false;
            }
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                closeMenu();
            }
            return true;
        });
        menu.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private void openMenu() {
        if (menuOpen) {
            return;
        }
        menuOpen = true;
        menuChanged = false;
        View panel = (View) menu.getParent();
        // Das Raster darf den Fokus nicht zurückholen, solange das Menü offen ist.
        gridScroller.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        gridScroller.animate().alpha(0.4f).setDuration(234).start();
        panel.setTranslationX(-dp(MENU_WIDTH_DP));
        panel.setVisibility(View.VISIBLE);
        panel.animate().translationX(0).setDuration(234)
                .setInterpolator(new DecelerateInterpolator()).start();
        // Erste Zeile nach dem Titel und der Überschrift „Filter“.
        menu.getChildAt(2).requestFocus();
    }

    private void closeMenu() {
        if (!menuOpen) {
            return;
        }
        menuOpen = false;
        View panel = (View) menu.getParent();
        panel.animate().translationX(-dp(MENU_WIDTH_DP)).setDuration(208)
                .withEndAction(() -> panel.setVisibility(View.INVISIBLE)).start();
        gridScroller.animate().alpha(1f).setDuration(208).start();
        gridScroller.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        if (menuChanged) {
            load();
        } else if (focusedTile != null) {
            focusedTile.requestFocus();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            if (menuOpen) {
                closeMenu();
            } else {
                openMenu();
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onBackPressed() {
        if (menuOpen) {
            closeMenu();
            return;
        }
        super.onBackPressed();
    }

    // ------------------------------------------------------ Langes OK: Aktionen

    /** Kleines Overlay mit „Add to Home Screen“ (in AT4K wieder einblenden) und „App info“. */
    private void showAppActions(AppList.Entry app) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(20), dp(16), dp(16));
        box.setBackground(rounded(PANEL, 24));

        TextView title = new TextView(this);
        title.setText(app.label);
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        title.setTypeface(Fonts.get(title.getContext(), Fonts.BOLD));
        title.setMaxLines(1);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setPadding(dp(16), 0, dp(16), dp(10));
        box.addView(title);

        boolean hidden = LauncherBridge.isHidden(this, app.pkg);
        View add = actionRow(hidden ? "Show on Home Screen" : "Hide from Home Screen", () -> {
            dialog.dismiss();
            LauncherBridge.setHidden(this, app.pkg, !hidden);
            Toast.makeText(this, hidden ? "Shown on the home screen" : "Hidden from the home screen",
                    Toast.LENGTH_SHORT).show();
        });
        box.addView(add);
        box.addView(actionRow("App info", () -> {
            dialog.dismiss();
            openAppInfo(app.pkg);
        }));

        dialog.setContentView(box);
        dialog.show();
        dialog.getWindow().setLayout(dp(380), ViewGroup.LayoutParams.WRAP_CONTENT);
        add.requestFocus();
    }

    private View actionRow(String name, Runnable action) {
        TextView row = new TextView(this);
        row.setText(name);
        row.setTextColor(Color.WHITE);
        row.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        row.setTypeface(Fonts.get(row.getContext(), Fonts.BOLD));
        row.setLetterSpacing(0.02f);
        row.setPadding(dp(16), dp(12), dp(16), dp(12));
        row.setFocusable(true);
        row.setClickable(true);
        GradientDrawable normal = rounded(Color.TRANSPARENT, FOCUS_RADIUS_DP);
        GradientDrawable focused = rounded(FOCUS, FOCUS_RADIUS_DP);
        row.setBackground(normal);
        row.setOnFocusChangeListener((v, hasFocus) -> v.setBackground(hasFocus ? focused : normal));
        row.setOnClickListener(v -> action.run());
        return row;
    }

    // ----------------------------------------------------------------- About

    /** Gemeinsames About des App Switchers. */
    private void showAbout() {
        LauncherBridge.about(this);
    }

    // ---------------------------------------------------------------- Aktionen

    private void launch(AppList.Entry app) {
        if (!startSafely(app.launchIntent())) {
            return;
        }
        if (AllAppsPrefs.get(this, AllAppsPrefs.CLOSE_WHEN_LEAVING)) {
            finish();
        }
    }

    private void openAppInfo(String pkg) {
        startSafely(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", pkg, null)));
    }

    private boolean startSafely(Intent intent) {
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (Exception e) {
            Toast.makeText(this, "Can't open this", Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    // ------------------------------------------------------------------ Hilfen

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(radiusDp));
        return shape;
    }

    private float dpf(float value) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics()));
    }
}
