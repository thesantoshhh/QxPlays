package com.qxplays.player;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.List;

/** Runtime permission helper. */
public final class Perms {
    public static final String READ_MEDIA_VIDEO = "android.permission.READ_MEDIA_VIDEO";
    public static final String READ_MEDIA_AUDIO = "android.permission.READ_MEDIA_AUDIO";
    public static final String READ_EXTERNAL_STORAGE = "android.permission.READ_EXTERNAL_STORAGE";
    public static final String POST_NOTIFICATIONS = "android.permission.POST_NOTIFICATIONS";
    public static final String RECORD_AUDIO = "android.permission.RECORD_AUDIO";

    private Perms() {}

    /** Core permission needed to see the media library at all. */
    public static String[] mediaRead() {
        if (Build.VERSION.SDK_INT >= 33) return new String[]{ READ_MEDIA_VIDEO, READ_MEDIA_AUDIO };
        if (Build.VERSION.SDK_INT >= 24) return new String[]{ READ_EXTERNAL_STORAGE };
        return new String[0];
    }

    public static boolean hasMediaRead(Context ctx) {
        for (String p : mediaRead()) if (ctx.checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) return false;
        return true;
    }

    public static boolean has(Context ctx, String p) {
        return ctx.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean hasNotifications(Context ctx) {
        return Build.VERSION.SDK_INT < 33 || has(ctx, POST_NOTIFICATIONS);
    }

    public static boolean hasRecordAudio(Context ctx) { return has(ctx, RECORD_AUDIO); }

    /** Whether the app can freely browse shared storage (full file manager mode). */
    public static boolean hasAllFiles(Context ctx) {
        if (Build.VERSION.SDK_INT >= 30) return Environment.isExternalStorageManager();
        return has(ctx, READ_EXTERNAL_STORAGE); // legacy: read is enough to list
    }

    /** List of every runtime permission the app may request, with human rationale. */
    public static List<PermInfo> allPermissions(Context ctx) {
        List<PermInfo> out = new ArrayList<>();
        out.add(new PermInfo("Media library — videos", READ_MEDIA_VIDEO,
                "Scan and play your video files.", mediaReadGranted(ctx, READ_MEDIA_VIDEO)));
        out.add(new PermInfo("Media library — music", READ_MEDIA_AUDIO,
                "Scan and play your music and audio files.", mediaReadGranted(ctx, READ_MEDIA_AUDIO)));
        if (Build.VERSION.SDK_INT >= 33) {
            out.add(new PermInfo("Notifications", POST_NOTIFICATIONS,
                    "Show the playback controls in the notification shade.", has(ctx, POST_NOTIFICATIONS)));
        }
        out.add(new PermInfo("Microphone (optional)", RECORD_AUDIO,
                "Powers the live music visualizer. Audio is never recorded or stored.",
                has(ctx, RECORD_AUDIO)));
        if (Build.VERSION.SDK_INT >= 30) {
            out.add(new PermInfo("All files access (optional)", null,
                    "Enables the built-in file browser over your entire storage. Optional; the library works without it.",
                    hasAllFiles(ctx)));
        }
        return out;
    }

    private static boolean mediaReadGranted(Context ctx, String p) {
        if (Build.VERSION.SDK_INT >= 33) return has(ctx, p);
        return has(ctx, READ_EXTERNAL_STORAGE);
    }

    public static boolean[] grantResults(Activity act, String[] perms) {
        boolean[] out = new boolean[perms.length];
        for (int i = 0; i < perms.length; i++) {
            out[i] = act.checkSelfPermission(perms[i]) == PackageManager.PERMISSION_GRANTED;
        }
        return out;
    }

    public static void openAllFilesSettings(Activity act) {
        try {
            Intent i = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:" + act.getPackageName()));
            act.startActivity(i);
        } catch (Exception e) {
            try { act.startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)); }
            catch (Exception ignored) {}
        }
    }

    public static void openAppSettings(Activity act) {
        try {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + act.getPackageName()));
            act.startActivity(i);
        } catch (Exception ignored) {}
    }

    public static class PermInfo {
        public final String label;
        public final String permission;
        public final String rationale;
        public boolean granted;

        PermInfo(String label, String permission, String rationale, boolean granted) {
            this.label = label;
            this.permission = permission;
            this.rationale = rationale;
            this.granted = granted;
        }
    }
}
