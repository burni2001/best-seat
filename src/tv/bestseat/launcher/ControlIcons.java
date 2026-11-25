package tv.bestseat.launcher;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/**
 * Einfarbige Strichsymbole für die Kontrollzentrum-Kacheln (W31), gezeichnet in einem 24×24-Raster,
 * damit keine Bilddateien nötig sind und die Farbe bei Fokus wechseln kann.
 */
final class ControlIcons {

    private ControlIcons() {
    }

    /** Symbol für einen Eingang nach seinem Namen (vom Nutzer im TV vergeben, z. B. „PlayStation 5 Pro“). */
    static String sourceIcon(String label) {
        String l = label == null ? "" : label.toLowerCase(java.util.Locale.ROOT);
        if (has(l, "switch", "nintendo", "steam deck")) return "src_switch";
        if (has(l, "playstation", "ps5", "ps4", "ps3", "xbox")) return "src_tower";
        if (has(l, "console", "konsole", "game", "gaming", "spiel")) return "src_gamepad";
        if (has(l, "pc", "computer", "mac", "laptop", "notebook", "monitor")) return "src_monitor";
        if (has(l, "blu", "dvd", "disc", "player", "uhd")) return "src_disc";
        if (has(l, "soundbar", "receiver", "avr", "audio", "sonos", "amp", "speaker")) return "src_speaker";
        if (has(l, "antenna", "antenne", "tuner", "sat", "cable", "kabel", "dvb")) return "src_antenna";
        if (has(l, "apple tv", "fire", "chromecast", "shield", "roku", "stream", "box", "media")) return "src_box";
        return "src_hdmi";
    }

    private static boolean has(String label, String... words) {
        for (String w : words) if (label.contains(w)) return true;
        return false;
    }

    static final class Icon extends Drawable {
        private final String id;
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);

        Icon(String id, int color) {
            this.id = id == null ? "" : id;
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(2.3f); // kräftiger (Wunsch des Nutzers)
            stroke.setStrokeCap(Paint.Cap.ROUND);
            stroke.setStrokeJoin(Paint.Join.ROUND);
            fill.setStyle(Paint.Style.FILL);
            setColor(color);
        }

        /** Strichstärke in 24er-Rastereinheiten (Standard 2,3; die Pille nutzt das dünnere 1,8). */
        Icon strokeWidth(float w) {
            stroke.setStrokeWidth(w);
            invalidateSelf();
            return this;
        }

        void setColor(int color) {
            stroke.setColor(color);
            fill.setColor(color);
            invalidateSelf();
        }

