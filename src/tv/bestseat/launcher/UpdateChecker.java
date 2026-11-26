package tv.bestseat.launcher;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Looks for a newer Best Seat release on GitHub, shows its changelog and installs it.
 * Install: silently through Best Seat's own adb access; without adb access through the Android installer.
 * Only a release with a higher version than the installed build is offered, so a newer own build is never replaced.
 */
final class UpdateChecker {

    private static final String TAG = "Launcher";
    private static final String RELEASES = "https://api.github.com/repos/burni2001/best-seat/releases?per_page=10";
    private static final String ACTION_INSTALL = "tv.bestseat.launcher.UPDATE_INSTALL";
    private static final long[] INTERVAL_MS = {0L, 86400000L, 7 * 86400000L, 30 * 86400000L};
    static final String[] INTERVAL_NAMES = {"Never", "Daily", "Weekly", "Monthly"};

    interface Done {
        void run(boolean ok);
    }

    interface Progress {
        void run(int percent);
    }

    private static volatile boolean checking;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private UpdateChecker() {}

    /** Version name of the installed build, e.g. "0.27.32". */
    static String installedVersion(Context c) {
        try {
            return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "0";
        }
    }

    /** True if version a is higher than b (numeric parts, a leading "v" is ignored). */
    static boolean newer(String a, String b) {
        String[] x = a.replaceFirst("^v", "").split("[.\\-]");
        String[] y = b.replaceFirst("^v", "").split("[.\\-]");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length ? num(x[i]) : 0;
            int q = i < y.length ? num(y[i]) : 0;
            if (p != q) return p > q;
        }
        return false;
    }

    private static int num(String s) {
        try {
            return Integer.parseInt(s.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** A newer release than the installed build was found by the last check. */
    static boolean available(Context c) {
        String v = Prefs.updateVersion(c);
        return !v.isEmpty() && newer(v, installedVersion(c));
    }

    /** Called from the home screen: checks when the chosen interval has passed. */
    static void checkIfDue(Context c, Runnable changed) {
        long every = INTERVAL_MS[Math.max(0, Math.min(INTERVAL_MS.length - 1, Prefs.updateInterval(c)))];
        if (every == 0L) return;
        if (System.currentTimeMillis() - Prefs.updateLastCheck(c) < every) return;
        check(c, ok -> {
            if (changed != null) changed.run();
        });
    }

    /** Asks GitHub for the newest releases and stores the result; done runs on the main thread. */
    static void check(Context c, Done done) {
        if (checking) return;
        checking = true;
        Context app = c.getApplicationContext();
        new Thread(() -> {
            boolean ok = false;
            try {
                HttpURLConnection con = (HttpURLConnection) new URL(RELEASES).openConnection();
                con.setConnectTimeout(10000);
                con.setReadTimeout(15000);
                con.setRequestProperty("Accept", "application/vnd.github+json");
                con.setRequestProperty("User-Agent", "BestSeat");
                if (con.getResponseCode() == 200) {
                    String body = read(con.getInputStream());
                    JSONArray all = new JSONArray(body);
                    JSONArray keep = new JSONArray();
                    for (int i = 0; i < all.length(); i++) {
                        JSONObject r = all.getJSONObject(i);
                        if (r.optBoolean("draft") || r.optBoolean("prerelease")) continue;
                        String apk = "";
                        JSONArray assets = r.optJSONArray("assets");
                        for (int k = 0; assets != null && k < assets.length(); k++) {
                            String n = assets.getJSONObject(k).optString("name");
                            if (n.endsWith(".apk")) apk = assets.getJSONObject(k).optString("browser_download_url");
                        }
                        JSONObject o = new JSONObject();
                        o.put("tag", r.optString("tag_name"));
                        o.put("notes", r.optString("body"));
                        o.put("apk", apk);
                        keep.put(o);
                    }
                    Prefs.setUpdateReleases(app, keep.toString());
                    String version = "", url = "";
                    for (int i = 0; i < keep.length(); i++) {
                        JSONObject o = keep.getJSONObject(i);
                        if (!o.getString("apk").isEmpty() && (version.isEmpty() || newer(o.getString("tag"), version))) {
                            version = o.getString("tag").replaceFirst("^v", "");
                            url = o.getString("apk");
                        }
                    }
                    Prefs.setUpdateVersion(app, version);
                    Prefs.setUpdateUrl(app, url);
                    Prefs.setUpdateLastCheck(app, System.currentTimeMillis());
                    ok = true;
                }
                con.disconnect();
            } catch (Exception e) {
                Log.w(TAG, "update check failed", e);
            }
            checking = false;
            boolean result = ok;
            MAIN.post(() -> done.run(result));
        }, "update-check").start();
    }

    static boolean checking() {
        return checking;
    }

    private static String read(InputStream in) throws java.io.IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        in.close();
        return out.toString("UTF-8");
    }

    private static File apkFile(Context c) {
        File dir = c.getExternalCacheDir();
        if (dir == null) dir = c.getCacheDir();
        return new File(dir, "update.apk");
    }

    /** Removes a downloaded update file, e.g. after the update was installed. */
    static void cleanUp(Context c) {
        File f = apkFile(c);
        if (f.exists() && !f.delete()) Log.w(TAG, "could not delete " + f);
    }

    /**
     * Downloads the newest release, checks it and installs it. progress runs on the main thread, done too with
     * the result of the download and check; the install itself replaces the app and ends this process.
     */
    static void update(Activity a, Progress progress, Done done) {
        String url = Prefs.updateUrl(a);
        if (url.isEmpty() || !available(a)) {
            done.run(false);
            return;
        }
        Context app = a.getApplicationContext();
        new Thread(() -> {
            File f = apkFile(app);
            boolean ok = false;
            try {
                HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
                con.setConnectTimeout(10000);
                con.setReadTimeout(30000);
                con.setRequestProperty("User-Agent", "BestSeat");
                con.setInstanceFollowRedirects(true);
                if (con.getResponseCode() == 200) {
                    long total = con.getContentLengthLong();
                    long got = 0;
                    int last = -1;
                    try (InputStream in = con.getInputStream(); OutputStream out = new FileOutputStream(f)) {
                        byte[] buf = new byte[65536];
                        int n;
                        while ((n = in.read(buf)) > 0) {
                            if (a.isDestroyed()) throw new java.io.IOException("cancelled");
                            out.write(buf, 0, n);
                            got += n;
                            int pct = total > 0 ? (int) (got * 100 / total) : 0;
                            if (pct != last) {
                                last = pct;
                                MAIN.post(() -> progress.run(pct));
                            }
                        }
                    }
                    ok = verify(app, f);
                }
                con.disconnect();
            } catch (Exception e) {
                Log.w(TAG, "update download failed", e);
            }
            if (!ok) f.delete();
            boolean result = ok;
            MAIN.post(() -> {
                done.run(result);
                if (result && !a.isDestroyed()) install(a, f);
            });
        }, "update-download").start();
    }

    /** The file is a Best Seat APK with a higher version code than the installed build. */
    private static boolean verify(Context c, File f) {
        android.content.pm.PackageInfo info = c.getPackageManager().getPackageArchiveInfo(f.getPath(), 0);
        if (info == null || !c.getPackageName().equals(info.packageName)) return false;
        try {
            long mine = c.getPackageManager().getPackageInfo(c.getPackageName(), 0).getLongVersionCode();
            return info.getLongVersionCode() > mine;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /** Silently through adb if allowed, otherwise through the Android installer with its confirmation. */
    private static void install(Activity a, File f) {
        Context app = a.getApplicationContext();
        if (Permissions.granted(a, Permissions.ADB)) {
            new Thread(() -> {
                try {
                    // The package manager cannot read the app's folder on the shared storage (SELinux: "fuse"),
                    // so the shell copies the file to /data/local/tmp first. When the new build replaces this one the
                    // process ends and nothing comes back; any answer that does come back means the install failed.
                    String tmp = "/data/local/tmp/bestseat-update.apk";
                    String out = tv.bestseat.launcher.switcher.AdbClient.shell(app, "cp " + f.getPath() + " " + tmp
                            + " && pm install -r --user 0 " + tmp + "; rm -f " + tmp, 120000);
                    if (out != null && out.contains("Success")) return;
                    Log.w(TAG, "adb install did not replace the app: " + out);
                    MAIN.post(() -> installWithSystem(a, f));
                } catch (Exception e) {
                    Log.w(TAG, "adb install failed, using the installer", e);
                    MAIN.post(() -> installWithSystem(a, f));
                }
            }, "update-install").start();
        } else {
            installWithSystem(a, f);
        }
    }

    private static void installWithSystem(Activity a, File f) {
        try {
            PackageInstaller installer = a.getPackageManager().getPackageInstaller();
            PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                    PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            int id = installer.createSession(params);
            try (PackageInstaller.Session s = installer.openSession(id);
                    InputStream in = new FileInputStream(f);
                    OutputStream out = s.openWrite("update", 0, f.length())) {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                s.fsync(out);
                out.close();
                BroadcastReceiver receiver = new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1);
                        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                            Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
                            if (confirm != null) a.startActivity(confirm);
                        } else {
                            try {
                                a.getApplicationContext().unregisterReceiver(this);
                            } catch (RuntimeException ignored) {
                                // already gone
                            }
                            if (status != PackageInstaller.STATUS_SUCCESS) {
                                android.widget.Toast.makeText(a, "Update not installed", android.widget.Toast.LENGTH_LONG).show();
                            }
                        }
                    }
                };
                a.getApplicationContext().registerReceiver(receiver, new IntentFilter(ACTION_INSTALL),
                        Context.RECEIVER_NOT_EXPORTED);
                Intent intent = new Intent(ACTION_INSTALL).setPackage(a.getPackageName());
                PendingIntent pi = PendingIntent.getBroadcast(a, id, intent,
                        PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
                s.commit(pi.getIntentSender());
            }
        } catch (Exception e) {
            Log.w(TAG, "installer failed", e);
            android.widget.Toast.makeText(a, "Update could not be installed", android.widget.Toast.LENGTH_LONG).show();
        }
    }
}
