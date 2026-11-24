package tv.bestseat.launcher;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.TransitionDrawable;
import android.os.Build;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Top Shelf der Startseite: großes Banner oder die Inhaltskarten der fokussierten App, dazu der
 * Hintergrund dahinter (Bild weichgezeichnet, passende Farbe oder ganzflächige Bannerfarbe).
 */
final class TopShelf {

    private static final String TAG = "Launcher.Home";
    // Wie der tvOS-Screenshot des Nutzers: Leiste ab ~785 px, nächste Reihe ab ~1056 px (~24 px sichtbar).
    static final int SHELF_DP = 378;
    private static final float BACKDROP_BLUR = 9f;
    private static final float BACKDROP_BLUR_FROZEN = 26f;
    private static final float BACKDROP_ALPHA = 1f;

    private final HomeActivity home;
    private final HomeBanners banners;
    private final ExecutorService shelfWorker = Executors.newSingleThreadExecutor();

    private FrameLayout shelf;
    private ImageView backdrop;
    private ImageView hero;
    private FrameLayout.LayoutParams heroLp;
    /** Ganzflächige Farbe wie bei tvOS, wenn eine App ohne Inhalte ein einfarbiges Banner hat. */
    private View flatBg;
    private float flatTarget;
    private TextView heroTitle;
    private TextView heroSub;
    private FrameLayout heroGroup;
    private LinearLayout contentGroup;
    private TextView shelfApp;
    private TextView shelfSection;
    private LinearLayout cardRow;
    private CardScrollView cardScroll;
    private TextView cardTitle;
    private String shelfKey;
    private Runnable pendingShelfLoad;

    /** Fokus unter der Favoriten-Reihe: Top Shelf eingefroren, Hintergrund unscharf oder schlicht (W17). */
    private boolean frozen;
    private float backdropBlur = BACKDROP_BLUR;
    private ValueAnimator freezeAnim;
    private int builtBackground = -1;

    private Bitmap backdropBmp;
    private Object backdropSource;
    /** Karte, von der man zuletzt in die Favoritenleiste heruntergekommen ist (-1 = keine). */
    private int rememberedCard = -1;

    private ValueAnimator flatColorAnim;
    /** Banner auf der Farbfläche wartet, bis die Fläche ganz deckt (sonst sieht man seine Kanten). */
    private boolean heroPending;

    TopShelf(HomeActivity home, HomeBanners banners) {
        this.home = home;
        this.banners = banners;
    }

    void destroy() {
        shelfWorker.shutdownNow();
    }

    // ---------------------------------------------------------------- Aufbau

