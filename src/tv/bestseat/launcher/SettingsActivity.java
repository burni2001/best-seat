package tv.bestseat.launcher;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/** Einstellungen der Startseite. Hinweiszeile unten zur Zeile mit Fokus. */
public final class SettingsActivity extends Activity {
    /** Schrift beim Aufbau; ändert sie sich, wird die Seite neu erstellt. */
    private int builtFont;


    private LinearLayout list;
    private TextView hint;
    private TextView title;
    private android.widget.ImageView pageIcon;
    /** Zeile, die nach dem Neuaufbau (Wechsel Hell/Dunkel) den Fokus bekommt. */
    private static String focusAfterRecreate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.apply(this);
        builtFont = Prefs.font(this);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Ui.BACKGROUND);
        // Aufbau wie die tvOS-Einstellungen (Screenshots des Nutzers): Titel mittig oben, links großes
        // Symbol der Seite mit Erklärtext darunter, rechts die Liste. Maße in px am TV / 2.
        title = Ui.text(this, "Settings", 26, Ui.TEXT_DIM, true);
        title.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP);
        tp.topMargin = Ui.dp(this, 28);
        root.addView(title, tp);

        pageIcon = new android.widget.ImageView(this);
        pageIcon.setBackground(Ui.rounded(Ui.light ? android.graphics.Color.argb(215, 255, 255, 255)
                : android.graphics.Color.argb(80, 255, 255, 255), Ui.dp(this, 60)));
        int ip = Ui.dp(this, 72);
        pageIcon.setPadding(ip, ip, ip, ip);
        FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams(Ui.dp(this, 269), Ui.dp(this, 269));
        ilp.leftMargin = Ui.dp(this, 128);
        ilp.topMargin = Ui.dp(this, 119);
        root.addView(pageIcon, ilp);

        hint = Ui.text(this, "", 13, Ui.TEXT_DIM, false);
        hint.setGravity(Gravity.CENTER_HORIZONTAL);
        hint.setMaxLines(4);
        FrameLayout.LayoutParams hlp = new FrameLayout.LayoutParams(Ui.dp(this, 400),
                ViewGroup.LayoutParams.WRAP_CONTENT);
        hlp.leftMargin = Ui.dp(this, 128 + 135 - 200);
        hlp.topMargin = Ui.dp(this, 119 + 269 + 22);
        root.addView(hint, hlp);

        scroll = new ScrollView(this);
        scroll.setClipChildren(false);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setVerticalFadingEdgeEnabled(true);
        scroll.setFadingEdgeLength(Ui.dp(this, 24));
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setClipChildren(false);
        list.setClipToPadding(false);
        int pad = Ui.dp(this, 8);
        list.setPadding(pad, pad, pad, Ui.dp(this, 40));
        scroll.addView(list);
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(Ui.dp(this, 406),
                ViewGroup.LayoutParams.MATCH_PARENT);
        slp.leftMargin = Ui.dp(this, 522);
        slp.topMargin = Ui.dp(this, 86);
        root.addView(scroll, slp);
        setContentView(root);
        build();
        if (focusAfterRecreate != null) {
            focusTag(focusAfterRecreate);
            focusAfterRecreate = null;
        }
    }

    // Seiten: Übersicht und Untergruppen (W18). Zurück führt von einer Untergruppe zur Übersicht.
    private static final String PAGE_HOME = "home";
    private static final String PAGE_SHELF = "shelf";
    private static final String PAGE_SAVER = "saver";
    private static final String PAGE_CONTROL = "control";
    private static final String PAGE_SWITCHER = "switcher";
    private static final String PAGE_SWITCHER_HIDDEN = "switcher_hidden";
    private static final String PAGE_BACKUP = "backup";
    private static final String PAGE_VPN_APPS = "vpn_apps";
    /** Auswahl einer Option als eigene Unterseite mit Haken (wie tvOS „Erscheinungsbild“). */
    private static final String PAGE_CHOICE = "choice";
    private String choiceTitle;
    private String[] choiceNames;
    private java.util.function.IntSupplier choiceGet;
    private java.util.function.IntConsumer choiceSet;
    private String choiceHint;
    private String choiceParent;
    private String choiceTag;
    private String choiceIcon;
    private String page;
    private ScrollView scroll;
    /** Kontrollzentrum-Kachel, die gerade verschoben wird (W12). */
    private String movingId;

    private void build() {
        list.removeAllViews();
        if (PAGE_HOME.equals(page)) {
            title.setText("Home Screen");
            homeRows();
        } else if (PAGE_SHELF.equals(page)) {
            title.setText("Content Shelf");
            shelfRows();
        } else if (PAGE_SAVER.equals(page)) {
            title.setText("Screensaver");
            saverRows();
        } else if (PAGE_SWITCHER.equals(page)) {
            title.setText("App Switcher");
            switcherRows();
        } else if (PAGE_SWITCHER_HIDDEN.equals(page)) {
            title.setText("Hidden in App Switcher");
            switcherHiddenRows();
        } else if (PAGE_CONTROL.equals(page)) {
            title.setText("Control Center");
            controlRows();
        } else if (PAGE_VPN_APPS.equals(page)) {
            title.setText("Ask for VPN");
            vpnAppsRows();
        } else if (PAGE_BACKUP.equals(page)) {
            title.setText("Backup");
            backupRows();
        } else if (PAGE_CHOICE.equals(page)) {
            title.setText(choiceTitle);
            choiceRows();
        } else {
            title.setText("Settings");
            mainRows();
        }
        String icon;
        if (PAGE_HOME.equals(page)) icon = ControlItems.ALL_APPS;
        else if (PAGE_SHELF.equals(page)) icon = ControlItems.PICTURE;
        else if (PAGE_SAVER.equals(page)) icon = ControlItems.SCREENSAVER;
        else if (PAGE_CONTROL.equals(page)) icon = ControlItems.SETTINGS;
        else if (PAGE_SWITCHER.equals(page) || PAGE_SWITCHER_HIDDEN.equals(page)) icon = ControlItems.APP_SWITCHER;
        else if (PAGE_BACKUP.equals(page)) icon = ControlItems.RESTART;
        else if (PAGE_VPN_APPS.equals(page)) icon = ControlItems.VPN;
        else if (PAGE_CHOICE.equals(page)) icon = choiceIcon;
        else icon = ControlItems.HOME;
        pageIcon.setImageDrawable(new ControlIcons.Icon(icon, Ui.light ? android.graphics.Color.argb(255, 70, 70, 74)
                : android.graphics.Color.argb(255, 40, 40, 42)));
        // Erste wählbare Zeile (Abschnittstitel sind nicht wählbar).
        for (int i = 0; i < list.getChildCount(); i++) {
            if (list.getChildAt(i).isFocusable()) {
                list.getChildAt(i).requestFocus();
                break;
            }
        }
    }

    /** Abschnittstitel in Großbuchstaben wie tvOS (z. B. „ANZEIGEZOOM“). */
    private void section(String text) {
        TextView t = Ui.text(this, text.toUpperCase(java.util.Locale.ROOT), 13, Ui.TEXT_DIM, true);
        t.setPadding(Ui.dp(this, 14), Ui.dp(this, list.getChildCount() == 0 ? 0 : 12), 0, Ui.dp(this, 6));
        list.addView(t);
    }

    /** Zeile mit aktuellem Wert und „›“; OK öffnet die Auswahl als Unterseite. */
    private LinearLayout choiceRow(String label, String[] names, java.util.function.IntSupplier get,
            java.util.function.IntConsumer set, String hintText, String tag) {
        int cur = Math.max(0, Math.min(names.length - 1, get.getAsInt()));
        TextView value = Ui.dimText(this, names[cur] + "  \u203A");
        LinearLayout r = row(label, value, hintText);
        r.setTag(tag);
        String parent = page;
        r.setOnClickListener(v -> {
            choiceTitle = label;
            choiceNames = names;
            choiceGet = get;
            choiceSet = set;
            choiceHint = hintText;
            choiceParent = parent;
            choiceTag = tag;
            choiceIcon = parent == null ? ControlItems.HOME : PAGE_HOME.equals(parent) ? ControlItems.ALL_APPS
                    : PAGE_SAVER.equals(parent) ? ControlItems.SCREENSAVER
                    : PAGE_SWITCHER.equals(parent) ? ControlItems.APP_SWITCHER : ControlItems.HOME;
            page = PAGE_CHOICE;
            build();
            int c = Math.max(0, Math.min(names.length - 1, get.getAsInt()));
            if (c < list.getChildCount()) list.getChildAt(c).requestFocus();
        });
        return r;
    }

    private void choiceRows() {
        int cur = choiceGet.getAsInt();
        for (int i = 0; i < choiceNames.length; i++) {
            final int index = i;
            TextView check = Ui.dimText(this, i == cur ? "\u2713" : "");
            LinearLayout r = row(choiceNames[i], check, choiceHint);
            r.setOnClickListener(v -> {
                choiceSet.accept(index);
                closeChoice();
            });
            // Schriftauswahl: jeden Namen in seiner eigenen Schrift zeigen.
            if ("font".equals(choiceTag) && r.getChildAt(0) instanceof TextView) {
                ((TextView) r.getChildAt(0)).setTypeface(Fonts.get(this, i, Fonts.MEDIUM));
            }
        }
    }

    /** Zurück zur Seite mit der Zeile, deren Wert gewählt wurde. */
    private void closeChoice() {
        if (!PAGE_CHOICE.equals(page)) return;
        page = choiceParent;
        if (Prefs.isLight(this) != Ui.light || Prefs.font(this) != builtFont) {
            // Design oder Schrift gewechselt: Seite neu erstellen, Fokus wieder auf die Zeile.
            focusAfterRecreate = choiceTag;
            recreate();
            return;
        }
        build();
        focusTag(choiceTag);
    }

    private void mainRows() {
        group(PAGE_HOME, "Home Screen", "Change tiles, favorites and the look of the home screen.");
        group(PAGE_SHELF, "Content Shelf", "Change what the Content Shelf shows above the favorites.");
        group(PAGE_SAVER, "Screensaver", "Choose what the screensaver shows and when it starts.");
        group(PAGE_CONTROL, "Control Center", "Choose and sort the tiles in the Control Center.");
        group(PAGE_SWITCHER, "App Switcher", "Change the bar of recently used apps.");
        section("Look");
        choiceRow("Appearance", Prefs.APPEARANCE_NAMES, () -> Prefs.appearance(this),
                i -> Prefs.setAppearance(this, i), "Choose a dark or light look, or light only during the day.", "appearance");
        choiceRow("Font", Fonts.NAMES, () -> Prefs.font(this),
                i -> Prefs.setFont(this, i), "Choose the font for all launcher text.", "font");
        choiceRow("Clock time zone", Prefs.ZONE_NAMES, () -> Prefs.clockZone(this),
                i -> Prefs.setClockZone(this, i), "Choose which time the clocks show.", "zone");
        Switch reduce = Ui.toggle(this);
        reduce.setChecked(Prefs.reduceMotion(this));
        row("Reduce motion", reduce, "Fade apps, folders and the Control Center in and out instead of zooming.")
                .setOnClickListener(v -> {
                    Prefs.setReduceMotion(this, !Prefs.reduceMotion(this));
                    reduce.setChecked(Prefs.reduceMotion(this));
                });
        Switch blurAll = Ui.toggle(this);
        blurAll.setChecked(Prefs.blurEnabled(this));
        row("Blur effects", blurAll, "Blur backgrounds and panels; turn off to save graphics power.").setOnClickListener(v -> {
            Prefs.setBlurEnabled(this, !Prefs.blurEnabled(this));
            blurAll.setChecked(Prefs.blurEnabled(this));
        });
        externalSettings();
        section("General");
        group(PAGE_BACKUP, "Backup", "Save or restore tiles, folders and settings.");
        row("About", null, "See the version and credits.").setOnClickListener(v -> Dialogs.about(this));
    }

    /** Einstellungen der Helfer-Apps verlinken; Apps, die auf dem TV fehlen, erscheinen nicht (W98). */
    private void externalSettings() {
        String[][] apps = {
                {Companions.AIRPLAY, "AirPlay", "Open the AirPlay settings."},
                {Companions.HUESYNC, "Ambience", "Open the Ambience settings."},
                {Companions.TVHELPER, "Google TV Helper", "Open the Google TV Helper settings."}};
        boolean any = false;
        for (String[] app : apps) {
            Intent launch = getPackageManager().getLeanbackLaunchIntentForPackage(app[0]);
            if (launch == null) launch = getPackageManager().getLaunchIntentForPackage(app[0]);
            if (launch == null) continue;
            if (!any) {
                section("External settings");
                any = true;
            }
            final Intent open = launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            row(app[1], Ui.dimText(this, "\u203A"), app[2]).setOnClickListener(v -> startActivity(open));
        }
    }

    private void group(String id, String label, String hintText) {
        LinearLayout r = row(label, Ui.dimText(this, "\u203A"), hintText);
        r.setTag("page:" + id);
        r.setOnClickListener(v -> {
            page = id;
            build();
        });
    }

    private void homeRows() {
        section("Layout");
        String[] columnNames = new String[Prefs.COLUMN_CHOICES.length];
        for (int i = 0; i < columnNames.length; i++) columnNames[i] = String.valueOf(Prefs.COLUMN_CHOICES[i]);
        choiceRow("Tiles per row", columnNames, () -> {
            int current = Prefs.columns(this);
            for (int i = 0; i < Prefs.COLUMN_CHOICES.length; i++) if (Prefs.COLUMN_CHOICES[i] == current) return i;
            return 0;
        }, i -> Prefs.setColumns(this, Prefs.COLUMN_CHOICES[i]), "Choose how many tiles fit in one row.", "columns");

        Switch fav = Ui.toggle(this);
        fav.setChecked(Prefs.favorites(this));
        LinearLayout favRow = row("Favorites row", fav, "Keep the top row on its own bar and fill the Content Shelf only from it.");
        favRow.setTag("favorites");
        favRow.setOnClickListener(v -> {
            Prefs.setFavorites(this, !Prefs.favorites(this));
            rebuildKeeping(v);
        });
        choiceRow("Background", Prefs.BACKGROUND_NAMES, () -> Prefs.background(this),
                i -> Prefs.setBackground(this, i), "Choose what fills the background of the home screen.", "background");

        section("Tiles");
        Switch nonTv = Ui.toggle(this);
        nonTv.setChecked(Prefs.nonTvApps(this));
        row("Show phone apps", nonTv, "Show apps made for phones, too.").setOnClickListener(v -> {
            Prefs.setNonTvApps(this, !Prefs.nonTvApps(this));
            nonTv.setChecked(Prefs.nonTvApps(this));
        });

        Switch shine = Ui.toggle(this);
        shine.setChecked(Prefs.shine(this));
        row("Shine effect when focusing app", shine, "Let a soft light sweep across the selected app tile.").setOnClickListener(v -> {
            Prefs.setShine(this, !Prefs.shine(this));
            shine.setChecked(Prefs.shine(this));
        });

        Switch settingsTile = Ui.toggle(this);
        settingsTile.setChecked(Prefs.settingsTile(this));
        row("Settings tile", settingsTile, "Show the Settings tile after the apps.").setOnClickListener(v -> {
            boolean show = !Prefs.settingsTile(this);
            Prefs.setSettingsTile(this, show);
            settingsTile.setChecked(show);
            if (!show) {
                // Ohne Kachel bleiben die Einstellungen nur über das Kontrollzentrum erreichbar.
                java.util.Set<String> h = Prefs.controlHidden(this);
                if (h.remove(ControlItems.HOME)) Prefs.setControlHidden(this, h);
                Toast.makeText(this, "Open these settings from the Control Center", Toast.LENGTH_LONG).show();
            }
        });

        Switch hiddenTile = Ui.toggle(this);
        hiddenTile.setChecked(Prefs.hiddenTile(this));
        LinearLayout hiddenTileRow = row("Hidden apps tile", hiddenTile, "Show the Hidden apps tile after the apps.");
        hiddenTileRow.setTag("hiddenTile");
        hiddenTileRow.setOnClickListener(v -> {
            Prefs.setHiddenTile(this, !Prefs.hiddenTile(this));
            rebuildKeeping(v);
        });
        if (!Prefs.hiddenTile(this)) {
            row("Hidden apps", Ui.dimText(this, "\u203A"), "Show or open the apps you hid from the home screen.")
                    .setOnClickListener(v -> startActivity(new Intent(HomeActivity.ACTION_HIDDEN)
                            .setClass(this, HomeActivity.class)));
        }

        Switch usbNotice = Ui.toggle(this);
        usbNotice.setChecked(Prefs.hideUsbNotice(this));
        row("Hide USB notice", usbNotice, "Close the full-screen \u201CUSB storage missing\u201D message right away.")
                .setOnClickListener(v -> {
                    Prefs.setHideUsbNotice(this, !Prefs.hideUsbNotice(this));
                    usbNotice.setChecked(Prefs.hideUsbNotice(this));
                    if (Prefs.hideUsbNotice(this) && !tv.bestseat.launcher.switcher.AdbAccess.hasKey(this)) {
                        Toast.makeText(this, "Allow adb access first (App Switcher settings)", Toast.LENGTH_LONG).show();
                    }
                });

        section("Top bar");
        Switch clockSwitch = Ui.toggle(this);
        clockSwitch.setChecked(Prefs.showClock(this));
        row("Clock", clockSwitch, "Show the time next to the profile picture.").setOnClickListener(v -> {
            Prefs.setShowClock(this, !Prefs.showClock(this));
            clockSwitch.setChecked(Prefs.showClock(this));
        });
        Switch vpn = Ui.toggle(this);
        vpn.setChecked(Prefs.showVpn(this));
        row("VPN indicator", vpn, "Show a VPN label at the top while the VPN is on.").setOnClickListener(v -> {
            Prefs.setShowVpn(this, !Prefs.showVpn(this));
            vpn.setChecked(Prefs.showVpn(this));
        });
        LinearLayout vpnAppsRow = row("Ask for VPN", Ui.dimText(this, "\u203A"),
                "Choose apps that offer to connect the VPN first when it is off.");
        vpnAppsRow.setTag("page:" + PAGE_VPN_APPS);
        vpnAppsRow.setOnClickListener(v -> {
            page = PAGE_VPN_APPS;
            build();
        });

        section("Apps");
        row("All Apps", Ui.dimText(this, "\u203A"), "See every app in one list, hidden ones too.")
                .setOnClickListener(v -> startActivity(new Intent(this, tv.bestseat.launcher.allapps.AllAppsActivity.class)));

        row("Refresh home screen", null, "Reload all apps and tile pictures.").setOnClickListener(v -> {
            startActivity(new Intent(HomeActivity.ACTION_REFRESH).setClass(this, HomeActivity.class));
            Toast.makeText(this, "Home screen reloaded", Toast.LENGTH_SHORT).show();
        });
    }

    private void shelfRows() {
        Switch shineShelf = Ui.toggle(this);
        shineShelf.setChecked(Prefs.shineShelf(this));
        row("Shine effect when focusing content tile", shineShelf, "Let a soft light sweep across the selected content tile.").setOnClickListener(v -> {
            Prefs.setShineShelf(this, !Prefs.shineShelf(this));
            shineShelf.setChecked(Prefs.shineShelf(this));
        });
        row("Content Shelf diagnostics", null, "Check which apps give content to the Content Shelf.")
                .setOnClickListener(v -> startActivity(new Intent(this, DiagnosticsActivity.class)));
    }

    /** App Switcher (aus der früheren App Switcher-App übernommen). */
    private void switcherRows() {
        Switch on = Ui.toggle(this);
        on.setChecked(tv.bestseat.launcher.switcher.SwitcherPrefs.isEnabled(this));
        row("App Switcher", on, "Turn the recent apps bar and Last app on or off.").setOnClickListener(v -> {
            boolean next = !tv.bestseat.launcher.switcher.SwitcherPrefs.isEnabled(this);
            tv.bestseat.launcher.switcher.SwitcherPrefs.setEnabled(this, next);
            on.setChecked(next);
        });
        Switch blur = Ui.toggle(this);
        blur.setChecked(tv.bestseat.launcher.switcher.SwitcherPrefs.isBlurEnabled(this));
        row("Blur background", blur, "Blur the app behind the bar instead of only darkening it.").setOnClickListener(v -> {
            boolean next = !tv.bestseat.launcher.switcher.SwitcherPrefs.isBlurEnabled(this);
            tv.bestseat.launcher.switcher.SwitcherPrefs.setBlurEnabled(this, next);
            blur.setChecked(next);
        });
        choiceRow("Style", tv.bestseat.launcher.switcher.SwitcherPrefs.STYLE_NAMES,
                () -> tv.bestseat.launcher.switcher.SwitcherPrefs.style(this),
                i -> tv.bestseat.launcher.switcher.SwitcherPrefs.setStyle(this, i),
                "Choose between the large cards and a bar with the apps side by side.", "switcher_style");
        LinearLayout hiddenRow = row("Hidden apps", Ui.dimText(this, "\u203A"), "Choose which apps never show up in the bar.");
        hiddenRow.setTag("page:" + PAGE_SWITCHER_HIDDEN);
        hiddenRow.setOnClickListener(v -> {
            page = PAGE_SWITCHER_HIDDEN;
            build();
        });
        row("Allow adb access", null, "Confirm the TV's debugging prompt once so the bar can show running apps and blur.")
                .setOnClickListener(v -> tv.bestseat.launcher.switcher.AdbAccess.request(this));
        row("Open App Switcher", null, "Show the recent apps bar now.").setOnClickListener(v ->
                startActivity(new Intent(this, tv.bestseat.launcher.switcher.SwitcherActivity.class)));
    }

    /** Apps, die vor dem Start bei getrenntem VPN fragen (W127). Schalter an = fragen. */
    private void vpnAppsRows() {
        android.content.pm.PackageManager pm = getPackageManager();
        java.util.Map<String, String> apps = new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (String category : new String[] {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            Intent query = new Intent(Intent.ACTION_MAIN).addCategory(category);
            for (android.content.pm.ResolveInfo ri : pm.queryIntentActivities(query, 0)) {
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(getPackageName()) || pkg.equals(VpnGate.VPN_APP) || !seen.add(pkg)) continue;
                apps.put(ri.loadLabel(pm) + "\u0000" + pkg, pkg);
            }
        }
        java.util.Set<String> chosen = Prefs.vpnApps(this);
        for (java.util.Map.Entry<String, String> e : apps.entrySet()) {
            String pkg = e.getValue();
            String label = e.getKey().substring(0, e.getKey().indexOf('\u0000'));
            Switch sw = Ui.toggle(this);
            sw.setChecked(chosen.contains(pkg));
            LinearLayout r = row(label, sw, "Offer to connect the VPN before this app opens.");
            r.setOnClickListener(v -> {
                boolean next = !Prefs.vpnApps(this).contains(pkg);
                Prefs.setVpnApp(this, pkg, next);
                sw.setChecked(next);
            });
        }
    }

    /** Apps, die in der Leiste nie erscheinen (Blacklist). Schalter an = ausgeblendet. */
    private void switcherHiddenRows() {
        android.content.pm.PackageManager pm = getPackageManager();
        java.util.Map<String, String> apps = new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (String category : new String[] {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            Intent query = new Intent(Intent.ACTION_MAIN).addCategory(category);
            for (android.content.pm.ResolveInfo ri : pm.queryIntentActivities(query, 0)) {
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(getPackageName()) || !seen.add(pkg)) continue;
                apps.put(ri.loadLabel(pm) + "\u0000" + pkg, pkg);
            }
        }
        for (java.util.Map.Entry<String, String> e : apps.entrySet()) {
            String pkg = e.getValue();
            String label = e.getKey().substring(0, e.getKey().indexOf('\u0000'));
            Switch sw = Ui.toggle(this);
            sw.setChecked(tv.bestseat.launcher.switcher.SwitcherPrefs.isBlacklisted(this, pkg));
            LinearLayout r = row(label, sw, "Hide this app in the App Switcher.");
            r.setOnClickListener(v -> {
                boolean next = !tv.bestseat.launcher.switcher.SwitcherPrefs.isBlacklisted(this, pkg);
                tv.bestseat.launcher.switcher.SwitcherPrefs.setBlacklisted(this, pkg, next);
                sw.setChecked(next);
            });
        }
    }

    /** Kontrollzentrum: Kacheln ein-/ausblenden (OK) und verschieben (OK halten) (W12). */
    private void controlRows() {
        java.util.List<String> order = Prefs.controlOrder(this);
        java.util.Set<String> hidden = Prefs.controlHidden(this);
        for (String id : order) {
            boolean moving = id.equals(movingId);
            android.view.View value;
            if (moving) {
                value = Ui.dimText(this, "\u25B2\u25BC");
            } else {
                Switch sw = Ui.toggle(this);
                sw.setChecked(!hidden.contains(id));
                value = sw;
            }
            LinearLayout r = row(ControlItems.title(id), value, moving
                    ? "Move with up and down, then press OK."
                    : "Show or hide this tile. Hold OK to move it.");
            r.setTag("cc:" + id);
            r.setOnClickListener(v -> {
                if (movingId != null) {
                    movingId = null;
                    rebuildKeeping(v);
                    return;
                }
                if (id.equals(ControlItems.HOME) && !Prefs.settingsTile(this)
                        && !Prefs.controlHidden(this).contains(id)) {
                    Toast.makeText(this, "Needed while the Settings tile is hidden", Toast.LENGTH_SHORT).show();
                    return;
                }
                java.util.Set<String> h = Prefs.controlHidden(this);
                if (!h.contains(id) && h.size() >= ControlItems.IDS.length - 1) {
                    Toast.makeText(this, "Keep at least one tile", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (!h.remove(id)) h.add(id);
                Prefs.setControlHidden(this, h);
                rebuildKeeping(v);
            });
            r.setOnLongClickListener(v -> {
                movingId = id;
                rebuildKeeping(v);
                return true;
            });
        }
        row("Reset Control Center", null, "Bring back all tiles in their first order.").setOnClickListener(v -> {
            movingId = null;
            Prefs.resetControl(this);
            build();
        });
    }

    /** Verschieben im Kontrollzentrum-Bereich: hoch/runter tauscht, OK/Zurück beendet. */
    private boolean moveControl(int delta) {
        java.util.List<String> order = Prefs.controlOrder(this);
        int i = order.indexOf(movingId);
        int j = i + delta;
        if (i < 0 || j < 0 || j >= order.size()) return true;
        order.remove(i);
        order.add(j, movingId);
        Prefs.setControlOrder(this, order);
        build();
        focusTag("cc:" + movingId);
        return true;
    }

    @Override
    public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        int code = event.getKeyCode();
        if (movingId != null && event.getAction() == android.view.KeyEvent.ACTION_DOWN) {
            if (code == android.view.KeyEvent.KEYCODE_DPAD_UP) return moveControl(-1);
            if (code == android.view.KeyEvent.KEYCODE_DPAD_DOWN) return moveControl(1);
            if (code == android.view.KeyEvent.KEYCODE_DPAD_LEFT || code == android.view.KeyEvent.KEYCODE_DPAD_RIGHT) return true;
        }
        if (movingId == null && event.getAction() == android.view.KeyEvent.ACTION_DOWN
                && (code == android.view.KeyEvent.KEYCODE_DPAD_DOWN || code == android.view.KeyEvent.KEYCODE_DPAD_UP)) {
            // Alle Menüs laufen durch: vom letzten Eintrag nach unten zum ersten, vom ersten nach oben zum letzten (W97).
            android.view.View first = null, last = null;
            for (int i = 0; i < list.getChildCount(); i++) {
                android.view.View v = list.getChildAt(i);
                if (!v.isFocusable() || v.getVisibility() != android.view.View.VISIBLE) continue;
                if (first == null) first = v;
                last = v;
            }
            android.view.View cur = getCurrentFocus();
            if (first != null && last != null && first != last) {
                if (code == android.view.KeyEvent.KEYCODE_DPAD_DOWN && cur == last) {
                    first.requestFocus();
                    // Ganz nach oben, damit der Abschnittstitel über der ersten Zeile (z. B. „Layout“) sichtbar bleibt.
                    scroll.post(() -> scroll.scrollTo(0, 0));
                    return true;
                }
                if (code == android.view.KeyEvent.KEYCODE_DPAD_UP && cur == first) {
                    last.requestFocus();
                    return true;
                }
            }
        }
        if (code == android.view.KeyEvent.KEYCODE_BACK && (movingId != null || page != null)) {
            if (event.getAction() == android.view.KeyEvent.ACTION_UP) {
                if (movingId != null) {
                    String id = movingId;
                    movingId = null;
                    build();
                    focusTag("cc:" + id);
                } else if (PAGE_CHOICE.equals(page)) {
                    closeChoice();
                } else {
                    String was = page;
                    page = PAGE_SWITCHER_HIDDEN.equals(was) ? PAGE_SWITCHER
                            : PAGE_VPN_APPS.equals(was) ? PAGE_HOME : null;
                    build();
                    focusTag("page:" + was);
                }
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private void focusTag(String tag) {
        for (int i = 0; i < list.getChildCount(); i++) {
            if (tag.equals(list.getChildAt(i).getTag())) {
                list.getChildAt(i).requestFocus();
                return;
            }
        }
    }

    /** Schoner-Einstellungen; Thema und Uhr nur, wenn sie zur gewählten Quelle passen. */
    private void saverRows() {
        int source = Prefs.saverSource(this);
        section("Content");
        choiceRow("Screensaver", Prefs.SAVER_NAMES, () -> Prefs.saverSource(this),
                i -> Prefs.setSaverSource(this, i), "Choose what the screensaver shows.", "saver_source");

        if (source == Prefs.SAVER_PHOTOS) {
            String[] topics = new String[SaverSources.PHOTO_TOPICS.length];
            for (int i = 0; i < topics.length; i++) topics[i] = SaverSources.PHOTO_TOPICS[i][0];
            choiceRow("Photo topic", topics, () -> Prefs.saverTopic(this), i -> Prefs.setSaverTopic(this, i),
                    "Choose which kind of photos to show.", "saver_topic");
        }

        String[] delays = new String[Prefs.SAVER_DELAYS.length];
        for (int i = 0; i < delays.length; i++) delays[i] = delayText(Prefs.SAVER_DELAYS[i]);
        choiceRow("Start after", delays, () -> {
            int current = Prefs.saverDelay(this);
            for (int i = 0; i < Prefs.SAVER_DELAYS.length; i++) if (Prefs.SAVER_DELAYS[i] == current) return i;
            return 0;
        }, i -> Prefs.setSaverDelay(this, Prefs.SAVER_DELAYS[i]),
                "Choose how long the home screen waits before the screensaver starts.", "saver_delay");

        if (source != Prefs.SAVER_CLOCK || source == Prefs.SAVER_AERIALS) section("Overlays");
        if (source != Prefs.SAVER_CLOCK) {
            Switch clock = Ui.toggle(this);
            clock.setChecked(Prefs.saverClock(this));
            row("Clock on screensaver", clock, "Show the time over videos and photos.").setOnClickListener(v -> {
                Prefs.setSaverClock(this, !Prefs.saverClock(this));
                clock.setChecked(Prefs.saverClock(this));
            });
        }

        if (source == Prefs.SAVER_AERIALS) {
            Switch place = Ui.toggle(this);
            place.setChecked(Prefs.saverLocation(this));
            row("Location on screensaver", place, "Show where each aerial video was filmed.").setOnClickListener(v -> {
                Prefs.setSaverLocation(this, !Prefs.saverLocation(this));
                place.setChecked(Prefs.saverLocation(this));
            });
        }

        section("Preview");
        row("Start screensaver now", null, "Preview the screensaver now.")
                .setOnClickListener(v -> startActivity(new Intent(this, ScreensaverActivity.class)));
    }

    // ---------------------------------------------------------------- Sicherung (W34)

    private void backupRows() {
        TextView last = Ui.dimText(this, "\u2026");
        LinearLayout save = row("Back up now", last,
                "Save tiles, folders and settings to the TV's Downloads folder.");
        save.setTag("backup_save");
        save.setOnClickListener(v -> {
            last.setText("Saving\u2026");
            new Thread(() -> {
                String error = null;
                try {
                    Backup.save(this);
                } catch (Exception e) {
                    error = e.getMessage();
                }
                String result = error;
                String when = Backup.lastBackup(this);
                runOnUiThread(() -> {
                    last.setText(when != null ? when : "No backup");
                    Toast.makeText(this, result == null ? "Backup saved" : "Backup failed: " + result,
                            Toast.LENGTH_LONG).show();
                });
            }).start();
        });
        LinearLayout restore = row("Restore backup", null,
                "Replace the current layout and settings with the saved backup.");
        restore.setOnClickListener(v -> confirmRestore());
        new Thread(() -> {
            String when = Backup.lastBackup(this);
            runOnUiThread(() -> last.setText(when != null ? when : "No backup"));
        }).start();
    }

    private void confirmRestore() {
        Dialogs.Panel p = Dialogs.alert(this, "Restore backup?", "Your current layout and settings will be replaced.");
        LinearLayout cancel = Dialogs.alertButton(this, p, "Cancel", () -> { });
        Dialogs.alertButton(this, p, "Restore", () -> new Thread(() -> {
            String error = null;
            try {
                Backup.restore(this);
            } catch (Exception e) {
                error = e.getMessage();
            }
            String result = error;
            runOnUiThread(() -> {
                if (result != null) {
                    Toast.makeText(this, "Restore failed: " + result, Toast.LENGTH_LONG).show();
                    return;
                }
                Toast.makeText(this, "Backup restored", Toast.LENGTH_SHORT).show();
                // Neu starten, damit alle Seiten das wiederhergestellte Layout laden.
                Intent home = new Intent(this, HomeActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(home);
                list.postDelayed(() -> android.os.Process.killProcess(android.os.Process.myPid()), 800);
            });
        }).start());
        p.dialog.show();
        cancel.requestFocus();
    }

    private static String delayText(int minutes) {
        return minutes <= 0 ? "Never" : minutes + " min";
    }

    /** Liste neu aufbauen und den Fokus auf der Zeile mit demselben Tag lassen. */
    private void rebuildKeeping(android.view.View focused) {
        Object tag = focused.getTag();
        build();
        if (tag != null) focusTag(tag.toString());
    }

    private LinearLayout row(String label, android.view.View value, String hintText) {
        LinearLayout row = Ui.row(this);
        // tvOS-Liste: Zeilen 64 px hoch, Schritt 80 px.
        row.setMinimumHeight(Ui.dp(this, 33));
        row.setPadding(Ui.dp(this, 14), Ui.dp(this, 4), Ui.dp(this, 14), Ui.dp(this, 4));
        row.addView(Ui.text(this, label, 17, Ui.TEXT, true), Ui.fill());
        if (value != null) row.addView(value);
        android.view.View.OnFocusChangeListener base = row.getOnFocusChangeListener();
        row.setOnFocusChangeListener((v, has) -> {
            base.onFocusChange(v, has);
            if (has) hint.setText(hintText);
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Ui.dp(this, 7);
        list.addView(row, lp);
        return row;
    }
}
