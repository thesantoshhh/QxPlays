package com.qxplays.player;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.audiofx.BassBoost;
import android.media.audiofx.Equalizer;
import android.media.audiofx.LoudnessEnhancer;
import android.media.audiofx.Virtualizer;
import android.media.audiofx.Visualizer;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Surface;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Foreground playback service. Owns the MediaPlayer, media session, audio focus,
 * equalizer, visualizer, sleep timer and the playback notification.
 * Fully offline: no network anywhere.
 */
public class PlaybackService extends Service {

    public static final String ACTION_PLAY_QUEUE = "com.qxplays.player.PLAY_QUEUE";
    public static final String ACTION_TOGGLE = "com.qxplays.player.TOGGLE";
    public static final String ACTION_PLAY = "com.qxplays.player.PLAY";
    public static final String ACTION_PAUSE = "com.qxplays.player.PAUSE";
    public static final String ACTION_RESUME = "com.qxplays.player.RESUME";
    public static final String ACTION_NEXT = "com.qxplays.player.NEXT";
    public static final String ACTION_PREV = "com.qxplays.player.PREV";
    public static final String ACTION_SEEK = "com.qxplays.player.SEEK";
    public static final String ACTION_SPEED = "com.qxplays.player.SPEED";
    public static final String ACTION_REPEAT = "com.qxplays.player.REPEAT";
    public static final String ACTION_SHUFFLE = "com.qxplays.player.SHUFFLE";
    public static final String ACTION_PLAY_INDEX = "com.qxplays.player.PLAY_INDEX";
    public static final String ACTION_REMOVE = "com.qxplays.player.REMOVE";
    public static final String ACTION_SLEEP = "com.qxplays.player.SLEEP";
    public static final String ACTION_STOP = "com.qxplays.player.STOP";
    public static final String ACTION_VIDEO_CLOSED = "com.qxplays.player.VIDEO_CLOSED";
    public static final String ACTION_SUBTITLE = "com.qxplays.player.SUBTITLE";

    private static final int NOTIF_ID = 42;
    public static final String CHANNEL_ID = "qxplays_playback";

    private MediaPlayer mp;
    private MediaSession session;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final List<MediaItem> queue = new ArrayList<>();
    private int index = -1;
    private MediaItem current;
    private boolean resumeOnFocusGain;
    private Surface videoSurface;

    private AudioManager am;
    private AudioFocusRequest focusRequest;
    private boolean focusGranted;

    private Equalizer eq;
    private BassBoost bass;
    private Virtualizer virt;
    private LoudnessEnhancer loud;
    private Visualizer viz;

    private Runnable ticker;
    private Runnable sleepRunnable;
    private Runnable sleepTicker;
    private long sleepDeadline;

    private static volatile Service instance;

    public static Service get() { return instance; }

    public static void setSurfaceFromUi(Surface s) {
        Service svc = instance;
        if (svc instanceof PlaybackService) ((PlaybackService) svc).attachSurface(s);
    }

    // ---------------------------------------------------------------- lifecycle

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        am = (AudioManager) getSystemService(AUDIO_SERVICE);
        createChannel();
        session = new MediaSession(this, "QxPlays");
        session.setCallback(new MediaSession.Callback() {
            @Override public void onPlay() { resume(); }
            @Override public void onPause() { pause(true); }
            @Override public void onSkipToNext() { next(); }
            @Override public void onSkipToPrevious() { prev(); }
            @Override public void onSeekTo(long pos) { seekTo(pos); }
            @Override public void onStop() { stopPlayback(); }
        });
        session.setActive(true);

