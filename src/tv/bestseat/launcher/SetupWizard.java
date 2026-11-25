package tv.bestseat.launcher;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.widget.LinearLayout;
import android.widget.Toast;

/**
 * Einrichtungsassistent beim ersten Start (W124): Willkommen, adb-Freigabe, Berechtigungen, Sicherung einspielen,
 * Erscheinungsbild, Bildschirmschoner, Fertig. Jeder Schritt ist ein Fenster im Stil der übrigen
 * Best-Seat-Abfragen. Zurück oder Home bricht ab; in den Einstellungen lässt er sich neu starten.
 * Bei einem Update (nicht frisch installiert) erscheint er nicht von selbst.
 */
final class SetupWizard {

    private static final int[] DELAYS = {2, 3, 5, 0};

    private final HomeActivity home;
    private final boolean lightBefore;

    private SetupWizard(HomeActivity home) {
        this.home = home;
        this.lightBefore = Prefs.isLight(home);
    }

    /** Beim ersten Start einer frischen Installation zeigen. */
    static void maybeShow(HomeActivity home) {
        if (Prefs.setupDone(home)) return;
        Prefs.setSetupDone(home, true);
        try {
            PackageInfo info = home.getPackageManager().getPackageInfo(home.getPackageName(), 0);
            if (info.firstInstallTime != info.lastUpdateTime) return;
        } catch (Exception e) {
            return;
        }
        start(home);
    }

    static void start(HomeActivity home) {
        new SetupWizard(home).welcome();
    }

    private Dialogs.Panel step(String title, String message) {
        return Dialogs.alert(home, title, message, 300);
    }

    private void show(Dialogs.Panel p, LinearLayout focus) {
        home.show(p);
        focus.requestFocus();
    }

    // ---------------------------------------------------------------- Schritte

    private void welcome() {
        Dialogs.Panel p = step("Welcome to Best Seat",
                "Set up a few things in under a minute. You can change all of them later in Settings.");
        LinearLayout go = Dialogs.alertButton(home, p, "Start Setup", this::adb);
        Dialogs.alertButton(home, p, "Skip", () -> { });
        show(p, go);
    }

    private void adb() {
        try {
            tv.bestseat.launcher.switcher.AdbAccess.requireGranted(home);
            permissions(this::backup);
            return;
        } catch (Exception notYet) {
            // Freigabe fehlt noch: fragen.
        }
        Dialogs.Panel p = step("Allow adb Access",
                "Best Seat uses its own adb connection for the App Switcher, force quit, the VPN prompt, "
                        + "backups and the screensaver in idle apps. Confirm “Allow USB debugging” "
                        + "on the TV and tick Always allow.");
        LinearLayout allow = Dialogs.alertButton(home, p, "Allow", this::waitForAdb);
        Dialogs.alertButton(home, p, "Not Now", () -> permissions(this::appearance));
        show(p, allow);
    }

    private void waitForAdb() {
        Toast.makeText(home, "Confirm “Allow USB debugging” on the TV", Toast.LENGTH_LONG).show();
        new Thread(() -> {
            boolean ok;
            try {
                String out = tv.bestseat.launcher.switcher.AdbClient.shell(home.getApplicationContext(), "echo ok", 60000);
                ok = out != null && out.contains("ok");
            } catch (Exception e) {
                ok = false;
            }
            boolean granted = ok;
            home.runOnUiThread(() -> {
                if (home.isDestroyed()) return;
                if (granted) {
                    permissions(this::backup);
                } else {
                    Toast.makeText(home, "No adb access. You can allow it later in Settings > Permissions.",
                            Toast.LENGTH_LONG).show();
                    permissions(this::appearance);
                }
            });
        }, "setup-adb").start();
    }

    /** Remaining permissions (adb has its own step). With adb access they are granted without dialogs. */
    private void permissions(Runnable next) {
        java.util.List<Integer> missing = Permissions.missing(home);
        missing.remove(Integer.valueOf(Permissions.ADB));
        if (missing.isEmpty()) {
            next.run();
            return;
        }
        StringBuilder names = new StringBuilder();
        for (int p : missing) names.append(names.length() > 0 ? ", " : "").append(Permissions.name(p));
        Dialogs.Panel p = step("Allow Permissions", "Best Seat still needs " + names
                + ". You can also allow them later in Settings > Permissions.");
        LinearLayout allow = Dialogs.alertButton(home, p, "Allow", () -> allowEach(missing, 0, next));
        Dialogs.alertButton(home, p, "Not Now", next);
        show(p, allow);
    }