    /** Hintergrund ganz unten in root: Bild, Abdunklung und ganzflächige Farbe. */
    void addBackground(FrameLayout root) {
        // Hintergrund: Banner der fokussierten App, klein, unscharf und hochskaliert (spart GPU).
        backdrop = new ImageView(home);
        backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
        int screenW = home.getResources().getDisplayMetrics().widthPixels;
        int smallW = 320;
        int smallH = 180;
        backdrop.setPivotX(0);
        backdrop.setPivotY(0);
        float scale = screenW / (float) smallW;
        backdrop.setScaleX(scale);
        backdrop.setScaleY(scale);
        backdrop.setAlpha(BACKDROP_ALPHA);
        setBackdropBlur(BACKDROP_BLUR);
        root.addView(backdrop, new FrameLayout.LayoutParams(smallW, smallH));

        View shade = new View(home);
        // tvOS: Der Hintergrund füllt immer den ganzen Bildschirm (unten nur stärker unscharf),
        // dunkel abgedunkelt, hell aufgehellt; kein Verlauf mehr in eine einfarbige Fläche.
        shade.setBackgroundColor(Ui.light ? Color.argb(115, 255, 255, 255) : Color.argb(105, 0, 0, 0));
        root.addView(shade, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        flatBg = new View(home);
        flatBg.setAlpha(0f);
        root.addView(flatBg, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
    }

    /** Die Fläche des Top Shelf oben auf der Seite. */
    View createShelf() {
        // Top Shelf: entweder großes Banner (heroGroup) oder Inhalte der App (contentGroup).
        shelf = new FrameLayout(home);
        shelf.setClipChildren(false);
        heroGroup = new FrameLayout(home);
        heroGroup.setClipChildren(false);
        shelf.addView(heroGroup, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        hero = new ImageView(home);
        hero.setScaleType(ImageView.ScaleType.CENTER_CROP);
        hero.setOutlineProvider(new RoundedOutline(Ui.dp(home, 12)));
        hero.setClipToOutline(true);
        hero.setElevation(Ui.dp(home, 20));
        FrameLayout.LayoutParams hp = new FrameLayout.LayoutParams(Ui.dp(home, 423), Ui.dp(home, 238),
                Gravity.END | Gravity.TOP);
        // tvOS 2-up-Karte: 846x476 px, Radius 24, Oberkante y 170.
        hp.topMargin = Ui.dp(home, 85);
        hp.rightMargin = Ui.dp(home, HomeActivity.SIDE_DP);
        heroLp = hp;
        heroGroup.addView(hero, hp);

        LinearLayout titles = new LinearLayout(home);
        titles.setOrientation(LinearLayout.VERTICAL);
        heroTitle = Ui.text(home, "", 38, Color.WHITE, true);
        heroTitle.setMaxLines(2);
        heroTitle.setShadowLayer(12, 0, 2, Color.argb(160, 0, 0, 0));
        heroSub = Ui.text(home, "", 16, Color.rgb(200, 202, 208), false);
        heroSub.setPadding(0, Ui.dp(home, 4), 0, 0);
        titles.addView(heroTitle);
        titles.addView(heroSub);
        FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(Ui.dp(home, 380),
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.START | Gravity.BOTTOM);
        tp.leftMargin = Ui.dp(home, HomeActivity.SIDE_DP);
        tp.bottomMargin = Ui.dp(home, 49);
        heroGroup.addView(titles, tp);

        contentGroup = new LinearLayout(home);
        contentGroup.setOrientation(LinearLayout.VERTICAL);
        contentGroup.setClipChildren(false);
        contentGroup.setClipToPadding(false);
        contentGroup.setVisibility(View.GONE);
        LinearLayout captionLine = new LinearLayout(home);
        captionLine.setOrientation(LinearLayout.HORIZONTAL);
        captionLine.setGravity(Gravity.BOTTOM);
        // Überschrift-Schatten braucht Platz (Polsterung im Text), Ausgleich hier: Text bleibt an x SIDE_DP.
        int sh = Ui.dp(home, 10);
        captionLine.setPadding(Ui.dp(home, HomeActivity.SIDE_DP) - sh, 0, Ui.dp(home, HomeActivity.SIDE_DP), 0);
        captionLine.setClipChildren(false);
        captionLine.setClipToPadding(false);
        // tvOS-Abschnittstitel: Bold 48 px, weiss 50 %, bei x 90 / y 94.
        int capShadow = Ui.light ? Color.argb(90, 0, 0, 0) : Color.argb(150, 0, 0, 0);
        shelfApp = Ui.text(home, "", 24, Color.WHITE, true);
        shelfApp.setShadowLayer(Ui.dp(home, 8), 0, Ui.dp(home, 1), capShadow);
        shelfApp.setPadding(sh, sh, 0, sh);
        shelfSection = Ui.text(home, "", 18, Color.argb(220, 255, 255, 255), false);
        shelfSection.setShadowLayer(Ui.dp(home, 8), 0, Ui.dp(home, 1), capShadow);
        shelfSection.setTypeface(Fonts.get(shelfSection.getContext(), Fonts.MEDIUM));
        shelfSection.setPadding(Ui.dp(home, 14), sh, sh, sh + Ui.dp(home, 3));
        captionLine.addView(shelfApp);
        captionLine.addView(shelfSection);
        contentGroup.addView(captionLine);
        cardScroll = new CardScrollView(home);
        cardScroll.setClipChildren(false);
        cardScroll.setClipToPadding(false);
        // Unfokussiert links bündig mit den App-Kacheln darunter (W84), nicht mit dem Rahmen der Leiste.
        cardScroll.setPadding(Ui.dp(home, HomeActivity.SIDE_DP), Ui.dp(home, 12), Ui.dp(home, HomeActivity.SIDE_DP), Ui.dp(home, 24));
        cardRow = new LinearLayout(home);
        cardRow.setOrientation(LinearLayout.HORIZONTAL);
        cardRow.setClipChildren(false);
        cardRow.setClipToPadding(false);
        cardScroll.addView(cardRow);
        contentGroup.addView(cardScroll);
        cardTitle = Ui.text(home, "", 16, Ui.TEXT, true);
        cardTitle.setPadding(Ui.dp(home, HomeActivity.SIDE_DP), 0, Ui.dp(home, HomeActivity.SIDE_DP), 0);
        cardTitle.setShadowLayer(8, 0, 1, Color.BLACK);
        // Keine Textzeile unter den Inhaltskarten (B17); cardTitle bleibt unsichtbar im Code.
        cardTitle.setVisibility(View.GONE);
        contentGroup.addView(cardTitle);
        FrameLayout.LayoutParams cg = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP);
        cg.topMargin = Ui.dp(home, 44) - sh;
        shelf.addView(contentGroup, cg);
        return shelf;
    }

    /** Seite gescrollt: y in px, f = Anteil (0–1) bis der Top Shelf ganz weg ist. */
    void onScroll(int y, float f) {
        shelf.setAlpha(1f - f);
        if (flatTarget > 0f) {
            flatBg.setAlpha(flatTarget * (1f - f));
            // Banner nur auf ganz deckender Fläche zeigen: beim Wegscrollen sofort weg,
            // beim Zurückscrollen erst oben wieder einblenden.
            if (y > 0 && !heroPending && hero.getAlpha() > 0f) {
                hero.animate().cancel();
                hero.setAlpha(0f);
                heroPending = true;
            }
            maybeShowHero();
        }
    }

    /** Startseite wieder vorne: Hintergrund-Einstellungen übernehmen und Inhalte neu laden. */
    void onResume(TileView current) {
        // Hintergrund-Art oder Blur-Schalter geändert: Hintergrund neu setzen.
        int bgState = Prefs.background(home) * 2 + (Prefs.blurEnabled(home) ? 1 : 0);
        if (builtBackground != -1 && bgState != builtBackground) {
            builtBackground = bgState;
            backdropSource = null;
            setBackdropBlur(backdropBlur);
            if (current != null) show(current.tile);
        }
        builtBackground = bgState;
        // Nach dem Schauen ändern sich Watch Next und Vorschauen: neu laden.
        ShelfContent.invalidate();
        if (frozen) {
            freeze(true, false);
        } else if (current != null && current.hasFocus()) {
            updateContent(current.tile);
        }
    }

    // ---------------------------------------------------------------- Karten und Tasten

    /** Liefert die gemerkte Karte (-1 = keine) und vergisst sie. */
    int takeRememberedCard() {
        int r = rememberedCard;
        rememberedCard = -1;
        return r;
    }

    /** Inhaltskarten sind zu sehen. */
    boolean cardsShown() {
        return cardRow != null && cardRow.getChildCount() > 0 && cardRow.isShown();
    }

    boolean cardsFocused() {
        return cardRow != null && cardRow.hasFocus();
    }

    /** Fokus auf die gemerkte Karte, sonst auf die erste. */
    void focusCard(int remembered) {
        int target = remembered >= 0 && remembered < cardRow.getChildCount() ? remembered : 0;
        if (target == 0) cardScroll.scrollTo(0, 0);
        cardRow.getChildAt(target).requestFocus();
    }

    /** Geplantes Laden von Inhalten abbrechen (Fokus ging in eine Reihe unter den Favoriten). */
    void cancelPendingLoad() {
        if (pendingShelfLoad != null) home.handler.removeCallbacks(pendingShelfLoad);
    }

    // ---------------------------------------------------------------- Hintergrund

    /**
     * Hintergrund einfrieren (Fokus unter den Favoriten) oder wieder freigeben.
     * „Blurred content“: letzter Inhalt stärker unscharf; „Plain“: Bild blendet aus.
     */
    void freeze(boolean freeze, boolean animate) {
        float toBlur = freeze ? BACKDROP_BLUR_FROZEN : BACKDROP_BLUR;
        float toAlpha = BACKDROP_ALPHA;
        if (freeze == frozen && animate) return;
        frozen = freeze;
        if (freezeAnim != null) freezeAnim.cancel();
        freezeAnim = null;
        backdrop.animate().cancel();
        if (!animate) {
            setBackdropBlur(toBlur);
            backdrop.setAlpha(toAlpha);
            return;
        }
        final float fromBlur = backdropBlur;
        ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
        a.setDuration(Ui.ms(freeze ? 450 : 300));
        a.addUpdateListener(an -> setBackdropBlur(fromBlur + (toBlur - fromBlur) * an.getAnimatedFraction()));
        a.addListener(new AnimEnd(() -> freezeAnimEnded(a)));
        freezeAnim = a;
        a.start();
        backdrop.animate().alpha(toAlpha).setDuration(Ui.ms(freeze ? 450 : 300)).start();
    }

    void freezeAnimEnded(ValueAnimator a) {
        if (freezeAnim == a) freezeAnim = null;
    }

    private void setBackdropBlur(float radius) {
        backdropBlur = radius;
        if (Build.VERSION.SDK_INT >= 31) {
            // Nur „Blurred content“ mit eingeschaltetem Blur-Schalter weichzeichnet.
            float r = Prefs.blurEnabled(home) && Prefs.background(home) == Prefs.BG_BLURRED ? radius : 0f;
            backdrop.setRenderEffect(r > 0f
                    ? RenderEffect.createBlurEffect(r, r, Shader.TileMode.CLAMP) : null);
        }
    }

    /** Passende Farbe für den Hintergrund „Matching color“: aus Banner oder Symbol der App. */
    private int matchColor(Tile tile) {
        if (tile.banner == null) return tile.color != 0 ? tile.color : Ui.TILE_FALLBACK;
        return AppRepository.backgroundFor(tile.banner);
    }

    private boolean matchingBackground() {
        return Prefs.background(home) == Prefs.BG_MATCHING;
    }

    /**
     * Hintergrund weich überblenden statt hart wechseln (Wunsch des Nutzers). Jedes Bild wird erst auf
     * dieselbe kleine Größe gebracht (mittig beschnitten), damit beim Überblenden nichts verzerrt.
     */
    private void setBackdrop(Drawable d) {
        setBackdrop(d, null);
    }

    /**
     * sourceKey: Adresse des Bildes, falls bekannt. Dasselbe Vorschaubild kommt mal in voller Größe, mal
     * in 480 px (zwei verschiedene Bitmaps); ohne Adresse blendete der Hintergrund bei jedem Wechsel
     * zwischen Karte und App neu über (B41).
     */
    private void setBackdrop(Drawable d, String sourceKey) {
        Object key = sourceKey != null ? sourceKey : d instanceof BitmapDrawable
                ? ((BitmapDrawable) d).getBitmap()
                : d instanceof ColorDrawable ? (Object) ((ColorDrawable) d).getColor() : d;
        if (key.equals(backdropSource)) return;
        backdropSource = key;
        int bw = 320, bh = 180;
        Bitmap next = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(next);
        int iw = d.getIntrinsicWidth(), ih = d.getIntrinsicHeight();
        if (iw > 0 && ih > 0) {
            float sc = Math.max(bw / (float) iw, bh / (float) ih);
            int dw = Math.round(iw * sc), dh = Math.round(ih * sc);
            int l = (bw - dw) / 2, t = (bh - dh) / 2;
            Rect old = new Rect(d.getBounds());
            d.setBounds(l, t, l + dw, t + dh);
            d.draw(c);
            d.setBounds(old);
        } else {
            Rect old = new Rect(d.getBounds());
            d.setBounds(0, 0, bw, bh);
            d.draw(c);
            d.setBounds(old);
        }
        Drawable nd = new BitmapDrawable(home.getResources(), next);
        if (backdropBmp == null) {
            backdrop.setImageDrawable(nd);
        } else {
            // Ausgang ist der Hintergrund, wie er gerade zu sehen ist (auch mitten in einer Überblendung).
            Bitmap from = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
            Drawable cur = backdrop.getDrawable();
            if (cur != null) {
                Rect old = new Rect(cur.getBounds());
                cur.setBounds(0, 0, bw, bh);
                cur.draw(new Canvas(from));
                cur.setBounds(old);
            }
            TransitionDrawable td = new TransitionDrawable(
                    new Drawable[] {new BitmapDrawable(home.getResources(), from), nd});
            td.setCrossFadeEnabled(true);
            backdrop.setImageDrawable(td);
            td.startTransition((int) Ui.ms(252));
        }
        backdropBmp = next;
    }

    private void maybeShowHero() {
        if (!heroPending || flatTarget <= 0f) return;
        boolean ready = flatBg.getAlpha() >= 0.99f && home.scroll.getScrollY() == 0
                && (flatColorAnim == null || !flatColorAnim.isRunning());
        if (!ready) return;
        heroPending = false;
        hero.animate().cancel();
        hero.animate().alpha(1f).setStartDelay(0).setDuration(Ui.ms(100)).start();
    }

    /** Ganzflächige Farbe ein- (Farbe) oder ausblenden (0); setzt die Bannerkarte zurück. */
    private void setFlat(int color) {
        if (color != 0) {
            if (flatColorAnim != null) flatColorAnim.cancel();
            Drawable bgd = flatBg.getBackground();
            int from = bgd instanceof ColorDrawable ? ((ColorDrawable) bgd).getColor() : color;
            if (flatTarget > 0f && flatBg.getAlpha() > 0.05f && from != color) {
                // Farbe zur nächsten überblenden, statt zu springen.
                flatColorAnim = ValueAnimator.ofArgb(from, color);
                flatColorAnim.setDuration(Ui.ms(252));
                flatColorAnim.addUpdateListener(an -> {
                    flatBg.setBackgroundColor((int) an.getAnimatedValue());
                    if (an.getAnimatedFraction() >= 1f) home.handler.post(this::maybeShowHero);
                });
                flatColorAnim.start();
            } else {
                flatBg.setBackgroundColor(color);
            }
            flatTarget = 1f;
            flatBg.animate().alpha(home.scroll.getScrollY() == 0 ? 1f : flatBg.getAlpha()).setDuration(Ui.ms(196))
                    .withEndAction(this::maybeShowHero).start();
            return;
        }
        flatTarget = 0f;
        flatBg.animate().alpha(0f).setDuration(Ui.ms(196)).start();
        hero.setElevation(Ui.dp(home, 20));
        hero.setClipToOutline(true);
        heroLp.gravity = Gravity.END | Gravity.TOP;
        heroLp.rightMargin = Ui.dp(home, HomeActivity.SIDE_DP);
        hero.setLayoutParams(heroLp);
        ((View) heroTitle.getParent()).setVisibility(View.VISIBLE);
    }

    // ---------------------------------------------------------------- Banner und Inhalte

    /** Fokus auf einer Kachel der Favoriten: Banner oder Inhalte dieser App zeigen. */
    void show(Tile tile) {
        Drawable big = tile.banner != null ? banners.hero(tile) : null;
        int edge = big != null ? banners.flatEdgeColor(big) : 0;
        boolean hasContent = false;
        if (tile.kind == Tile.APP && tile.pkg != null) {
            List<ShelfContent.Item> cached = ShelfContent.cached(tile.pkg);
            hasContent = cached != null && !cached.isEmpty();
        }
        // Nach dem Kontrollzentrum ist der Zwischenspeicher leer, die Karten stehen aber noch da (B43).
        // Dann zählen sie als Inhalt: sonst kam die ganzflächige Randfarbe des Banners (Netflix weiß, RTL
        // schwarz), obwohl Inhalte folgen (B53).
        boolean keepContentBg = tile.key.equals(shelfKey) && cardRow != null && cardRow.getChildCount() > 0;
        if (keepContentBg) hasContent = true;
        if (big != null && edge != 0 && !hasContent) {
            // Wie bei tvOS: ganzer Bildschirm in der Randfarbe des Banners, Banner groß und mittig oben,
            // ohne Karte, Schatten und Titel – Banner und Fläche gehen ineinander über.
            hero.setImageDrawable(big);
            hero.setBackground(null);
            hero.setScaleType(ImageView.ScaleType.FIT_CENTER);
            hero.setElevation(0);
            hero.setClipToOutline(false);
            // Höchstens 1,6-fach vergrößern (320-px-Banner → 512 px), sonst wirkt das Logo unscharf
            // (Nutzer: 2,25-fach zu schlecht aufgelöst). Große Banner höchstens 846 px.
            int w = Math.min(Ui.dp(home, 423), Math.round(big.getIntrinsicWidth() * 1.6f));
            // Eigenes HD-Banner: wieder so groß wie früher (720 px), jetzt scharf.
            if (banners.isHd(big)) w = Ui.dp(home, 360);
            int padX = (Ui.dp(home, 423) - w) / 2;
            int padY = Math.max(0, (Ui.dp(home, 238) - w * big.getIntrinsicHeight() / Math.max(1, big.getIntrinsicWidth())) / 2);
            hero.setPadding(padX, padY, padX, padY);
            heroLp.gravity = Gravity.CENTER_HORIZONTAL | Gravity.TOP;
            heroLp.rightMargin = 0;
            hero.setLayoutParams(heroLp);
            // Logo erst einblenden, wenn die Farbfläche fertig ist; sonst sieht man die Kanten des
            // Bannerrechtecks, solange die Fläche dahinter noch einblendet oder die Farbe wechselt.
            Drawable fb = flatBg.getBackground();
            boolean settled = flatTarget > 0f && flatBg.getAlpha() > 0.99f
                    && fb instanceof ColorDrawable && ((ColorDrawable) fb).getColor() == edge
                    && (flatColorAnim == null || !flatColorAnim.isRunning());
            setFlat(edge);
            hero.animate().cancel();
            if (!settled || home.scroll.getScrollY() > 0) {
                hero.setAlpha(0f);
                heroPending = true;
                maybeShowHero();
            } else {
                heroPending = false;
                hero.setAlpha(1f);
            }
        } else if (big != null) {
            hero.setImageDrawable(big);
            hero.setBackground(null);
            hero.setScaleType(ImageView.ScaleType.CENTER_CROP);
            hero.setPadding(0, 0, 0, 0);
        } else {
            hero.setImageDrawable(tile.icon);
            hero.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            hero.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[] {lighter(tile.color), tile.color}));
            int p = Ui.dp(home, 60);
            hero.setPadding(p, p, p, p);
        }
        if (!(big != null && edge != 0 && !hasContent)) {
            heroPending = false;
            hero.animate().cancel();
            hero.setAlpha(1f);
        }
        if (flatTarget > 0f && !(big != null && edge != 0 && !hasContent)) setFlat(0);
        heroTitle.setText(tile.label);
        ((View) heroTitle.getParent()).setVisibility(flatTarget > 0f ? View.INVISIBLE : View.VISIBLE);
        String sub;
        switch (tile.kind) {
            case Tile.INPUT: sub = "TV input"; break;
            case Tile.HIDDEN_APPS: sub = "Show or open apps you hid from the home screen"; break;
            case Tile.SETTINGS: sub = "Best Seat settings"; break;
            default: sub = tile.available ? "" : "Waiting for USB storage";
        }
        heroSub.setText(sub);
        heroSub.setVisibility(sub.isEmpty() ? View.GONE : View.VISIBLE);
        // Apps mit Inhalten: Hintergrund kommt aus dem ersten Inhalt (showContent). Vorher das Banner zu setzen,
        // blendete bei jedem Wechsel zwischen Karte und App erst zum Banner und dann zurück (B41).
        // Nach dem Kontrollzentrum ist der Zwischenspeicher leer, die Karten stehen aber noch da: Hintergrund
        // dann nicht kurz aufs Banner wechseln lassen (B43).
        if (matchingBackground()) {
            int mc = matchColor(tile);
            setBackdrop(new ColorDrawable(mc), "color:" + mc);
        } else if (!hasContent) {
            setBackdrop(tile.banner != null ? tile.banner : new ColorDrawable(tile.color));
        }
        updateContent(tile);
    }

