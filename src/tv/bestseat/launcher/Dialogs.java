package tv.bestseat.launcher;

import android.app.Activity;
import android.app.Dialog;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Milchglas-Fenster über der Startseite. */
final class Dialogs {

    private Dialogs() {}

    static final class Panel {
        final Dialog dialog;
        final LinearLayout content;
        View box;
        /** Tasten zuerst hierher (z. B. Verschieben im Ordner); true = verbraucht. */
        android.content.DialogInterface.OnKeyListener keyHook;

        Panel(Dialog dialog, LinearLayout content) {
            this.dialog = dialog;
            this.content = content;
        }

        /** Menü neben die Kachel setzen (rechts, sonst links), wie das tvOS-Kontextmenü. */
        Panel anchor(View anchor) {
            if (anchor == null || box == null) return this;
            box.setAlpha(0f);
            dialog.setOnShowListener(d -> box.post(() -> {
                View root = (View) box.getParent();
                int[] a = new int[2];
                anchor.getLocationOnScreen(a);
                int[] r = new int[2];
                root.getLocationOnScreen(r);
                int gap = Ui.dp(anchor.getContext(), 30);
                int aw = Math.round(anchor.getWidth() * anchor.getScaleX());
                int ax = a[0] - r[0];
                int ay = a[1] - r[1];
                int x = ax + aw + gap;
                if (x + box.getWidth() > root.getWidth() - gap) x = ax - gap - box.getWidth();
                int y = Math.max(gap, Math.min(ay, root.getHeight() - gap - box.getHeight()));
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) box.getLayoutParams();
                lp.gravity = Gravity.TOP | Gravity.START;
                lp.leftMargin = Math.max(gap, x);
                lp.topMargin = y;
                lp.bottomMargin = 0;
                box.setLayoutParams(lp);
                box.setScaleX(0.92f);
                box.setScaleY(0.92f);
                box.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(Ui.ms(180)).start();
            }));
            return this;
        }
    }

    /** tvOS-Kontextmenü (langes OK): 440 px breit, Material, kleine graue Kopfzeile. */
    static Panel menu(Activity activity, String title) {
        return panel(activity, title, 220);
    }

    /** Klein gerechnetes, weichgezeichnetes Bild des Fensters hinter dem Menü (mit der leichten Abdunklung). */
    private static android.graphics.Bitmap screenBehind(Activity activity) {
        try {
            View root = activity.getWindow().getDecorView();
            if (root.getWidth() <= 0 || root.getHeight() <= 0) return null;
            android.graphics.Bitmap small = android.graphics.Bitmap.createBitmap(192, 108,
                    android.graphics.Bitmap.Config.ARGB_8888);
            android.graphics.Canvas c = new android.graphics.Canvas(small);
            c.scale(192f / root.getWidth(), 108f / root.getHeight());
            root.draw(c);
            c.drawColor(Color.argb(77, 0, 0, 0));
            return Snapshot.blur(small);
        } catch (RuntimeException e) {
            return null;
        }
    }

    static Panel panel(Activity activity, String title, int widthDp) {
        Dialog dialog = new Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar);
        FrameLayout dim = new FrameLayout(activity);
        // tvOS dunkelt hinter Menüs und Hinweisen nur leicht ab (schwarz 30 %).
        dim.setBackgroundColor(Color.argb(77, 0, 0, 0));
        dim.setClipChildren(false);

        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setClipChildren(false);
        panel.setClipToPadding(false);
        boolean narrow = widthDp <= 240;
        int p = Ui.dp(activity, narrow ? 0 : 16);
        panel.setPadding(p, 0, p, Ui.dp(activity, narrow ? 5 : 12));
        float radius = Ui.dp(activity, 15);
        android.graphics.drawable.GradientDrawable panelBg = Ui.material(activity, radius);
        // Glass: eigenes Milchglas mit Lupe statt der Fensterunschärfe des TVs, die nur grob in Blöcken
        // rechnet und durch das durchsichtige Menü als eckige Rechtecke durchschien.
        if (LiquidGlass.on && panelBg instanceof LiquidGlass.Rounded) {
            android.graphics.Bitmap shot = screenBehind(activity);
            if (shot != null) ((LiquidGlass.Rounded) panelBg).backdrop(shot, panel);
        }
        panel.setBackground(panelBg);
        panel.setElevation(Ui.dp(activity, 15));

        if (title != null) {
            // Kopfzeile wie im tvOS-Kontextmenü: Medium 23 px, halbtransparent.
            TextView t = Ui.text(activity, title, 12, Ui.TEXT, true);
            t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 11.5f);
            t.setAlpha(0.5f);
            t.setPadding(Ui.dp(activity, 12), Ui.dp(activity, 11), Ui.dp(activity, 12), Ui.dp(activity, 4));
            panel.addView(t);
        } else {
            panel.setPadding(p, Ui.dp(activity, 5), p, Ui.dp(activity, 5));
        }
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setClipChildren(false);
        content.setClipToPadding(false);
        // Die Liste wird an der Kastenkante abgeschnitten (lange Listen wie Hidden apps
        // ragten sonst darüber hinaus). Innenabstand lässt Platz für den Fokus-Zoom,
        // am oberen/unteren Rand blendet die Liste weich aus.
        ScrollView scroll = new ScrollView(activity);
        scroll.setClipChildren(true);
        scroll.setClipToPadding(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setVerticalFadingEdgeEnabled(true);
        scroll.setFadingEdgeLength(Ui.dp(activity, 28));
        int pad = Ui.dp(activity, 4);
        content.setPadding(Ui.dp(activity, narrow ? 5 : 8), pad, Ui.dp(activity, narrow ? 5 : 8), pad);
        scroll.addView(content);
        panel.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        // Der Kasten selbst schneidet ebenfalls an seiner runden Kante ab. Ausnahme: das schmale
        // Kontextmenü; dort ragt die fokussierte Zeile wie bei tvOS etwas über den Rand hinaus.
        panel.setOutlineProvider(new RoundedOutline(radius));
        panel.setClipToOutline(!narrow);
        panel.setClipChildren(!narrow);
        if (narrow) {
            scroll.setClipChildren(false);
            scroll.setClipToPadding(false);
            scroll.setVerticalFadingEdgeEnabled(false);
        }

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Ui.dp(activity, widthDp),
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        lp.topMargin = lp.bottomMargin = Ui.dp(activity, 40);
        dim.addView(panel, lp);
        dialog.setContentView(dim);
        Panel result = new Panel(dialog, content);
        result.box = panel;

        // Milchglas, wo der TV Fensterunschärfe kann; sonst bleibt die dunkle Fläche.
        Window w = dialog.getWindow();
        if (w != null && Build.VERSION.SDK_INT >= 31 && Prefs.blurEnabled(activity) && !LiquidGlass.on) {
            w.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
            WindowManager.LayoutParams a = w.getAttributes();
            a.setBlurBehindRadius(Ui.dp(activity, 30));
            w.setAttributes(a);
        }
        return result;
    }

    /**
     * Geöffneter Ordner wie tvOS (W33): unscharfes Bild der Startseite dahinter, Ordnername
     * zentriert über einem kompakten Kasten.
     */
    /**
     * Klein gerechnetes, weichgezeichnetes Abbild dessen, was hinter dem Ordnerkasten zu sehen ist:
     * Startseite mit der grauen Schicht darüber, für die Lupe im Glas.
     */
    private static android.graphics.Bitmap lensBackdrop(android.graphics.Bitmap backdrop) {
        if (backdrop == null) return null;
        try {
            android.graphics.Bitmap small = android.graphics.Bitmap.createBitmap(192, 108,
                    android.graphics.Bitmap.Config.ARGB_8888);
            android.graphics.Canvas c = new android.graphics.Canvas(small);
            c.drawBitmap(backdrop, null, new android.graphics.Rect(0, 0, 192, 108),
                    new android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG));
            c.drawColor(Color.argb(128, 130, 130, 130));
            return Snapshot.blur(small);
        } catch (RuntimeException e) {
            return null;
        }
    }

    static Panel folderPanel(Activity activity, String title, int widthDp, int heightDp,
            android.graphics.Bitmap backdrop, View origin) {
        Dialog dialog = new Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar);
        FrameLayout root = new FrameLayout(activity);
        root.setClipChildren(false);
        if (backdrop != null) {
            android.widget.ImageView bg = new android.widget.ImageView(activity);
            bg.setScaleType(android.widget.ImageView.ScaleType.FIT_XY);
            bg.setImageBitmap(backdrop);
            if (Build.VERSION.SDK_INT >= 31 && Prefs.blurEnabled(activity)) {
                float r = Ui.dp(activity, 18);
                bg.setRenderEffect(android.graphics.RenderEffect.createBlurEffect(r, r,
                        android.graphics.Shader.TileMode.CLAMP));
            }
            root.addView(bg, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
        }
        View shade = new View(activity);
        // tvOS: graue Schicht (#828282, 50 %) über dem unscharfen Startbildschirm.
        shade.setBackgroundColor(Color.argb(128, 130, 130, 130));
        root.addView(shade, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        final View backdropView = root.getChildCount() > 1 ? root.getChildAt(0) : null;

        LinearLayout column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER_HORIZONTAL);
        column.setClipChildren(false);
        // Ordnername auf einer Pille (tvOS: Bold 48, weiß 50 %, Fläche schwarz 45 %, Radius 12).
        TextView t = Ui.text(activity, title, 24, Ui.FOLDER_TITLE, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(Ui.dp(activity, 15), Ui.dp(activity, 4), Ui.dp(activity, 15), Ui.dp(activity, 5));
        t.setBackground(Ui.roundedGlass(Ui.FOLDER_PILL, Ui.dp(activity, 6), 0.8f));
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        tp.bottomMargin = Ui.dp(activity, 18);
        column.addView(t, tp);

        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(activity, 18);
        // tvOS: Kasten schwarz 45 % (hell #FEFDFF 50 %), Radius 60 px.
        int boxA = LiquidGlass.on ? (Ui.light ? 65 : 55) : (Ui.light ? 128 : 115);
        android.graphics.drawable.GradientDrawable boxBg = Ui.roundedGlass(
                Ui.light ? Color.argb(boxA, 254, 253, 255) : Color.argb(boxA, 0, 0, 0), Ui.dp(activity, 30), 1f);
        // Glass: Der Kasten zeigt das unscharfe Bild dahinter durch eine Lupe (wie die Kacheln im Kontrollzentrum).
        final android.graphics.Bitmap lensShot = LiquidGlass.on ? lensBackdrop(backdrop) : null;
        if (lensShot != null && boxBg instanceof LiquidGlass.Rounded) ((LiquidGlass.Rounded) boxBg).backdrop(lensShot, box);
        box.setBackground(boxBg);
        box.setOutlineProvider(new RoundedOutline(Ui.dp(activity, 30)));
        box.setClipToOutline(true);
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setClipChildren(false);
        content.setClipToPadding(false);
        ScrollView scroll = new ScrollView(activity);
        scroll.setVerticalScrollBarEnabled(false);
        // Ohne Ausblendkante: Sie zeichnet über eine Ebene nur so breit wie der Inhalt und schnitt
        // so den Schatten der vergrößerten Randkachel gerade ab (Steam Link im Ordner Gaming).
        scroll.setVerticalFadingEdgeEnabled(false);
        // Inhalt füllt den ganzen Kasten und wird nicht an seiner eigenen Höhe abgeschnitten: Sonst endet
        // der stärkere Fokus-Schatten (0.17.2) bei nur einer Reihe kantig unter dem App-Namen.
        scroll.setFillViewport(true);
        scroll.setClipChildren(false);
        int pad = Ui.dp(activity, 16);
        // Seitlicher Rand ganz im Inhalt: Der Inhalt reicht bis an den Kasten, der Schatten der
        // vergrößerten Randkachel hat so Platz und wird erst an der runden Kante des Kastens begrenzt.
        content.setPadding(pad + p, Ui.dp(activity, 4), pad + p, pad);
        scroll.addView(content);
        // Rand des Kastens als Innenabstand der Scrollfläche, die darin zeichnen darf: Sonst schneidet
        // sie den Schatten der vergrößerten Kachel am Rand gerade ab (Steam Link im Ordner Gaming).
        scroll.setPadding(0, p, 0, p);
        scroll.setClipToPadding(false);
        // Feste Größe (Platz für 3×3 Kacheln), unabhängig von der Zahl der Apps; mehr scrollt.
        box.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                Ui.dp(activity, heightDp) + 2 * p));
        column.addView(box, new LinearLayout.LayoutParams(Ui.dp(activity, widthDp),
                ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        lp.topMargin = lp.bottomMargin = Ui.dp(activity, 40);
        root.addView(column, lp);
        dialog.setContentView(root);

        // Öffnen: Kasten wächst aus der Ordnerkachel heraus, Hintergrund blendet ein.
        final boolean reduce = Prefs.reduceMotion(activity);
        final float small = reduce ? 1f : 0.35f;
        column.setAlpha(0f);
        column.setScaleX(small);
        column.setScaleY(small);
        if (backdropView != null) backdropView.setAlpha(0f);
        shade.setAlpha(0f);
        dialog.setOnShowListener(d -> column.post(() -> {
            if (origin != null) {
                int[] o = new int[2];
                int[] c = new int[2];
                origin.getLocationOnScreen(o);
                column.getLocationOnScreen(c);
                // getLocationOnScreen ist schon skaliert: auf die unskalierte linke/obere Kante zurückrechnen.
                float sx = column.getScaleX();
                column.setPivotX(o[0] + origin.getWidth() / 2f - (c[0] - column.getPivotX() * (1 - sx)));
                column.setPivotY(o[1] + origin.getHeight() / 2f - (c[1] - column.getPivotY() * (1 - sx)));
            }
            android.view.animation.DecelerateInterpolator ease = new android.view.animation.DecelerateInterpolator(2f);
            column.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(Ui.ms(320)).setInterpolator(ease)
                    .setUpdateListener(lensShot != null ? a -> box.invalidate() : null).start();
            if (backdropView != null) backdropView.animate().alpha(1f).setDuration(Ui.ms(260)).start();
            shade.animate().alpha(1f).setDuration(Ui.ms(260)).start();
        }));
        // Schließen mit Zurück: genau umgekehrt (Wunsch des Nutzers). Kasten schrumpft in die
        // Ordnerkachel zurück und blendet aus, Hintergrund blendet zum Schluss mit aus.
        final boolean[] closing = {false};
        final Panel result = new Panel(dialog, content);
        dialog.setOnKeyListener((d, code, ev) -> {
            if (result.keyHook != null && result.keyHook.onKey(d, code, ev)) return true;
            if (code != android.view.KeyEvent.KEYCODE_BACK) return false;
            if (ev.getAction() != android.view.KeyEvent.ACTION_UP || closing[0]) return true;
            closing[0] = true;
            android.view.animation.AccelerateInterpolator ease = new android.view.animation.AccelerateInterpolator(2f);
            long dur = Ui.ms(320);
            column.animate().cancel();
            column.animate().alpha(0f).scaleX(small).scaleY(small).setDuration(dur).setInterpolator(ease)
                    .withEndAction(() -> {
                        if (dialog.isShowing()) dialog.dismiss();
                    }).start();
            long fade = Ui.ms(260);
            if (backdropView != null) {
                backdropView.animate().cancel();
                backdropView.animate().alpha(0f).setStartDelay(dur - fade).setDuration(fade).start();
            }
            shade.animate().cancel();
            shade.animate().alpha(0f).setStartDelay(dur - fade).setDuration(fade).start();
            return true;
        });
        return result;
    }

    /** tvOS-Hinweisfenster: 504 px breit, Radius 48, Titel Bold 31, Text Medium 25, Knöpfe 440×66. */
    static Panel alert(Activity activity, String title, String message) {
        return alert(activity, title, message, 252);
    }

    /** Hinweisfenster in anderer Breite (z. B. mit Eingabefeld und QR-Code). */
    static Panel alert(Activity activity, String title, String message, int widthDp) {
        Panel p = panel(activity, null, widthDp);
        LinearLayout box = (LinearLayout) p.box;
        box.setBackground(Ui.material(activity, Ui.dp(activity, 24)));
        box.setOutlineProvider(new RoundedOutline(Ui.dp(activity, 24)));
        box.setPadding(Ui.dp(activity, 16), Ui.dp(activity, 24), Ui.dp(activity, 16), Ui.dp(activity, 16));
        TextView t = Ui.text(activity, title, 16, Ui.TEXT, false);
        t.setTypeface(Fonts.get(t.getContext(), Fonts.BOLD));
        t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15.5f);
        t.setGravity(Gravity.CENTER);
        t.setMaxLines(2);
        t.setPadding(Ui.dp(activity, 8), 0, Ui.dp(activity, 8), Ui.dp(activity, message == null ? 12 : 8));
        box.addView(t, 0, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        if (message != null) {
            TextView m = Ui.text(activity, message, 13, Ui.TEXT, false);
            m.setTypeface(Fonts.get(m.getContext(), Fonts.MEDIUM));
            m.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12.5f);
            m.setAlpha(Ui.light ? 0.6f : 0.5f);
            m.setGravity(Gravity.CENTER);
            m.setMaxLines(6);
            m.setSingleLine(false);
            m.setPadding(Ui.dp(activity, 8), 0, Ui.dp(activity, 8), Ui.dp(activity, 12));
            box.addView(m, 1, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        return p;
    }

    /** Knopf im Hinweisfenster: ohne Fokus grau, mit Fokus weiß mit dunkler Schrift, etwas größer. */
    static LinearLayout alertButton(Activity activity, Panel panel, String label, Runnable action) {
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER);
        row.setFocusable(true);
        row.setClickable(true);
        row.setMinimumHeight(Ui.dp(activity, 33));
        TextView text = Ui.text(activity, label, 15, Ui.TEXT, true);
        text.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14.5f);
        row.addView(text);
        float r = Ui.dp(activity, 8);
        row.setBackground(Ui.rounded(Ui.BUTTON, r));
        row.setOnFocusChangeListener((v, has) -> {
            v.setBackground(Ui.rounded(has ? Ui.MENU_FOCUSED : Ui.BUTTON, r));
            text.setTextColor(has ? Ui.TEXT_ON_FOCUS : Ui.TEXT);
            v.animate().scaleX(has ? 1.045f : 1f).scaleY(has ? 1.045f : 1f)
                    .translationZ(has ? Ui.dp(activity, 6) : 0).setDuration(Ui.ms(140)).start();
        });
        row.setOnClickListener(v -> {
            panel.dialog.dismiss();
            action.run();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = panel.content.getChildCount() > 0 ? Ui.dp(activity, 8) : 0;
        panel.content.addView(row, lp);
        return row;
    }

    interface TextCallback {
        void done(String text);
    }

    /** Namen eingeben (Ordner, Profil) im tvOS-Hinweisfenster. Liefert das Fenster. */
    static Panel askText(Activity activity, String title, String preset, TextCallback callback) {
        Panel p = alert(activity, title, null);
        android.widget.EditText input = new android.widget.EditText(activity);
        input.setSingleLine(true);
        input.setText(preset);
        input.selectAll();
        input.setTextColor(Ui.TEXT);
        input.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14.5f);
        input.setPadding(Ui.dp(activity, 12), Ui.dp(activity, 8), Ui.dp(activity, 12), Ui.dp(activity, 8));
        input.setBackground(Ui.rounded(Ui.BUTTON, Ui.dp(activity, 8)));
        input.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        ip.bottomMargin = Ui.dp(activity, 12);
        p.content.addView(input, ip);
        Runnable save = () -> {
            String text = input.getText().toString().trim();
            callback.done(text.isEmpty() ? preset : text);
        };
        alertButton(activity, p, "Save", save);
        alertButton(activity, p, "Cancel", () -> { });
        input.setOnEditorActionListener((v, actionId, e) -> {
            p.dialog.dismiss();
            save.run();
            return true;
        });
        p.dialog.show();
        input.requestFocus();
        return p;
    }

    /** Menüeintrag wie tvOS: 70 px hoch, Medium 25 px; mit Fokus weiße Fläche und dunkle Schrift. */
    static LinearLayout actionRow(Activity activity, Panel panel, String label, Runnable action) {
        return actionRow(activity, panel, label, null, false, action);
    }

    /** Zeile wie im tvOS-Kontextmenü: Symbol rechts (z. B. ⊕), destructive = rote Schrift. */
    static LinearLayout actionRow(Activity activity, Panel panel, String label, String symbol, boolean destructive,
            Runnable action) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setFocusable(true);
        row.setClickable(true);
        row.setMinimumHeight(Ui.dp(activity, 35));
        row.setPadding(Ui.dp(activity, 8), 0, Ui.dp(activity, 8), 0);
        TextView text = Ui.text(activity, label, 13, Ui.TEXT, true);
        text.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12.5f);
        row.addView(text, Ui.fill());
        int red = Ui.light ? Color.rgb(215, 0, 21) : Color.rgb(255, 69, 58);
        int normal = destructive ? red : Ui.TEXT;
        int focused = destructive ? red : Ui.TEXT_ON_FOCUS;
        text.setTextColor(normal);
        TextView sym = null;
        if (symbol != null) {
            sym = Ui.text(activity, symbol, 15, normal, false);
            sym.setPadding(Ui.dp(activity, 8), 0, Ui.dp(activity, 2), 0);
            row.addView(sym);
        }
        final TextView symView = sym;
        float r = Ui.dp(activity, 8.5f);
        row.setOnFocusChangeListener((v, has) -> {
            v.setBackground(has ? Ui.rounded(Ui.MENU_FOCUSED, r) : null);
            text.setTextColor(has ? focused : normal);
            if (symView != null) symView.setTextColor(has ? focused : normal);
            // tvOS: Die fokussierte Zeile ist so breit wie das Menü plus ein Stück darüber hinaus.
            v.animate().scaleX(has ? 1.08f : 1f).scaleY(has ? 1.1f : 1f)
                    .translationZ(has ? Ui.dp(activity, 8) : 0).setDuration(Ui.ms(140)).start();
        });
        row.setOnClickListener(v -> {
            panel.dialog.dismiss();
            action.run();
        });
        panel.content.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return row;
    }

    @SuppressWarnings("deprecation") // versionCode: getLongVersionCode gibt es erst ab Android 9.
    static void about(Activity activity) {
        Panel p = panel(activity, "Best Seat", 460);
        String version;
        try {
            PackageInfo info = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
            version = "Version " + info.versionName + " (build " + info.versionCode + ")";
        } catch (PackageManager.NameNotFoundException e) {
            version = "Version unknown";
        }
        p.content.addView(Ui.text(activity, version, 15, Ui.TEXT_DIM, false));
        section(activity, p.content, "Created by", "Börni (burni2001)");
        section(activity, p.content, "Credits", "Built with Claude Code by Anthropic",
                "Features modelled on AT4K Launcher", "Design inspired by tvOS");
        section(activity, p.content, "Package", activity.getPackageName());
        View close = button(activity, p, "Close", () -> { });
        p.dialog.show();
        close.requestFocus();
    }

    /**
     * Knopf wie in tvOS-Dialogen: mittig, abgerundet, Text mittig; ohne Fokus dezent grau,
     * mit Fokus weiß, leicht größer und mit Schatten.
     */
    static View button(Activity activity, Panel panel, String label, Runnable action) {
        TextView b = Ui.text(activity, label, 15, Ui.TEXT, true);
        b.setTypeface(Fonts.get(b.getContext(), Fonts.MEDIUM));
        b.setGravity(Gravity.CENTER);
        b.setFocusable(true);
        b.setClickable(true);
        float r = Ui.dp(activity, 12);
        b.setBackground(Ui.rounded(Ui.ROW, r));
        b.setOnFocusChangeListener((v, has) -> {
            v.setBackground(Ui.rounded(has ? Ui.ROW_FOCUSED : Ui.ROW, r));
            b.setTextColor(has ? Ui.TEXT_ON_FOCUS : Ui.TEXT);
            v.animate().scaleX(has ? 1.06f : 1f).scaleY(has ? 1.06f : 1f)
                    .translationZ(has ? Ui.dp(activity, 10) : 0).setDuration(Ui.ms(140)).start();
        });
        b.setOnClickListener(v -> {
            panel.dialog.dismiss();
            action.run();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(activity, 220), Ui.dp(activity, 42));
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        lp.topMargin = Ui.dp(activity, 26);
        // Platz für den Schatten des fokussierten Knopfs; die Liste schneidet an ihrer Kante ab.
        lp.bottomMargin = Ui.dp(activity, 32);
        panel.content.addView(b, lp);
        return b;
    }

    private static void section(Activity activity, LinearLayout panel, String title, String... lines) {
        TextView caption = Ui.caption(activity, title);
        caption.setPadding(0, Ui.dp(activity, 16), 0, Ui.dp(activity, 4));
        panel.addView(caption);
        for (String line : lines) {
            panel.addView(Ui.text(activity, line, 16, Ui.TEXT, false));
        }
    }
}
