package tv.bestseat.launcher.switcher;

import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Process;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Liest aus der Nutzungsstatistik, welche Apps zuletzt im Vordergrund waren. */
final class RecentApps {

    /** Wie weit zurück die Nutzungsstatistik ausgewertet wird. */
    private static final long LOOKBACK_MS = 24L * 60 * 60 * 1000;

    private RecentApps() {}

    static boolean hasUsageAccess(Context context) {
        AppOpsManager appOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        int mode = appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    /** Alle startbaren Apps, die zuletzt vorne waren, die neueste zuerst. */
    static List<String> recentPackages(Context context) {
        UsageStatsManager usm = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        long now = System.currentTimeMillis();
        UsageEvents events = usm.queryEvents(now - LOOKBACK_MS, now);

        // Pro Paket den letzten Zeitpunkt merken, an dem es nach vorne kam.
        Map<String, Long> lastResumed = new LinkedHashMap<>();
        UsageEvents.Event event = new UsageEvents.Event();
        while (events.hasNextEvent()) {
            events.getNextEvent(event);
            if (event.getEventType() == UsageEvents.Event.ACTIVITY_RESUMED) {
                lastResumed.put(event.getPackageName(), event.getTimeStamp());
            }
        }

        List<Map.Entry<String, Long>> sorted = new ArrayList<>(lastResumed.entrySet());
        sorted.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));

        String own = context.getPackageName();
        SharedPreferences quits = forceQuits(context);
        SharedPreferences.Editor cleanup = quits.edit();
        List<String> result = new ArrayList<>();
        for (Map.Entry<String, Long> entry : sorted) {
            String pkg = entry.getKey();
            if (pkg.equals(own) || SwitcherPrefs.isBlacklisted(context, pkg)) {
                continue;
            }
            // Per Force Quit beendet und seitdem nicht wieder gestartet: ausblenden.
            if (quits.contains(pkg)) {
                if (entry.getValue() <= quits.getLong(pkg, 0)) {
                    continue;
                }
                cleanup.remove(pkg);
            }
            // Von außen beendet (Force stop, z. B. durch die App-Tasten von Google TV Helper 1.1.11+):
            // ausblenden, bis sie wieder startet, dann hebt Android den Zustand auf. Seit 1.8.6.
            if (isStopped(context, pkg)) {
                continue;
            }
            if (launchIntent(context, pkg) == null) {
                continue;
            }
            result.add(pkg);
        }
        cleanup.apply();
        return result;
    }

    /**
     * Ob vor dem Aufruf eine sichtbare Seite dieser App vorne war (All Apps oder
     * die Einstellungen). Das eigene Paket zählt in recentPackages() nie als App,
     * Eintrag 0 ist dann schon die vorherige App, wie auf der Startseite.
     * Die unsichtbaren Seiten (Leiste, Letzte App) zählen dabei nicht.
     */
    static boolean ownUiInFront(Context context) {
        UsageStatsManager usm = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        long now = System.currentTimeMillis();
        UsageEvents events = usm.queryEvents(now - LOOKBACK_MS, now);
        String own = context.getPackageName();
        String lastPkg = null;
        UsageEvents.Event event = new UsageEvents.Event();
        while (events.hasNextEvent()) {
            events.getNextEvent(event);
            if (event.getEventType() != UsageEvents.Event.ACTIVITY_RESUMED) {
                continue;
            }
            String cls = event.getClassName();
            // Kontrollzentrum und Schoner liegen über der App davor und zählen nicht als eigene Seite.
            if (SwitcherActivity.class.getName().equals(cls)
                    || LastAppActivity.class.getName().equals(cls)
                    || "tv.bestseat.launcher.ControlCenterActivity".equals(cls)
                    || "tv.bestseat.launcher.ScreensaverActivity".equals(cls)) {
                continue;
            }
            lastPkg = event.getPackageName();
        }
        return own.equals(lastPkg);
    }

    /** true, wenn Android die App als beendet führt (FLAG_STOPPED nach „Beenden erzwingen“). */
    private static boolean isStopped(Context context, String pkg) {
        try {
            ApplicationInfo info = context.getPackageManager().getApplicationInfo(pkg, 0);
            return (info.flags & ApplicationInfo.FLAG_STOPPED) != 0;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /**
     * Merkt sich, dass eine App per Force Quit beendet wurde. Sie bleibt
     * ausgeblendet, bis sie wieder nach vorne kommt.
     */
    static void markForceQuit(Context context, String pkg) {
        forceQuits(context).edit().putLong(pkg, System.currentTimeMillis()).apply();
    }

    private static SharedPreferences forceQuits(Context context) {
        return context.getSharedPreferences("force_quit", Context.MODE_PRIVATE);
    }

    /** Paket des aktuellen Home-Launchers (AT4K, später Best Seat). */
    static String homePackage(Context context) {
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        ResolveInfo info = context.getPackageManager()
                .resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
        return info != null ? info.activityInfo.packageName : null;
    }

    /** Intent, der eine App nach vorne holt, bevorzugt ihre TV-Startseite. */
    static Intent launchIntent(Context context, String pkg) {
        PackageManager pm = context.getPackageManager();
        Intent intent = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (intent == null) {
            intent = pm.getLaunchIntentForPackage(pkg);
        }
        if (intent == null) {
            return null;
        }
        // Holt eine bereits laufende App mit ihrem bisherigen Zustand zurück.
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        return intent;
    }

    /** Öffnet die Einstellung, in der die Nutzungsstatistik freigegeben wird. */
    static void openUsageAccessSettings(Context context) {
        Intent intent = new Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
        } catch (Exception ignored) {
            // Auf manchen TVs fehlt dieser Bildschirm, dann bleibt nur adb.
        }
    }
}
