package com.yusaa.gununkelimesi;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;
import android.provider.Settings;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/** Checks for app updates on startup, prompts the user, downloads the APK, and opens the installer. */
public class AppUpdater {

    public static final int REQUEST_UNKNOWN_APP_SOURCES = 4001;

    private static final String VERSION_URL = "https://ingilizce.yusaa.com.tr/app/version.json";
    private static final String UPDATES_DIR = "updates";
    private static final String PREFS = "gk_updater_prefs";
    private static final String KEY_PENDING_PATH = "pending_apk_path";
    private static final String KEY_PENDING_CODE = "pending_version_code";
    private static final int MAX_JSON_BYTES = 256 * 1024;
    private static final int MAX_REDIRECTS = 6;

    private static final AtomicBoolean CHECK_IN_FLIGHT = new AtomicBoolean(false);
    private static final AtomicBoolean DOWNLOAD_IN_FLIGHT = new AtomicBoolean(false);

    private static final class UpdateInfo {
        final int versionCode;
        final String versionName;
        final String apkUrl;
        final String notes;

        UpdateInfo(int versionCode, String versionName, String apkUrl, String notes) {
            this.versionCode = versionCode;
            this.versionName = versionName;
            this.apkUrl = apkUrl;
            this.notes = notes;
        }
    }

    private final WeakReference<Activity> activityRef;
    private final Context appContext;
    private AlertDialog activeDialog;
    private final AtomicBoolean downloadCancelled = new AtomicBoolean(false);
    private volatile HttpURLConnection activeDownloadConn;

    public AppUpdater(Activity activity) {
        this.activityRef = new WeakReference<>(activity);
        this.appContext = activity.getApplicationContext();
    }

    /**
     * Resumes a pending APK installation if the user already granted unknown-sources permission
     * across an activity/process restart. Returns true if an installation was launched.
     */
    public boolean resumePendingInstallIfReady(boolean isFreshLaunch) {
        SharedPreferences p = prefs();
        String path = p.getString(KEY_PENDING_PATH, "");
        int pendingCode = p.getInt(KEY_PENDING_CODE, -1);

        File file = (path == null || path.isEmpty()) ? null : new File(path);
        if (file == null || pendingCode <= BuildConfig.VERSION_CODE || !hasValidApkHeader(file)) {
            clearPendingInstall();
            cleanupOldApks(appContext, null);
            return false;
        }

        if (canInstallPackages(appContext)) {
            clearPendingInstall();
            Activity act = aliveActivity();
            if (act != null) {
                launchInstaller(act, file);
            }
            return true;
        }

        if (isFreshLaunch) {
            clearPendingInstall();
            cleanupOldApks(appContext, null);
        }
        return false;
    }

    /** Checks version.json on a background thread without blocking the UI thread. */
    public void checkOnStartup() {
        if (!CHECK_IN_FLIGHT.compareAndSet(false, true)) {
            return;
        }
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                String checkUrl = VERSION_URL + "?t=" + System.currentTimeMillis();
                conn = openHttpsConnection(checkUrl, 8000, 10000, false);
                if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    return;
                }
                byte[] body = readBounded(conn.getInputStream(), MAX_JSON_BYTES);
                if (body == null) {
                    return;
                }
                JSONObject obj = new JSONObject(new String(body, StandardCharsets.UTF_8));
                int versionCode = obj.optInt("versionCode", -1);
                String versionName = optCleanString(obj, "versionName");
                String apkUrlRaw = optCleanString(obj, "apkUrl");
                String notes = optCleanString(obj, "notes");

                if (versionCode <= BuildConfig.VERSION_CODE || apkUrlRaw.isEmpty()) {
                    return;
                }

                URL resolved = new URL(new URL(VERSION_URL), apkUrlRaw);
                if (!"https".equalsIgnoreCase(resolved.getProtocol())) {
                    return;
                }

