package tv.bestseat.launcher;

import android.app.Activity;
import android.database.Cursor;
import android.media.tv.TvContract;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Map;
import java.util.TreeMap;

/**
 * Zeigt, welche Inhalte der Launcher aus dem TV-Provider lesen darf: Kanäle,
 * Vorschauprogramme (Top Shelf) und Watch Next, gezählt je App. Auch im Log
 * (adb logcat -s Launcher.Diag).
 */
public final class DiagnosticsActivity extends Activity {

    private static final String TAG = "Launcher.Diag";
    private LinearLayout list;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.apply(this);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Ui.BACKGROUND);
        scroll.setClipChildren(false);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setClipChildren(false);
        list.setPadding(Ui.dp(this, 160), Ui.dp(this, 40), Ui.dp(this, 160), Ui.dp(this, 40));
        scroll.addView(list);
        setContentView(scroll);

        TextView title = Ui.text(this, "Content Shelf diagnostics", 30, Ui.TEXT, true);
        title.setPadding(0, 0, 0, Ui.dp(this, 16));
        list.addView(title);

        section("Channels", TvContract.Channels.CONTENT_URI, TvContract.Channels.COLUMN_PACKAGE_NAME);
        section("Preview programs (Content Shelf)", TvContract.PreviewPrograms.CONTENT_URI,
                TvContract.PreviewPrograms.COLUMN_PACKAGE_NAME);
        section("Watch Next", TvContract.WatchNextPrograms.CONTENT_URI,
                TvContract.WatchNextPrograms.COLUMN_PACKAGE_NAME);
        if (list.getChildAt(1) != null) list.getChildAt(1).requestFocus();
    }

    private void section(String name, Uri uri, String column) {
        TextView caption = Ui.caption(this, name);
        caption.setPadding(Ui.dp(this, 4), Ui.dp(this, 18), 0, Ui.dp(this, 6));
        list.addView(caption);
        Map<String, Integer> counts = new TreeMap<>();
        String error = null;
        try (Cursor c = getContentResolver().query(uri, new String[] {column}, null, null, null)) {
            if (c == null) {
                error = "No access (null cursor)";
            } else {
                while (c.moveToNext()) {
                    String pkg = c.getString(0);
                    if (pkg == null) pkg = "?";
                    Integer n = counts.get(pkg);
                    counts.put(pkg, n == null ? 1 : n + 1);
                }
            }
        } catch (RuntimeException e) {
            error = e.getClass().getSimpleName() + ": " + e.getMessage();
        }
        if (error != null) {
            addLine(error);
            Log.i(TAG, name + ": " + error);
            return;
        }
        if (counts.isEmpty()) {
            addLine("Nothing readable");
            Log.i(TAG, name + ": nothing readable");
            return;
        }
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            addLine(label(e.getKey()) + "  ·  " + e.getValue());
            Log.i(TAG, name + ": " + e.getKey() + " = " + e.getValue());
        }
    }

    private String label(String pkg) {
        try {
            return getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(pkg, 0)) + " (" + pkg + ")";
        } catch (Exception e) {
            return pkg;
        }
    }

    private void addLine(String text) {
        LinearLayout row = Ui.row(this);
        row.setMinimumHeight(Ui.dp(this, 40));
        row.addView(Ui.text(this, text, 15, Ui.TEXT, false), Ui.fill());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Ui.dp(this, 6);
        list.addView(row, lp);
    }
}
