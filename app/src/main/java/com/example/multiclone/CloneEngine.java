package com.example.multiclone;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.app.configuration.ClientConfiguration;

/**
 * Thin wrapper around the BlackBox virtual engine. If you swap engines or the engine
 * API changes, this is the only file you need to touch.
 */
public final class CloneEngine {
    private static final String TAG = "CloneEngine";
    /** Virtual user ids 0..MAX_USERS-1 => up to 10 separate instances per app. */
    public static final int MAX_USERS = 10;

    private CloneEngine() {}

    public static void attach(final Context base) {
        try {
            BlackBoxCore.get().doAttachBaseContext(base, new ClientConfiguration() {
                @Override
                public String getHostPackageName() {
                    return base.getPackageName();
                }
            });
        } catch (Throwable t) {
            Log.e(TAG, "attach failed", t);
        }
    }

    public static void create() {
        try {
            BlackBoxCore.get().doCreate();
        } catch (Throwable t) {
            Log.e(TAG, "create failed", t);
        }
    }

    /** All instances across all virtual users. */
    public static List<CloneEntry> listAll(Context ctx) {
        List<CloneEntry> out = new ArrayList<>();
        for (int u = 0; u < MAX_USERS; u++) {
            try {
                List<PackageInfo> pkgs = BlackBoxCore.get().getInstalledPackages(0, u);
                if (pkgs == null) continue;
                for (PackageInfo pi : pkgs) {
                    if (pi == null || pi.packageName == null) continue;
                    CloneEntry e = new CloneEntry(pi.packageName, u);
                    if (pi.applicationInfo != null) {
                        e.sourceDir = pi.applicationInfo.sourceDir;
                    }
                    resolveLabelIcon(ctx, e);
                    out.add(e);
                }
            } catch (Throwable t) {
                Log.w(TAG, "list user " + u + " failed", t);
            }
        }
        return out;
    }

    public static boolean isInstalled(String pkg, int userId) {
        try {
            List<PackageInfo> pkgs = BlackBoxCore.get().getInstalledPackages(0, userId);
            if (pkgs == null) return false;
            for (PackageInfo pi : pkgs) if (pkg.equals(pi.packageName)) return true;
        } catch (Throwable ignored) {}
        return false;
    }

    /** First free virtual user for this package, or -1 if all slots are used. */
    public static int nextFreeUser(String pkg) {
        for (int u = 0; u < MAX_USERS; u++) if (!isInstalled(pkg, u)) return u;
        return -1;
    }

    /** Clone an app that is installed on the device. Blocking: call off the UI thread. */
    public static boolean installFromDevice(String pkg, int userId) {
        try {
            BlackBoxCore.get().installPackageAsUser(pkg, userId);
        } catch (Throwable t) {
            Log.e(TAG, "install failed", t);
        }
        return isInstalled(pkg, userId);
    }

    /** Import an APK file that is not installed on the device. Blocking. */
    public static String installFromApk(Context ctx, File apk, int userId) {
        PackageInfo info = ctx.getPackageManager().getPackageArchiveInfo(apk.getAbsolutePath(), 0);
        if (info == null) return null;
        try {
            BlackBoxCore.get().installPackageAsUser(apk, userId);
        } catch (Throwable t) {
            Log.e(TAG, "apk install failed", t);
        }
        return isInstalled(info.packageName, userId) ? info.packageName : null;
    }

    public static void launch(String pkg, int userId) {
        BlackBoxCore.get().launchApk(pkg, userId);
    }

    public static void remove(String pkg, int userId) {
        try {
            BlackBoxCore.get().uninstallPackageAsUser(pkg, userId);
        } catch (Throwable t) {
            Log.e(TAG, "remove failed", t);
        }
    }

    public static void resolveLabelIcon(Context ctx, CloneEntry e) {
        PackageManager pm = ctx.getPackageManager();
        try {
            ApplicationInfo ai = pm.getApplicationInfo(e.pkg, 0);
            e.label = String.valueOf(pm.getApplicationLabel(ai));
            e.icon = pm.getApplicationIcon(ai);
            return;
        } catch (Throwable ignored) {}
        try {
            if (e.sourceDir != null) {
                PackageInfo pi = pm.getPackageArchiveInfo(e.sourceDir, 0);
                if (pi != null && pi.applicationInfo != null) {
                    pi.applicationInfo.sourceDir = e.sourceDir;
                    pi.applicationInfo.publicSourceDir = e.sourceDir;
                    e.label = String.valueOf(pm.getApplicationLabel(pi.applicationInfo));
                    Drawable d = pi.applicationInfo.loadIcon(pm);
                    e.icon = d;
                    return;
                }
            }
        } catch (Throwable ignored) {}
        e.label = e.pkg;
        e.icon = pm.getDefaultActivityIcon();
    }
}
