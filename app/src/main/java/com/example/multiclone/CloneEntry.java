package com.example.multiclone;

import android.graphics.drawable.Drawable;

/** One virtual instance: package + virtual user id (each id has its own data sandbox). */
public class CloneEntry {
    public final String pkg;
    public final int userId;
    public String label;
    public Drawable icon;
    public String sourceDir;

    public CloneEntry(String pkg, int userId) {
        this.pkg = pkg;
        this.userId = userId;
    }
}
