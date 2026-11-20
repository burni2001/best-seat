package tv.bestseat.launcher.allapps;

import tv.bestseat.launcher.Fonts;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
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

    // Raster wie die App-Liste von AT4K (am TV per Screenshot gemessen, 1920×1080,
    // 2 px pro dp): Kachel 247×140 px, Abstand 44 px, Zeilenraster 240 px.
    private static final float TILE_WIDTH_DP = 123.5f;
    private static final float TILE_HEIGHT_DP = 70f;
    private static final float GAP_DP = 22f;
    private static final float ROW_PITCH_DP = 120f;
    /** Vergrößerung der Kachel mit Fokus (AT4K: 289×163 statt 247×140 px). */
    private static final float FOCUS_SCALE = 1.165f;
    private static final int MENU_WIDTH_DP = 400;
    private static final int MENU_MARGIN_DP = 24;

    private static final int BACKGROUND = Color.rgb(10, 10, 12);

    private ScrollView gridScroller;
    private LinearLayout grid;
    private LinearLayout menu;
    private View menuPanel;
    /** Hinweis zur Menüzeile mit Fokus, wie in den Einstellungen der Startseite. */
    private TextView menuHint;
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
        LauncherBridge.applyTheme(this);
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

        menuPanel = buildMenu();
        FrameLayout.LayoutParams mlp = new FrameLayout.LayoutParams(
                dp(MENU_WIDTH_DP), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END);
        mlp.setMargins(0, dp(MENU_MARGIN_DP), dp(MENU_MARGIN_DP), dp(MENU_MARGIN_DP));
        root.addView(menuPanel, mlp);
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

        // Name wie auf der Startseite (W118): gleiche Größe, Stärke, Schatten und Abstand zur
        // vergrößerten Kachel; zu lange Namen laufen bei Fokus durch.
        TextView text = new TextView(this);
        text.setText(app.label);
        text.setTextColor(Color.WHITE);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f);
        text.setTypeface(Fonts.get(text.getContext(), Fonts.MEDIUM));
        text.setShadowLayer(dp(8), 0, dp(1), Color.argb(150, 0, 0, 0));
        text.setGravity(Gravity.CENTER_HORIZONTAL);
        text.setSingleLine(true);
        text.setEllipsize(TextUtils.TruncateAt.MARQUEE);
        text.setMarqueeRepeatLimit(-1);
        text.setHorizontalFadingEdgeEnabled(true);
        text.setAlpha(0f);
        // Breiter als die Kachel (bis in die Abstände), mittig darunter.
        int labelWidth = Math.round(dpf(TILE_WIDTH_DP + GAP_DP));
        FrameLayout.LayoutParams textParams = new FrameLayout.LayoutParams(
                labelWidth, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        // Wie HomeActivity: Unterkante der vergrößerten Kachel plus 3 dp, dazu 1 dp nach unten verschoben.
        textParams.topMargin = Math.round(dpf(TILE_HEIGHT_DP) * (1f + (FOCUS_SCALE - 1f) / 2f)) + dp(3);
        text.setTranslationY(dp(1));
        int overhang = (labelWidth - Math.round(dpf(TILE_WIDTH_DP))) / 2;
        textParams.leftMargin = -overhang;
        textParams.rightMargin = -overhang;
        tile.addView(text, textParams);

        tile.setOnFocusChangeListener((v, hasFocus) -> {
            float scale = hasFocus ? FOCUS_SCALE : 1f;
            image.animate().scaleX(scale).scaleY(scale).translationZ(hasFocus ? dp(10) : 0)
                    .setDuration(195).start();
            text.animate().alpha(hasFocus ? 1f : 0f).setDuration(hasFocus ? 220 : 100).start();
            text.setSelected(hasFocus);
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
        // Aufbau wie die Einstellungen der Startseite: Abschnittstitel, helle Zeilen mit Fokus,
        // Hinweis zur Zeile mit Fokus unten. Material-Kasten am rechten Rand.
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(LauncherBridge.material(this, dp(15)));
        panel.setElevation(dp(15));
        panel.setPadding(dp(14), dp(22), dp(14), dp(18));
        panel.setVisibility(View.INVISIBLE);

        TextView title = LauncherBridge.text(this, "All Apps", 22, false, true);
        title.setPadding(dp(14), 0, 0, dp(6));
        panel.addView(title);

        ScrollView scroller = new ScrollView(this);
        scroller.setVerticalScrollBarEnabled(false);
        scroller.setClipChildren(false);
        scroller.setClipToPadding(false);
        menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setClipChildren(false);
        menu.setClipToPadding(false);
        menu.setPadding(dp(8), dp(4), dp(8), dp(8));
        scroller.addView(menu);
        panel.addView(scroller, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        menuHint = LauncherBridge.text(this, "", 13, true, false);
        menuHint.setMaxLines(3);
        menuHint.setPadding(dp(14), dp(10), dp(14), 0);
        panel.addView(menuHint);

        addSection("Filter");
        addToggle("Show system apps", AllAppsPrefs.SHOW_SYSTEM, true,
                "Include system apps in the list.");
        addToggle("Show non-TV apps", AllAppsPrefs.SHOW_NON_TV, true,
                "Include apps made for phones and tablets.");

        addSection("Settings");
        addRow("Sort by", () -> AllAppsPrefs.get(this, AllAppsPrefs.SORT_BY_INSTALL) ? "Newest" : "Name", v -> {
            AllAppsPrefs.toggle(this, AllAppsPrefs.SORT_BY_INSTALL);
            menuChanged = true;
        }, "Choose how the apps are ordered.");
        addToggle("Close when leaving", AllAppsPrefs.CLOSE_WHEN_LEAVING, false,
                "Close All Apps when you open an app.");
        // „Blurred“ braucht den Bildschirm dahinter und wirkt erst beim nächsten Öffnen.
        addRow("Background", () -> backgroundLabel(AllAppsPrefs.background(this)), v -> {
            String next = AllAppsPrefs.nextBackground(this);
            if (!AllAppsPrefs.BG_BLURRED.equals(next)) {
                applyBackground(next);
            }
        }, "Choose what fills the background of All Apps.");

        addSection("");
        addRow("About", null, v -> showAbout(), "See the version and who made Best Seat.");
        return panel;
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

    /** Abschnittstitel in Großbuchstaben wie in den Einstellungen der Startseite. */
    private void addSection(String name) {
        TextView label = LauncherBridge.text(this, name.toUpperCase(), 13, true, true);
        label.setPadding(dp(14), dp(menu.getChildCount() == 0 ? 0 : 12), 0, dp(6));
        menu.addView(label);
    }

    /** Zeile mit Schalter, OK schaltet um. reload: Raster beim Schließen neu laden. */
    private void addToggle(String name, String key, boolean reload, String hint) {
        LinearLayout row = menuRow(name, hint);
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
    private void addRow(String name, Supplier<String> value, View.OnClickListener action, String hint) {
        LinearLayout row = menuRow(name, hint);
        TextView valueView = LauncherBridge.text(this, "", 16, true, false);
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

    /** Zeile wie in den Einstellungen der Startseite; Pfeil links schließt das Menü. */
    private LinearLayout menuRow(String name, String hint) {
        LinearLayout row = LauncherBridge.settingsRow(this);
        row.setMinimumHeight(dp(33));
        row.setPadding(dp(14), dp(4), dp(14), dp(4));
        TextView text = LauncherBridge.text(this, name, 17, false, true);
        row.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        View.OnFocusChangeListener base = row.getOnFocusChangeListener();
        row.setOnFocusChangeListener((v, has) -> {
            base.onFocusChange(v, has);
            if (has) menuHint.setText(hint);
        });
        row.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode != KeyEvent.KEYCODE_DPAD_LEFT) {
                return false;
            }
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                closeMenu();
            }
            return true;
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(7);
        menu.addView(row, lp);
        return row;
    }

    private void openMenu() {
        if (menuOpen) {
            return;
        }
        menuOpen = true;
        menuChanged = false;
        View panel = menuPanel;
        // Das Raster darf den Fokus nicht zurückholen, solange das Menü offen ist.
        gridScroller.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        gridScroller.animate().alpha(0.4f).setDuration(234).start();
        panel.setTranslationX(dp(MENU_WIDTH_DP + MENU_MARGIN_DP));
        panel.setVisibility(View.VISIBLE);
        panel.animate().translationX(0).setDuration(234)
                .setInterpolator(new DecelerateInterpolator()).start();
        for (int i = 0; i < menu.getChildCount(); i++) {
            if (menu.getChildAt(i).isFocusable()) {
                menu.getChildAt(i).requestFocus();
                break;
            }
        }
    }

    private void closeMenu() {
        if (!menuOpen) {
            return;
        }
        menuOpen = false;
        View panel = menuPanel;
        panel.animate().translationX(dp(MENU_WIDTH_DP + MENU_MARGIN_DP)).setDuration(208)
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

    /** Kontextmenü wie auf der Startseite: neben der Kachel, ohne Kopfzeile. */
    private void showAppActions(AppList.Entry app) {
        boolean hidden = LauncherBridge.isHidden(this, app.pkg);
        LauncherBridge.contextMenu(this, focusedTile,
                new String[] {hidden ? "Show on Home Screen" : "Hide from Home Screen", "App Info"},
                new Runnable[] {
                    () -> {
                        LauncherBridge.setHidden(this, app.pkg, !hidden);
                        Toast.makeText(this, hidden ? "Shown on the home screen" : "Hidden from the home screen",
                                Toast.LENGTH_SHORT).show();
                    },
                    () -> openAppInfo(app.pkg)
                });
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

    private float dpf(float value) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics()));
    }
}
