package com.qxplays.player;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Global playback controller. The PlaybackService owns the MediaPlayer;
 * this facade mirrors its state for the UI and forwards commands.
 */
public class Player {

    public static final int STATE_IDLE = 0, STATE_LOADING = 1, STATE_PLAYING = 2, STATE_PAUSED = 3, STATE_COMPLETED = 4, STATE_ERROR = 5;
    public static final int REPEAT_OFF = 0, REPEAT_ALL = 1, REPEAT_ONE = 2;

    public interface Listener {
        void onStateChanged();
        void onTrackChanged();
        void onProgress(long posMs, long durMs);
        void onQueueChanged();
        void onSleepTick(int remainSec);
        void onWave(byte[] wave, int samplingRate);
    }

    private static final List<Listener> LISTENERS = new CopyOnWriteArrayList<>();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    // mirrored state (service is the source of truth)
    public static volatile int state = STATE_IDLE;
    public static volatile int index = -1;
    public static volatile long positionMs;
    public static volatile long durationMs;
    public static volatile boolean buffering;
    public static volatile int repeatMode = REPEAT_OFF;
    public static volatile boolean shuffle;
    public static volatile float speed = 1.0f;
    public static volatile String error = "";
    public static volatile int sleepRemainSec = 0;
    public static volatile String subtitleText = "";
    public static MediaItem current;
    public static List<MediaItem> queue = new ArrayList<>();

    public static boolean isActive() { return state == STATE_PLAYING || state == STATE_PAUSED || state == STATE_LOADING || state == STATE_COMPLETED; }
    public static boolean isPlaying() { return state == STATE_PLAYING; }
    public static boolean hasCurrent() { return current != null; }

    public static void subscribe(Listener l) {
        if (!LISTENERS.contains(l)) LISTENERS.add(l);
        main(() -> {
            l.onStateChanged();
            l.onTrackChanged();
            l.onQueueChanged();
            l.onProgress(positionMs, durationMs);
        });
    }

    public static void unsubscribe(Listener l) { LISTENERS.remove(l); }

    // ---------------- commands ----------------

    public static void playQueue(Context ctx, List<MediaItem> items, int startIndex) {
        if (items == null || items.isEmpty()) return;
        Intent i = new Intent(ctx, PlaybackService.class).setAction(PlaybackService.ACTION_PLAY_QUEUE);
        JSONArray arr = new JSONArray();
        for (MediaItem it : items) arr.put(it.toJson());
        i.putExtra("queue", arr.toString());
        i.putExtra("index", Math.max(0, Math.min(startIndex, items.size() - 1)));
        startService(ctx, i);
    }

    public static void playItem(Context ctx, MediaItem item) {
        List<MediaItem> list = new ArrayList<>();
        list.add(item);
        playQueue(ctx, list, 0);
    }

    public static void playAll(Context ctx, List<MediaItem> items) { playQueue(ctx, items, 0); }

    public static void appendAndPlay(Context ctx, MediaItem item) {
        List<MediaItem> list = new ArrayList<>(queue);
        list.add(item);
        playQueue(ctx, list, list.size() - 1);
    }