        registerReceiver(noisy, new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));
        if (Build.VERSION.SDK_INT >= 26) {
            registerReceiver(noisy, new IntentFilter("android.media.AUDIO_BECOMING_NOISY"));
        }

        ticker = () -> {
            try {
                if (mp != null && Player.isPlaying()) {
                    Player.setProgress(mp.getCurrentPosition(), mp.getDuration());
                }
            } catch (Exception ignored) {}
            if (mp != null) h.postDelayed(ticker, 500);
        };
        h.postDelayed(ticker, 500);
    }

    private final BroadcastReceiver noisy = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (Player.isPlaying()) pause(true);
        }
    };

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        ensureForeground();
        String action = intent == null ? null : intent.getAction();
        if (action == null) return START_NOT_STICKY;

        switch (action) {
            case ACTION_PLAY_QUEUE: {
                String json = intent.getStringExtra("queue");
                int idx = intent.getIntExtra("index", 0);
                try {
                    JSONArray arr = new JSONArray(json);
                    List<MediaItem> items = new ArrayList<>();
                    for (int i = 0; i < arr.length(); i++) {
                        MediaItem it = MediaItem.fromJson(arr.getJSONObject(i));
                        if (it.uri != null && !it.uri.isEmpty()) items.add(it);
                    }
                    if (!items.isEmpty()) {
                        setQueue(items, idx, true);
                        loadAndPlay(idx, true);
                    }
                } catch (Exception ignored) {}
                break;
            }
            case ACTION_PLAY_INDEX: {
                int idx = intent.getIntExtra("index", -1);
                if (idx >= 0 && idx < queue.size()) loadAndPlay(idx, false);
                break;
            }
            case ACTION_TOGGLE: if (Player.isPlaying()) pause(true); else resume(); break;
            case ACTION_PLAY: case ACTION_RESUME: resume(); break;
            case ACTION_PAUSE: pause(true); break;
            case ACTION_NEXT: next(); break;
            case ACTION_PREV: prev(); break;
            case ACTION_SEEK: seekTo(intent.getLongExtra("pos", 0)); break;
            case ACTION_SPEED: {
                Player.speed = intent.getFloatExtra("speed", 1f);
                applySpeed();
                break;
            }
            case ACTION_REPEAT: Player.repeatMode = intent.getIntExtra("mode", 0); break;
            case ACTION_SHUFFLE: Player.shuffle = intent.getBooleanExtra("on", false); break;
            case ACTION_REMOVE: {
                int idx = intent.getIntExtra("index", -1);
                removeFromQueue(idx);
                break;
            }
            case ACTION_SLEEP: {
                int minutes = intent.getIntExtra("minutes", 0);
                setSleepTimer(minutes);
                break;
            }
            case ACTION_SUBTITLE: {
                String uri = intent.getStringExtra("uri");
                addSubtitle(uri);
                break;
            }
            case ACTION_VIDEO_CLOSED: {
                boolean keep = intent.getBooleanExtra("keep", false);
                attachSurface(null);
                if (!keep && Player.isPlaying()) pause(true);
                if (!keep) {
                    stopForegroundCompat();
                    stopSelf();
                }
                break;
            }
            case ACTION_STOP: stopPlayback(); break;
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        try { unregisterReceiver(noisy); } catch (Exception ignored) {}
        releaseEffects();
        if (session != null) { try { session.release(); } catch (Exception ignored) {} }
        if (mp != null) { try { mp.release(); } catch (Exception ignored) {} }
        mp = null;
        instance = null;
        h.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    // ---------------------------------------------------------------- queue

    private void setQueue(List<MediaItem> items, int startIdx, boolean applyRepeatPrefs) {
        queue.clear();
        queue.addAll(items);
        index = startIdx;
        Player.setMirroredState(Player.STATE_IDLE, index, null, queue);
    }

    private MediaItem itemAt(int i) {
        if (queue.isEmpty()) return null;
        return queue.get(Math.floorMod(i, queue.size()));
    }

    private void removeFromQueue(int idx) {
        if (idx < 0 || idx >= queue.size()) return;
        if (idx == index) {
            // removing the playing item → advance first (may stop & clear queue at end)
            next();
            if (queue.isEmpty()) return;
            if (idx >= queue.size()) idx = queue.size() - 1;
        }
        if (idx < index) index--;
        queue.remove(idx);
        if (index >= queue.size()) index = queue.size() - 1;
        Player.setMirroredState(Player.state, index, current, queue);
        updateSession();
        updateNotification();
    }

    private void next() {
        if (queue.isEmpty()) return;
        if (Player.repeatMode == Player.REPEAT_ONE) {
            seekTo(0);
            resume();
            return;
        }
        if (index >= queue.size() - 1) {
            if (Player.repeatMode == Player.REPEAT_ALL) {
                index = 0;
                loadAndPlay(0, false);
            } else {
                // end of queue
                stopPlayback();
            }
            return;
        }
        index++;
        loadAndPlay(index, false);
    }

    private void prev() {
        if (queue.isEmpty()) return;
        try {
            if (mp != null && mp.getCurrentPosition() > 5000) { seekTo(0); return; }
        } catch (Exception ignored) {}
        if (index <= 0) {
            index = queue.size() - 1;
        } else {
            index--;
        }
        loadAndPlay(index, false);
    }

    // ---------------------------------------------------------------- playback

    private void loadAndPlay(int idx, boolean freshStart) {
        MediaItem item = itemAt(idx);
        if (item == null) return;
        current = item;
        index = idx;
        releaseEffects();
        try {
            if (mp != null) mp.reset();
            else {
                mp = new MediaPlayer();
                mp.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(item.isVideo ? AudioAttributes.CONTENT_TYPE_MOVIE : AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build());
                mp.setWakeMode(getApplicationContext(), android.os.PowerManager.PARTIAL_WAKE_LOCK);
                mp.setOnPreparedListener(p -> onPrepared(item, freshStart));
                mp.setOnCompletionListener(p -> onComplete());
                mp.setOnErrorListener((p, what, extra) -> { onError(); return true; });
                mp.setOnInfoListener((p, what, extra) -> {
                    if (what == MediaPlayer.MEDIA_INFO_BUFFERING_START) { Player.buffering = true; Player.setPlayState(Player.state); }
                    else if (what == MediaPlayer.MEDIA_INFO_BUFFERING_END) { Player.buffering = false; Player.setPlayState(Player.state); }
                    return false;
                });
            }
            if (videoSurface != null && item.isVideo) mp.setSurface(videoSurface);
            String uri = item.uri;
            if (uri.startsWith("file://")) mp.setDataSource(uri.substring(7));
            else mp.setDataSource(this, Uri.parse(uri));
            mp.prepareAsync();
            if (freshStart) Player.speed = Prefs.defaultSpeed();
            Player.setMirroredState(Player.STATE_LOADING, index, item, queue);
            updateNotification();
            updateSession();
            requestFocus();
        } catch (Exception e) {
            onError();
        }
    }

    private void onPrepared(MediaItem item, boolean freshStart) {
        applySpeed();
        attachEffects();
        long seekTo = 0;
        if (freshStart) seekTo = History.resumeFor(item.uri);
        try {
            if (seekTo > 0) mp.seekTo((int) seekTo);
            mp.start();
        } catch (Exception ignored) {}
        Player.setMirroredState(Player.STATE_PLAYING, index, item, queue);
        History.record(item, seekTo);
        updateNotification();
        updateSession();
    }

    private void onComplete() {
        History.record(current, 0);
        if (sleepAfterTrack) {
            sleepAfterTrack = false;
            pause(false);
            cancelSleep();
            return;
        }
        if (Player.repeatMode == Player.REPEAT_ONE) {
            seekTo(0);
            resume();
            return;
        }
        if (index < queue.size() - 1 || Player.repeatMode == Player.REPEAT_ALL) {
            next();
        } else {
            Player.setPlayState(Player.STATE_COMPLETED);
            updateSession();
            stopForegroundCompat();
            stopSelf();
        }
    }

    private void onError() {
        Player.error = "Cannot play this file";
        Player.setPlayState(Player.STATE_ERROR);
        // try to skip to the next item once
        if (index < queue.size() - 1) {
            h.postDelayed(() -> { if (Player.state == Player.STATE_ERROR) next(); }, 800);
        }
        updateNotification();
    }

    private void resume() {
        if (current == null) return;
        if (mp == null) { loadAndPlay(index, false); return; }
        try {
            if (!mp.isPlaying()) {
                requestFocus();
                mp.start();
            }
        } catch (Exception e) { onError(); return; }
        Player.setPlayState(Player.STATE_PLAYING);
        updateNotification();
        updateSession();
    }

    private void pause(boolean fromUser) {
        try {
            if (mp != null && mp.isPlaying()) {
                mp.pause();
                if (current != null) History.record(current, mp.getCurrentPosition());
            }
        } catch (Exception ignored) {}
        Player.setPlayState(Player.STATE_PAUSED);
        updateNotification();
        updateSession();
    }

    private void seekTo(long ms) {
        try {
            if (mp != null) {
                mp.seekTo((int) Math.max(0, ms));
                Player.setProgress(mp.getCurrentPosition(), mp.getDuration());
            }
        } catch (Exception ignored) {}
        updateSession();
    }

    private void addSubtitle(String path) {
        Player.subtitleText = "";
        try {
            if (mp == null) return;
            if (Build.VERSION.SDK_INT >= 26) {
                MediaPlayer.TrackInfo[] tracks = mp.getTrackInfo();
                for (MediaPlayer.TrackInfo t : tracks) {
                    if (t.getTrackType() == MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_TIMEDTEXT) {
                        mp.deselectTrack(trackIndexOf(tracks, t));
                    }
                }
            }
            if (path == null || path.isEmpty()) return;
            mp.addTimedTextSource(path, MediaPlayer.MEDIA_MIMETYPE_TEXT_SUBRIP);
            mp.setOnTimedTextListener((p, text) ->
                    Player.subtitleText = (text != null && text.getText() != null) ? text.getText().toString() : "");
            if (Build.VERSION.SDK_INT >= 26) {
                MediaPlayer.TrackInfo[] tracks = mp.getTrackInfo();
                for (MediaPlayer.TrackInfo t : tracks) {
                    if (t.getTrackType() == MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_TIMEDTEXT) {
                        mp.selectTrack(trackIndexOf(tracks, t));
                        break;
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    private int trackIndexOf(MediaPlayer.TrackInfo[] tracks, MediaPlayer.TrackInfo target) {
        for (int i = 0; i < tracks.length; i++) if (tracks[i] == target) return i;
        return -1;
    }

    private void applySpeed() {
        try {
            if (mp == null) return;
            android.media.PlaybackParams p = new android.media.PlaybackParams()
                    .setSpeed(Math.max(0.25f, Math.min(Player.speed, 4f)));
            if (current != null && !current.isVideo) p.setPitch(Math.max(0.25f, Math.min(Player.speed, 4f)));
            mp.setPlaybackParams(p);
        } catch (Exception ignored) {}
    }

    private void attachSurface(Surface s) {
        videoSurface = s;
        try {
            if (mp != null) {
                mp.setSurface(s);
                if (s != null && current != null && current.isVideo && !mp.isPlaying()
                        && Player.state == Player.STATE_PAUSED) {
                    // keep paused; surface will render next frame on resume
                }
            }
        } catch (Exception ignored) {}
    }

    private void stopPlayback() {
        releaseEffects();
        try {
            if (mp != null) {
                if (current != null && Player.state == Player.STATE_PLAYING) {
                    History.record(current, mp.getCurrentPosition());
                }
                mp.stop();
                mp.release();
            }
        } catch (Exception ignored) {}
        mp = null;
        abandonFocus();
        cancelSleep();
        Player.setMirroredState(Player.STATE_IDLE, -1, null, null);
        updateSession();
        stopForegroundCompat();
        stopSelf();
    }

    // ---------------------------------------------------------------- audio focus

    private void requestFocus() {
        if (focusGranted) return;
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                        .setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build())
                        .setOnAudioFocusChangeListener(this::onFocusChange)
                        .build();
                focusGranted = am.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
            } else {
                focusGranted = am.requestAudioFocus(this::onFocusChangeLegacy,
                        AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
                        == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
            }
        } catch (Exception e) {
            focusGranted = true; // don't block playback on focus quirks
        }
    }

    private void abandonFocus() {
        try {
            if (!focusGranted) return;
            if (Build.VERSION.SDK_INT >= 26 && focusRequest != null) am.abandonAudioFocusRequest(focusRequest);
            else am.abandonAudioFocus(null);
        } catch (Exception ignored) {}
        focusGranted = false;
    }

    private void onFocusChange(int change) {
        if (change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
            if (Player.isPlaying()) { resumeOnFocusGain = true; pause(false); }
        } else if (change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
            try { if (mp != null) mp.setVolume(0.2f, 0.2f); } catch (Exception ignored) {}
        } else if (change == AudioManager.AUDIOFOCUS_GAIN) {
            try { if (mp != null) mp.setVolume(1f, 1f); } catch (Exception ignored) {}
            if (resumeOnFocusGain) { resumeOnFocusGain = false; resume(); }
        } else if (change == AudioManager.AUDIOFOCUS_LOSS) {
            resumeOnFocusGain = false;
            if (Player.isPlaying()) pause(false);
            abandonFocus();
        }
    }

    private void onFocusChangeLegacy(int change) { onFocusChange(change); }

    // ---------------------------------------------------------------- sleep timer

    private boolean sleepAfterTrack;

    private void setSleepTimer(int minutes) {
        cancelSleep();
        sleepAfterTrack = false;
        Prefs.setSleepMinutes(Math.max(0, minutes));
        if (minutes < 0) {
            // "end of current track"
            sleepAfterTrack = true;
            Player.setSleepRemain(-1);
            return;
        }
        if (minutes <= 0) { Player.setSleepRemain(0); return; }
        sleepDeadline = System.currentTimeMillis() + minutes * 60_000L;
        Player.setSleepRemain(minutes * 60);
        sleepRunnable = () -> {
            if (Player.isPlaying()) pause(false);
            cancelSleep();
            Player.setSleepRemain(0);
        };
        h.postDelayed(sleepRunnable, minutes * 60_000L);
        sleepTicker = new Runnable() {
            @Override public void run() {
                if (sleepDeadline <= 0) return;
                int remain = (int) ((sleepDeadline - System.currentTimeMillis()) / 1000);
                if (remain <= 0) return;
                Player.setSleepRemain(remain);
                h.postDelayed(this, 1000);
            }
        };
        h.postDelayed(sleepTicker, 1000);
    }

    private void cancelSleep() {
        if (sleepRunnable != null) h.removeCallbacks(sleepRunnable);
        if (sleepTicker != null) h.removeCallbacks(sleepTicker);
        sleepRunnable = null;
        sleepTicker = null;
        sleepDeadline = 0;
        Player.setSleepRemain(0);
    }

    // ---------------------------------------------------------------- audio effects

    private void attachEffects() {
        releaseEffects();
        if (mp == null) return;
        try {
            int sessionId = mp.getAudioSessionId();
            eq = new Equalizer(0, sessionId);
            eq.setEnabled(Prefs.eqEnabled());
            applyEqualizerPrefs();
            bass = new BassBoost(0, sessionId);
            bass.setEnabled(Prefs.bassEnabled());
            try { bass.setStrength((short) Prefs.bassStrength()); } catch (Exception ignored) {}
            virt = new Virtualizer(0, sessionId);
            virt.setEnabled(Prefs.virtEnabled());
            try { virt.setStrength((short) Prefs.virtStrength()); } catch (Exception ignored) {}
            loud = new LoudnessEnhancer(sessionId);
            loud.setEnabled(Prefs.loudEnabled());
            try { loud.setTargetGain(Prefs.loudGain()); } catch (Exception ignored) {}
        } catch (Exception ignored) {}

        try {
            if (Prefs.visualizer() && hasRecordAudio() && mp != null) {
                viz = new Visualizer(mp.getAudioSessionId());
                int[] range = Visualizer.getCaptureSizeRange();
                int size = Math.min(256, range[1]);
                viz.setCaptureSize(size);
                viz.setDataCaptureListener(new Visualizer.OnDataCaptureListener() {
                    @Override public void onWaveFormDataCapture(Visualizer v, byte[] waveform, int samplingRate) {
                        Player.pushWave(waveform, samplingRate);
                    }
                    @Override public void onFftDataCapture(Visualizer v, byte[] fft, int samplingRate) {}
                }, Visualizer.getMaxCaptureRate() / 4, false, true);
                viz.setEnabled(true);
            }
        } catch (Exception ignored) {}
    }

    private boolean hasRecordAudio() {
        return Build.VERSION.SDK_INT < 23
                || checkSelfPermission(Perms.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    private void releaseEffects() {
        releaseFx(eq); eq = null;
        releaseFx(bass); bass = null;
        releaseFx(virt); virt = null;
        releaseFx(loud); loud = null;
        if (viz != null) { try { viz.setEnabled(false); viz.release(); } catch (Exception ignored) {} viz = null; }
    }

    private void releaseFx(Object fx) {
        if (fx == null) return;
        try {
            if (fx instanceof Equalizer) { ((Equalizer) fx).setEnabled(false); ((Equalizer) fx).release(); }
            else if (fx instanceof BassBoost) { ((BassBoost) fx).setEnabled(false); ((BassBoost) fx).release(); }
            else if (fx instanceof Virtualizer) { ((Virtualizer) fx).setEnabled(false); ((Virtualizer) fx).release(); }
            else if (fx instanceof LoudnessEnhancer) { ((LoudnessEnhancer) fx).setEnabled(false); ((LoudnessEnhancer) fx).release(); }
        } catch (Exception ignored) {}
    }

    // ---- equalizer control (called from EqualizerDialog) ----

    public static int[] eqBandLevels() {
        Service s = instance;
        if (s instanceof PlaybackService && ((PlaybackService) s).eq != null) {
            Equalizer e = ((PlaybackService) s).eq;
            short[] levels = new short[e.getNumberOfBands()];
            for (int i = 0; i < levels.length; i++) levels[i] = e.getBandLevel((short) i);
            int[] out = new int[levels.length];
            for (int i = 0; i < levels.length; i++) out[i] = levels[i];
            return out;
        }
        return null;
    }

    public static short[] eqBandRange() {
        Service s = instance;
        if (s instanceof PlaybackService && ((PlaybackService) s).eq != null) {
            Equalizer e = ((PlaybackService) s).eq;
            return e.getBandLevelRange();
        }
        return null;
    }

    public static int eqBandCount() {
        Service s = instance;
        if (s instanceof PlaybackService && ((PlaybackService) s).eq != null) {
            return ((PlaybackService) s).eq.getNumberOfBands();
        }
        return 5;
    }

    public static void eqSetBand(int band, int level) {
        Service s = instance;
        if (s instanceof PlaybackService && ((PlaybackService) s).eq != null) {
            try { ((PlaybackService) s).eq.setBandLevel((short) band, (short) level); } catch (Exception ignored) {}
        }
    }

    public static void eqSetEnabled(boolean on) {
        Service s = instance;
        if (s instanceof PlaybackService && ((PlaybackService) s).eq != null) {
            try { ((PlaybackService) s).eq.setEnabled(on); } catch (Exception ignored) {}
        }
        Prefs.sp().edit().putBoolean(Prefs.KEY_EQ_ENABLED, on).apply();
    }

    public static void bassSetEnabled(boolean on, int strength) {
        Service s = instance;
        if (s instanceof PlaybackService && ((PlaybackService) s).bass != null) {
            try {
                ((PlaybackService) s).bass.setEnabled(on);
                ((PlaybackService) s).bass.setStrength((short) strength);
            } catch (Exception ignored) {}
        }
        Prefs.sp().edit().putBoolean(Prefs.KEY_BASS_ENABLED, on).putInt(Prefs.KEY_BASS_STRENGTH, strength).apply();
    }

    public static void virtSetEnabled(boolean on, int strength) {
        Service s = instance;
        if (s instanceof PlaybackService && ((PlaybackService) s).virt != null) {
            try {
                ((PlaybackService) s).virt.setEnabled(on);
                ((PlaybackService) s).virt.setStrength((short) strength);
            } catch (Exception ignored) {}
        }
        Prefs.sp().edit().putBoolean(Prefs.KEY_VIRT_ENABLED, on).putInt(Prefs.KEY_VIRT_STRENGTH, strength).apply();
    }

    public static void loudSetEnabled(boolean on, int gain) {
        Service s = instance;
        if (s instanceof PlaybackService && ((PlaybackService) s).loud != null) {
            try {
                ((PlaybackService) s).loud.setEnabled(on);
                ((PlaybackService) s).loud.setTargetGain(gain);
            } catch (Exception ignored) {}
        }
        Prefs.sp().edit().putBoolean(Prefs.KEY_LOUD_ENABLED, on).putInt(Prefs.KEY_LOUD_GAIN, gain).apply();
    }

    private void applyEqualizerPrefs() {
        if (eq == null) return;
        try {
            int preset = Prefs.eqPreset();
            if (preset >= 0 && preset < eq.getNumberOfPresets()) {
                eq.usePreset((short) preset);
            } else {
                int[] bands = Prefs.eqBands();
                if (bands != null) {
                    for (int i = 0; i < Math.min(bands.length, eq.getNumberOfBands()); i++) {
                        eq.setBandLevel((short) i, (short) bands[i]);
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    public static int eqPresetCount() {
        Service s = instance;
        if (s instanceof PlaybackService && ((PlaybackService) s).eq != null) {
            return ((PlaybackService) s).eq.getNumberOfPresets();
        }
        return 0;
    }

    public static String eqPresetName(int i) {
        Service s = instance;
        if (s instanceof PlaybackService && ((PlaybackService) s).eq != null) {
            try { return ((PlaybackService) s).eq.getPresetName((short) i); } catch (Exception ignored) {}
        }
        return "";
    }

    public static void eqUsePreset(int i) {
        Service s = instance;
        if (s instanceof PlaybackService && ((PlaybackService) s).eq != null) {
            try { ((PlaybackService) s).eq.usePreset((short) i); } catch (Exception ignored) {}
        }
    }

    // ---------------------------------------------------------------- session + notification

    private void updateSession() {
        if (session == null) return;
        try {
            if (current != null) {
                android.media.MediaMetadata.Builder b = new android.media.MediaMetadata.Builder()
                        .putString(android.media.MediaMetadata.METADATA_KEY_TITLE, current.name)
                        .putLong(android.media.MediaMetadata.METADATA_KEY_DURATION, Player.durationMs > 0 ? Player.durationMs : current.durationMs);
                if (current.isVideo) b.putString(android.media.MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, "Video");
                session.setMetadata(b.build());
            }
            PlaybackState.Builder sb = new PlaybackState.Builder()
                    .setActions(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_PLAY_PAUSE
                            | PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS
                            | PlaybackState.ACTION_SEEK_TO | PlaybackState.ACTION_STOP);
            int st = Player.state == Player.STATE_PLAYING ? PlaybackState.STATE_PLAYING
                    : Player.state == Player.STATE_PAUSED ? PlaybackState.STATE_PAUSED
                    : Player.state == Player.STATE_LOADING ? PlaybackState.STATE_BUFFERING
                    : PlaybackState.STATE_STOPPED;
            sb.setState(st, Player.positionMs, Player.speed);
            session.setPlaybackState(sb.build());
        } catch (Exception ignored) {}
    }

    private void ensureForeground() {
        Notification n = buildNotification();
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NOTIF_ID, n);
        }
    }

    private void stopForegroundCompat() {
        try {
            if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE);
            else stopForeground(true);
        } catch (Exception ignored) {}
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID,
                    getString(R.string.notification_channel_playback), NotificationManager.IMPORTANCE_LOW);
            ch.setDescription(getString(R.string.notification_channel_playback_desc));
            ch.setShowBadge(false);
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    private PendingIntent actionIntent(String action) {
        Intent i = new Intent(this, PlaybackService.class).setAction(action);
        return PendingIntent.getService(this, action.hashCode(), i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private PendingIntent contentIntent() {
        Intent i = new Intent(this, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(this, 1, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private Notification buildNotification() {
        boolean playing = Player.state == Player.STATE_PLAYING || Player.state == Player.STATE_LOADING;
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        b.setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(current != null ? current.name : getString(R.string.app_name))
                .setContentText(current != null
                        ? (current.isVideo ? "Video · " : "Audio · ") + C.fmtDur(current.durationMs)
                        : "Nothing playing")
                .setContentIntent(contentIntent())
                .setDeleteIntent(actionIntent(ACTION_STOP))
                .setOngoing(playing)
                .setOnlyAlertOnce(true)
                .setShowWhen(false);

        if (Build.VERSION.SDK_INT >= 21) {
            Notification.Action prev = new Notification.Action.Builder(
                    android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_prev),
                    "Previous", actionIntent(ACTION_PREV)).build();
            Notification.Action playPause = new Notification.Action.Builder(
                    android.graphics.drawable.Icon.createWithResource(this,
                            playing ? R.drawable.ic_pause : R.drawable.ic_play),
                    playing ? "Pause" : "Play",
                    actionIntent(playing ? ACTION_PAUSE : ACTION_RESUME)).build();
            Notification.Action next = new Notification.Action.Builder(
                    android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_next),
                    "Next", actionIntent(ACTION_NEXT)).build();
            b.addAction(prev).addAction(playPause).addAction(next);
            b.setStyle(new Notification.MediaStyle()
                    .setMediaSession(session.getSessionToken())
                    .setShowActionsInCompactView(0, 1, 2));
        }
        return b.build();
    }

    private void updateNotification() {
        ensureForeground();
    }
}
