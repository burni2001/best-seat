package tv.bernhard.launcher;

import android.content.ComponentName;
import android.content.Intent;
import android.graphics.drawable.Drawable;

/** Eine Kachel: App, TV-Eingang oder eingebaute Funktion. */
final class Tile {

    static final int APP = 0;
    static final int INPUT = 1;
    static final int HIDDEN_APPS = 2;
    static final int SETTINGS = 3;
    static final int FOLDER = 4;
    /** Kacheln im Ordner (nur bei FOLDER). */
    java.util.List<Tile> members;

    final String key;
    final int kind;
    String label;
    String pkg;
    ComponentName component;
    Intent intent;
    Drawable banner;
    Drawable icon;
    /** Fläche hinter dem Symbol, wenn die App kein Banner hat. */
    int color;
    /** Liegt auf dem USB-Stick (adopted storage). */
    boolean external;
    /** false: bekannt, aber gerade nicht startbar (Stick nach dem Aufwachen noch nicht da). */
    boolean available = true;

    Tile(String key, int kind, String label) {
        this.key = key;
        this.kind = kind;
        this.label = label;
    }

    static String appKey(String pkg) {
        return "app:" + pkg;
    }

    static String inputKey(String id) {
        return "input:" + id;
    }
}
