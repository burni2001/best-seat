package tv.bernhard.launcher.allapps;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

/** Sammelt alle startbaren Apps, auch die, die der Launcher versteckt. */
final class AppList {

    /** Eine startbare App mit allem, was die Kachel braucht. */
    static final class Entry {
        final String pkg;
        final ComponentName component;
        final String label;
        /** TV-Banner (16:9) oder null, dann wird das Symbol gezeigt. */
        final Drawable banner;
        final Drawable icon;
        final long installTime;

        Entry(String pkg, ComponentName component, String label, Drawable banner, Drawable icon,
                long installTime) {
            this.pkg = pkg;
            this.component = component;
            this.label = label;
            this.banner = banner;
            this.icon = icon;
            this.installTime = installTime;
        }

        Intent launchIntent() {
            return new Intent(Intent.ACTION_MAIN)
                    .setComponent(component)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        }
    }

    private AppList() {
    }

    /**
     * Alle Apps mit LEANBACK_LAUNCHER oder LAUNCHER, eine Kachel pro Paket,
     * gefiltert und sortiert nach den Einstellungen.
     */
    static List<Entry> load(Context context) {
        boolean showSystem = AllAppsPrefs.get(context, AllAppsPrefs.SHOW_SYSTEM);
        boolean showNonTv = AllAppsPrefs.get(context, AllAppsPrefs.SHOW_NON_TV);
        boolean byInstall = AllAppsPrefs.get(context, AllAppsPrefs.SORT_BY_INSTALL);

        PackageManager pm = context.getPackageManager();
        // Pro Paket zählt die TV-Activity zuerst, nur ohne sie die normale Startseite.
        Map<String, ResolveInfo> byPackage = new LinkedHashMap<>();
        collect(pm, Intent.CATEGORY_LEANBACK_LAUNCHER, byPackage);
        Set<String> tvApps = new HashSet<>(byPackage.keySet());
        collect(pm, Intent.CATEGORY_LAUNCHER, byPackage);

        List<Entry> entries = new ArrayList<>();
        for (ResolveInfo info : byPackage.values()) {
            ActivityInfo activity = info.activityInfo;
            ApplicationInfo app = activity.applicationInfo;
            // Systemapp heißt: vorinstalliert und nie aktualisiert. Netflix, YouTube
            // usw. kommen über den Play Store als Update und bleiben damit sichtbar.
            boolean system = (app.flags & ApplicationInfo.FLAG_SYSTEM) != 0
                    && (app.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0;
            if (system && !showSystem) {
                continue;
            }
            if (!tvApps.contains(activity.packageName) && !showNonTv) {
                continue;
            }
            String label = tv.bernhard.launcher.AppRepository.displayLabel(activity.packageName,
                    String.valueOf(info.loadLabel(pm)).trim());
            Drawable banner = activity.loadBanner(pm);
            if (banner == null) {
                banner = app.loadBanner(pm);
            }
            Drawable icon = info.loadIcon(pm);
            long installTime = 0;
            try {
                installTime = pm.getPackageInfo(activity.packageName, 0).firstInstallTime;
            } catch (PackageManager.NameNotFoundException ignored) {
                // Gerade deinstalliert, bleibt mit 0 am Ende.
            }
            entries.add(new Entry(activity.packageName,
                    new ComponentName(activity.packageName, activity.name), label, banner, icon,
                    installTime));
        }

        Collator collator = Collator.getInstance();
        collator.setStrength(Collator.PRIMARY);
        Collections.sort(entries, (a, b) -> collator.compare(a.label, b.label));
        if (byInstall) {
            // Neueste zuerst, bei gleichem Zeitpunkt bleibt die Reihenfolge nach Name.
            Collections.sort(entries, (a, b) -> Long.compare(b.installTime, a.installTime));
        }
        return entries;
    }

    private static void collect(PackageManager pm, String category, Map<String, ResolveInfo> out) {
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(category);
        for (ResolveInfo info : pm.queryIntentActivities(query, 0)) {
            String pkg = info.activityInfo.packageName;
            if (!out.containsKey(pkg)) {
                out.put(pkg, info);
            }
        }
    }
}