    /** Inhalte der App zeigen: sofort aus dem Zwischenspeicher, sonst kurz nach dem Fokus laden. */
    private void updateContent(Tile tile) {
        if (pendingShelfLoad != null) home.handler.removeCallbacks(pendingShelfLoad);
        boolean eligible = tile.kind == Tile.APP && tile.available && tile.pkg != null;
        if (!eligible) {
            showHero();
            return;
        }
        List<ShelfContent.Item> cached = ShelfContent.cached(tile.pkg);
        if (cached != null) {
            showContent(tile, cached);
            return;
        }
        if (!tile.key.equals(shelfKey)) showHero();
        final Context app = home.getApplicationContext();
        pendingShelfLoad = () -> shelfWorker.execute(() -> {
            List<ShelfContent.Item> items = ShelfContent.load(app, tile.pkg);
            home.handler.post(() -> {
                if (!home.isDestroyed() && tile.key.equals(home.focusedKey) && home.currentTileView != null
                        && home.currentTileView.hasFocus()) {
                    showContent(tile, items);
                }
            });
        });
        home.handler.postDelayed(pendingShelfLoad, 250);
    }

    private void showHero() {
        shelfKey = null;
        if (heroGroup.getVisibility() == View.VISIBLE) return;
        contentGroup.setVisibility(View.GONE);
        cardRow.removeAllViews();
        heroGroup.setAlpha(0f);
        heroGroup.setVisibility(View.VISIBLE);
        heroGroup.animate().alpha(1f).setDuration(Ui.ms(200)).start();
    }

