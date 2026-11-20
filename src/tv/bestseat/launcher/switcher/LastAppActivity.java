package tv.bestseat.launcher.switcher;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import java.util.List;

/**
 * Springt zur zuletzt genutzten App. Ein zweiter Aufruf springt wieder zurück.
 * Die Startseite (Home-Launcher) wird übersprungen, dafür gibt es die Home-Taste.
 */
public class LastAppActivity extends Activity {

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

        String home = RecentApps.homePackage(this);
        List<String> recent = RecentApps.recentPackages(this);

        // Eintrag 0 ist die App, aus der heraus die Taste gedrückt wurde.
        // Ziel ist die nächste App danach, die Startseite zählt nicht.
        String target = null;
        for (int i = 1; i < recent.size(); i++) {
            if (!recent.get(i).equals(home)) {
                target = recent.get(i);
                break;
            }
        }
        // Steht man auf der Startseite, in All Apps oder in den Einstellungen,
        // ist Eintrag 0 selbst schon das Ziel (das eigene Paket steht nicht in der Liste).
        if ((!recent.isEmpty() && recent.get(0).equals(home)) || RecentApps.ownUiInFront(this)) {
            target = null;
            for (String pkg : recent) {
                if (!pkg.equals(home)) {
                    target = pkg;
                    break;
                }
            }
        }

        if (target == null) {
            Toast.makeText(this, "No previous app found", Toast.LENGTH_SHORT).show();
        } else {
            Intent intent = RecentApps.launchIntent(this, target);
            startActivity(intent);
        }
        finish();
    }
}
