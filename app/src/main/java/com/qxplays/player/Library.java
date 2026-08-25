package com.qxplays.player;

import android.content.Context;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Media library access: MediaStore scan + direct file browsing. */
public class Library {

    public static List<MediaItem> scanVideos(Context ctx) {
        return scan(ctx, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true);
    }

    public static List<MediaItem> scanAudio(Context ctx) {
        return scan(ctx, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, false);
    }

    private static List<MediaItem> scan(Context ctx, Uri base, boolean video) {
        List<MediaItem> out = new ArrayList<>();
        String[] proj;
        if (Build.VERSION.SDK_INT >= 29) {
            proj = new String[]{
                    MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME,
                    MediaStore.MediaColumns.MIME_TYPE, MediaStore.MediaColumns.SIZE,
                    MediaStore.MediaColumns.DURATION, MediaStore.MediaColumns.DATE_ADDED,
                    MediaStore.MediaColumns.DATA, "relative_path",
                    MediaStore.MediaColumns.WIDTH, MediaStore.MediaColumns.HEIGHT
            };
        } else {
            proj = new String[]{
                    MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME,
                    MediaStore.MediaColumns.MIME_TYPE, MediaStore.MediaColumns.SIZE,
                    MediaStore.MediaColumns.DURATION, MediaStore.MediaColumns.DATE_ADDED,
                    MediaStore.MediaColumns.DATA,
                    MediaStore.MediaColumns.WIDTH, MediaStore.MediaColumns.HEIGHT
            };
        }
        try (Cursor c = ctx.getContentResolver().query(base, proj, null, null, null)) {
            if (c != null) {
                while (c.moveToNext()) {
                    try {
                        MediaItem it = MediaItem.fromCursor(ctx, c, video);
                        if (it.durationMs > 0 && it.name != null) out.add(it);
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception ignored) {}
        return out;
    }

    public interface Sort {
        int NAME_ASC = 0, NAME_DESC = 1, DATE_NEW = 2, DATE_OLD = 3, SIZE_BIG = 4, SIZE_SMALL = 5, DUR_LONG = 6, DUR_SHORT = 7;
    }

    public static void sort(List<MediaItem> items, int mode) {
        Comparator<MediaItem> cmp;
        switch (mode) {
            case Sort.NAME_DESC:
                cmp = (a, b) -> b.name.compareToIgnoreCase(a.name); break;
            case Sort.DATE_NEW:
                cmp = (a, b) -> Long.compare(b.dateAdded, a.dateAdded); break;
            case Sort.DATE_OLD:
                cmp = (a, b) -> Long.compare(a.dateAdded, b.dateAdded); break;
            case Sort.SIZE_BIG:
                cmp = (a, b) -> Long.compare(b.size, a.size); break;
            case Sort.SIZE_SMALL:
                cmp = (a, b) -> Long.compare(a.size, b.size); break;
            case Sort.DUR_LONG:
                cmp = (a, b) -> Long.compare(b.durationMs, a.durationMs); break;
            case Sort.DUR_SHORT:
                cmp = (a, b) -> Long.compare(a.durationMs, b.durationMs); break;
            default:
                cmp = (a, b) -> a.name.compareToIgnoreCase(b.name);
        }
        Collections.sort(items, cmp);
    }

    public static List<String> folders(List<MediaItem> items) {
        List<String> out = new ArrayList<>();
        for (MediaItem it : items) {
            String f = it.folder == null ? "Internal storage" : it.folder;
            if (!out.contains(f)) out.add(f);
        }
        Collections.sort(out, String::compareToIgnoreCase);
        return out;
    }

    public static List<MediaItem> inFolder(List<MediaItem> items, String folder) {
        List<MediaItem> out = new ArrayList<>();
        for (MediaItem it : items) {
            String f = it.folder == null ? "Internal storage" : it.folder;
            if (f.equals(folder)) out.add(it);
        }
        return out;
    }

    public static boolean hasDirectFileAccess(Context ctx) {
        return Perms.hasAllFiles(ctx);
    }

    /** Recursive listing of a directory for the built-in file browser (videos + audio). */
    public static List<MediaItem> browseFiles(File dir, int depth) {
        List<MediaItem> out = new ArrayList<>();
        File[] files = dir.listFiles();
        if (files == null) return out;
        for (File f : files) {
            try {
                if (f.isDirectory()) {
                    if (depth < 6) out.addAll(browseFiles(f, depth + 1));
                } else if (Utils.isSupportedMedia(f.getName())) {
                    String ext = Utils.extOf(f.getName());
                    boolean video = !(ext.equals("mp3") || ext.equals("m4a") || ext.equals("aac")
                            || ext.equals("flac") || ext.equals("wav") || ext.equals("ogg")
                            || ext.equals("opus") || ext.equals("amr") || ext.equals("mid")
                            || ext.equals("midi") || ext.equals("xmf") || ext.equals("mka")
                            || ext.equals("wma") || ext.equals("aiff") || ext.equals("aif")
                            || ext.equals("m4b") || ext.equals("ape") || ext.equals("weba"));
                    out.add(MediaItem.fromFile(f, video));
                }
            } catch (Exception ignored) {}
        }
        return out;
    }

    /** Embedded album art for audio (file or content uri). */
    public static android.graphics.Bitmap albumArt(Context ctx, String uri) {
        MediaMetadataRetriever r = new MediaMetadataRetriever();
        try {
            if (uri.startsWith("file://")) {
                r.setDataSource(uri.substring(7));
            } else {
                r.setDataSource(ctx, Uri.parse(uri));
            }
            if (Build.VERSION.SDK_INT >= 28) {
                byte[] art = r.getEmbeddedPicture();
                if (art != null && art.length > 0) {
                    android.graphics.Bitmap b = android.graphics.BitmapFactory.decodeByteArray(art, 0, art.length);
                    if (b != null) return b;
                }
            }
        } catch (Exception ignored) {
        } finally {
            try { r.release(); } catch (Exception ignored) {}
        }
        return null;
    }

    /** Video frame thumbnail. */
    public static android.graphics.Bitmap videoFrame(Context ctx, String uri, int maxW) {
        MediaMetadataRetriever r = new MediaMetadataRetriever();
        try {
            if (uri.startsWith("file://")) {
                r.setDataSource(uri.substring(7));
            } else {
                r.setDataSource(ctx, Uri.parse(uri));
            }
            android.graphics.Bitmap frame;
            if (Build.VERSION.SDK_INT >= 27) {
                frame = r.getScaledFrameAtTime(1000,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC, maxW, maxW);
            } else {
                frame = r.getFrameAtTime(1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                if (frame != null) {
                    int w = frame.getWidth();
                    if (w > maxW) {
                        int h = frame.getHeight() * maxW / w;
                        frame = android.graphics.Bitmap.createScaledBitmap(frame, maxW, h, true);
                    }
                }
            }
            return frame;
        } catch (Exception ignored) {
            return null;
        } finally {
            try { r.release(); } catch (Exception ignored) {}
        }
    }

    /** Probe real metadata for the Details sheet. */
    public static java.util.Map<String, String> probe(Context ctx, String uri) {
        java.util.Map<String, String> out = new java.util.LinkedHashMap<>();
        MediaMetadataRetriever r = new MediaMetadataRetriever();
        try {
            if (uri.startsWith("file://")) r.setDataSource(uri.substring(7));
            else r.setDataSource(ctx, Uri.parse(uri));
            String[][] keys = {
                    {"android.media.metadata.TITLE", "Title"},
                    {"android.media.metadata.ARTIST", "Artist"},
                    {"android.media.metadata.ALBUM", "Album"},
                    {"android.media.metadata.YEAR", "Year"},
                    {"android.media.metadata.DURATION", "Duration"},
                    {"android.media.metadata.BITRATE", "Bitrate"},
                    {"android.media.metadata.VIDEO_WIDTH", "Width"},
                    {"android.media.metadata.VIDEO_HEIGHT", "Height"},
                    {"android.media.metadata.MIMETYPE", "MIME"},
                    {"android.media.metadata.SAMPLERATE", "Sample rate"},
            };
            for (String[] kv : keys) {
                String v = extractMeta(r, kv[0]);
                if (v != null && !v.isEmpty() && !v.equals("0")) out.put(kv[1], v);
            }
        } catch (Exception ignored) {
        } finally {
            try { r.release(); } catch (Exception ignored) {}
        }
        return out;
    }

    /**
     * extractMetadata(String) is the real modern API on devices; some android.jar
     * stubs only expose the ancient int-based variant, so call reflectively.
     */
    private static String extractMeta(MediaMetadataRetriever r, String key) {
        try {
            java.lang.reflect.Method m = MediaMetadataRetriever.class.getMethod("extractMetadata", String.class);
            Object v = m.invoke(r, key);
            return v == null ? null : String.valueOf(v);
        } catch (Exception e) {
            return null;
        }
    }
}
