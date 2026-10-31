package com.example.multiclone;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Toast;

/** Launcher shortcut target: starts the given clone then finishes. */
public class ShortcutActivity extends Activity {
    public static final String EXTRA_PKG = "pkg";
    public static final String EXTRA_USER = "user";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        String pkg = getIntent().getStringExtra(EXTRA_PKG);
        int user = getIntent().getIntExtra(EXTRA_USER, 0);
        try {
            if (pkg != null) CloneEngine.launch(pkg, user);
        } catch (Throwable t) {
            Toast.makeText(this, "Launch failed: " + t.getMessage(), Toast.LENGTH_LONG).show();
        }
        finish();
    }
}