                final UpdateInfo info = new UpdateInfo(
                        versionCode, versionName, resolved.toString(), notes);
                Activity act = aliveActivity();
                if (act != null) {
                    act.runOnUiThread(() -> {
                        Activity current = aliveActivity();
                        if (current != null) {
                            showUpdatePrompt(current, info);
                        }
                    });
                }
            } catch (Exception ignored) {
                // Silent failure on startup check so offline/server issues never block the app.
            } finally {
                if (conn != null) {
                    try {
                        conn.disconnect();
                    } catch (Exception ignored) {
                    }
                }
                CHECK_IN_FLIGHT.set(false);
            }
        }, "gk-update-check").start();
    }

    /** Completes a pending installation after returning from the Unknown App Sources settings screen. */
    public void handleActivityResult(int requestCode) {
        if (requestCode != REQUEST_UNKNOWN_APP_SOURCES) {
            return;
        }
        SharedPreferences p = prefs();
        String path = p.getString(KEY_PENDING_PATH, "");
        int pendingCode = p.getInt(KEY_PENDING_CODE, -1);
        clearPendingInstall();
        if (path == null || path.isEmpty()) {
            return;
        }
        Activity act = aliveActivity();
        if (act == null) {
            return;
        }
        File file = new File(path);
        if (canInstallPackages(appContext)
                && pendingCode > BuildConfig.VERSION_CODE
                && hasValidApkHeader(file)) {
            launchInstaller(act, file);
        } else if (!canInstallPackages(appContext)) {
            Toast.makeText(act, "Kurulum i\u00e7in bilinmeyen kaynak izni verilmedi",
                    Toast.LENGTH_LONG).show();
        }
    }

    /** Cancels any active download and dismisses dialogs to prevent window leaks on destroy. */
    public void detach() {
        downloadCancelled.set(true);
        HttpURLConnection conn = activeDownloadConn;
        activeDownloadConn = null;
        disconnectAsync(conn);
        dismissActiveDialog();
    }

    private void showUpdatePrompt(Activity act, UpdateInfo info) {
        if (DOWNLOAD_IN_FLIGHT.get()) {
            return;
        }
        dismissActiveDialog();

        String vLabel = "";
        if (!info.versionName.isEmpty()) {
            vLabel = (info.versionName.startsWith("v") || info.versionName.startsWith("V"))
                    ? info.versionName
                    : "v" + info.versionName;
        }
        String title = vLabel.isEmpty()
                ? "Yeni s\u00fcr\u00fcm mevcut"
                : "Yeni s\u00fcr\u00fcm mevcut (" + vLabel + ")";

        StringBuilder msg = new StringBuilder("Yeni s\u00fcr\u00fcm var, indirilsin mi?");
        if (!vLabel.isEmpty()) {
            msg.append("\n\nMevcut: v").append(BuildConfig.VERSION_NAME)
                    .append(" \u2192 Yeni: ").append(vLabel);
        }
        if (!info.notes.isEmpty()) {
            msg.append("\n\nYenilikler:\n").append(info.notes);
        }

        AlertDialog dialog = new AlertDialog.Builder(act, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(title)
                .setMessage(msg.toString())
                .setPositiveButton("\u0130ndir ve Kur", (d, which) -> {
                    Activity current = aliveActivity();
                    if (current != null) {
                        startDownload(current, info);
                    }
                })
                .setNegativeButton("Daha Sonra", null)
                .create();
        activeDialog = dialog;
        dialog.show();
    }

    private void startDownload(Activity act, UpdateInfo info) {
        if (!DOWNLOAD_IN_FLIGHT.compareAndSet(false, true)) {
            return;
        }
        downloadCancelled.set(false);
        dismissActiveDialog();

        LinearLayout layout = new LinearLayout(act);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = UiStyle.dp(act, 20);
        layout.setPadding(pad, UiStyle.dp(act, 16), pad, UiStyle.dp(act, 8));

        TextView statusText = new TextView(act);
        statusText.setText("APK indiriliyor...");
        statusText.setTextSize(14);
        statusText.setTextColor(UiStyle.TEXT_WHITE);
        layout.addView(statusText);

        ProgressBar progressBar = new ProgressBar(
                act, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setIndeterminate(true);
        progressBar.setMax(100);
        LinearLayout.LayoutParams pbLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        pbLp.setMargins(0, UiStyle.dp(act, 14), 0, 0);
        layout.addView(progressBar, pbLp);

        AlertDialog progressDialog = new AlertDialog.Builder(
                act, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("G\u00fcncelleme \u0130ndiriliyor")
                .setView(layout)
                .setCancelable(false)
                .setNegativeButton("\u0130ptal", (d, which) -> cancelDownload())
                .create();
        progressDialog.setCanceledOnTouchOutside(false);
        activeDialog = progressDialog;
        progressDialog.show();

        new Thread(() -> {
            File tmpFile = null;
            File apkFile = null;
            boolean success = false;
            try {
                File dir = new File(appContext.getCacheDir(), UPDATES_DIR);
                if (!dir.exists() && !dir.mkdirs()) {
                    throw new IOException("Updates cache dir unavailable");
                }
                cleanupOldApks(appContext, null);

                tmpFile = new File(dir, "update-" + info.versionCode + ".apk.tmp");
                apkFile = new File(dir, "update-" + info.versionCode + ".apk");

                HttpURLConnection conn = openHttpsConnection(info.apkUrl, 15000, 30000, true);
                if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    throw new IOException("HTTP " + conn.getResponseCode());
                }

                long total = conn.getContentLengthLong();
                try (InputStream is = conn.getInputStream();
                     FileOutputStream fos = new FileOutputStream(tmpFile)) {
                    byte[] buf = new byte[8192];
                    int read;
                    long downloaded = 0;
                    long lastUiUpdate = 0;
                    int lastPct = -1;
                    while ((read = is.read(buf)) > 0) {
                        if (downloadCancelled.get()) {
                            break;
                        }
                        fos.write(buf, 0, read);
                        downloaded += read;
                        long now = SystemClock.uptimeMillis();
                        if (total > 0) {
                            int pct = (int) Math.min(100L, (downloaded * 100L) / total);
                            if (pct != lastPct && (now - lastUiUpdate >= 120L || pct == 100)) {
                                lastPct = pct;
                                lastUiUpdate = now;
                                postProgress(statusText, progressBar, pct, downloaded, total);
                            }
                        } else if (now - lastUiUpdate >= 200L) {
                            lastUiUpdate = now;
                            postProgress(statusText, progressBar, -1, downloaded, -1L);
                        }
                    }
                    fos.flush();
                }

                if (downloadCancelled.get()) {
                    deleteQuietly(tmpFile);
                    return;
                }
                if (!hasValidApkHeader(tmpFile)) {
                    throw new IOException("Downloaded file is not a valid APK");
                }
                if (apkFile.exists() && !apkFile.delete()) {
                    throw new IOException("Cannot replace existing APK");
                }
                if (!tmpFile.renameTo(apkFile)) {
                    throw new IOException("Cannot finalize APK file");
                }
                success = true;
            } catch (Exception ignored) {
                deleteQuietly(tmpFile);
            } finally {
                HttpURLConnection conn = activeDownloadConn;
                activeDownloadConn = null;
                if (conn != null) {
                    try {
                        conn.disconnect();
                    } catch (Exception ignored) {
                    }
                }
                DOWNLOAD_IN_FLIGHT.set(false);
            }

            final boolean ok = success;
            final boolean cancelled = downloadCancelled.get();
            final File finalApk = apkFile;
            Activity uiAct = aliveActivity();
            if (uiAct != null) {
                uiAct.runOnUiThread(() -> {
                    Activity current = aliveActivity();
                    if (current == null) {
                        return;
                    }
                    if (activeDialog == progressDialog) {
                        dismissActiveDialog();
                    } else {
                        try {
                            if (progressDialog.isShowing()) {
                                progressDialog.dismiss();
                            }
                        } catch (Exception ignored) {
                        }
                    }
                    if (ok && !cancelled && finalApk != null) {
                        installOrRequestPermission(current, finalApk, info.versionCode);
                    } else if (!cancelled) {
                        Toast.makeText(current,
                                "G\u00fcncelleme indirilemedi, daha sonra tekrar dene",
                                Toast.LENGTH_LONG).show();
                    }
                });
            }
        }, "gk-update-download").start();
    }

    private void postProgress(TextView statusText, ProgressBar progressBar,
                              int pct, long downloaded, long total) {
        Activity act = aliveActivity();
        if (act == null) {
            return;
        }
        act.runOnUiThread(() -> {
            if (aliveActivity() == null || downloadCancelled.get()) {
                return;
            }
            Locale tr = new Locale("tr");
            if (pct >= 0 && total > 0) {
                if (progressBar.isIndeterminate()) {
                    progressBar.setIndeterminate(false);
                }
                progressBar.setProgress(pct);
                statusText.setText(String.format(tr,
                        "APK indiriliyor... %%%d (%.1f / %.1f MB)",
                        pct, downloaded / (1024f * 1024f), total / (1024f * 1024f)));
            } else {
                statusText.setText(String.format(tr,
                        "APK indiriliyor... (%.1f MB)",
                        downloaded / (1024f * 1024f)));
            }
        });
    }

    private void cancelDownload() {
        downloadCancelled.set(true);
        HttpURLConnection conn = activeDownloadConn;
        activeDownloadConn = null;
        disconnectAsync(conn);
        dismissActiveDialog();
    }

    private void installOrRequestPermission(Activity act, File apkFile, int versionCode) {
        if (canInstallPackages(appContext)) {
            launchInstaller(act, apkFile);
            return;
        }
        dismissActiveDialog();
        AlertDialog dialog = new AlertDialog.Builder(
                act, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Kurulum \u0130zni Gerekli")
                .setMessage("G\u00fcncellemeyi kurabilmek i\u00e7in a\u00e7\u0131lacak ayarlar ekran\u0131ndan \"Bu kaynaktan izin ver\" se\u00e7ene\u011fini a\u00e7man gerekiyor.")
                .setPositiveButton("Ayarlara Git", (d, which) -> {
                    savePendingInstall(apkFile, versionCode);
                    Activity current = aliveActivity();
                    if (current != null) {
                        openUnknownSourcesSettings(current);
                    }
                })
                .setNegativeButton("\u0130ptal", null)
                .create();
        activeDialog = dialog;
        dialog.show();
    }

    @SuppressWarnings("deprecation")
    private void openUnknownSourcesSettings(Activity act) {
        try {
            Intent intent = new Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + act.getPackageName()));
            act.startActivityForResult(intent, REQUEST_UNKNOWN_APP_SOURCES);
        } catch (ActivityNotFoundException e) {
            try {
                Intent fallback = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                act.startActivityForResult(fallback, REQUEST_UNKNOWN_APP_SOURCES);
            } catch (ActivityNotFoundException e2) {
                clearPendingInstall();
                Toast.makeText(act, "Bilinmeyen kaynak ayarlar\u0131 a\u00e7\u0131lamad\u0131",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    private void launchInstaller(Activity act, File apkFile) {
        try {
            Uri uri = ApkProvider.getUriForFile(act, apkFile);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            intent.setClipData(ClipData.newRawUri("", uri));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            act.startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException | IllegalArgumentException e) {
            Toast.makeText(act, "Kurulum ekran\u0131 a\u00e7\u0131lamad\u0131",
                    Toast.LENGTH_LONG).show();
        }
    }

    private HttpURLConnection openHttpsConnection(String urlString, int connectTimeoutMs,
                                                  int readTimeoutMs, boolean trackDownload)
            throws IOException {
        URL currentUrl = new URL(urlString);
        for (int i = 0; i <= MAX_REDIRECTS; i++) {
            if (trackDownload && downloadCancelled.get()) {
                throw new IOException("Download cancelled");
            }
            if (!"https".equalsIgnoreCase(currentUrl.getProtocol())) {
                throw new IOException("Non-HTTPS URL rejected");
            }
            HttpURLConnection conn = (HttpURLConnection) currentUrl.openConnection();
            conn.setInstanceFollowRedirects(false);
            conn.setConnectTimeout(connectTimeoutMs);
            conn.setReadTimeout(readTimeoutMs);
            conn.setUseCaches(false);
            conn.setRequestProperty("Cache-Control", "no-cache");
            conn.setRequestProperty("Pragma", "no-cache");
            if (trackDownload) {
                activeDownloadConn = conn;
            }
            int code = conn.getResponseCode();
            if (code == HttpURLConnection.HTTP_MOVED_PERM
                    || code == HttpURLConnection.HTTP_MOVED_TEMP
                    || code == HttpURLConnection.HTTP_SEE_OTHER
                    || code == 307
                    || code == 308) {
                String location = conn.getHeaderField("Location");
                conn.disconnect();
                if (trackDownload && activeDownloadConn == conn) {
                    activeDownloadConn = null;
                }
                if (location == null || location.trim().isEmpty()) {
                    throw new IOException("Redirect without Location");
                }
                currentUrl = new URL(currentUrl, location.trim());
                continue;
            }
            return conn;
        }
        throw new IOException("Too many redirects");
    }

    private static byte[] readBounded(InputStream is, int maxBytes) throws IOException {
        try (InputStream in = is; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int total = 0;
            int read;
            while ((read = in.read(buf)) > 0) {
                total += read;
                if (total > maxBytes) {
                    return null;
                }
                out.write(buf, 0, read);
            }
            return out.toByteArray();
        }
    }

    private static String optCleanString(JSONObject obj, String key) {
        if (obj.isNull(key)) {
            return "";
        }
        return obj.optString(key, "").trim();
    }

    private static boolean canInstallPackages(Context ctx) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O
                || ctx.getPackageManager().canRequestPackageInstalls();
    }

    private static boolean hasValidApkHeader(File file) {
        if (file == null || !file.isFile() || file.length() <= 4) {
            return false;
        }
        byte[] magic = new byte[4];
        try (FileInputStream fis = new FileInputStream(file)) {
            int read = fis.read(magic);
            return read == 4
                    && magic[0] == 0x50
                    && magic[1] == 0x4B
                    && magic[2] == 0x03
                    && magic[3] == 0x04;
        } catch (Exception e) {
            return false;
        }
    }

    private static void cleanupOldApks(Context ctx, File keep) {
        File dir = new File(ctx.getCacheDir(), UPDATES_DIR);
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        String keepPath = keep == null ? null : keep.getAbsolutePath();
        for (File f : files) {
            if (keepPath == null || !keepPath.equals(f.getAbsolutePath())) {
                deleteQuietly(f);
            }
        }
    }

    private static void deleteQuietly(File f) {
        if (f != null && f.exists()) {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        }
    }

    private static void disconnectAsync(HttpURLConnection conn) {
        if (conn == null) {
            return;
        }
        new Thread(() -> {
            try {
                conn.disconnect();
            } catch (Exception ignored) {
            }
        }, "gk-update-disconnect").start();
    }

    private SharedPreferences prefs() {
        return appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private void savePendingInstall(File apkFile, int versionCode) {
        prefs().edit()
                .putString(KEY_PENDING_PATH, apkFile.getAbsolutePath())
                .putInt(KEY_PENDING_CODE, versionCode)
                .apply();
    }

    private void clearPendingInstall() {
        prefs().edit()
                .remove(KEY_PENDING_PATH)
                .remove(KEY_PENDING_CODE)
                .apply();
    }

    private Activity aliveActivity() {
        Activity act = activityRef.get();
        if (act == null || act.isFinishing() || act.isDestroyed()) {
            return null;
        }
        return act;
    }

    private void dismissActiveDialog() {
        AlertDialog dialog = activeDialog;
        activeDialog = null;
        if (dialog != null) {
            try {
                if (dialog.isShowing()) {
                    dialog.dismiss();
                }
            } catch (Exception ignored) {
            }
        }
    }
}
