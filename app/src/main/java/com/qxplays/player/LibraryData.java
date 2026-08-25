package com.qxplays.player;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Holds the current library contents; refresh runs off the main thread. */
public class LibraryData {
    public interface Listener { void onLibraryChanged(); }

    private static final List<Listener> LISTENERS = new CopyOnWriteArrayList<>();
    private static final ExecutorService POOL = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public static volatile List<MediaItem> videos = new ArrayList<>();
    public static volatile List<MediaItem> audio = new ArrayList<>();
    public static volatile boolean loaded = false;
    public static volatile boolean loading = false;

    public static void subscribe(Listener l) {
        if (!LISTENERS.contains(l)) LISTENERS.add(l);
    }
    public static void unsubscribe(Listener l) { LISTENERS.remove(l); }

    public static void refresh(Context ctx, boolean force) {
        if (loading) return;
        loading = true;
        final Context app = ctx.getApplicationContext();
        POOL.execute(() -> {
            List<MediaItem> v = new ArrayList<>();
            List<MediaItem> a = new ArrayList<>();
            try {
                if (Perms.hasMediaRead(app)) {
                    v = Library.scanVideos(app);
                    a = Library.scanAudio(app);
                    Library.sort(v, 0);
                    Library.sort(a, 0);
                }
            } catch (Exception ignored) {}
            videos = v;
            audio = a;
            loaded = true;
            loading = false;
            MAIN.post(() -> { for (Listener l : LISTENERS) l.onLibraryChanged(); });
        });
    }

    public static MediaItem findByUri(String uri) {
        if (uri == null) return null;
        for (MediaItem it : videos) if (uri.equals(it.uri)) return it;
        for (MediaItem it : audio) if (uri.equals(it.uri)) return it;
        return null;
    }
}
