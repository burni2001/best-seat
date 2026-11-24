package tv.bestseat.launcher;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.LauncherApps;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.animation.ValueAnimator;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowManager;
import android.os.SystemClock;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Startseite: Top Shelf oben, darunter das Kachelraster. */
public final class HomeActivity extends Activity
        implements PackageCallback.Listener, VpnCallback.Listener {

    private static final String TAG = "Launcher.Home";
    static final String ACTION_REFRESH = "tv.bestseat.launcher.REFRESH";
    static final String ACTION_EXPORT_BANNERS = "tv.bestseat.launcher.EXPORT_BANNERS";
    static final String ACTION_HIDDEN = "tv.bestseat.launcher.SHOW_HIDDEN";

    /** Rand links/rechts wie tvOS (90 px). */
    static final int SIDE_DP = 45;
    private static final long STORAGE_TIMEOUT_MS = 90_000;

    final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    LayoutStore store;
    private final HomeBanners banners = new HomeBanners(this);
    private final TopShelf topShelf = new TopShelf(this, banners);
    private final HomeFolders folders = new HomeFolders(this);
    List<Tile> allTiles = new ArrayList<>();
    final List<TileView> tileViews = new ArrayList<>();
    private final List<Tile> visible = new ArrayList<>();

    PageScrollView scroll;
    private LinearLayout grid;
    /** Fläche der Favoriten-Leiste (null ohne Favoriten). */
    private DockDrawable dock;
    TileView currentTileView;
    private LinearLayout topBar;
    private LinearLayout topContent;
    private final Runnable hideTopBar = this::fadeTopBar;
    /** VPN-Anzeige oben blendet nach so vielen ms ohne Taste aus (OLED: nichts steht lange still). */
    private static final long TOP_BAR_IDLE_MS = 15_000;
    private TextView vpnPill;
    private StatusPill statusPill;

    String focusedKey;
    private String movingKey;
    int lastDx;
    private int lastDy;
    private int builtColumns;
    private boolean builtNonTv;
    private boolean builtFavorites;
    private String builtTiles = "";
    private boolean pendingHidden;
    private int builtStoreVersion;
    private boolean builtLight;
    private int builtFont;
    private boolean resumed;
    /** Sichtbar seit (uptime), 0 = gestoppt. Für „Home auf der Startseite“: Android pausiert vor onNewIntent. */
    private long visibleSince;
    private Dialog openDialog;

    private String waitingPkg;
    private long waitingSince;
    private Dialogs.Panel waitingPanel;

    private long lastInput = SystemClock.elapsedRealtime();
    private final Runnable saverTimer = this::startScreensaver;

    private LauncherApps launcherApps;
    private PackageCallback packageCallback;
    private ConnectivityManager connectivity;
    private VpnCallback vpnCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        UsbNotice.install(this);
        builtLight = Ui.apply(this);
        builtFont = Prefs.font(this);
        pendingHidden = ACTION_HIDDEN.equals(getIntent().getAction());
        store = LayoutStore.get(this);
        buildUi();

        launcherApps = (LauncherApps) getSystemService(Context.LAUNCHER_APPS_SERVICE);
        packageCallback = new PackageCallback(this);
        launcherApps.registerCallback(packageCallback, handler);

        connectivity = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        vpnCallback = new VpnCallback(this);
        try {
            NetworkRequest request = new NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
                    .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                    .build();
            connectivity.registerNetworkCallback(request, vpnCallback, handler);
        } catch (RuntimeException e) {
            Log.w(TAG, "VPN callback failed", e);
        }
        reload();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            launcherApps.unregisterCallback(packageCallback);
            connectivity.unregisterNetworkCallback(vpnCallback);
        } catch (RuntimeException ignored) {
        }
        worker.shutdownNow();
        topShelf.destroy();
    }

    /** Laufende Startseite für die Übergabe der Pille an das Kontrollzentrum (gleicher Prozess). */
    private static java.lang.ref.WeakReference<HomeActivity> current;

    /** Vom Kontrollzentrum: eigene Pille liegt darüber (true) bzw. ist wieder weg (false). */
    static void setPillHidden(boolean hidden) {
        HomeActivity h = current == null ? null : current.get();
        if (h != null && h.statusPill != null) {
            h.statusPill.setVisibility(hidden ? View.INVISIBLE : View.VISIBLE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        current = new java.lang.ref.WeakReference<>(this);
        if (statusPill != null) {
            statusPill.refreshAvatar();
            statusPill.refreshClock();
            statusPill.setVisibility(ControlCenterActivity.hidesHomePill() ? View.INVISIBLE : View.VISIBLE);
        }
        // Erscheinungsbild geändert (Einstellung oder Automatik zur vollen Stunde): Seite neu aufbauen.
        if (Prefs.isLight(this) != builtLight || Prefs.font(this) != builtFont) {
            recreate();
            return;
        }
        resumed = true;
        noteInput();
        if (builtColumns != 0 && (builtColumns != Prefs.columns(this) || builtNonTv != Prefs.nonTvApps(this)
                || builtFavorites != Prefs.favorites(this)
                || !builtTiles.equals(Prefs.settingsTile(this) + "/" + Prefs.hiddenTile(this))
                || builtStoreVersion != store.version)) {
            reload();
        }
        vpnPill.setVisibility(Prefs.showVpn(this) && vpnConnected ? View.VISIBLE : View.GONE);
        topShelf.onResume(currentTileView);
    }

    @Override
    protected void onPause() {
        super.onPause();
        resumed = false;
        // Die Pille bleibt stehen: Das Kontrollzentrum blendet sie erst aus, wenn seine eigene Pille
        // genau darüber gezeichnet ist (setPillHidden), sonst blitzt die Uhr kurz weg.
        handler.removeCallbacks(saverTimer);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        stopMoving(true);
    }

    @Override
    protected void onStart() {
        super.onStart();
        visibleSince = android.os.SystemClock.uptimeMillis();
        visibleHome = new java.lang.ref.WeakReference<>(this);
        removeZoom(false);
        maybeZoomBack();
    }

    @Override
    protected void onStop() {
        super.onStop();
        visibleSince = 0;
        if (visibleHome != null && visibleHome.get() == this) visibleHome = null;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (ACTION_REFRESH.equals(intent.getAction())) {
            // Von Hand angestoßen (Fernbedienung über den Google TV Helper): immer neu aufbauen, auch wenn
            // sich an den Apps nichts geändert hat, und kurz Bescheid geben (W119).
            ShelfContent.invalidate();
            banners.clear();
            tilesSignature = "";
            android.widget.Toast.makeText(this, "Refreshing home screen…", android.widget.Toast.LENGTH_SHORT).show();
            reload();
            return;
        }
        if (ACTION_EXPORT_BANNERS.equals(intent.getAction())) {
            banners.export(allTiles, worker);
            return;
        }
        if (ACTION_HIDDEN.equals(intent.getAction())) {
            hiddenApps();
            return;
        }
        // Home-Taste, während die Startseite schon vorne ist: zurück an den Anfang (wie tvOS).
        // „Schon vorne“ = seit über 0,5 s sichtbar. resumed taugt nicht: Android pausiert die Aktivität,
        // bevor es den neuen Intent zustellt. Aus einer App oder dem Schoner kommend war sie gestoppt.
        boolean onHome = visibleSince != 0 && android.os.SystemClock.uptimeMillis() - visibleSince > 500;
        // Doppel-Home erkennt tvQuickActions selbst (schickt dann CONTROL_CENTER), hier kommt nur einfaches Home an.
        if (intent.hasCategory(Intent.CATEGORY_HOME)) {
            if (ControlCenterActivity.wasJustOpen()) {
                // Home bei offenem CC: schließt es animiert, kein Sprung an den Anfang (W104).
                ControlCenterActivity.closeAnimated(this);
                return;
            }
            // Home, während die Startseite schon vorne ist: sofort an den Anfang.
            if (onHome) resetToStart();
        }
    }


    private void resetToStart() {
        if (isDestroyed()) return;
        if (openDialog != null) openDialog.dismiss();
        stopMoving(true);
        if (!tileViews.isEmpty()) {
            lastDx = 0;
            lastDy = 0;
            tileViews.get(0).requestFocus();
        }
        scroll.animateTo(0);
    }

    /** Startseite, solange sie sichtbar ist (für das Kontrollzentrum per Intent). */
    private static java.lang.ref.WeakReference<HomeActivity> visibleHome;

    /**
     * Kontrollzentrum per Intent (tvQuickActions, Doppel-HOME): Ist die Startseite sichtbar, öffnet sie es
     * selbst, damit es wie über die Menü-Taste den weichgezeichneten Hintergrund bekommt. false: keine
     * Startseite sichtbar (eine App ist vorne).
     */
    static boolean openControlCenterFromIntent() {
        HomeActivity h = visibleHome != null ? visibleHome.get() : null;
        if (h == null || h.visibleSince == 0 || h.isFinishing()) return false;
        h.handler.post(() -> h.openControlCenter(null));
        return true;
    }

    private void openControlCenter(String overPkg) {
        ControlCenterActivity.homeBlur = null;
        // Milchglas des Kontrollzentrums: Fensterinhalt der Startseite so, wie er gerade auf dem Bildschirm
        // steht (PixelCopy, auch Hardware-Bitmaps und Weichzeichner), klein kopiert und weichgezeichnet (B48).
        if (overPkg == null && Prefs.blurEnabled(this) && getWindow() != null
                && getWindow().getDecorView().isAttachedToWindow()) {
            try {
                final android.graphics.Bitmap small = android.graphics.Bitmap.createBitmap(192, 108,
                        android.graphics.Bitmap.Config.ARGB_8888);
                android.view.PixelCopy.request(getWindow(), small, result -> {
                    if (result == android.view.PixelCopy.SUCCESS) ControlCenterActivity.homeBlur = Snapshot.blur(small);
                    startControlCenter(null);
                }, handler);
                return;
            } catch (RuntimeException e) {
                Log.w(TAG, "No snapshot for the Control Center", e);
            }
        }
        startControlCenter(overPkg);
    }

    private void startControlCenter(String overPkg) {
        if (isDestroyed()) return;
        Intent cc = new Intent(this, ControlCenterActivity.class);
        if (overPkg != null) {
            cc.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(ControlCenterActivity.EXTRA_OVER_APP, overPkg);
        }
        startActivity(cc, android.app.ActivityOptions.makeCustomAnimation(this, 0, 0).toBundle());
        overridePendingTransition(0, 0);
    }

    /** stillResumed: auch eine App gelten lassen, deren Pause Android noch nicht eingetragen hat (onStart). */
    private static String appLeftByHome(Context c, long pressWall, boolean stillResumed) {
        try {
            android.app.usage.UsageStatsManager usm =
                    (android.app.usage.UsageStatsManager) c.getSystemService(Context.USAGE_STATS_SERVICE);
            android.app.usage.UsageEvents events = usm.queryEvents(pressWall - 60_000, System.currentTimeMillis());
            android.app.usage.UsageEvents.Event e = new android.app.usage.UsageEvents.Event();
            String own = c.getPackageName();
            String lastPkg = null;
            long lastPause = 0;
            String resumedPkg = null;
            while (events.hasNextEvent()) {
                events.getNextEvent(e);
                if (own.equals(e.getPackageName())) continue;
                if (e.getEventType() == android.app.usage.UsageEvents.Event.ACTIVITY_PAUSED) {
                    lastPkg = e.getPackageName();
                    lastPause = e.getTimeStamp();
                    resumedPkg = null;
                } else if (e.getEventType() == android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED) {
                    // Wieder vorne: (noch) nicht verlassen.
                    lastPkg = null;
                    resumedPkg = e.getPackageName();
                }
            }
            if (stillResumed && resumedPkg != null) return resumedPkg;
            if (lastPkg == null || Math.abs(pressWall - lastPause) > 1500) return null;
            return lastPkg;
        } catch (RuntimeException ex) {
            Log.w(TAG, "usage stats", ex);
            return null;
        }
    }

    // ---------------------------------------------------------------- Oberfläche

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        rootView = root;
        root.setBackgroundColor(Ui.BACKGROUND);
        root.setClipChildren(false);

        topShelf.addBackground(root);

        scroll = new PageScrollView(this);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setClipChildren(false);
        page.setClipToPadding(false);
        scroll.addView(page);
        // B37: Nach einem Kaltstart (Android hatte den Prozess beendet) standen kurz nur leere
        // Top-Shelf-Karten da. Seite erst zeigen, wenn die Kacheln geladen sind (revealPage).
        scroll.setAlpha(0f);
        root.addView(scroll, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        page.addView(topShelf.createShelf(), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                Ui.dp(this, TopShelf.SHELF_DP)));

        grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setClipChildren(false);
        grid.setClipToPadding(false);
        // Wenig Platz unter der letzten Reihe: das Scrollen endet, sobald sie ganz zu sehen ist (B11).
        // Mindesthöhe = Bildschirm, damit auch bei wenigen Reihen der Top Shelf wegscrollen kann.
        grid.setPadding(Ui.dp(this, SIDE_DP), Ui.dp(this, 14), Ui.dp(this, SIDE_DP), Ui.dp(this, 56));
        grid.setMinimumHeight(getResources().getDisplayMetrics().heightPixels);
        page.addView(grid, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        // Kopfzeile oben rechts wie tvOS: VPN und die Pille mit Uhrzeit und Profilbild (StatusPill).
        // Gleiche Lage wie im Kontrollzentrum; blendet beim Scrollen und nach 15 s ohne Taste aus.
        topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.TOP | Gravity.END);
        topBar.setPadding(Ui.dp(this, SIDE_DP), Ui.dp(this, StatusPill.TOP_DP), Ui.dp(this, StatusPill.RIGHT_DP), 0);
        topContent = new LinearLayout(this);
        topContent.setOrientation(LinearLayout.HORIZONTAL);
        topContent.setGravity(Gravity.CENTER_VERTICAL);
        topBar.addView(topContent);
        vpnPill = Ui.text(this, "VPN", 13, Ui.TEXT, true);
        vpnPill.setPadding(Ui.dp(this, 10), Ui.dp(this, 2), Ui.dp(this, 10), Ui.dp(this, 2));
        vpnPill.setBackground(Ui.rounded(Color.argb(150, 40, 160, 90), Ui.dp(this, 10)));
        vpnPill.setVisibility(View.GONE);
        LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        vp.rightMargin = Ui.dp(this, 10);
        topContent.addView(vpnPill, vp);
        statusPill = new StatusPill(this, false);
        topContent.addView(statusPill, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                Ui.dp(this, StatusPill.HEIGHT_DP)));
        root.addView(topBar, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP));

        int shelfPx = Ui.dp(this, TopShelf.SHELF_DP);
        scroll.listener = y -> {
            float f = Math.max(0f, Math.min(1f, y / (float) (shelfPx * 0.8f)));
            topShelf.onScroll(y, f);
            topBar.setAlpha(1f - Math.min(1f, f * 3f));
            applyRowLift(y);
        };
        grid.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> applyRowLift(scroll.getScrollY()));

        setContentView(root);
    }

    // ---------------------------------------------------------------- Laden

    private final Runnable reloadRunnable = this::reload;

    private void reload() {
        final Context app = getApplicationContext();
        worker.execute(() -> {
            List<Tile> loaded;
            try {
                loaded = AppRepository.load(app);
            } catch (RuntimeException e) {
                Log.e(TAG, "load failed", e);
                handler.post(this::revealPage);
                return;
            }
            final String appSig = signature(app, loaded);
            handler.post(() -> {
                if (isDestroyed()) return;
                // Auch Einstellungen, die das Raster ändern (Spalten, Favoriten, eingebaute Kacheln, Layout),
                // sonst bleibt nach einer Änderung das alte Raster stehen (B40).
                final String sig = appSig + "#" + layoutSignature();
                // Philips meldet z. B. org.droidtv.gel jede Minute als geändert. Ohne echte Änderung
                // nicht neu aufbauen, sonst spielt die fokussierte Kachel ihre Fokus-Animation erneut.
                if (sig.equals(tilesSignature) && !tileViews.isEmpty()) {
                    Log.i(TAG, "Reload: unchanged, grid kept");
                    checkWaiting();
                    return;
                }
                tilesSignature = sig;
                allTiles = loaded;
                buildGrid();
                checkWaiting();
                revealPage();
            });
        });
    }

    private boolean pageRevealed;

    /** Erstes Laden fertig: Seite einblenden, ein Bild später, damit Karten und Kacheln schon stehen. */
    private void revealPage() {
        if (pageRevealed) return;
        pageRevealed = true;
        scroll.postOnAnimation(() -> scroll.animate().alpha(1f).setDuration(Ui.ms(160)).start());
    }

    private String tilesSignature = "";

    private String layoutSignature() {
        return Prefs.columns(this) + "/" + Prefs.favorites(this) + "/" + Prefs.nonTvApps(this) + "/"
                + Prefs.settingsTile(this) + "/" + Prefs.hiddenTile(this) + "/" + store.version + "/"
                + Profiles.currentId(this);
    }

    /** Alles, was die Kacheln sichtbar ändert: Reihenfolge, Namen, Verfügbarkeit, Ordnerinhalt, App-Version. */
    private static String signature(Context c, List<Tile> tiles) {
        StringBuilder b = new StringBuilder();
        for (Tile t : tiles) appendSig(c, b, t);
        return b.toString();
    }

    private static void appendSig(Context c, StringBuilder b, Tile t) {
        b.append(t.key).append('|').append(t.kind).append('|').append(t.label).append('|')
                .append(t.available).append('|').append(t.external).append('|').append(t.color);
        if (t.pkg != null) {
            try {
                b.append('|').append(c.getPackageManager().getPackageInfo(t.pkg, 0).lastUpdateTime);
            } catch (Exception ignored) {
                b.append("|-");
            }
        }
        if (t.members != null) {
            b.append('[');
            for (Tile m : t.members) appendSig(c, b, m);
            b.append(']');
        }
        b.append(';');
    }

    void buildGrid() {
        int columns = Prefs.columns(this);
        builtColumns = columns;
        builtNonTv = Prefs.nonTvApps(this);
        final boolean favorites = Prefs.favorites(this);
        builtFavorites = favorites;
        builtTiles = Prefs.settingsTile(this) + "/" + Prefs.hiddenTile(this);
        builtStoreVersion = store.version;
        String keep = focusedKey;
        boolean hadFocus = grid.hasFocus() || grid.getChildCount() == 0;

        visible.clear();
        int hiddenCount = 0;
        // Ordner stehen in store.order wie Kacheln; ihre Apps erscheinen nur im Ordner.
        java.util.Map<String, Tile> byKey = new java.util.HashMap<>();
        for (Tile t : allTiles) byKey.put(t.key, t);
        java.util.Set<String> inFolders = new java.util.HashSet<>();
        for (LayoutStore.Folder f : store.folders.values()) inFolders.addAll(f.keys);
        java.util.Set<String> placed = new java.util.HashSet<>();
        for (String key : new ArrayList<>(store.order)) {
            if (LayoutStore.isFolder(key)) {
                Tile folder = folders.tile(key, byKey);
                if (folder != null) visible.add(folder);
                continue;
            }
            Tile t = byKey.get(key);
            if (t == null || inFolders.contains(key) || !placed.add(key)) continue;
            if (store.hidden.contains(t.key)) hiddenCount++;
            else visible.add(t);
        }
        for (Tile t : allTiles) {
            if (placed.contains(t.key) || inFolders.contains(t.key)) continue;
            if (store.hidden.contains(t.key)) hiddenCount++;
            else visible.add(t);
        }
        if (hiddenCount > 0 && Prefs.hiddenTile(this)) {
            Tile h = new Tile("builtin:hidden", Tile.HIDDEN_APPS, "Hidden apps");
            h.color = Color.rgb(44, 46, 54);
            h.icon = getDrawable(android.R.drawable.ic_menu_view);
            visible.add(h);
        }
        Tile s = new Tile("builtin:settings", Tile.SETTINGS, "Settings");
        s.color = Color.rgb(44, 46, 54);
        s.icon = new ControlIcons.Icon(ControlItems.SETTINGS, Color.argb(235, 255, 255, 255));
        if (Prefs.settingsTile(this)) visible.add(s);

        grid.removeAllViews();
        tileViews.clear();
        dock = null;
        int content = getResources().getDisplayMetrics().widthPixels - 2 * Ui.dp(this, SIDE_DP);
        int gap = Ui.dp(this, columns >= 7 ? 18 : 24);
        int w = (content - gap * (columns - 1)) / columns;
        // tvOS-Kacheln sind 5:3 (250×150), nicht 16:9.
        int h = Math.round(w * 3f / 5f);

        LinearLayout row = null;
        for (int i = 0; i < visible.size(); i++) {
            if (i % columns == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setClipChildren(false);
                row.setClipToPadding(false);
                LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                // Mehr Luft zwischen den Reihen, damit der Name unter der fokussierten Kachel frei steht (B13).
                rp.bottomMargin = Ui.dp(this, 24);
                if (favorites && i == 0) {
                    // Favoriten-Leiste wie bei tvOS: eigene, leicht helle Milchglas-Fläche um die oberste Reihe.
                    int px = Ui.dp(this, 20);
                    // Ohne Namen, Kacheln vertikal mittig auf der Leiste (W20); Maße wie tvOS-Dock.
                    int py = Ui.dp(this, 20);
                    row.setPadding(px, py, px, py);
                    // Wie tvOS (Screenshot des Nutzers, YouTube links im Fokus): Die Leiste wächst an der
                    // Seite nur so weit, dass die vergrößerte Kachel rundum denselben Abstand hat wie oben
                    // und unten (~22 px); der Radius dieser Seite ist konzentrisch (Kachelradius + Abstand).
                    float growW = w * (TileView.FOCUS_SCALE - 1f) / 2f;
                    float growH = h * (TileView.FOCUS_SCALE - 1f) / 2f;
                    float gapV = py - growH;
                    float extend = Math.max(0f, growW - (px - gapV));
                    float tileR = w * 0.12f * TileView.FOCUS_SCALE;
                    dock = new DockDrawable(Ui.DOCK, Ui.DOCK_STROKE, Math.max(1, Ui.dp(this, 1)),
                            Ui.dp(this, 30), extend, tileR + gapV);
                    row.setBackground(dock);
                    rp.leftMargin = -px;
                    rp.rightMargin = -px;
                    rp.bottomMargin = Ui.dp(this, 35); // tvOS: 70 px Leiste bis nächste Reihe (oben angehoben, siehe applyRowLift)
                }
                grid.addView(row, rp);
            }
            final Tile tile = visible.get(i);
            final int rowIndex = i / columns;
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setClipChildren(false);
            cell.setClipToPadding(false);
            final TileView tv = new TileView(this, tile, w, h);
            tv.setId(View.generateViewId());
            cell.addView(tv);
            final TextView label = Ui.text(this, tile.label, 14, Ui.TEXT, true);
            label.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14.5f);
            label.setGravity(Gravity.CENTER);
            label.setAlpha(0f);
            // tvOS: Name weiß in beiden Designs, weicher Schatten (hell sehr dezent).
            label.setTextColor(Color.WHITE);
            label.setShadowLayer(Ui.dp(this, 8), 0, Ui.dp(this, 1), Ui.light ? Color.argb(70, 0, 0, 0) : Color.argb(150, 0, 0, 0));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = Math.round(h * (TileView.FOCUS_SCALE - 1f) / 2f) + Ui.dp(this, 3);
            // Abstand wie im tvOS-Screenshot des Nutzers: 23 px von der vergrößerten Kachel bis zur
            // Oberkante der Schrift (nur gezeichnet, Reihenabstand bleibt). Schrift Medium statt Bold.
            label.setTranslationY(Ui.dp(this, 1));
            label.setTypeface(Fonts.get(label.getContext(), Fonts.MEDIUM));
            // Zu lange Namen laufen bei Fokus durch, wie im Kontrollzentrum (W116).
            Ui.marquee(label);
            if (!(favorites && rowIndex == 0)) cell.addView(label, lp);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i % columns != 0) cp.leftMargin = gap;
            row.addView(cell, cp);

            final boolean firstInRow = i % columns == 0;
            final boolean lastInRow = i % columns == columns - 1 || i == visible.size() - 1;
            tv.setOnFocusChangeListener((v, has) -> {
                tv.focus(has, lastDx, lastDy);
                if (dock != null && has) {
                    boolean dockRow = favorites && rowIndex == 0;
                    dock.setEdges(dockRow && firstInRow, dockRow && lastInRow);
                }
                label.animate().alpha(has ? 1f : 0f).setDuration(Ui.ms(has ? 220 : 100)).start();
                label.setSelected(has);
                if (has) {
                    focusedKey = tile.key;
                    currentTileView = tv;
                    if (favorites && rowIndex > 0) {
                        // Nur die Favoriten füllen den Top Shelf; darunter bleibt der letzte Inhalt stehen.
                        topShelf.cancelPendingLoad();
                        topShelf.freeze(true, true);
                    } else {
                        topShelf.freeze(false, true);
                        topShelf.show(tile);
                    }
                    scrollToRow(rowIndex);
                }
            });
            tv.setOnClickListener(v -> {
                if (movingKey == null) {
                    handler.postDelayed(() -> {
                        if (isFinishing()) return;
                        captureZoom(tv);
                        open(tile);
                    }, pressRemaining());
                }
            });
            tv.setOnLongClickListener(v -> {
                if (movingKey == null) tileMenu(tile);
                return true;
            });
            tileViews.add(tv);
        }

        TileView target = null;
        for (TileView tv : tileViews) {
            if (tv.tile.key.equals(keep)) target = tv;
        }
        if (target == null && !tileViews.isEmpty()) target = tileViews.get(0);
        if (target != null && hadFocus) {
            lastDx = 0;
            lastDy = 0;
            target.requestFocus();
        }
        if (movingKey != null) {
            for (TileView tv : tileViews) tv.setWiggle(tv.tile.key.equals(movingKey));
        }
        if (pendingHidden) {
            pendingHidden = false;
            handler.post(this::hiddenApps);
        }
    }

    /**
     * tvOS (Screenshots des Nutzers): Oben auf den Favoriten liegen die Reihen darunter 28 px näher an der
     * Leiste (~24 px schauen hervor), heruntergescrollt ist der Abstand 70 px. Der Versatz läuft mit dem Scrollen aus.
     */
    private void applyRowLift(int y) {
        View first = grid.getChildAt(0);
        if (first == null) return;
        int base = Math.max(1, grid.getTop() + first.getTop() - Ui.dp(this, 30));
        float t = Math.max(0f, Math.min(1f, y / (float) base));
        float lift = -Ui.dp(this, 14) * (1f - t);
        for (int i = 1; i < grid.getChildCount(); i++) grid.getChildAt(i).setTranslationY(lift);
    }

    private void scrollToRow(int rowIndex) {
        scroll.post(() -> {
            if (rowIndex == 0) {
                scroll.animateTo(0);
                return;
            }
            View row = grid.getChildAt(rowIndex);
            if (row == null) return;
            // Wie tvOS (Screenshot des Nutzers): Ab Reihe 1 steht die oberste Reihe (Favoriten-Leiste)
            // ~60 px unter dem oberen Rand und bleibt sichtbar. Weiter wird nur gescrollt, wenn die
            // fokussierte Reihe samt Namen nicht mehr ganz aufs Bild passt, dann in ganzen Reihenschritten.
            View first = grid.getChildAt(0);
            int base = grid.getTop() + first.getTop() - Ui.dp(this, 30);
            int y = base;
            int screenH = scroll.getHeight();
            int bottom = grid.getTop() + row.getBottom();
            int need = bottom - (screenH - Ui.dp(this, 24)) - base;
            if (need > 0) {
                View r1 = grid.getChildAt(1), r2 = grid.getChildAt(2);
                int pitch = (r1 != null && r2 != null) ? r2.getTop() - r1.getTop() : row.getHeight();
                pitch = Math.max(1, pitch);
                y = base + (need + pitch - 1) / pitch * pitch;
            }
            scroll.animateTo(Math.max(0, y));
        });
    }

    // ---------------------------------------------------------------- Bildschirmschoner

    /**
     * Ruhezeit neu starten. Ist der Schoner an, bleibt der Bildschirm auf der Startseite an
     * (sonst greift nach ~5 min der Philips-Schoner), bis der eigene Schoner startet.
     */
    private void noteInput() {
        lastInput = SystemClock.elapsedRealtime();
        if (topContent != null) {
            if (topContent.getAlpha() < 1f) topContent.animate().alpha(1f).setDuration(Ui.ms(300)).start();
            handler.removeCallbacks(hideTopBar);
            handler.postDelayed(hideTopBar, TOP_BAR_IDLE_MS);
        }
        handler.removeCallbacks(saverTimer);
        int minutes = Prefs.saverDelay(this);
        if (minutes > 0 && resumed) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            handler.postDelayed(saverTimer, minutes * 60_000L);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    private void fadeTopBar() {
        if (topContent != null) topContent.animate().alpha(0f).setDuration(Ui.ms(1200)).start();
    }

    private void openControlCenter() {
        // Gleicher Weg wie Doppel-Home, sonst fehlt das Abbild für das Milchglas (B52).
        openControlCenter(null);
    }

    private void startScreensaver() {
        if (!resumed) return;
        Log.i(TAG, "idle, starting screensaver");
        try {
            startActivity(new Intent(this, ScreensaverActivity.class)
                    .putExtra(ScreensaverActivity.EXTRA_IDLE_SINCE, lastInput));
            overridePendingTransition(android.R.anim.fade_in, 0);
        } catch (RuntimeException e) {
            Log.w(TAG, "screensaver failed", e);
        }
    }

    // ---------------------------------------------------------------- Bedienung

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        noteInput();
        int code = event.getKeyCode();
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            switch (code) {
                case KeyEvent.KEYCODE_DPAD_LEFT: lastDx = -1; lastDy = 0; break;
                case KeyEvent.KEYCODE_DPAD_RIGHT: lastDx = 1; lastDy = 0; break;
                case KeyEvent.KEYCODE_DPAD_UP: lastDx = 0; lastDy = -1; break;
                case KeyEvent.KEYCODE_DPAD_DOWN: lastDx = 0; lastDy = 1; break;
                default: break;
            }
        }
        if (movingKey != null) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                switch (code) {
                    case KeyEvent.KEYCODE_DPAD_LEFT: moveBy(-1); return true;
                    case KeyEvent.KEYCODE_DPAD_RIGHT: moveBy(1); return true;
                    case KeyEvent.KEYCODE_DPAD_UP: moveBy(-Prefs.columns(this)); return true;
                    case KeyEvent.KEYCODE_DPAD_DOWN: moveBy(Prefs.columns(this)); return true;
                    default: break;
                }
            }
            if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                    || code == KeyEvent.KEYCODE_BACK) {
                if (event.getAction() == KeyEvent.ACTION_UP) stopMoving(true);
                return true;
            }
        }
        if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER) {
            // Drücken wie tvOS: Kachel gibt beim Drücken leicht nach und federt beim Loslassen zurück.
            View f = getCurrentFocus();
            if (f instanceof TileView) {
                TileView tvp = (TileView) f;
                if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                    pressDownAt = android.os.SystemClock.uptimeMillis();
                    tvp.press(true);
                } else if (event.getAction() == KeyEvent.ACTION_UP) {
                    handler.postDelayed(() -> tvp.press(false), pressRemaining());
                }
            }
        }
        if (code == KeyEvent.KEYCODE_BACK) {
            // Zurück auf der Startseite: an den Anfang, sonst nichts (Launcher bleibt).
            if (event.getAction() == KeyEvent.ACTION_UP && !tileViews.isEmpty()) {
                if (scroll.getScrollY() > 0 || !tileViews.get(0).hasFocus()) {
                    lastDx = 0;
                    lastDy = -1;
                    tileViews.get(0).requestFocus();
                    scroll.animateTo(0);
                }
            }
            return true;
        }
        // Pfeil rechts auf der letzten Kachel einer Reihe: Kontrollzentrum statt Sprung in die
        // nächste Reihe (Wunsch des Nutzers; die Fernbedienung hat keine Menü-Taste).
        if (code == KeyEvent.KEYCODE_DPAD_RIGHT) {
            int columns = Prefs.columns(this);
            for (int i = 0; i < tileViews.size(); i++) {
                if (!tileViews.get(i).hasFocus()) continue;
                if (i % columns == columns - 1 || i == tileViews.size() - 1) {
                    if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) openControlCenter();
                    return true;
                }
                break;
            }
        }
        // W89: Pfeil hoch aus der Favoritenleiste in die Karten: immer die erste, außer man kam gerade von
        // einer Karte herunter, dann zurück auf diese. Jede andere Bewegung auf den Kacheln vergisst sie.
        if (event.getAction() == KeyEvent.ACTION_DOWN && currentTileView != null && currentTileView.hasFocus()
                && (code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN
                || code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT)) {
            int remembered = topShelf.takeRememberedCard();
            int index = tileViews.indexOf(currentTileView);
            if (code == KeyEvent.KEYCODE_DPAD_UP && index >= 0 && index < Prefs.columns(this)
                    && topShelf.cardsShown()) {
                topShelf.focusCard(remembered);
                return true;
            }
        }
        // Pfeil hoch in der obersten App-Reihe, wenn der Top Shelf keine Karten hat: Kontrollzentrum (B15).
        if (code == KeyEvent.KEYCODE_DPAD_UP && !topShelf.cardsShown()) {
            int columns = Prefs.columns(this);
            for (int i = 0; i < tileViews.size() && i < columns; i++) {
                if (!tileViews.get(i).hasFocus()) continue;
                if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) openControlCenter();
                return true;
            }
        }
        // Pfeil hoch auf einer Top-Shelf-Karte: Kontrollzentrum (Wunsch des Nutzers, wie die Statuspille bei tvOS).
        if (code == KeyEvent.KEYCODE_DPAD_UP && topShelf.cardsFocused()) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) openControlCenter();
            return true;
        }
        // Pfeil runter in eine kürzere letzte Reihe: auf deren letzte Kachel statt ins Leere.
        if (code == KeyEvent.KEYCODE_DPAD_DOWN && event.getAction() == KeyEvent.ACTION_DOWN) {
            int columns = Prefs.columns(this);
            for (int i = 0; i < tileViews.size(); i++) {
                if (!tileViews.get(i).hasFocus()) continue;
                int lastRow = (tileViews.size() - 1) / columns;
                if (i / columns < lastRow && i + columns >= tileViews.size()) {
                    tileViews.get(tileViews.size() - 1).requestFocus();
                    return true;
                }
                break;
            }
        }
        // Menü-Taste öffnet das Kontrollzentrum (Kachelmenü: langes OK).
        if (code == KeyEvent.KEYCODE_MENU) {
            if (event.getAction() == KeyEvent.ACTION_UP) openControlCenter();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    void open(Tile tile) {
        if (tile.kind != Tile.APP && tile.kind != Tile.INPUT) zoomBmp = null;
        switch (tile.kind) {
            case Tile.HIDDEN_APPS: hiddenApps(); return;
            case Tile.SETTINGS: startActivity(new Intent(this, SettingsActivity.class)); return;
            case Tile.FOLDER: folders.open(tile.key); return;
            default: break;
        }
        if (!tile.available) {
            waitForStorage(tile);
            return;
        }
        launch(tile);
    }

    /**
     * Start-Animation wie tvOS: Die Kachel fliegt sofort auf den Zuschauer zu und füllt den Schirm,
     * während die App im Hintergrund startet (je nach App 1–2 s bis zum ersten Bild, vom USB-Stick
     * länger). Sobald das App-Fenster da ist, blendet es darüber ein.
     */
    private android.graphics.Bitmap zoomBmp;
    private android.graphics.RectF zoomRect;
    private float zoomRadius;
    private View zoomOverlay;
    private FrameLayout rootView;
    private final Runnable zoomTimeout = () -> {
        // Kein App-Fenster gekommen (Start fehlgeschlagen o. ä.): Startseite wieder zeigen. Auch, wenn die
        // Startseite nie gestoppt wurde: Die App ist dann durchsichtig (z. B. TV-Einstellungen als
        // Seitenpanel), onStart kommt nie, und die Fläche bliebe sonst stehen (B39).
        if (hasWindowFocus() || visibleSince != 0) removeZoom(true);
    };
    private long pressDownAt;
    private static final long MIN_PRESS_MS = 110;

    /** Wie lange das Eindrücken noch zu sehen sein soll, bevor die App startet (auch bei kurzem Tippen). */
    private long pressRemaining() {
        long left = Ui.ms(MIN_PRESS_MS) - (android.os.SystemClock.uptimeMillis() - pressDownAt);
        return Math.max(0, Math.min(Ui.ms(MIN_PRESS_MS), left));
    }

    /** Bild und sichtbare Lage der Kachel merken (vor dem Schließen eines Ordners). */
    void captureZoom(View v) {
        zoomBmp = null;
        int w = v.getWidth(), h = v.getHeight();
        if (w <= 0 || h <= 0) return;
        try {
            android.graphics.Bitmap b = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888);
            v.draw(new android.graphics.Canvas(b));
            int[] loc = new int[2];
            v.getLocationOnScreen(loc);
            zoomRect = new android.graphics.RectF(loc[0], loc[1], loc[0] + w * v.getScaleX(), loc[1] + h * v.getScaleY());
            zoomRadius = w * 0.12f * v.getScaleX();
            zoomBmp = b;
        } catch (RuntimeException | OutOfMemoryError e) {
            zoomBmp = null;
        }
    }

    private void startZoom() {
        zoomAnim(false);
    }

    /**
     * reverse = false: Kachel zoomt beim Start auf den ganzen Schirm (bleibt bis zum App-Fenster).
     * reverse = true: Rückkehr aus der App, genau umgekehrt: vom ganzen Schirm zurück in die Kachel,
     * danach weg (W82). Kostet wenig: ein Kachelbild, zwei Views, ein Animator.
     */
    private void zoomAnim(boolean reverse) {
        android.graphics.Bitmap bmp = zoomBmp;
        android.graphics.RectF r = zoomRect;
        zoomBmp = null;
        removeZoom(false);
        if (bmp == null || r == null || rootView == null) return;
        final int sw = rootView.getWidth(), sh = rootView.getHeight();
        if (sw <= 0 || sh <= 0) return;
        int[] rl = new int[2];
        rootView.getLocationOnScreen(rl);
        // Fläche in der Hintergrundfarbe der Kachel wächst auf den ganzen Schirm; das Kachelbild selbst
        // wird höchstens doppelt so groß und landet in der Mitte (Wunsch des Nutzers).
        FrameLayout box = new FrameLayout(this);
        box.setClipChildren(false);
        final View fill = new View(this);
        fill.setBackgroundColor(edgeColor(bmp));
        fill.setPivotX(0f);
        fill.setPivotY(0f);
        final float[] radius = {zoomRadius};
        fill.setOutlineProvider(new RoundOutline(radius));
        fill.setClipToOutline(true);
        box.addView(fill, new FrameLayout.LayoutParams(sw, sh));
        final int iw = Math.round(r.width()), ih = Math.round(r.height());
        final ImageView iv = new ImageView(this);
        iv.setImageBitmap(bmp);
        iv.setScaleType(ImageView.ScaleType.FIT_XY);
        final float[] iRadius = {zoomRadius};
        iv.setOutlineProvider(new RoundOutline(iRadius));
        iv.setClipToOutline(true);
        box.addView(iv, new FrameLayout.LayoutParams(iw, ih));
        rootView.addView(box, new FrameLayout.LayoutParams(sw, sh));
        zoomOverlay = box;

        final float x0 = r.left - rl[0], y0 = r.top - rl[1];
        final float s0x = r.width() / sw, s0y = r.height() / sh;
        final float rad0 = zoomRadius;
        final float maxScale = 2f;
        // Bild: Mitte wandert von der Kachel in die Bildschirmmitte, Größe von 1x auf 2x (Pivot Mitte).
        final float cx0 = x0 + iw / 2f, cy0 = y0 + ih / 2f;
        iv.setTranslationX(x0);
        iv.setTranslationY(y0);
        final android.view.animation.Interpolator curve =
                new android.view.animation.PathInterpolator(0.3f, 0f, 0.15f, 1f);
        ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
        a.setDuration(Ui.ms(420));
        // Umgekehrt: am Anfang schnell, sanft in der Kachel landen.
        a.setInterpolator(t -> reverse ? 1f - curve.getInterpolation(t) : curve.getInterpolation(t));
        a.addUpdateListener(an -> {
            float f = (float) an.getAnimatedValue();
            float sx = s0x + (1f - s0x) * f, sy = s0y + (1f - s0y) * f;
            fill.setScaleX(sx);
            fill.setScaleY(sy);
            fill.setTranslationX(x0 * (1f - f));
            fill.setTranslationY(y0 * (1f - f));
            radius[0] = rad0 * (1f - f) / Math.max(0.01f, sx);
            fill.invalidateOutline();
            float is = 1f + (maxScale - 1f) * f;
            float cx = cx0 + (sw / 2f - cx0) * f, cy = cy0 + (sh / 2f - cy0) * f;
            iv.setScaleX(is);
            iv.setScaleY(is);
            iv.setTranslationX(cx - iw / 2f);
            iv.setTranslationY(cy - ih / 2f);
        });
        if (reverse) {
            iv.setScaleX(maxScale);
            iv.setScaleY(maxScale);
            iv.setTranslationX(sw / 2f - iw / 2f);
            iv.setTranslationY(sh / 2f - ih / 2f);
            a.addListener(new ZoomEnd(() -> {
                if (zoomOverlay == box) removeZoom(false);
            }));
            // Erst schrumpfen, wenn die Startseite wirklich zu sehen ist (Fensterfokus); bis dahin steht
            // die volle Fläche. Apps zeigen beim Beenden oft noch kurz Schwarz.
            pendingZoomBack = a;
            zoomBackRunning = true;
            handler.removeCallbacks(startZoomBack);
            handler.postDelayed(startZoomBack, 1500);
            if (hasWindowFocus()) handler.post(startZoomBack);
            return;
        }
        a.start();
        handler.removeCallbacks(zoomTimeout);
        handler.postDelayed(zoomTimeout, 6000);
    }

    /**
     * Rückkehr auf die Startseite: Wurde gerade eine App verlassen, deren Kachel (oder deren Ordner)
     * sichtbar ist, schrumpft sie umgekehrt zur Start-Animation in die Kachel zurück.
     */
    private ValueAnimator pendingZoomBack;
    private boolean zoomBackRunning;
    private final Runnable startZoomBack = () -> {
        ValueAnimator a = pendingZoomBack;
        pendingZoomBack = null;
        if (a != null) a.start();
    };

    private void maybeZoomBack() {
        if (Prefs.reduceMotion(this) || !pageRevealed || rootView == null) return;
        String pkg = appLeftByHome(this, System.currentTimeMillis(), true);
        Log.i(TAG, "Zoom back: " + pkg);
        if (pkg == null) return;
        TileView target = null;
        for (TileView tv : tileViews) {
            Tile t = tv.tile;
            if (pkg.equals(t.pkg)) target = tv;
            if (target == null && t.members != null) {
                for (Tile m : t.members) if (pkg.equals(m.pkg)) target = tv;
            }
            if (target != null) break;
        }
        // Nicht isShown(): Bis onResume ist das Fenster der gestoppten Seite noch unsichtbar geschaltet.
        if (target == null || target.getVisibility() != View.VISIBLE || !target.isAttachedToWindow()) {
            Log.i(TAG, "zoom back: no tile " + (target == null ? "found" : "shown"));
            return;
        }
        int[] loc = new int[2];
        target.getLocationOnScreen(loc);
        int sh = rootView.getHeight();
        if (loc[1] + target.getHeight() <= 0 || loc[1] >= sh) {
            Log.i(TAG, "zoom back: tile off screen " + loc[1]);
            return;
        }
        captureZoom(target);
        zoomAnim(true);
    }

    /** Hintergrundfarbe der Kachel: Mittel aus Pixeln knapp innerhalb der vier Ränder. */
    private static int edgeColor(android.graphics.Bitmap b) {
        int w = b.getWidth(), h = b.getHeight();
        int in = Math.max(2, Math.round(Math.min(w, h) * 0.04f));
        int[][] pts = {
            {in, h / 2}, {w - 1 - in, h / 2}, {w / 2, in}, {w / 2, h - 1 - in},
            {in, h / 4}, {w - 1 - in, h / 4}, {in, h * 3 / 4}, {w - 1 - in, h * 3 / 4},
            {w / 4, in}, {w * 3 / 4, in}, {w / 4, h - 1 - in}, {w * 3 / 4, h - 1 - in},
        };
        long rr = 0, gg = 0, bb = 0;
        int n = 0;
        for (int[] p : pts) {
            int c = b.getPixel(p[0], p[1]);
            if (android.graphics.Color.alpha(c) < 200) continue;
            rr += android.graphics.Color.red(c);
            gg += android.graphics.Color.green(c);
            bb += android.graphics.Color.blue(c);
            n++;
        }
        if (n == 0) return Color.BLACK;
        return Color.rgb((int) (rr / n), (int) (gg / n), (int) (bb / n));
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && pendingZoomBack != null) {
            handler.removeCallbacks(startZoomBack);
            startZoomBack.run();
        } else if (hasFocus && zoomOverlay != null && !zoomBackRunning) {
            // Zurück auf der Startseite, die Start-Fläche steht noch (durchsichtige App): weg damit (B39).
            removeZoom(true);
        }
    }

    private void removeZoom(boolean animated) {
        handler.removeCallbacks(zoomTimeout);
        handler.removeCallbacks(startZoomBack);
        pendingZoomBack = null;
        zoomBackRunning = false;
        final View iv = zoomOverlay;
        zoomOverlay = null;
        if (iv == null || rootView == null) return;
        if (animated) {
            iv.animate().alpha(0f).setDuration(Ui.ms(250)).withEndAction(() -> rootView.removeView(iv)).start();
        } else {
            rootView.removeView(iv);
        }
    }

    void launch(Tile tile) {
        boolean reduce = Prefs.reduceMotion(this);
        if (reduce) zoomBmp = null;
        boolean zoom = zoomBmp != null;
        try {
            if (tile.intent == null) throw new IllegalStateException("no intent");
            android.os.Bundle opts = zoom || reduce
                    ? android.app.ActivityOptions.makeCustomAnimation(this, android.R.anim.fade_in, 0).toBundle()
                    : null;
            startActivity(tile.intent, opts);
            if (zoom) startZoom();
        } catch (RuntimeException e) {
            zoomBmp = null;
            removeZoom(false);
            Log.w(TAG, "launch failed: " + tile.key, e);
            Toast.makeText(this, "Can't open " + tile.label, Toast.LENGTH_SHORT).show();
        }
    }

    private void tileMenu(Tile tile) {
        if (tile.kind == Tile.HIDDEN_APPS || tile.kind == Tile.SETTINGS) return;
        if (tile.kind == Tile.FOLDER) {
            folders.menu(tile);
            return;
        }
        // Wie das tvOS-Kontextmenü: ohne Kopfzeile, „Edit Home Screen“ startet das Verschieben,
        // „Add to Folder …“ öffnet die Ordnerwahl daneben.
        Dialogs.Panel p = Dialogs.menu(this, null).anchor(currentTileView);
        LinearLayout first = Dialogs.actionRow(this, p, "Edit Home Screen", () -> startMoving(tile.key));
        Dialogs.actionRow(this, p, "Add to Folder \u2026", () -> folders.choose(tile));
        Dialogs.actionRow(this, p, "Hide from Home Screen", () -> {
            // Fokus auf die Kachel, die an diese Stelle nachrückt (sonst die davor), damit die
            // Startseite stehen bleibt statt nach oben zu springen.
            int index = -1;
            for (int i = 0; i < visible.size(); i++) {
                if (visible.get(i).key.equals(tile.key)) index = i;
            }
            String next = null;
            if (index >= 0 && index + 1 < visible.size()) next = visible.get(index + 1).key;
            else if (index > 0) next = visible.get(index - 1).key;
            store.setHidden(tile.key, true);
            focusedKey = next;
            buildGrid();
        });
        if (tile.kind == Tile.APP && tile.pkg != null && tile.available) {
            Dialogs.actionRow(this, p, "App Info", () -> openAppInfo(tile.pkg));
        }
        if (!tile.available) {
            Dialogs.actionRow(this, p, "Remove Tile", "\u2296", true, () -> {
                store.forget(tile.key);
                reload();
            });
        }
        show(p);
        first.requestFocus();
    }

    void openAppInfo(String pkg) {
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", pkg, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (RuntimeException e) {
            Toast.makeText(this, "App info not available", Toast.LENGTH_SHORT).show();
        }
    }

    private void hiddenApps() {
        Dialogs.Panel p = Dialogs.panel(this, "Hidden apps", 560);
        View firstFocus = null;
        for (Tile t : allTiles) {
            if (!store.hidden.contains(t.key)) continue;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setClipChildren(false);
            row.setPadding(Ui.dp(this, 6), 0, 0, 0);
            row.addView(Ui.text(this, t.label, 17, Ui.TEXT, true), Ui.fill());
            TextView openPill = Ui.pill(this, "Open");
            openPill.setOnClickListener(v -> {
                p.dialog.dismiss();
                open(t);
            });
            TextView showPill = Ui.pill(this, "Show on home");
            showPill.setOnClickListener(v -> {
                p.dialog.dismiss();
                store.setHidden(t.key, false);
                focusedKey = t.key;
                buildGrid();
            });
            LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            pl.leftMargin = Ui.dp(this, 10);
            row.addView(openPill, pl);
            LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            sl.leftMargin = Ui.dp(this, 10);
            row.addView(showPill, sl);
            LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            rl.bottomMargin = Ui.dp(this, 10);
            p.content.addView(row, rl);
            if (firstFocus == null) firstFocus = openPill;
        }
        show(p);
        if (firstFocus != null) firstFocus.requestFocus();
    }

    void show(Dialogs.Panel p) {
        if (openDialog != null) openDialog.dismiss();
        openDialog = p.dialog;
        p.dialog.setOnDismissListener(d -> {
            if (openDialog == p.dialog) openDialog = null;
        });
        p.dialog.show();
    }

    // ---------------------------------------------------------------- Verschieben

    void startMoving(String key) {
        movingKey = key;
        for (TileView tv : tileViews) {
            tv.setWiggle(tv.tile.key.equals(key));
            if (tv.tile.key.equals(key)) tv.requestFocus();
        }
        Toast.makeText(this, "Use the arrows to move, OK to finish", Toast.LENGTH_SHORT).show();
    }

    private void moveBy(int delta) {
        List<String> keys = new ArrayList<>();
        for (Tile t : visible) {
            if (t.kind == Tile.APP || t.kind == Tile.INPUT || t.kind == Tile.FOLDER) keys.add(t.key);
        }
        int from = keys.indexOf(movingKey);
        if (from < 0) return;
        int to = Math.max(0, Math.min(keys.size() - 1, from + delta));
        if (to == from) return;
        keys.remove(from);
        keys.add(to, movingKey);
        store.reorderVisible(keys);
        // allTiles in die neue Reihenfolge bringen, ohne neu zu laden.
        List<Tile> sorted = new ArrayList<>();
        for (String k : store.order) {
            for (Tile t : allTiles) {
                if (t.key.equals(k)) sorted.add(t);
            }
        }
        allTiles = sorted;
        focusedKey = movingKey;
        buildGrid();
    }

    private void stopMoving(boolean save) {
        if (movingKey == null) return;
        movingKey = null;
        for (TileView tv : tileViews) tv.setWiggle(false);
        if (save) store.save();
    }

    // ---------------------------------------------------------------- USB-Stick

    private void waitForStorage(Tile tile) {
        waitingPkg = tile.pkg;
        waitingSince = System.currentTimeMillis();
        Dialogs.Panel p = Dialogs.panel(this, "Waiting for USB storage…", 480);
        p.content.addView(Ui.text(this, tile.label + " opens as soon as the USB stick is ready.", 16,
                Ui.TEXT_DIM, false));
        View spacer = new View(this);
        p.content.addView(spacer, new LinearLayout.LayoutParams(1, Ui.dp(this, 18)));
        LinearLayout cancel = Dialogs.actionRow(this, p, "Cancel", () -> waitingPkg = null);
        cancel.setGravity(Gravity.CENTER);
        p.dialog.setOnCancelListener(d -> waitingPkg = null);
        waitingPanel = p;
        show(p);
        cancel.requestFocus();
        handler.removeCallbacks(pollStorage);
        handler.postDelayed(pollStorage, 1000);
    }

    // Lambda statt anonymer Klasse: d8 stürzt bei anonymen Klassen in diesem Build ab.
    private final Runnable pollStorage = this::pollStorageTick;

    private void pollStorageTick() {
        if (waitingPkg == null) return;
        if (isAvailable(waitingPkg)) {
            reload();
            return;
        }
        if (System.currentTimeMillis() - waitingSince > STORAGE_TIMEOUT_MS) {
            if (waitingPanel != null) waitingPanel.dialog.dismiss();
            waitingPkg = null;
            Toast.makeText(this, "USB storage didn't come back", Toast.LENGTH_LONG).show();
            return;
        }
        handler.postDelayed(pollStorage, 1000);
    }

    private boolean isAvailable(String pkg) {
        try {
            return !launcherApps.getActivityList(pkg, Process.myUserHandle()).isEmpty();
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Nach dem Neuladen: wartet jemand auf eine App, die jetzt da ist? */
    private void checkWaiting() {
        if (waitingPkg == null) return;
        for (Tile t : allTiles) {
            if (waitingPkg.equals(t.pkg) && t.available) {
                waitingPkg = null;
                handler.removeCallbacks(pollStorage);
                if (waitingPanel != null) waitingPanel.dialog.dismiss();
                Log.i(TAG, "Storage back, launching " + t.pkg);
                launch(t);
                return;
            }
        }
    }

    // ---------------------------------------------------------------- Rückmeldungen

    @Override
    public void onPackagesChanged(String[] packages, boolean available, boolean removed) {
        if (removed) {
            for (String p : packages) {
                // Echte Deinstallation: Kachel vergessen. Beim Aushängen des Sticks kommt
                // stattdessen onPackagesUnavailable, die Kachel bleibt.
                if (!isAvailable(p)) store.forget(Tile.appKey(p));
            }
        }
        Log.i(TAG, "Packages " + (removed ? "removed" : available ? "available" : "unavailable")
                + ": " + String.join(",", packages));
        handler.removeCallbacks(reloadRunnable);
        handler.postDelayed(reloadRunnable, 400);
    }

    private boolean vpnConnected;

    @Override
    public void onVpnChanged(boolean connected) {
        handler.post(() -> {
            vpnConnected = connected;
            vpnPill.setVisibility(Prefs.showVpn(this) && connected ? View.VISIBLE : View.GONE);
        });
    }
}
