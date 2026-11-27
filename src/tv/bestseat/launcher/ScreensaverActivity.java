package tv.bestseat.launcher;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.util.TypedValue;
import android.view.Choreographer;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import java.io.File;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Bildschirmschoner: Aerial-Videos, Online-Fotos oder schwarzer Schirm mit wandernder Uhr.
 * Videos und Fotos laufen in einer eigenen Ebene (SurfaceView) in Panel-Auflösung, also echtem 4K.
 * Hält den Bildschirm an (sonst springt nach ~5 min der Philips-Schoner ein), bis die Ruhezeit
 * des TVs seit der letzten Eingabe um ist; danach darf der TV in den Standby.
 * Jede Taste außer Lautstärke beendet den Schoner und wird nicht weitergegeben.
 */
public final class ScreensaverActivity extends Activity implements SurfaceHolder.Callback {

    static final String ACTION = "tv.bestseat.launcher.SCREENSAVER";
    /** SystemClock.elapsedRealtime() der letzten Eingabe. */
    static final String EXTRA_IDLE_SINCE = "idle_since";
    /** Optional: Quelle erzwingen (0 Aerials, 1 Fotos, 2 Uhr), z. B. für tvQuickActions oder Tests. */
    static final String EXTRA_SOURCE = "source";

    private static final String TAG = "Launcher.Saver";
    private static final long PHOTO_MS = 14_000;
    private static final long FADE_MS = 2_000;
    private static final long STALL_MS = 45_000;
    /** So lange steht die Uhr an einer Stelle, dann aus- und woanders wieder einblenden. */
    private static final long CLOCK_HOP_MS = 10_000;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Random random = new Random();

    private FrameLayout root;
    private SurfaceView surface;
    private View veil;
    private LinearLayout location;
    private TextView locationName;
    private TextView locationPlace;
    private String shownPlace;
    private SaverSources.Aerial playing;
    private LinearLayout clockBox;
    private TextClock overlayClock;
    private boolean surfaceReady;
    private boolean destroyed;
    private boolean exitPending;
    private boolean overlaysHidden;
    private final Runnable releaseTask = this::releaseScreen;
    private final Runnable watchEndTask = this::watchEnd;
    private int mode = -1;
    private int physW;
    private int physH;

    // Aerials
    private List<SaverSources.Aerial> aerials;
    private int aerialIndex;
    private MediaPlayer player;
    private boolean rendering;
    private int errors;

    // Fotos
    private List<String> photos;
    private int photoIndex;
    private int photoErrors;
    private Bitmap current;
    private long currentStart;
    private boolean currentLeft;
    private Bitmap incoming;
    private long incomingStart;
    private Bitmap pending;
    private boolean loading;
    private final Paint photoPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Matrix matrix = new Matrix();
    private final Choreographer.FrameCallback frame = this::onFrame;
    private boolean framePosted;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        physW = 3840;
        physH = 2160;
        try {
            physW = getDisplay().getMode().getPhysicalWidth();
            physH = getDisplay().getMode().getPhysicalHeight();
        } catch (RuntimeException ignored) {
        }

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        surface = new SurfaceView(this);
        surface.getHolder().addCallback(this);
        root.addView(surface, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        veil = new View(this);
        veil.setBackgroundColor(Color.BLACK);
        root.addView(veil, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        location = new LinearLayout(this);
        location.setOrientation(LinearLayout.VERTICAL);
        locationName = Ui.text(this, "", 14, Color.argb(200, 255, 255, 255), true);
        locationName.setLetterSpacing(0.12f);
        locationName.setShadowLayer(Ui.dp(this, 6), 0, 0, Color.argb(170, 0, 0, 0));
        location.addView(locationName);
        locationPlace = Ui.text(this, "", 22, Color.argb(235, 255, 255, 255), false);
        locationPlace.setShadowLayer(Ui.dp(this, 6), 0, 0, Color.argb(170, 0, 0, 0));
        location.addView(locationPlace);
        location.setAlpha(0f);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.START);
        lp.setMargins(Ui.dp(this, 56), 0, 0, Ui.dp(this, 44));
        root.addView(location, lp);
        setContentView(root);

        scheduleRelease(getIntent().getLongExtra(EXTRA_IDLE_SINCE, SystemClock.elapsedRealtime()));
        int source = getIntent().getIntExtra(EXTRA_SOURCE, Prefs.saverSource(this));
        if (source == Prefs.SAVER_AERIALS) startAerials();
        else if (source == Prefs.SAVER_PHOTOS) startPhotos();
        else startClock();
    }

