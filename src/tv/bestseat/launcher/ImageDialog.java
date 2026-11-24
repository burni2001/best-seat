package tv.bestseat.launcher;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.util.function.Consumer;

/**
 * Fenster „Change Image“ (W126) für Home Screen, Ordner und All Apps: Bild-Adresse eintippen oder
 * per QR-Code vom Handy hochladen. Aufbau wie die übrigen Eingabefenster (Titel, Text, Feld, Knöpfe).
 */
final class ImageDialog {

    /** Offenes Fenster; die Home-Taste schließt es, auch wenn es in All Apps offen ist. */
    private static java.lang.ref.WeakReference<android.app.Dialog> current;

    private ImageDialog() {}

    static void dismissOpen() {
        android.app.Dialog d = current == null ? null : current.get();
        current = null;
        if (d != null && d.isShowing()) {
            try {
                d.dismiss();
            } catch (RuntimeException ignored) {
                // Fenster schon weg
            }
        }
    }

    /**
     * Fenster zeigen. show zeigt das Fenster (der Home Screen merkt es sich dabei), changed läuft nach
     * einem neuen oder entfernten Bild, dismissed beim Schließen.
     */
    static void open(Activity a, String pkg, String label, Runnable changed, Consumer<Dialogs.Panel> show,
            Runnable dismissed) {
        final Dialogs.Panel[] holder = new Dialogs.Panel[1];
        ImageServer server = new ImageServer(a, pkg, label, () -> {
            if (holder[0] != null && holder[0].dialog.isShowing()) holder[0].dialog.dismiss();
            Toast.makeText(a, "New image for " + label, Toast.LENGTH_SHORT).show();
            changed.run();
        });
        String url = server.start();
        Dialogs.Panel p = Dialogs.alert(a, "Change Image", url != null
                ? "Type the address of an image, or scan the code to upload a picture from your phone."
                : "Type the address of an image.", 300);
        holder[0] = p;
        if (url != null) {
            ImageView qr = new ImageView(a);
            qr.setImageBitmap(qrBitmap(url));
            qr.setBackground(Ui.rounded(Color.WHITE, Ui.dp(a, 8)));
            int pad = Ui.dp(a, 4);
            qr.setPadding(pad, pad, pad, pad);
            LinearLayout.LayoutParams qp = new LinearLayout.LayoutParams(Ui.dp(a, 132), Ui.dp(a, 132));
            qp.gravity = Gravity.CENTER_HORIZONTAL;
            qp.bottomMargin = Ui.dp(a, 14);
            p.content.addView(qr, qp);
        }
        EditText input = new EditText(a);
        input.setSingleLine(true);
        input.setHint("https://…");
        input.setHintTextColor(Ui.TEXT_DIM);
        input.setTextColor(Ui.TEXT);
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f);
        input.setTypeface(Fonts.get(a, Fonts.REGULAR));
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setImeOptions(EditorInfo.IME_ACTION_GO);
        input.setPadding(Ui.dp(a, 12), Ui.dp(a, 8), Ui.dp(a, 12), Ui.dp(a, 8));
        input.setBackground(Ui.rounded(Ui.BUTTON, Ui.dp(a, 8)));
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        ip.bottomMargin = Ui.dp(a, 12);
        p.content.addView(input, ip);

        Runnable useAddress = () -> loadFrom(a, pkg, label, input.getText().toString().trim(), changed);
        Dialogs.alertButton(a, p, "Use Address", useAddress);
        input.setOnEditorActionListener((v, actionId, e) -> {
            p.dialog.dismiss();
            useAddress.run();
            return true;
        });
        if (TileImages.has(a, pkg)) {
            Dialogs.alertButton(a, p, "Use App Image", () -> {
                TileImages.remove(a, pkg);
                changed.run();
            });
        }
        Dialogs.alertButton(a, p, "Cancel", () -> { });
        show.accept(p);
        current = new java.lang.ref.WeakReference<>(p.dialog);
        // Eigener Schließen-Hörer nach show(); der Server endet mit dem Fenster.
        p.dialog.setOnDismissListener(d -> {
            server.stop();
            dismissed.run();
        });
        input.requestFocus();
    }

    /** Eingegebene Bild-URL im Hintergrund laden und als Kachelbild speichern. */
    private static void loadFrom(Activity a, String pkg, String label, String address, Runnable changed) {
        if (address.isEmpty()) return;
        if (!address.startsWith("http://") && !address.startsWith("https://")) address = "https://" + address;
        final String from = address;
        Toast.makeText(a, "Loading image…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            byte[] data = ImageServer.download(from);
            boolean ok = data != null && TileImages.save(a, pkg, data);
            a.runOnUiThread(() -> {
                if (a.isDestroyed()) return;
                if (!ok) {
                    Toast.makeText(a, data == null ? "Could not load that address" : "That is not an image",
                            Toast.LENGTH_LONG).show();
                    return;
                }
                Toast.makeText(a, "New image for " + label, Toast.LENGTH_SHORT).show();
                changed.run();
            });
        }, "tile-image").start();
    }

    /** QR-Code als Bild: schwarze Module auf Weiß mit zwei Modulen Ruhezone (dazu der weiße Rahmen). */
    private static Bitmap qrBitmap(String text) {
        boolean[][] m = QrCode.encode(text);
        int n = m.length;
        int quiet = 2;
        int scale = 8;
        int w = (n + 2 * quiet) * scale;
        int[] px = new int[w * w];
        for (int y = 0; y < w; y++) {
            for (int x = 0; x < w; x++) {
                int mx = x / scale - quiet;
                int my = y / scale - quiet;
                boolean dark = mx >= 0 && my >= 0 && mx < n && my < n && m[my][mx];
                px[y * w + x] = dark ? Color.BLACK : Color.WHITE;
            }
        }
        return Bitmap.createBitmap(px, w, w, Bitmap.Config.ARGB_8888);
    }
}
