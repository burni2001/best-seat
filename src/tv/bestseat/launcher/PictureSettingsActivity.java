package tv.bestseat.launcher;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

/**
 * Öffnet das Bildmenü des TVs immer frisch (seit 0.17.17, für die Prime-Taste in tvQuickActions).
 *
 * Grund: Das Bildmenü ({@code android.settings.PICTURE_ACTIVITY}) ist durchsichtig und landet
 * in der Aufgabe der Android-Einstellungen. War die vorher offen, lag die alte Einstellungsseite
 * sichtbar darunter, und Zurück führte dorthin. CLEAR_TASK räumt diese Aufgabe vorher ab.
 */
public final class PictureSettingsActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            startActivity(fresh(new Intent("android.settings.PICTURE_ACTIVITY")));
        } catch (RuntimeException e) {
            Log.w("Launcher", "Picture settings not available", e);
            Toast.makeText(this, "Picture settings not available", Toast.LENGTH_SHORT).show();
        }
        finish();
    }

    /** Neue Aufgabe, eine schon offene Aufgabe derselben App vorher leeren. */
    static Intent fresh(Intent intent) {
        return intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
    }
}
