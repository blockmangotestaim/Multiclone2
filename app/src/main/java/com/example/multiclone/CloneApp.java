package com.example.multiclone;

import android.app.Application;
import android.content.Context;

/** Host application. The engine must hook in before and after normal startup. */
public class CloneApp extends Application {
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        CloneEngine.attach(base);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        CloneEngine.create();
    }
}