    private void showContent(Tile tile, List<ShelfContent.Item> items) {
        if (items.isEmpty()) {
            showHero();
            return;
        }
        boolean same = tile.key.equals(shelfKey) && cardRow.getChildCount() == items.size();
        shelfKey = tile.key;
        // Über den Karten nur die Art des Inhalts (z. B. „Continue watching“), nicht der App-Name (tvOS).
        shelfApp.setText(sectionTitle(tile, items.get(0)));
        shelfSection.setText("");
        cardTitle.setText("");
        if (heroGroup.getVisibility() == View.VISIBLE) {
            heroGroup.setVisibility(View.GONE);
            contentGroup.setAlpha(0f);
            contentGroup.setVisibility(View.VISIBLE);
            contentGroup.animate().alpha(1f).setDuration(Ui.ms(220)).start();
            setFlat(0);
        }
        // Hintergrund: Bild des ersten Inhalts statt Banner (wie tvOS). Kommt man gerade von einer Karte herunter,
        // bleibt deren Bild stehen; beim Hochgehen landet man wieder auf ihr (B42).
        int bgIndex = same && rememberedCard >= 0 && rememberedCard < items.size() ? rememberedCard : 0;
        String bgImage = items.get(bgIndex).image;
        if (!matchingBackground()) Images.load(home, bgImage, 480, bmp -> {
            if (bmp != null && tile.key.equals(home.focusedKey) && !matchingBackground()) setBackdrop(new BitmapDrawable(home.getResources(), bmp), bgImage);
        });
        if (same) return;
        cardRow.removeAllViews();
        cardScroll.scrollTo(0, 0);
        // tvOS: 16:9-Karten 846x476 px, Abstand 48 px (2 nebeneinander).
        int height = Ui.dp(home, 238);
        int gap = Ui.dp(home, 24);
        // Zwei breite Karten füllen genau die Breite der App-Reihe: links und rechts bündig (W84).
        int rowWidth = home.getResources().getDisplayMetrics().widthPixels - 2 * Ui.dp(home, HomeActivity.SIDE_DP);
        int wide = (rowWidth - gap) / 2;
        for (int i = 0; i < items.size(); i++) {
            final ShelfContent.Item item = items.get(i);
            float aspect = Math.max(0.66f, Math.min(1.78f, item.aspect));
            int width = aspect >= 1.7f ? wide : Math.round(height * aspect);
            final ShelfCard card = new ShelfCard(home, item, width, height);
            card.setId(View.generateViewId());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(width, height);
            if (i > 0) lp.leftMargin = gap;
            cardRow.addView(card, lp);
            if (item.image != null) Images.into(home, item.image, width, card.image, card::showFallback);
            if (home.currentTileView != null) card.setNextFocusDownId(home.currentTileView.getId());
            if (i == 0) card.setNextFocusLeftId(card.getId());
            card.setOnFocusChangeListener((v, has) -> {
                card.focus(has, home.lastDx);
                if (has) {
                    rememberedCard = cardRow.indexOfChild(card);
                    String t = item.title != null ? item.title : "";
                    if (item.subtitle != null && !item.subtitle.isEmpty()) t += "  ·  " + item.subtitle;
                    cardTitle.setText(t);
                    shelfApp.setText(sectionTitle(tile, item));
                    // Fokussierte Karte links ausrichten (wie tvOS), letzte Karten bleiben am Ende.
                    cardScroll.smoothScrollTo(Math.max(0, card.getLeft() - cardScroll.getPaddingLeft()), 0);
                    Bitmap bmp = Images.memory(item.image);
                    if (bmp != null && !matchingBackground()) setBackdrop(new BitmapDrawable(home.getResources(), bmp), item.image);
                    home.scroll.animateTo(0);
                } else if (!cardRow.hasFocus()) {
                    cardTitle.setText("");
                }
            });
            card.setOnClickListener(v -> openItem(tile, item));
        }
        // Letzte Karte: Pfeil rechts bleibt stehen.
        View last = cardRow.getChildAt(cardRow.getChildCount() - 1);
        last.setNextFocusRightId(last.getId());
    }

    /** Abschnittstitel über den Karten: Art des Inhalts; nur wenn die fehlt, der App-Name. */
    private static String sectionTitle(Tile tile, ShelfContent.Item item) {
        String section = item.section != null ? item.section.trim() : "";
        return section.isEmpty() ? tile.label : section;
    }

    private void openItem(Tile tile, ShelfContent.Item item) {
        if (item.intentUri != null) {
            try {
                Intent intent = Intent.parseUri(item.intentUri, Intent.URI_INTENT_SCHEME);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                home.startActivity(intent);
                return;
            } catch (Exception e) {
                Log.w(TAG, "shelf intent failed: " + item.intentUri, e);
            }
        }
        home.launch(tile);
    }

    private static int lighter(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[2] = Math.min(1f, hsv[2] + 0.15f);
        return Color.HSVToColor(hsv);
    }
}
