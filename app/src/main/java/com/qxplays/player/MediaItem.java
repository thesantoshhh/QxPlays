package com.qxplays.player;

import android.content.Context;
import android.net.Uri;
import android.provider.MediaStore;

import org.json.JSONObject;

/** One playable media file (video or audio) from the library, a folder, or a playlist. */
public class MediaItem {
    public long id;              // MediaStore id (or -1 for SAF / file items)
    public String uri;           // playable uri string
    public String name;
    public String mime;
    public long durationMs;
    public long size;
    public long dateAdded;       // unix seconds
    public String folder;        // parent folder name
    public int width, height;
    public boolean isVideo;
    public String dataPath;      // raw file path when known (may be null)

    public static MediaItem fromCursor(Context ctx, android.database.Cursor c, boolean video) {
        MediaItem it = new MediaItem();
        it.isVideo = video;
        it.id = c.getLong(c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID));
        it.name = c.getString(c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME));
        it.mime = c.getString(c.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE));
        it.size = c.getLong(c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE));
        int dDur = c.getColumnIndex(MediaStore.MediaColumns.DURATION);
        it.durationMs = dDur >= 0 ? c.getLong(dDur) : 0;
        int dDate = c.getColumnIndex(MediaStore.MediaColumns.DATE_ADDED);
        it.dateAdded = dDate >= 0 ? c.getLong(dDate) : 0;
        int dData = c.getColumnIndex(MediaStore.MediaColumns.DATA);
        it.dataPath = dData >= 0 && !c.isNull(dData) ? c.getString(dData) : null;
        int dRel = c.getColumnIndex("relative_path");
        String rel = dRel >= 0 && !c.isNull(dRel) ? c.getString(dRel) : null;
        it.folder = folderNameFrom(rel, it.dataPath);
        int dW = c.getColumnIndex(MediaStore.MediaColumns.WIDTH);
        int dH = c.getColumnIndex(MediaStore.MediaColumns.HEIGHT);
        it.width = dW >= 0 ? c.getInt(dW) : 0;
        it.height = dH >= 0 ? c.getInt(dH) : 0;
        Uri base = video ? MediaStore.Video.Media.EXTERNAL_CONTENT_URI : MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        it.uri = android.content.ContentUris.withAppendedId(base, it.id).toString();
        return it;
    }

    /** A manually built item (SAF tree, direct file, playlist entries). */
    public static MediaItem fromFile(java.io.File f, boolean video) {
        MediaItem it = new MediaItem();
        it.id = -1;
        it.uri = Uri.fromFile(f).toString();
        it.name = f.getName();
        it.mime = video ? "video/*" : "audio/*";
        it.size = f.length();
        it.dateAdded = f.lastModified() / 1000;
        it.folder = f.getParentFile() == null ? "" : f.getParentFile().getName();
        it.isVideo = video;
        it.dataPath = f.getAbsolutePath();
        return it;
    }

    public static MediaItem fromSaf(Context ctx, android.net.Uri docUri, String mime, long size, long modified, String displayName) {
        MediaItem it = new MediaItem();
        it.id = -1;
        it.uri = docUri.toString();
        if (displayName != null && !displayName.isEmpty()) {
            it.name = displayName;
        } else {
            it.name = android.provider.DocumentsContract.getDocumentId(docUri);
            int cut = it.name.lastIndexOf(':');
            if (cut >= 0) it.name = it.name.substring(cut + 1);
        }
        it.mime = mime;
        it.size = size;
        it.dateAdded = modified / 1000;
        it.folder = "";
        it.isVideo = mime != null && mime.startsWith("video/");
        return it;
    }

    private static String folderNameFrom(String relativePath, String dataPath) {
        String p = relativePath;
        if (p == null && dataPath != null) {
            int cut = dataPath.lastIndexOf('/');
            p = cut >= 0 ? dataPath.substring(0, cut) : "";
        }
        if (p == null || p.isEmpty()) return "Internal storage";
        p = p.replaceAll("/+$", "");
        int cut = p.lastIndexOf('/');
        String name = cut >= 0 ? p.substring(cut + 1) : p;
        return name.isEmpty() ? "Internal storage" : name;
    }

    public String subtitleSidecar() {
        if (dataPath == null) return null;
        java.io.File f = new java.io.File(Utils.baseName(dataPath) + ".srt");
        return f.exists() ? f.getAbsolutePath() : null;
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("uri", uri);
            o.put("name", name);
            o.put("mime", mime == null ? "" : mime);
            o.put("dur", durationMs);
            o.put("video", isVideo);
        } catch (Exception ignored) {}
        return o;
    }

    public static MediaItem fromJson(JSONObject o) {
        MediaItem it = new MediaItem();
        it.uri = o.optString("uri");
        it.name = o.optString("name");
        it.mime = o.optString("mime");
        it.durationMs = o.optLong("dur", 0);
        it.isVideo = o.optBoolean("video", false);
        it.id = -1;
        it.folder = "";
        return it;
    }
}
