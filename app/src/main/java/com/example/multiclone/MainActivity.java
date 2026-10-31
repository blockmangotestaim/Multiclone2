package com.example.multiclone;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.IconCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private static final int REQ_APK = 11;
    private static final int REQ_PERMS = 12;

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final List<CloneEntry> items = new ArrayList<>();
    private Adapter adapter;
    private View empty;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        RecyclerView list = findViewById(R.id.list);
        empty = findViewById(R.id.empty);
        adapter = new Adapter();
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);
        findViewById(R.id.fab).setOnClickListener(v -> pickInstalledApp());
        requestHostPermissions();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        io.shutdownNow();
    }

    // ---------- data ----------
    private void refresh() {
        io.execute(() -> {
            List<CloneEntry> all = CloneEngine.listAll(getApplicationContext());
            Collections.sort(all, (a, c) -> {
                int r = String.valueOf(a.label).compareToIgnoreCase(String.valueOf(c.label));
                return r != 0 ? r : Integer.compare(a.userId, c.userId);
            });
            ui.post(() -> {
                items.clear();
                items.addAll(all);
                adapter.notifyDataSetChanged();
                empty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
            });
        });
    }

    private void toast(String s) { ui.post(() -> Toast.makeText(this, s, Toast.LENGTH_LONG).show()); }

    // ---------- add / clone ----------
    private void pickInstalledApp() {
        PackageManager pm = getPackageManager();
        Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        final List<ResolveInfo> apps = new ArrayList<>();
        for (ResolveInfo ri : pm.queryIntentActivities(q, 0)) {
            if (!ri.activityInfo.packageName.equals(getPackageName())) apps.add(ri);
        }
        Collections.sort(apps, (a, c) -> a.loadLabel(pm).toString().compareToIgnoreCase(c.loadLabel(pm).toString()));

        ArrayAdapter<ResolveInfo> ad = new ArrayAdapter<ResolveInfo>(this, android.R.layout.select_dialog_item, apps) {
            @NonNull @Override
            public View getView(int pos, View cv, @NonNull ViewGroup parent) {
                TextView tv = (TextView) super.getView(pos, cv, parent);
                ResolveInfo ri = apps.get(pos);
                tv.setText(ri.loadLabel(pm));
                Drawable ic = ri.loadIcon(pm);
                tv.setCompoundDrawablesWithIntrinsicBounds(ic, null, null, null);
                tv.setCompoundDrawablePadding(24);
                return tv;
            }
        };
        new AlertDialog.Builder(this).setTitle("Clone an installed app")
                .setAdapter(ad, (d, which) -> cloneInstalled(apps.get(which).activityInfo.packageName))
                .setNegativeButton("Cancel", null).show();
    }

    private void cloneInstalled(String pkg) {
        io.execute(() -> {
            int u = CloneEngine.nextFreeUser(pkg);
            if (u < 0) { toast("All " + CloneEngine.MAX_USERS + " slots are used for this app"); return; }
            toast("Cloning… (instance " + (u + 1) + ")");
            boolean ok = CloneEngine.installFromDevice(pkg, u);
            toast(ok ? "Cloned" : "Clone failed (see logcat tag CloneEngine)");
            ui.post(this::refresh);
        });
    }

    private void pickApk() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT).setType("application/vnd.android.package-archive")
                .addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(i, "Select APK"), REQ_APK);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_APK || res != Activity.RESULT_OK || data == null || data.getData() == null) return;
        final Uri uri = data.getData();
        io.execute(() -> {
            try {
                File f = new File(getCacheDir(), "import.apk");
                try (InputStream in = getContentResolver().openInputStream(uri);
                     OutputStream out = new FileOutputStream(f)) {
                    byte[] buf = new byte[64 * 1024];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                }
                android.content.pm.PackageInfo pi = getPackageManager().getPackageArchiveInfo(f.getAbsolutePath(), 0);
                if (pi == null) { toast("Not a valid APK (split/bundle files are not supported)"); return; }
                int u = CloneEngine.nextFreeUser(pi.packageName);
                if (u < 0) { toast("No free slot for this app"); return; }
                toast("Importing…");
                String r = CloneEngine.installFromApk(getApplicationContext(), f, u);
                toast(r != null ? "Imported " + r : "Import failed (see logcat tag CloneEngine)");
                f.delete();
                ui.post(this::refresh);
            } catch (Throwable t) {
                toast("Import error: " + t.getMessage());
            }
        });
    }

    // ---------- actions ----------
    private void launch(CloneEntry e) {
        try { CloneEngine.launch(e.pkg, e.userId); }
        catch (Throwable t) { toast("Launch failed: " + t.getMessage()); }
    }

    private void addShortcut(CloneEntry e) {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(this)) { toast("Launcher does not support pinning"); return; }
        Intent i = new Intent(this, ShortcutActivity.class).setAction(Intent.ACTION_VIEW)
                .putExtra(ShortcutActivity.EXTRA_PKG, e.pkg).putExtra(ShortcutActivity.EXTRA_USER, e.userId);
        ShortcutInfoCompat.Builder sb = new ShortcutInfoCompat.Builder(this, e.pkg + "#" + e.userId)
                .setShortLabel(label(e)).setIntent(i);
        sb.setIcon(IconCompat.createWithBitmap(toBitmap(e.icon)));
        ShortcutManagerCompat.requestPinShortcut(this, sb.build(), null);
    }

    private String label(CloneEntry e) { return e.label + (e.userId > 0 ? " (" + (e.userId + 1) + ")" : ""); }

    private static Bitmap toBitmap(Drawable d) {
        if (d instanceof BitmapDrawable && ((BitmapDrawable) d).getBitmap() != null) return ((BitmapDrawable) d).getBitmap();
        int w = Math.max(d.getIntrinsicWidth(), 96), h = Math.max(d.getIntrinsicHeight(), 96);
        Bitmap bm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bm);
        d.setBounds(0, 0, w, h);
        d.draw(c);
        return bm;
    }

    private void showActions(CloneEntry e) {
        String[] acts = {"Launch", "Clone again (new instance)", "Add launcher shortcut", "Remove (deletes its data)"};
        new AlertDialog.Builder(this).setTitle(label(e)).setItems(acts, (d, w) -> {
            switch (w) {
                case 0: launch(e); break;
                case 1: cloneInstalledOrApk(e); break;
                case 2: addShortcut(e); break;
                case 3:
                    new AlertDialog.Builder(this).setMessage("Remove this instance and all of its data?")
                            .setPositiveButton("Remove", (x, y) -> io.execute(() -> {
                                CloneEngine.remove(e.pkg, e.userId);
                                ui.post(this::refresh);
                            })).setNegativeButton("Cancel", null).show();
                    break;
            }
        }).show();
    }

    private void cloneInstalledOrApk(CloneEntry e) {
        // Same package installed on device -> clone from it; otherwise ask for the APK again.
        try {
            getPackageManager().getPackageInfo(e.pkg, 0);
            cloneInstalled(e.pkg);
        } catch (PackageManager.NameNotFoundException ex) {
            toast("This app is not installed on the device; import its APK again to make another instance");
            pickApk();
        }
    }

    // ---------- permissions ----------
    private void requestHostPermissions() {
        List<String> need = new ArrayList<>();
        need.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        need.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (Build.VERSION.SDK_INT >= 33) need.add("android.permission.POST_NOTIFICATIONS");
        List<String> missing = new ArrayList<>();
        for (String p : need)
            if (ActivityCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) missing.add(p);
        if (!missing.isEmpty()) ActivityCompat.requestPermissions(this, missing.toArray(new String[0]), REQ_PERMS);
    }

    // ---------- menu ----------
    @Override
    public boolean onCreateOptionsMenu(Menu m) {
        m.add(0, 1, 0, "Import APK file");
        m.add(0, 2, 1, "Host app permissions");
        m.add(0, 3, 2, "Notes (GameGuardian / 32-bit)");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem it) {
        switch (it.getItemId()) {
            case 1: pickApk(); return true;
            case 2:
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
                return true;
            case 3:
                new AlertDialog.Builder(this).setTitle("Notes").setMessage(
                        "• Each instance has its own data sandbox (virtual user).\n"
                        + "• Permissions granted to MultiClone are shared with clones; apps also ask for their own.\n"
                        + "• To use GameGuardian, clone GameGuardian AND the target game here, then run GG from inside "
                        + "MultiClone. Use it on offline/single-player games only; online games may ban you.\n"
                        + "• 32-bit apps need a device that can run 32-bit code (many 2023+ phones are 64-bit only).")
                        .setPositiveButton("OK", null).show();
                return true;
        }
        return super.onOptionsItemSelected(it);
    }

    // ---------- list ----------
    private class Adapter extends RecyclerView.Adapter<Adapter.VH> {
        class VH extends RecyclerView.ViewHolder {
            ImageView icon; TextView title, sub;
            VH(View v) { super(v); icon = v.findViewById(R.id.icon); title = v.findViewById(R.id.title); sub = v.findViewById(R.id.sub); }
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup p, int t) {
            return new VH(LayoutInflater.from(p.getContext()).inflate(R.layout.item_clone, p, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            CloneEntry e = items.get(pos);
            h.icon.setImageDrawable(e.icon);
            h.title.setText(label(e));
            h.sub.setText(e.pkg + "  •  instance " + (e.userId + 1));
            h.itemView.setOnClickListener(v -> launch(e));
            h.itemView.setOnLongClickListener(v -> { showActions(e); return true; });
        }

        @Override public int getItemCount() { return items.size(); }
    }
}