    private void allowEach(java.util.List<Integer> missing, int i, Runnable next) {
        if (i >= missing.size()) {
            next.run();
            return;
        }
        int perm = missing.get(i);
        Runnable after = () -> allowEach(missing, i + 1, next);
        if (Permissions.granted(home, Permissions.ADB)) {
            Permissions.fix(home, perm, after);
            return;
        }
        Dialogs.Panel p = step(Permissions.name(perm), Permissions.hint(perm));
        LinearLayout open = Dialogs.alertButton(home, p, "Open", () -> Permissions.fix(home, perm, after));
        Dialogs.alertButton(home, p, "Skip", after);
        show(p, open);
    }

    private void backup() {
        new Thread(() -> {
            String when = Backup.lastBackup(home);
            home.runOnUiThread(() -> {
                if (home.isDestroyed()) return;
                if (when == null) {
                    appearance();
                    return;
                }
                Dialogs.Panel p = step("Restore Backup?",
                        "A backup from " + when + " was found. Restore its tiles, folders and settings?");
                LinearLayout restore = Dialogs.alertButton(home, p, "Restore", this::restore);
                Dialogs.alertButton(home, p, "Start Fresh", this::appearance);
                show(p, restore);
            });
        }, "setup-backup").start();
    }

    private void restore() {
        new Thread(() -> {
            String error = null;
            try {
                Backup.restore(home);
            } catch (Exception e) {
                error = e.getMessage();
            }
            String result = error;
            home.runOnUiThread(() -> {
                if (result != null) {
                    Toast.makeText(home, "Restore failed: " + result, Toast.LENGTH_LONG).show();
                    appearance();
                    return;
                }
                Prefs.setSetupDone(home, true);
                Toast.makeText(home, "Backup restored", Toast.LENGTH_SHORT).show();
                // Neu starten wie in den Einstellungen, damit alles das eingespielte Layout lädt.
                home.startActivity(new Intent(home, HomeActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
                home.getWindow().getDecorView().postDelayed(
                        () -> android.os.Process.killProcess(android.os.Process.myPid()), 800);
            });
        }, "setup-restore").start();
    }

    private void appearance() {
        Dialogs.Panel p = step("Appearance", "Choose a dark or light look, or light only during the day.");
        LinearLayout focus = null;
        for (int i = 0; i < Prefs.APPEARANCE_NAMES.length; i++) {
            final int index = i;
            LinearLayout b = Dialogs.alertButton(home, p, Prefs.APPEARANCE_NAMES[i], () -> {
                Prefs.setAppearance(home, index);
                screensaver();
            });
            if (i == Prefs.appearance(home)) focus = b;
        }
        show(p, focus);
    }

    private void screensaver() {
        Dialogs.Panel p = step("Screensaver", "Choose what the screensaver shows.");
        LinearLayout focus = null;
        for (int i = 0; i < Prefs.SAVER_NAMES.length; i++) {
            final int index = i;
            LinearLayout b = Dialogs.alertButton(home, p, Prefs.SAVER_NAMES[i], () -> {
                Prefs.setSaverSource(home, index);
                saverDelay();
            });
            if (i == Prefs.saverSource(home)) focus = b;
        }
        show(p, focus);
    }

    private void saverDelay() {
        Dialogs.Panel p = step("Start Screensaver After",
                "Choose how long to wait without input before the screensaver starts.");
        LinearLayout focus = null;
        for (int minutes : DELAYS) {
            LinearLayout b = Dialogs.alertButton(home, p, minutes <= 0 ? "Never" : minutes + " min", () -> {
                Prefs.setSaverDelay(home, minutes);
                done();
            });
            if (focus == null || minutes == Prefs.saverDelay(home)) focus = b;
        }
        show(p, focus);
    }

    private void done() {
        Dialogs.Panel p = step("All Set",
                "Hold OK on a tile for more options. Everything you chose can be changed in Settings.");
        LinearLayout ok = Dialogs.alertButton(home, p, "Done", () -> {
            // Erscheinungsbild erst jetzt übernehmen, sonst würde der Neuaufbau den Assistenten schließen.
            if (Prefs.isLight(home) != lightBefore) home.recreate();
        });
        show(p, ok);
    }
}
