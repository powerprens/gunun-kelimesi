package com.yusaa.gununkelimesi;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Dependency-free read-only ContentProvider serving downloaded update APKs
 * from the app's cache/updates/ directory via content:// URIs.
 */
public class ApkProvider extends ContentProvider {

    private static final String UPDATES_DIR = "updates";
    private static final String AUTHORITY_SUFFIX = ".apkprovider";
    private static final String APK_MIME = "application/vnd.android.package-archive";
    private static final String[] COLUMNS = {
            OpenableColumns.DISPLAY_NAME,
            OpenableColumns.SIZE
    };

    public static String getAuthority(Context context) {
        return context.getPackageName() + AUTHORITY_SUFFIX;
    }

    /**
     * Builds a content:// URI for an APK located directly inside cache/updates/.
     * Throws IllegalArgumentException if the file is outside cache/updates/ or not an .apk file.
     */
    public static Uri getUriForFile(Context context, File apkFile) {
        if (context == null || apkFile == null) {
            throw new IllegalArgumentException("Context and APK file must not be null");
        }
        try {
            File updatesDir = new File(context.getCacheDir(), UPDATES_DIR).getCanonicalFile();
            File canonicalFile = apkFile.getCanonicalFile();
            File parent = canonicalFile.getParentFile();
            if (parent == null || !parent.equals(updatesDir)) {
                throw new IllegalArgumentException(
                        "APK file must reside in cache/" + UPDATES_DIR + ": " + apkFile);
            }
            String name = canonicalFile.getName();
            if (!isValidApkFileName(name)) {
                throw new IllegalArgumentException("Invalid APK file name: " + name);
            }
            return new Uri.Builder()
                    .scheme(ContentResolver.SCHEME_CONTENT)
                    .authority(getAuthority(context))
                    .appendPath(name)
                    .build();
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to resolve canonical path for " + apkFile, e);
        }
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public void attachInfo(Context context, ProviderInfo info) {
        super.attachInfo(context, info);
        if (info != null) {
            if (info.exported) {
                throw new SecurityException("Provider must not be exported");
            }
            if (!info.grantUriPermissions) {
                throw new SecurityException("Provider must grant uri permissions");
            }
        }
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        File file = resolveApkFile(uri);
        if (file == null) {
            throw new IllegalArgumentException("Invalid APK URI: " + uri);
        }
        String[] requested = (projection != null) ? projection : COLUMNS;
        String[] cols = new String[requested.length];
        Object[] values = new Object[requested.length];
        int i = 0;
        for (String col : requested) {
            if (OpenableColumns.DISPLAY_NAME.equals(col)) {
                cols[i] = OpenableColumns.DISPLAY_NAME;
                values[i++] = file.getName();
            } else if (OpenableColumns.SIZE.equals(col)) {
                cols[i] = OpenableColumns.SIZE;
                values[i++] = file.length();
            }
        }
        cols = Arrays.copyOf(cols, i);
        values = Arrays.copyOf(values, i);
        MatrixCursor cursor = new MatrixCursor(cols, 1);
        if (file.isFile()) {
            cursor.addRow(values);
        }
        return cursor;
    }

    @Override
    public String getType(Uri uri) {
        File file = resolveApkFile(uri);
        return (file != null) ? APK_MIME : null;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) {
            throw new SecurityException("Read-only provider");
        }
        File file = resolveApkFile(uri);
        if (file == null || !file.isFile()) {
            throw new FileNotFoundException("APK not found: " + uri);
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("No external inserts");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("No external deletes");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("No external updates");
    }

    private File resolveApkFile(Uri uri) {
        Context ctx = getContext();
        if (ctx == null || uri == null) {
            return null;
        }
        if (!ContentResolver.SCHEME_CONTENT.equals(uri.getScheme())) {
            return null;
        }
        String authority = uri.getAuthority();
        if (authority == null) {
            return null;
        }
        int atIdx = authority.lastIndexOf('@');
        String hostAuthority = (atIdx >= 0) ? authority.substring(atIdx + 1) : authority;
        if (!getAuthority(ctx).equals(hostAuthority)) {
            return null;
        }
        List<String> segments = uri.getPathSegments();
        if (segments == null || segments.size() != 1) {
            return null;
        }
        String name = segments.get(0);
        if (!isValidApkFileName(name)) {
            return null;
        }
        try {
            File updatesDir = new File(ctx.getCacheDir(), UPDATES_DIR).getCanonicalFile();
            File target = new File(updatesDir, name).getCanonicalFile();
            File parent = target.getParentFile();
            if (parent == null || !parent.equals(updatesDir)) {
                return null;
            }
            return target;
        } catch (IOException e) {
            return null;
        }
    }

    private static boolean isValidApkFileName(String name) {
        if (name == null || name.isEmpty() || name.startsWith(".")) {
            return false;
        }
        if (name.indexOf('/') >= 0 || name.indexOf('\\') >= 0 || name.indexOf('\0') >= 0) {
            return false;
        }
        return name.toLowerCase(Locale.US).endsWith(".apk");
    }
}