    /** Bildschirm nur so lange anhalten, wie der TV ohnehin an bliebe; dann Standby erlauben. */
    private void scheduleRelease(long idleSince) {
        handler.removeCallbacks(releaseTask);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        // Only the TV's "Shut-off timer when inactive" (secure sleep_timeout) counts: the Android
        // screen_off_timeout is not one of the TV's own timers and released the lock too early.
        long timeout = 1_800_000;
        try {
            long sleep = Settings.Secure.getLong(getContentResolver(), "sleep_timeout");
            if (sleep > 0) timeout = sleep;
        } catch (Exception ignored) {
        }
        long delay = Math.max(10_000, idleSince + timeout - SystemClock.elapsedRealtime());
        Log.i(TAG, "keeping the screen on for " + delay / 1000 + " s (timeout " + timeout / 1000 + " s)");
        handler.postDelayed(releaseTask, delay);
    }

    private void releaseScreen() {
        Log.i(TAG, "idle timeout reached, allowing standby");
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Standby, Home-Taste oder eine andere App: Schoner endet ganz.
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        destroyed = true;
        handler.removeCallbacksAndMessages(null);
        Choreographer.getInstance().removeFrameCallback(frame);
        releasePlayer();
        worker.shutdownNow();
        if (current != null) current.recycle();
        if (incoming != null) incoming.recycle();
        if (pending != null) pending.recycle();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        if (code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN
                || code == KeyEvent.KEYCODE_VOLUME_MUTE) {
            return super.dispatchKeyEvent(event);
        }
        // Pfeile bedienen den Schoner (Wunsch des Nutzers): links/rechts wechseln, hoch/runter Uhr und Text.
        if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT
                || code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                scheduleRelease(SystemClock.elapsedRealtime());
                if (code == KeyEvent.KEYCODE_DPAD_LEFT) skip(-1);
                else if (code == KeyEvent.KEYCODE_DPAD_RIGHT) skip(1);
                else if (code == KeyEvent.KEYCODE_DPAD_UP) toggleClock();
                else toggleLocation();
            }
            return true;
        }
        // Andere Tasten schlucken; erst beim Loslassen beenden, damit die Startseite kein halbes Tastenpaar bekommt.
        if (event.getAction() == KeyEvent.ACTION_DOWN) exitPending = true;
        else if (event.getAction() == KeyEvent.ACTION_UP && exitPending) exit();
        return true;
    }

    /** Nächstes (1) oder vorheriges (-1) Video; bei Fotos sofort das nächste Foto. */
    private void skip(int direction) {
        if (mode == Prefs.SAVER_AERIALS && aerials != null && !aerials.isEmpty()) {
            // aerialIndex zeigt schon auf das nächste Video; zurück heißt also zwei Schritte.
            if (direction < 0) aerialIndex = Math.floorMod(aerialIndex - 2, aerials.size());
            handler.removeCallbacks(watchEndTask);
            veil.animate().cancel();
            veil.animate().alpha(1f).setDuration(250).withEndAction(this::playNext).start();
        } else if (mode == Prefs.SAVER_PHOTOS && current != null) {
            // Das aktuelle Foto gilt als abgelaufen; liegt das nächste schon bereit, blendet es gleich über.
            currentStart = SystemClock.uptimeMillis() - PHOTO_MS;
            requestFrame();
        }
    }

    /** Pfeil runter hat die Ortsangabe für diesen Schoner ein- (true) oder ausgeschaltet (false); null folgt den Einstellungen. */
    private Boolean locationOn;

    private boolean locationEnabled() {
        return locationOn != null ? locationOn : Prefs.saverLocation(this);
    }

    /** Pfeil hoch: Uhr ein- bzw. ausblenden (legt sie an, wenn sie in den Einstellungen aus ist). */
    private void toggleClock() {
        // Ohne Uhr (in den Einstellungen aus) zeigt der erste Druck sie an, statt nichts zu verstecken.
        boolean none = overlayClock == null && clockBox == null;
        overlaysHidden = !none && !overlaysHidden;
        boolean show = !overlaysHidden;
        if (show && overlayClock == null && clockBox == null) addOverlayClock(true);
        int vis = show ? View.VISIBLE : View.INVISIBLE;
        if (overlayClock != null) {
            overlayClock.setVisibility(vis);
            if (show) overlayClock.animate().alpha(1f).setStartDelay(0).setDuration(500).withEndAction(null).start();
        }
        if (clockBox != null) {
            clockBox.setVisibility(vis);
            if (show) clockBox.animate().alpha(1f).setStartDelay(0).setDuration(500).withEndAction(null).start();
        }
        Log.i(TAG, "clock " + (show ? "shown" : "hidden"));
    }

    /**
     * Pfeil runter: Ortsangabe ein- bzw. ausschalten. Ein: jetzt kurz einblenden, danach bei jedem
     * neuen Video bzw. neuen Ort wieder kurz (wie tvOS). Aus: sofort ausblenden, bis wieder eingeschaltet.
     */
    private void toggleLocation() {
        if (mode != Prefs.SAVER_AERIALS || player == null) return;
        locationOn = !locationEnabled();
        if (!locationOn) {
            location.animate().cancel();
            location.animate().alpha(0f).setStartDelay(0).setDuration(400).withEndAction(null).start();
            Log.i(TAG, "location off");
            return;
        }
        location.setVisibility(View.VISIBLE);
        shownPlace = null;
        try {
            updatePlace(player.getCurrentPosition());
        } catch (IllegalStateException e) {
            // Video wird gerade gewechselt; der nächste Takt blendet ein.
        }
        Log.i(TAG, "location shown");
    }

    private void exit() {
        if (isFinishing()) return;
        finish();
        Ui.transition(this, 0, android.R.anim.fade_out);
    }

    // ---------------------------------------------------------------- Surface

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        surfaceReady = true;
        if (mode == Prefs.SAVER_AERIALS && aerials != null && player == null) playNext();
        if (mode == Prefs.SAVER_PHOTOS) requestFrame();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Log.i(TAG, "surface " + width + "x" + height);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        surfaceReady = false;
        Choreographer.getInstance().removeFrameCallback(frame);
        framePosted = false;
        releasePlayer();
    }

    // ---------------------------------------------------------------- Aerials

    private void startAerials() {
        mode = Prefs.SAVER_AERIALS;
        addOverlayClock();
        boolean uhd = physW >= 3840;
        worker.execute(() -> {
            List<SaverSources.Aerial> list = SaverSources.aerials(this, uhd);
            handler.post(() -> {
                if (destroyed) return;
                if (list.isEmpty()) {
                    startClock();
                    return;
                }
                aerials = list;
                if (surfaceReady) playNext();
            });
        });
    }

    private void playNext() {
        if (destroyed || !surfaceReady || mode != Prefs.SAVER_AERIALS) return;
        if (errors >= 5) {
            Log.w(TAG, "too many video errors, switching to clock");
            releasePlayer();
            startClock();
            return;
        }
        SaverSources.Aerial aerial = aerials.get(aerialIndex++ % aerials.size());
        releasePlayer();
        rendering = false;
        MediaPlayer p = new MediaPlayer();
        player = p;
        try {
            p.setSurface(surface.getHolder().getSurface());
            p.setVolume(0f, 0f);
            p.setOnPreparedListener(mp -> {
                if (mp != player) return;
                mp.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING);
                mp.start();
            });
            p.setOnInfoListener((mp, what, extra) -> {
                if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START && mp == player) {
                    rendering = true;
                    errors = 0;
                    veil.animate().alpha(0f).setDuration(1500).start();
                    playing = aerial;
                    shownPlace = null;
                    handler.postDelayed(watchEndTask, 1000);
                }
                return false;
            });
            p.setOnCompletionListener(mp -> {
                if (mp == player) playNext();
            });
            p.setOnErrorListener((mp, what, extra) -> {
                Log.w(TAG, "video error " + what + "/" + extra + " for " + aerial.url);
                if (mp == player) {
                    errors++;
                    veil.setAlpha(1f);
                    handler.postDelayed(this::playNext, 1000);
                }
                return true;
            });
            Log.i(TAG, "playing " + aerial.name + " " + aerial.url);
            p.setDataSource(aerial.url);
            p.prepareAsync();
        } catch (Exception e) {
            Log.w(TAG, "video start failed: " + e);
            errors++;
            handler.postDelayed(this::playNext, 1000);
            return;
        }
        // Hängt der Stream, nach 45 s zum nächsten Video.
        handler.postDelayed(() -> {
            if (p == player && !rendering) {
                Log.w(TAG, "video stalled: " + aerial.url);
                errors++;
                playNext();
            }
        }, STALL_MS);
    }

    /** Kurz vor Videoende abblenden, damit der Wechsel weich ist. */
    private void watchEnd() {
        MediaPlayer p = player;
        if (destroyed || p == null || !rendering) return;
        try {
            updatePlace(p.getCurrentPosition());
            int left = p.getDuration() - p.getCurrentPosition();
            if (p.getDuration() > 0 && left < 1500) {
                veil.animate().alpha(1f).setDuration(Math.max(200, left - 200)).start();
                return;
            }
        } catch (IllegalStateException e) {
            return;
        }
        handler.postDelayed(watchEndTask, 400);
    }

    /** Beschreibung der Stelle einblenden, sobald das Video sie erreicht; nach 10 s wieder aus. */
    private void updatePlace(int positionMs) {
        SaverSources.Aerial a = playing;
        if (a == null || !locationEnabled()) return;
        java.util.Map.Entry<Integer, String> e = a.places.floorEntry(positionMs / 1000);
        String place = e != null ? e.getValue() : (shownPlace == null ? "" : shownPlace);
        if (shownPlace != null && shownPlace.equals(place)) return;
        shownPlace = place;
        locationName.setText(a.name.toUpperCase(java.util.Locale.ROOT));
        locationPlace.setText(place);
        locationPlace.setVisibility(place.isEmpty() ? View.GONE : View.VISIBLE);
        location.animate().cancel();
        location.animate().alpha(0f).setStartDelay(0).setDuration(location.getAlpha() > 0 ? 600 : 0).withEndAction(() ->
                location.animate().alpha(1f).setStartDelay(300).setDuration(900).withEndAction(() ->
                        location.animate().alpha(0f).setStartDelay(7_000).setDuration(1200).withEndAction(null).start())
                        .start())
                .start();
    }

    private void releasePlayer() {
        MediaPlayer p = player;
        player = null;
        if (p != null) {
            try {
                p.release();
            } catch (RuntimeException ignored) {
            }
        }
    }

    // ---------------------------------------------------------------- Fotos

    private void startPhotos() {
        mode = Prefs.SAVER_PHOTOS;
        surface.getHolder().setFixedSize(physW, physH);
        veil.setVisibility(View.GONE);
        addOverlayClock();
        int topic = Prefs.saverTopic(this);
        worker.execute(() -> {
            List<String> list = SaverSources.photos(this, topic);
            handler.post(() -> {
                if (destroyed) return;
                if (list.isEmpty()) {
                    startClock();
                    return;
                }
                photos = list;
                loadNextPhoto();
            });
        });
    }

    private void loadNextPhoto() {
        if (loading || destroyed || photos == null) return;
        loading = true;
        worker.execute(() -> {
            Bitmap bitmap = null;
            for (int attempt = 0; attempt < 4 && bitmap == null && !destroyed; attempt++) {
                String url = photos.get(photoIndex++ % photos.size());
                try {
                    bitmap = decode(SaverSources.photoFile(this, url));
                } catch (Exception e) {
                    Log.w(TAG, "photo failed: " + url + " " + e);
                }
            }
            Bitmap result = bitmap;
            handler.post(() -> {
                loading = false;
                if (destroyed) {
                    if (result != null) result.recycle();
                    return;
                }
                if (result == null) {
                    if (++photoErrors >= 3 && current == null) startClock();
                    else handler.postDelayed(this::loadNextPhoto, 10_000);
                    return;
                }
                photoErrors = 0;
                pending = result;
                if (surfaceReady) requestFrame();
            });
        });
    }

    /** Auf Bildschirmgröße plus Zoom-Reserve verkleinert dekodieren (Hardware-Bitmap, nicht im Java-Heap). */
    private Bitmap decode(File file) throws Exception {
        int targetW = physW;
        int targetH = physH;
        return ImageDecoder.decodeBitmap(ImageDecoder.createSource(file), (decoder, info, source) -> {
            int w = info.getSize().getWidth();
            int h = info.getSize().getHeight();
            float s = Math.min(1f, Math.max(targetW * 1.08f / w, targetH * 1.08f / h));
            decoder.setTargetSize(Math.max(1, Math.round(w * s)), Math.max(1, Math.round(h * s)));
        });
    }

    private void requestFrame() {
        if (framePosted) return;
        framePosted = true;
        Choreographer.getInstance().postFrameCallback(frame);
    }

    private void onFrame(long frameTimeNanos) {
        framePosted = false;
        if (destroyed || !surfaceReady || mode != Prefs.SAVER_PHOTOS) return;
        long now = SystemClock.uptimeMillis();
        if (incoming == null && pending != null && (current == null || now - currentStart >= PHOTO_MS)) {
            incoming = pending;
            pending = null;
            incomingStart = now;
        }
        Surface s = surface.getHolder().getSurface();
        Canvas canvas;
        try {
            canvas = s.lockHardwareCanvas();
        } catch (RuntimeException e) {
            Log.w(TAG, "lockHardwareCanvas failed: " + e);
            return;
        }
        try {
            canvas.drawColor(Color.BLACK);
            if (current != null) drawPhoto(canvas, current, currentStart, currentLeft, 255);
            if (incoming != null) {
                float t = Math.min(1f, (now - incomingStart) / (float) FADE_MS);
                drawPhoto(canvas, incoming, incomingStart, !currentLeft, Math.round(255 * t));
                if (t >= 1f) {
                    if (current != null) current.recycle();
                    current = incoming;
                    currentStart = incomingStart;
                    currentLeft = !currentLeft;
                    incoming = null;
                    loadNextPhoto();
                }
            }
        } finally {
            s.unlockCanvasAndPost(canvas);
        }
        // Weiter zeichnen, solange sich etwas bewegt (Zoom läuft immer, solange ein Foto da ist).
        if (current != null || incoming != null) requestFrame();
    }

    /** Langsamer Zoom mit seitlichem Schwenk (Ken Burns), abwechselnd nach links und rechts. */
    private void drawPhoto(Canvas canvas, Bitmap bitmap, long start, boolean toLeft, int alpha) {
        int cw = canvas.getWidth();
        int ch = canvas.getHeight();
        float p = Math.min(1f, (SystemClock.uptimeMillis() - start) / (float) (PHOTO_MS + 2 * FADE_MS));
        float cover = Math.max(cw / (float) bitmap.getWidth(), ch / (float) bitmap.getHeight());
        float scale = cover * (1f + 0.06f * p);
        float overX = bitmap.getWidth() * scale - cw;
        float overY = bitmap.getHeight() * scale - ch;
        float pan = toLeft ? 0.65f - 0.3f * p : 0.35f + 0.3f * p;
        matrix.setScale(scale, scale);
        matrix.postTranslate(-overX * pan, -overY * 0.5f);
        photoPaint.setAlpha(alpha);
        canvas.drawBitmap(bitmap, matrix, photoPaint);
    }

    // ---------------------------------------------------------------- Uhr

    /**
     * Kleine Uhr über Videos/Fotos. Wechselt wie die große Uhr alle paar Sekunden die Stelle
     * (OLED, Wunsch des Nutzers), bleibt aber im oberen Teil, damit sie die Ortsangabe unten links
     * nicht verdeckt.
     */
    private void addOverlayClock() {
        addOverlayClock(false);
    }

    private void addOverlayClock(boolean force) {
        if (!force && !Prefs.saverClock(this)) return;
        overlayClock = new TextClock(this);
        overlayClock.setFormat24Hour("HH:mm");
        overlayClock.setFormat12Hour("h:mm");
        Prefs.applyZone(this, overlayClock);
        // Größer und fett (Wunsch des Nutzers).
        overlayClock.setTextSize(TypedValue.COMPLEX_UNIT_SP, 44);
        overlayClock.setTypeface(Fonts.get(overlayClock.getContext(), Fonts.BOLD));
        // Off-white und ohne Schatten (Wunsch des Nutzers, W77).
        overlayClock.setTextColor(Color.rgb(242, 241, 236));
        overlayClock.setAlpha(0f);
        root.addView(overlayClock, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT));
        overlayClock.post(() -> hop(overlayClock, 0.6f));
    }

    /**
     * Ausblenden, an zufälliger Stelle wieder einblenden, ein paar Sekunden stehen lassen, wiederholen.
     * heightShare: Anteil der Bildschirmhöhe (von oben), in dem die Ansicht auftauchen darf.
     */
    private void hop(View v, float heightShare) {
        if (destroyed || v.getParent() == null) return;
        int margin = Ui.dp(this, 40);
        float maxX = Math.max(margin, root.getWidth() - v.getWidth() - margin);
        float maxY = Math.max(margin, root.getHeight() * heightShare - v.getHeight() - margin);
        float x = margin + random.nextFloat() * (maxX - margin);
        float y = margin + random.nextFloat() * (maxY - margin);
        v.animate().alpha(0f).setStartDelay(0).setDuration(v.getAlpha() > 0 ? 700 : 0).withEndAction(() -> {
            v.setTranslationX(x);
            v.setTranslationY(y);
            v.animate().alpha(1f).setStartDelay(150).setDuration(700).withEndAction(null).start();
        }).start();
        handler.postDelayed(() -> hop(v, heightShare), CLOCK_HOP_MS);
    }

    /** Schwarzer Schirm mit großer Uhr und Datum, die alle paar Sekunden an einer neuen Stelle auftauchen. */
    private void startClock() {
        if (mode == Prefs.SAVER_CLOCK) return;
        mode = Prefs.SAVER_CLOCK;
        Choreographer.getInstance().removeFrameCallback(frame);
        releasePlayer();
        surface.setVisibility(View.GONE);
        veil.setVisibility(View.GONE);
        location.setVisibility(View.GONE);
        if (overlayClock != null) {
            root.removeView(overlayClock);
            overlayClock = null;
        }
        clockBox = new LinearLayout(this);
        clockBox.setOrientation(LinearLayout.VERTICAL);
        clockBox.setGravity(Gravity.CENTER_HORIZONTAL);
        TextClock time = new TextClock(this);
        time.setFormat24Hour("HH:mm");
        time.setFormat12Hour("h:mm");
        time.setTextSize(TypedValue.COMPLEX_UNIT_SP, 96);
        Prefs.applyZone(this, time);
        time.setTypeface(Fonts.get(time.getContext(), Fonts.THIN));
        time.setTextColor(Color.rgb(200, 200, 204));
        clockBox.addView(time);
        TextClock date = new TextClock(this);
        date.setFormat24Hour("EEEE, d MMMM");
        date.setFormat12Hour("EEEE, MMMM d");
        date.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        Prefs.applyZone(this, date);
        date.setTypeface(Fonts.get(date.getContext(), Fonts.LIGHT));
        date.setTextColor(Color.rgb(130, 132, 138));
        clockBox.addView(date);
        clockBox.setAlpha(0f);
        root.addView(clockBox, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT));
        clockBox.post(() -> hop(clockBox, 1f));
    }
}
