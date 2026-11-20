package tv.bestseat.launcher;

import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.SystemClock;
import android.util.Log;

import tv.bestseat.launcher.switcher.AdbClient;

/**
 * Schließt das Vollbild „USB-Speicher fehlt“ (NewStorageActivity der TV-Einstellungen) sofort (W99).
 * Der TV zeigt es nach dem Aufwachen, weil der Stick mit den Apps kurz weg ist. Der Launcher lauscht
 * auf „Bildschirm an“, sucht dann bis zu 45 s per Nutzungsstatistik nach dem Fenster und schickt
 * über die eigene adb-Verbindung die Zurück-Taste.
 */
final class UsbNotice {

    private static final String TAG = "Launcher.UsbNotice";
    private static final long WATCH_MS = 45_000;
    private static final long POLL_MS = 350;
    private static boolean registered;
    private static boolean watching;

    private UsbNotice() {}

    /** Einmal pro Prozess: auf „Bildschirm an“ und „Apps vom Speicher entladen“ hören. */
    static synchronized void install(Context context) {
        Context app = context.getApplicationContext();
        if (!registered) {
            registered = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
            filter.addAction(Intent.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE);
            app.registerReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context c, Intent intent) {
                    watch(c);
                }
            }, filter);
        }
        watch(app);
    }

    static synchronized void watch(Context context) {
        if (watching || !Prefs.hideUsbNotice(context)) return;
        watching = true;
        Context app = context.getApplicationContext();
        new Thread(() -> {
            try {
                run(app);
            } finally {
                synchronized (UsbNotice.class) {
                    watching = false;
                }
            }
        }, "usb-notice").start();
    }

    private static void run(Context app) {
        long end = SystemClock.elapsedRealtime() + WATCH_MS;
        int presses = 0;
        long lastPress = 0;
        while (SystemClock.elapsedRealtime() < end && presses < 4) {
            if (!Prefs.hideUsbNotice(app)) return;
            if (noticeInFront(app) && SystemClock.elapsedRealtime() - lastPress > 1500) {
                try {
                    tv.bestseat.launcher.switcher.AdbAccess.requireGranted(app);
                    AdbClient.shell(app, "input keyevent KEYCODE_BACK", 3000);
                    presses++;
                    lastPress = SystemClock.elapsedRealtime();
                    Log.i(TAG, "Closed the USB notice (" + presses + ")");
                } catch (Exception e) {
                    Log.w(TAG, "Could not close the USB notice: " + e.getMessage());
                    return;
                }
            }
            SystemClock.sleep(POLL_MS);
        }
    }

    /** Ist das Fenster gerade vorne? Letztes Ereignis der Aktivität: fortgesetzt, nicht pausiert. */
    private static boolean noticeInFront(Context app) {
        try {
            UsageStatsManager usm = (UsageStatsManager) app.getSystemService(Context.USAGE_STATS_SERVICE);
            if (usm == null) return false;
            long now = System.currentTimeMillis();
            UsageEvents events = usm.queryEvents(now - 20_000, now + 1000);
            UsageEvents.Event e = new UsageEvents.Event();
            boolean front = false;
            while (events.hasNextEvent()) {
                events.getNextEvent(e);
                int type = e.getEventType();
                boolean notice = e.getClassName() != null && e.getClassName().endsWith("NewStorageActivity");
                if (type == UsageEvents.Event.ACTIVITY_RESUMED) {
                    front = notice;
                } else if (notice && (type == UsageEvents.Event.ACTIVITY_PAUSED
                        || type == UsageEvents.Event.ACTIVITY_STOPPED)) {
                    front = false;
                }
            }
            return front;
        } catch (RuntimeException ex) {
            return false;
        }
    }
}