    public static void toggle(Context ctx) { send(ctx, PlaybackService.ACTION_TOGGLE); }
    public static void next(Context ctx) { send(ctx, PlaybackService.ACTION_NEXT); }
    public static void prev(Context ctx) { send(ctx, PlaybackService.ACTION_PREV); }
    public static void pause(Context ctx) { send(ctx, PlaybackService.ACTION_PAUSE); }
    public static void resume(Context ctx) { send(ctx, PlaybackService.ACTION_RESUME); }
    public static void stop(Context ctx) { send(ctx, PlaybackService.ACTION_STOP); }
    public static void seek(Context ctx, long ms) {
        Intent i = new Intent(ctx, PlaybackService.class).setAction(PlaybackService.ACTION_SEEK);
        i.putExtra("pos", ms);
        startService(ctx, i);
    }
    public static void setSpeed(Context ctx, float s) {
        Intent i = new Intent(ctx, PlaybackService.class).setAction(PlaybackService.ACTION_SPEED);
        i.putExtra("speed", s);
        startService(ctx, i);
    }
    public static void setRepeat(Context ctx, int mode) {
        Intent i = new Intent(ctx, PlaybackService.class).setAction(PlaybackService.ACTION_REPEAT);
        i.putExtra("mode", mode);
        startService(ctx, i);
    }
    public static void setShuffle(Context ctx, boolean on) {
        Intent i = new Intent(ctx, PlaybackService.class).setAction(PlaybackService.ACTION_SHUFFLE);
        i.putExtra("on", on);
        startService(ctx, i);
    }
    public static void playIndex(Context ctx, int idx) {
        Intent i = new Intent(ctx, PlaybackService.class).setAction(PlaybackService.ACTION_PLAY_INDEX);
        i.putExtra("index", idx);
        startService(ctx, i);
    }
    public static void removeFromQueue(Context ctx, int idx) {
        Intent i = new Intent(ctx, PlaybackService.class).setAction(PlaybackService.ACTION_REMOVE);
        i.putExtra("index", idx);
        startService(ctx, i);
    }
    public static void setSleepTimer(Context ctx, int minutes) {
        Intent i = new Intent(ctx, PlaybackService.class).setAction(PlaybackService.ACTION_SLEEP);
        i.putExtra("minutes", minutes);
        startService(ctx, i);
    }
    public static void attachSurface(Context ctx, android.view.Surface surface) {
        PlaybackService.setSurfaceFromUi(surface);
    }
    public static void notifyVideoClosed(Context ctx, boolean keepPlaying) {
        Intent i = new Intent(ctx, PlaybackService.class).setAction(PlaybackService.ACTION_VIDEO_CLOSED);
        i.putExtra("keep", keepPlaying);
        startService(ctx, i);
    }

    private static void send(Context ctx, String action) {
        startService(ctx, new Intent(ctx, PlaybackService.class).setAction(action));
    }

    private static void startService(Context ctx, Intent i) {
        try {
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i);
            else ctx.startService(i);
        } catch (Exception ignored) {}
    }

    // ---------------- service -> ui mirror ----------------

    public static void setMirroredState(int s, int idx, MediaItem cur, List<MediaItem> q) {
        state = s;
        index = idx;
        current = cur;
        queue = q == null ? new ArrayList<>() : q;
        MAIN.post(() -> {
            for (Listener l : LISTENERS) l.onTrackChanged();
            for (Listener l : LISTENERS) l.onQueueChanged();
            for (Listener l : LISTENERS) l.onStateChanged();
        });
    }

    public static void setPlayState(int s) {
        state = s;
        MAIN.post(() -> { for (Listener l : LISTENERS) l.onStateChanged(); });
    }

    public static void setProgress(long pos, long dur) {
        positionMs = pos;
        durationMs = dur;
        MAIN.post(() -> { for (Listener l : LISTENERS) l.onProgress(pos, dur); });
    }

    public static void setSleepRemain(int sec) {
        sleepRemainSec = sec;
        MAIN.post(() -> { for (Listener l : LISTENERS) l.onSleepTick(sec); });
    }

    public static void pushWave(byte[] wave, int rate) {
        MAIN.post(() -> { for (Listener l : LISTENERS) l.onWave(wave, rate); });
    }

    public static void main(Runnable r) { MAIN.post(r); }

    // ---------------- queue helpers for UI ----------------

    public static MediaItem queueAt(int i) {
        return i >= 0 && i < queue.size() ? queue.get(i) : null;
    }

    public static int queueSize() { return queue.size(); }

    public static boolean isFavoriteCurrent() {
        return current != null && Favorites.has(current.uri);
    }
}
