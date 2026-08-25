package com.qxplays.player;

import android.content.Context;
import android.net.Uri;
import android.os.Build;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;

/** Small utilities: IO, hashing, paths. */
public final class Utils {
    private Utils() {}

    public static void copy(InputStream in, OutputStream out) throws Exception {
        byte[] buf = new byte[64 * 1024];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        out.flush();
    }

    public static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        copy(in, bos);
        return bos.toByteArray();
    }

    public static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(s.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) { return String.valueOf(s.hashCode()); }
    }

    public static String base64(byte[] b) { return android.util.Base64.encodeToString(b, android.util.Base64.NO_WRAP); }
    public static byte[] unbase64(String s) { return android.util.Base64.decode(s, android.util.Base64.NO_WRAP); }

    public static String extOf(String name) {
        int i = name.lastIndexOf('.');
        if (i < 0) return "";
        return name.substring(i + 1).toLowerCase(java.util.Locale.US);
    }

    public static String baseName(String name) {
        int i = name.lastIndexOf('.');
        return i > 0 ? name.substring(0, i) : name;
    }

    public static String safeName(String name) {
        return name.replaceAll("[^a-zA-Z0-9._ -]", "_");
    }

    /** Resolve a displayable file path for a MediaStore content uri (may be null on scoped storage). */
    public static String dataPathFor(Context ctx, Uri uri) {
        if (uri == null) return null;
        if ("file".equals(uri.getScheme())) return uri.getPath();
        if ("content".equals(uri.getScheme())) {
            try (android.database.Cursor c = ctx.getContentResolver().query(
                    uri, new String[]{ android.provider.MediaStore.MediaColumns.DATA }, null, null, null)) {
                if (c != null && c.moveToFirst()) return c.getString(0);
            } catch (Exception ignored) {}
        }
        return null;
    }

    /** Open an input stream for a uri (content or file). */
    public static InputStream openInput(Context ctx, String uri) throws Exception {
        if (uri.startsWith("file://")) return new FileInputStream(uri.substring(7));
        return ctx.getContentResolver().openInputStream(Uri.parse(uri));
    }

    public static boolean isVideo(String mime) {
        return mime != null && mime.startsWith("video/");
    }

    public static boolean isAudio(String mime) {
        return mime != null && mime.startsWith("audio/");
    }

    public static boolean isSupportedMedia(String name) {
        String e = extOf(name);
        switch (e) {
            case "mp4": case "m4v": case "mkv": case "webm": case "3gp": case "ts": case "m2ts":
            case "avi": case "mov": case "flv": case "mpg": case "mpeg": case "wmv": case "ogv":
            case "mp3": case "m4a": case "aac": case "flac": case "wav": case "ogg": case "opus":
            case "amr": case "mid": case "midi": case "xmf": case "mka": case "weba": case "ape":
            case "wma": case "m4b": case "aiff": case "aif": case "ac3": case "eac3": case "dts":
            case "3gpp":
                return true;
        }
        return false;
    }

    public static void deleteQuiet(File f) {
        if (f != null) { try { f.delete(); } catch (Exception ignored) {} }
    }

    public static long dirSize(File dir) {
        long total = 0;
        File[] files = dir.listFiles();
        if (files == null) return 0;
        for (File f : files) {
            if (f.isDirectory()) total += dirSize(f); else total += f.length();
        }
        return total;
    }

    public static boolean isAtLeast(int api) { return Build.VERSION.SDK_INT >= api; }
}
