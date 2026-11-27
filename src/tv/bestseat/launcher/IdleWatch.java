package tv.bestseat.launcher;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

/**
 * Leerlauf in anderen Apps erkennen (W133). Solange der Home Screen nicht vorne ist, fragt Best Seat
 * alle 30 s über die eigene adb-Verbindung nach der letzten Eingabe, der App vorne und laufender
 * Wiedergabe. Ist seit der eingestellten Zeit nichts gedrückt worden und spielt nichts, startet der
 * Best-Seat-Schoner, bevor der Einbrennschutz des TVs das Bild abdunkelt. Live-TV und Eingänge
 * (org.droidtv.*) bleiben außen vor. Beim Beenden des Schoners ist die App wieder vorne.
 */
final class IdleWatch {

    private static final String TAG = "Launcher.IdleWatch";
    private static final long POLL_MS = 30_000;
    /**
     * The TV's burn-in protection starts after 5 min without a key unless some app holds a bright wake
     * lock (SmartTube does while paused, TV Settings does not). Without a lock the saver must start
     * earlier; 4 min leaves room for the poll interval.
     */
    private static final int NO_LOCK_MAX_MIN = 4;
    /**
     * ImageStickingService counts a static OSD while a TPV app such as TV Settings is in front (+1 per
     * 10 s, reset by a key) and starts the burn-in protection at 12, i.e. after 2 min. Leave room for
     * the poll interval.
     */
    private static final long STATIC_OSD_MAX_MS = 60_000;
    private static final String PROBE = "dumpsys power | grep -m1 mLastUserActivityTime; "
            + "dumpsys power | grep -m1 mWakefulness=; "
            + "dumpsys activity activities | grep -m1 topResumedActivity; "
            + "echo playing=$(dumpsys media_session | grep -c 'state=PLAYING'); "
            + "echo audio=$(dumpsys audio | grep -c 'AudioPlaybackConfiguration.*state:started'); "
            + "echo bright=$(dumpsys power | grep -c SCREEN_BRIGHT_WAKE_LOCK)";

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static Context app;
    private static boolean running;
    private static volatile boolean busy;

    private static final Runnable TICK = IdleWatch::tick;

    private IdleWatch() {}

    /** Home Screen nicht mehr vorne: beobachten. */
    static void start(Context context) {
        app = context.getApplicationContext();
        if (running) return;
        running = true;
        MAIN.postDelayed(TICK, POLL_MS);
    }

    /** Home Screen wieder vorne: der übernimmt selbst. */
    static void stop() {
        running = false;
        MAIN.removeCallbacks(TICK);
    }

    private static void tick() {
        if (!running) return;
        MAIN.postDelayed(TICK, POLL_MS);
        int minutes = Prefs.saverDelay(app);
        if (minutes <= 0 || !Prefs.saverInApps(app) || busy) return;
        busy = true;
        new Thread(() -> {
            try {
                check(minutes);
            } catch (Exception e) {
                Log.w(TAG, "check failed: " + e.getMessage());
            } finally {
                busy = false;
            }
        }, "idle-watch").start();
    }

    private static void check(int minutes) throws Exception {
        tv.bestseat.launcher.switcher.AdbAccess.requireGranted(app);
        String out = tv.bestseat.launcher.switcher.AdbClient.shell(app, PROBE, 5000);
        long last = -1;
        boolean awake = false;
        String top = "";
        int playing = 0;
        boolean bright = true;
        int audio = 0;
        for (String line : out.split("\n")) {
            line = line.trim();
            if (line.startsWith("mLastUserActivityTime")) {
                last = Long.parseLong(line.substring(line.indexOf('=') + 1).trim());
            } else if (line.startsWith("mWakefulness=")) {
                awake = line.endsWith("Awake");
            } else if (line.startsWith("topResumedActivity=")) {
                top = line;
            } else if (line.startsWith("playing=")) {
                playing = Integer.parseInt(line.substring(8).trim());
            } else if (line.startsWith("audio=")) {
                audio = Integer.parseInt(line.substring(6).trim());
            } else if (line.startsWith("bright=")) {
                bright = Integer.parseInt(line.substring(7).trim()) > 0;
            }
        }
        // Some players (for example World of Wonder) publish no media session, but their audio track runs.
        if (last < 0 || !awake || playing > 0 || audio > 0) return;
        // Best Seat selbst (Home Screen, Schoner) und Live-TV/Eingänge des TVs nicht.
        if (top.isEmpty() || top.contains(" " + app.getPackageName() + "/") || top.contains(" org.droidtv.")) return;
        // mLastUserActivityTime zählt wie SystemClock.uptimeMillis().
        long idle = SystemClock.uptimeMillis() - last;
        if (!bright) minutes = Math.min(minutes, NO_LOCK_MAX_MIN);
        long limit = minutes * 60_000L;
        if (top.contains(" com.android.tv.settings/")) limit = Math.min(limit, STATIC_OSD_MAX_MS);
        if (idle < limit) return;
        long idleSince = SystemClock.elapsedRealtime() - idle;
        Log.i(TAG, "idle for " + idle / 1000 + " s in " + top + ", starting screensaver");
        tv.bestseat.launcher.switcher.AdbClient.shell(app, "am start -n " + app.getPackageName()
                + "/.ScreensaverActivity --el " + ScreensaverActivity.EXTRA_IDLE_SINCE + " " + idleSince, 5000);
    }
}