        @Override
        public void draw(Canvas c) {
            Rect b = getBounds();
            float s = Math.min(b.width(), b.height()) / 24f;
            c.save();
            c.translate(b.left, b.top);
            c.scale(s, s);
            Path p = new Path();
            switch (id) {
                case ControlItems.SCREENSAVER: // Mond
                    p.addCircle(12, 12, 8, Path.Direction.CW);
                    Path cut = new Path();
                    cut.addCircle(16.5f, 8.5f, 7, Path.Direction.CW);
                    p.op(cut, Path.Op.DIFFERENCE);
                    c.drawPath(p, fill);
                    break;
                case ControlItems.AMBILIGHT: // Bildschirm mit Lichtschein
                    c.drawRoundRect(new RectF(6, 7, 18, 15), 1.5f, 1.5f, stroke);
                    c.drawLine(2.5f, 9, 4, 10, stroke);
                    c.drawLine(2.5f, 13, 4, 12, stroke);
                    c.drawLine(21.5f, 9, 20, 10, stroke);
                    c.drawLine(21.5f, 13, 20, 12, stroke);
                    c.drawLine(9, 18.5f, 15, 18.5f, stroke);
                    break;
                case ControlItems.HUE_SYNC: // Glühbirne
                    p.moveTo(9.5f, 15.5f);
                    p.cubicTo(7, 14, 6, 12, 6, 9.5f);
                    p.cubicTo(6, 6, 8.7f, 3.5f, 12, 3.5f);
                    p.cubicTo(15.3f, 3.5f, 18, 6, 18, 9.5f);
                    p.cubicTo(18, 12, 17, 14, 14.5f, 15.5f);
                    p.close();
                    c.drawPath(p, stroke);
                    c.drawLine(9.5f, 18.5f, 14.5f, 18.5f, stroke);
                    c.drawLine(10.5f, 21, 13.5f, 21, stroke);
                    break;
                case ControlItems.INPUTS: // Pfeil in einen Rahmen
                    p.moveTo(10, 5);
                    p.lineTo(18, 5);
                    p.quadTo(20, 5, 20, 7);
                    p.lineTo(20, 17);
                    p.quadTo(20, 19, 18, 19);
                    p.lineTo(10, 19);
                    c.drawPath(p, stroke);
                    c.drawLine(3, 12, 14, 12, stroke);
                    c.drawLine(10.5f, 8.5f, 14, 12, stroke);
                    c.drawLine(10.5f, 15.5f, 14, 12, stroke);
                    break;
                case ControlItems.PICTURE: // Bild mit Berg und Sonne
                    c.drawRoundRect(new RectF(3.5f, 5, 20.5f, 19), 2, 2, stroke);
                    p.moveTo(5, 17);
                    p.lineTo(10, 11.5f);
                    p.lineTo(13.5f, 15);
                    p.lineTo(15.5f, 13);
                    p.lineTo(19, 17);
                    c.drawPath(p, stroke);
                    c.drawCircle(15.5f, 9, 1.4f, fill);
                    break;
                case ControlItems.SOUND: // Lautsprecher mit Wellen
                    p.moveTo(4, 9.5f);
                    p.lineTo(7.5f, 9.5f);
                    p.lineTo(12, 5.5f);
                    p.lineTo(12, 18.5f);
                    p.lineTo(7.5f, 14.5f);
                    p.lineTo(4, 14.5f);
                    p.close();
                    c.drawPath(p, stroke);
                    c.drawArc(new RectF(11, 8.5f, 18, 15.5f), -50, 100, false, stroke);
                    c.drawArc(new RectF(11, 5, 21.5f, 19), -50, 100, false, stroke);
                    break;
                case ControlItems.APP_SWITCHER: // drei Karten hintereinander
                    c.drawRoundRect(new RectF(7, 7, 17, 17), 2, 2, stroke);
                    c.drawLine(3.5f, 9, 3.5f, 15, stroke);
                    c.drawLine(20.5f, 9, 20.5f, 15, stroke);
                    break;
                case ControlItems.VPN: // Schild
                    p.moveTo(12, 3);
                    p.lineTo(19, 6);
                    p.lineTo(19, 11);
                    p.cubicTo(19, 15.5f, 16, 19, 12, 21);
                    p.cubicTo(8, 19, 5, 15.5f, 5, 11);
                    p.lineTo(5, 6);
                    p.close();
                    c.drawPath(p, stroke);
                    break;
                // ---- Eingänge (Sources): Symbol nach dem Namen des Eingangs, siehe sourceIcon()
                case "src_switch": // Handkonsole mit zwei Controllern an den Seiten
                    c.drawRoundRect(new RectF(7.5f, 6, 16.5f, 18), 1, 1, stroke);
                    c.drawRoundRect(new RectF(3, 6, 7.5f, 18), 2.5f, 2.5f, stroke);
                    c.drawRoundRect(new RectF(16.5f, 6, 21, 18), 2.5f, 2.5f, stroke);
                    c.drawCircle(5.25f, 10, 0.9f, fill);
                    c.drawCircle(18.75f, 14, 0.9f, fill);
                    break;
                case "src_tower": // stehende Konsole mit Sockel
                    c.drawRoundRect(new RectF(8.5f, 3, 15.5f, 19), 2.5f, 2.5f, stroke);
                    c.drawLine(12, 4.5f, 12, 17.5f, stroke);
                    c.drawLine(6.5f, 21, 17.5f, 21, stroke);
                    break;
                case "src_gamepad": // Controller
                    c.drawRoundRect(new RectF(2.5f, 7.5f, 21.5f, 17.5f), 5, 5, stroke);
                    c.drawLine(6.5f, 12.5f, 10.5f, 12.5f, stroke);
                    c.drawLine(8.5f, 10.5f, 8.5f, 14.5f, stroke);
                    c.drawCircle(15.5f, 11.3f, 1.1f, fill);
                    c.drawCircle(17.7f, 13.6f, 1.1f, fill);
                    break;
                case "src_box": // Zuspieler (Streaming-Box)
                    c.drawRoundRect(new RectF(3.5f, 8.5f, 20.5f, 16), 2.5f, 2.5f, stroke);
                    c.drawCircle(16.5f, 12.25f, 0.9f, fill);
                    break;
                case "src_monitor": // Computer
                    c.drawRoundRect(new RectF(3, 4, 21, 15.5f), 1.5f, 1.5f, stroke);
                    c.drawLine(12, 15.5f, 12, 19, stroke);
                    c.drawLine(8, 19.5f, 16, 19.5f, stroke);
                    break;
                case "src_disc": // Disc-Player
                    c.drawCircle(12, 12, 8.5f, stroke);
                    c.drawCircle(12, 12, 2.3f, stroke);
                    break;
                case "src_speaker": // Receiver, Soundbar
                    c.drawRoundRect(new RectF(2.5f, 8, 21.5f, 16), 1.5f, 1.5f, stroke);
                    c.drawCircle(16.5f, 12, 2, stroke);
                    c.drawLine(6, 11, 10.5f, 11, stroke);
                    c.drawLine(6, 13, 10.5f, 13, stroke);
                    break;
                case "src_antenna": // Antenne, Kabel, Satellit
                    c.drawRoundRect(new RectF(4, 9, 20, 19.5f), 1.5f, 1.5f, stroke);
                    c.drawLine(8, 4, 12, 9, stroke);
                    c.drawLine(16, 4, 12, 9, stroke);
                    break;
                case "src_hdmi": // HDMI-Stecker (alles andere)
                    p.moveTo(3.5f, 8);
                    p.lineTo(20.5f, 8);
                    p.lineTo(20.5f, 13);
                    p.lineTo(17.5f, 16.5f);
                    p.lineTo(6.5f, 16.5f);
                    p.lineTo(3.5f, 13);
                    p.close();
                    c.drawPath(p, stroke);
                    c.drawLine(8, 11.5f, 16, 11.5f, stroke);
                    break;
                case "controlcenter": // zwei Schalter übereinander (Kontrollzentrum in der Statuspille)
                    c.drawRoundRect(new RectF(4, 4.5f, 20, 11), 3.25f, 3.25f, stroke);
                    c.drawRoundRect(new RectF(4, 13, 20, 19.5f), 3.25f, 3.25f, stroke);
                    c.drawCircle(16.5f, 7.75f, 2.1f, fill);
                    c.drawCircle(7.5f, 16.25f, 2.1f, fill);
                    break;
                case ControlItems.SETTINGS: // Zahnrad (TV-Einstellungen)
                    gear(c);
                    break;
                case ControlItems.HOME: // Schieberegler (Launcher-Einstellungen)
                    c.drawLine(4, 7, 20, 7, stroke);
                    c.drawLine(4, 12, 20, 12, stroke);
                    c.drawLine(4, 17, 20, 17, stroke);
                    c.drawCircle(9, 7, 2.2f, fill);
                    c.drawCircle(15, 12, 2.2f, fill);
                    c.drawCircle(7.5f, 17, 2.2f, fill);
                    break;
                case ControlItems.RESTART: // Ein/Aus-Symbol: Kreis mit Lücke oben, senkrechter Strich
                    c.drawArc(new RectF(4.5f, 5, 19.5f, 20), -55, 290, false, stroke);
                    c.drawLine(12, 3, 12, 11.5f, stroke);
                    break;
                case ControlItems.ALL_APPS: // 3×3 Raster
                    for (int y = 0; y < 3; y++) {
                        for (int x = 0; x < 3; x++) {
                            c.drawRoundRect(new RectF(4 + x * 6, 4 + y * 6, 8 + x * 6, 8 + y * 6), 1.2f, 1.2f, fill);
                        }
                    }
                    break;
                default:
                    c.drawCircle(12, 12, 3, fill);
            }
            c.restore();
        }

        /** Zahnrad: acht Zähne um einen Ring. */
        private void gear(Canvas c) {
            for (int i = 0; i < 8; i++) {
                c.save();
                c.rotate(i * 45, 12, 12);
                c.drawRoundRect(new RectF(10.6f, 2.8f, 13.4f, 6.5f), 0.8f, 0.8f, fill);
                c.restore();
            }
            Paint ring = new Paint(stroke);
            ring.setStrokeWidth(3.2f);
            c.drawCircle(12, 12, 5.6f, ring);
        }

        @Override
        public void setAlpha(int alpha) {
            stroke.setAlpha(alpha);
            fill.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter filter) {
            stroke.setColorFilter(filter);
            fill.setColorFilter(filter);
        }

        @SuppressWarnings("deprecation") // Pflicht-Methode von Drawable, nur noch formal veraltet.
        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
