package com.qxplays.player;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Video player: real gestures (seek/volume/brightness), subtitles, PiP, background audio. */
public class PlayerActivity extends Activity implements Player.Listener, SurfaceHolder.Callback {

    private FrameLayout root;
    private SurfaceView surface;
    private View controls;
    private View topBar, bottomBar, gestureHint;
    private TextView seekPreview;
    private TextView title, posLabel, durLabel, subView, speedTag;
    private android.widget.ImageView playBtn;
    private SeekBar seek;
    private final Handler hideHandler = new Handler();
    private boolean controlsVisible = true;
    private boolean dragging, locked;
    private float savedBrightness = -1f;

    // gestures
    private float downX, downY, startBrightness;
    private int startVolume, startPos;
    private boolean longPressed, seekingHoriz;
    private int verticalMode; // 1 volume, 2 brightness
    private final Runnable longPressRunnable = new Runnable() {
        @Override public void run() {
            longPressed = true;
            speedTag.setVisibility(View.VISIBLE);
            speedTag.setText("2.0×");
            Ui.vib(PlayerActivity.this, 30);
            Player.setSpeed(PlayerActivity.this, 2f);
        }
    };

    private final Runnable hideControls = () -> setControlsVisible(false);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        applySystemUi(true);
        root = new FrameLayout(this);
        root.setBackgroundColor(C.BLACK);
        setContentView(root);

        surface = new SurfaceView(this);
        surface.getHolder().addCallback(this);
        root.addView(surface, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // gesture layer must sit UNDER the controls so buttons stay tappable
        buildGestureOverlay();
        buildSubtitleView();
        buildGestureHint();
        buildSeekPreview();
        buildControls();

        if (Prefs.rememberBrightness()) {
            savedBrightness = Prefs.sp().getFloat("brightness_value", -1f);
            if (savedBrightness >= 0) applyBrightness(savedBrightness);
        }

        Player.subscribe(this);
        updateTitle();
        updateProgress(Player.positionMs, Player.durationMs);
        scheduleHide();
    }

    @Override
    protected void onStart() {
        super.onStart();
        Player.subscribe(this); // resubscribe when returning from PiP/background
    }

    private void applySystemUi(boolean immersive) {
        if (Build.VERSION.SDK_INT >= 19) {
            int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
            if (immersive) flags |= View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
            getWindow().getDecorView().setSystemUiVisibility(flags);
        }
    }

    // ---------------------------------------------------------------- overlays

    private void buildSubtitleView() {
        subView = Ui.tv(this, "", 17, Color.WHITE, 1);
        subView.setGravity(Gravity.CENTER);
        subView.setShadowLayer(3, 0, 1, Color.BLACK);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        lp.setMargins(Ui.dp(this, 24), 0, Ui.dp(this, 24), Ui.dp(this, 90));
        root.addView(subView, lp);
    }

    private void buildGestureHint() {
        gestureHint = new LinearLayout(this);
        ((LinearLayout) gestureHint).setOrientation(LinearLayout.VERTICAL);
        TextView t = Ui.tv(this, "◀  double-tap to seek  ▶", 12.5f, 0xFFFFFFFF, 1);
        t.setShadowLayer(3, 0, 1, Color.BLACK);
        TextView t2 = Ui.tv(this, "swipe: seek · right edge: volume · left edge: brightness · hold: 2×",
                11.5f, 0xCCFFFFFF, 0);
        t2.setShadowLayer(3, 0, 1, Color.BLACK);
        ((LinearLayout) gestureHint).addView(t);
        ((LinearLayout) gestureHint).addView(t2);
        gestureHint.setPadding(Ui.dp(this, 20), Ui.dp(this, 20), Ui.dp(this, 20), Ui.dp(this, 20));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        root.addView(gestureHint, lp);
        gestureHint.setVisibility(View.INVISIBLE);
    }

    private void buildSeekPreview() {
        seekPreview = Ui.tv(this, "", 14, Color.WHITE, 2);
        seekPreview.setShadowLayer(3, 0, 1, Color.BLACK);
        seekPreview.setPadding(Ui.dp(this, 10), Ui.dp(this, 5), Ui.dp(this, 10), Ui.dp(this, 5));
        Ui.setBg(seekPreview, Ui.rect(Ui.dp(this, 8), 0x99000000));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        root.addView(seekPreview, lp);
        seekPreview.setVisibility(View.GONE);
    }

    private void buildControls() {
        controls = new FrameLayout(this);
        controls.setVisibility(View.VISIBLE);
        root.addView(controls, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        controls.setBackgroundColor(0x66000000);

        // top bar
        topBar = new LinearLayout(this);
        ((LinearLayout) topBar).setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(Ui.dp(this, 6), Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8));
        ((LinearLayout) topBar).addView(Ui.iconBtn(this, R.drawable.ic_back, 0xFFFFFFFF, v -> close(false)));
        title = Ui.tv(this, "", 16, Color.WHITE, 1);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        ((LinearLayout) topBar).addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        ((LinearLayout) topBar).addView(Ui.iconBtn(this, R.drawable.ic_eye_off, 0xFFFFFFFF, v -> setLocked(!locked)));
        ((LinearLayout) topBar).addView(Ui.iconBtn(this, R.drawable.ic_more, 0xFFFFFFFF, v -> moreMenu()));
        root.addView(topBar, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP));

        // bottom bar
        bottomBar = new LinearLayout(this);
        ((LinearLayout) bottomBar).setOrientation(LinearLayout.VERTICAL);
        bottomBar.setPadding(Ui.dp(this, 12), Ui.dp(this, 6), Ui.dp(this, 12), Ui.dp(this, 10));

        LinearLayout seekRow = new LinearLayout(this);
        seekRow.setGravity(Gravity.CENTER_VERTICAL);
        posLabel = Ui.tv(this, "0:00", 12, 0xFFFFFFFF, 0);
        durLabel = Ui.tv(this, "0:00", 12, 0xFFFFFFFF, 0);
        seek = new SeekBar(this);
        seek.getProgressDrawable().setColorFilter(C.accent(), android.graphics.PorterDuff.Mode.SRC_IN);
        seek.getThumb().setColorFilter(C.accent(), android.graphics.PorterDuff.Mode.SRC_IN);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                posLabel.setText(C.fmtDurClock(progress));
                if (fromUser) Player.seek(PlayerActivity.this, progress);
            }
            @Override public void onStartTrackingTouch(SeekBar sb) { dragging = true; }
            @Override public void onStopTrackingTouch(SeekBar sb) {
                dragging = false;
                scheduleHide();
            }
        });
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        slp.setMargins(Ui.dp(this, 10), 0, Ui.dp(this, 10), 0);
        seekRow.addView(posLabel);
        seekRow.addView(seek, slp);
        seekRow.addView(durLabel);
        ((LinearLayout) bottomBar).addView(seekRow);

        LinearLayout btns = new LinearLayout(this);
        btns.setGravity(Gravity.CENTER_VERTICAL);
        btns.setPadding(0, Ui.dp(this, 4), 0, 0);
        btns.addView(Ui.iconBtn(this, R.drawable.ic_prev, 0xFFFFFFFF, v -> Player.prev(this)));
        btns.addView(Ui.space(this, 16));
        playBtn = Ui.iconBtn(this, R.drawable.ic_play, 0xFFFFFFFF, 52, v -> Player.toggle(this));
        Ui.setBg(playBtn, Ui.rippleCircle(this, C.accent()));
        btns.addView(playBtn);
        btns.addView(Ui.space(this, 16));
        btns.addView(Ui.iconBtn(this, R.drawable.ic_next, 0xFFFFFFFF, v -> Player.next(this)));
        btns.addView(Ui.space(this, 10));
        btns.addView(Ui.iconBtn(this, R.drawable.ic_sleep, 0xFFFFFFFF, v ->
                Sheets.sleepTimer(this, min -> Player.setSleepTimer(this, min))));
        btns.addView(Ui.space(this, 10));
        btns.addView(Ui.iconBtn(this, R.drawable.ic_pip, 0xFFFFFFFF, v -> {
            if (Build.VERSION.SDK_INT >= 26 && !isInPictureInPictureMode()) enterPip();
        }));
        LinearLayout center = new LinearLayout(this);
        center.setGravity(Gravity.CENTER_HORIZONTAL);
        center.addView(btns);
        ((LinearLayout) bottomBar).addView(center);

        root.addView(bottomBar, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));

        // speed tag
        speedTag = Ui.tv(this, "", 14, Color.WHITE, 2);
        speedTag.setShadowLayer(3, 0, 1, Color.BLACK);
        speedTag.setPadding(Ui.dp(this, 12), Ui.dp(this, 6), Ui.dp(this, 12), Ui.dp(this, 6));
        Ui.setBg(speedTag, Ui.rect(Ui.dp(this, 10), 0x99000000));
        root.addView(speedTag, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END));
        speedTag.setVisibility(View.GONE);
    }

    private void buildGestureOverlay() {
        View g = new View(this) {
            private long lastTap, lastDown;
            private boolean moved, doubleArmed;
            private float lastX, lastY;

            @Override
            public boolean onTouchEvent(MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = lastX = e.getX();
                        downY = lastY = e.getY();
                        moved = false;
                        doubleArmed = false;
                        longPressed = false;
                        startBrightness = getWindow().getAttributes().screenBrightness;
                        startVolume = ((AudioManager) getSystemService(AUDIO_SERVICE))
                                .getStreamVolume(AudioManager.STREAM_MUSIC);
                        startPos = (int) Player.positionMs;
                        removeCallbacks(longPressRunnable);
                        postDelayed(longPressRunnable, 450);
                        lastDown = System.currentTimeMillis();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = e.getX() - downX;
                        float dy = e.getY() - downY;
                        if (Math.abs(dx) > Ui.dp(PlayerActivity.this, 12)
                                || Math.abs(dy) > Ui.dp(PlayerActivity.this, 12)) {
                            if (!moved) {
                                moved = true;
                                removeCallbacks(longPressRunnable);
                                doubleArmed = false;
                                if (Math.abs(dx) > Math.abs(dy)) {
                                    seekingHoriz = true;
                                    verticalMode = 0;
                                } else {
                                    seekingHoriz = false;
                                    verticalMode = e.getX() > getWidth() * 0.62f ? 1 : 2;
                                }
                            }
                            if (seekingHoriz) {
                                float frac = (e.getX() - downX) / getWidth();
                                long target = startPos + (long) (frac * 120_000);
                                target = Math.max(0, Math.min(Player.durationMs, target));
                                showSeekPreview(C.fmtDurClock(target) + " / " + C.fmtDurClock(Player.durationMs));
                                dragging = true;
                                seek.setProgress((int) target);
                            } else if (verticalMode == 1) {
                                float frac = (downY - e.getY()) / getHeight();
                                int vol = (int) (startVolume + frac * 15);
                                vol = Math.max(0, Math.min(15, vol));
                                ((AudioManager) getSystemService(AUDIO_SERVICE))
                                        .setStreamVolume(AudioManager.STREAM_MUSIC, vol, 0);
                                showSeekPreview("Volume " + vol + "/15");
                            } else if (verticalMode == 2) {
                                float frac = (downY - e.getY()) / getHeight();
                                float b = startBrightness;
                                if (b < 0) b = 0.4f;
                                b = Math.max(0.02f, Math.min(1f, b + frac));
                                applyBrightness(b);
                                showSeekPreview("Brightness " + Math.round(b * 100) + "%");
                            }
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        removeCallbacks(longPressRunnable);
                        if (longPressed) {
                            longPressed = false;
                            speedTag.setVisibility(View.GONE);
                            Player.setSpeed(PlayerActivity.this, Prefs.defaultSpeed());
                            return true;
                        }
                        if (moved) {
                            moved = false;
                            if (seekingHoriz) {
                                dragging = false;
                                Player.seek(PlayerActivity.this, seek.getProgress());
                                seekPreview.setVisibility(View.GONE);
                                scheduleHide();
                            } else {
                                seekPreview.setVisibility(View.GONE);
                            }
                            return true;
                        }
                        long now = System.currentTimeMillis();
                        if (now - lastTap < 280 && doubleArmed && Prefs.doubleTapSeek()) {
                            // double tap seek
                            doubleArmed = false;
                            lastTap = 0;
                            int step = Prefs.seekStep() * 1000;
                            long target = Player.positionMs + (e.getX() > getWidth() / 2f ? step : -step);
                            Player.seek(PlayerActivity.this, Math.max(0, target));
                            showSeekPreview(C.fmtDurClock(Math.max(0, target)));
                            Ui.vib(PlayerActivity.this, 25);
                            hideHandler.postDelayed(() -> seekPreview.setVisibility(View.GONE), 600);
                            return true;
                        }
                        if (now - lastTap < 280) {
                            doubleArmed = true;
                            lastTap = now;
                            return true;
                        }
                        lastTap = now;
                        doubleArmed = false;
                        if (locked) {
                            Ui.toast(PlayerActivity.this, "Controls locked — tap the eye button to unlock");
                            return true;
                        }
                        setControlsVisible(!controlsVisible);
                        return true;
                }
                return super.onTouchEvent(e);
            }
        };
        g.setBackgroundColor(Color.TRANSPARENT);
        root.addView(g, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void showSeekPreview(String text) {
        seekPreview.setText(text);
        seekPreview.setVisibility(View.VISIBLE);
        hideHandler.removeCallbacksAndMessages(null);
        hideHandler.postDelayed(() -> {
            if (!dragging && !seekingHoriz) seekPreview.setVisibility(View.GONE);
        }, 800);
    }

    private void setControlsVisible(boolean v) {
        controlsVisible = v;
        float a = v ? 1f : 0f;
        topBar.animate().alpha(a).setDuration(180).start();
        bottomBar.animate().alpha(a).setDuration(180).start();
        if (v) {
            applySystemUi(true);
            scheduleHide();
        } else {
            applySystemUi(true);
            hideHandler.removeCallbacks(hideControls);
        }
    }

    private void setLocked(boolean l) {
        locked = l;
        if (l) {
            Ui.toast(this, "Controls locked");
            topBar.setVisibility(View.INVISIBLE);
            bottomBar.setVisibility(View.INVISIBLE);
        } else {
            topBar.setVisibility(View.VISIBLE);
            bottomBar.setVisibility(View.VISIBLE);
        }
    }

    private void scheduleHide() {
        hideHandler.removeCallbacks(hideControls);
        hideHandler.postDelayed(hideControls, 4000);
    }

    // ---------------------------------------------------------------- surface

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Player.attachSurface(this, holder.getSurface());
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        Player.attachSurface(this, null);
    }

    // ---------------------------------------------------------------- menus

    private void moreMenu() {
        List<String> labels = new ArrayList<>();
        List<Integer> icons = new ArrayList<>();
        List<Runnable> acts = new ArrayList<>();
        labels.add("Rotate");
        icons.add(R.drawable.ic_rotate);
        acts.add(() -> {
            int o = getRequestedOrientation();
            if (o == android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
                setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            } else {
                setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            }
        });
        labels.add("Subtitle / none");
        icons.add(R.drawable.ic_subtitles);
        acts.add(this::pickSubtitle);
        labels.add("Playback speed");
        icons.add(R.drawable.ic_speed);
        acts.add(() -> Sheets.speed(this, sp -> Player.setSpeed(this, sp)));
        labels.add("Sleep timer");
        icons.add(R.drawable.ic_sleep);
        acts.add(() -> Sheets.sleepTimer(this, min -> Player.setSleepTimer(this, min)));
        labels.add("Play audio in background");
        icons.add(R.drawable.ic_volume);
        acts.add(() -> {
            Ui.toast(this, "Audio continues in background");
            close(true);
        });
        labels.add("Details");
        icons.add(R.drawable.ic_info);
        acts.add(() -> Sheets.details(this, Player.current, null));
        labels.add("Share");
        icons.add(R.drawable.ic_share);
        acts.add(() -> {
            if (Player.current != null) Sheets.share(this, Player.current);
        });
        Ui.menu(this, Player.current == null ? "Video" : Player.current.name, labels, icons, null, acts);
    }

    private void pickSubtitle() {
        MediaItem it = Player.current;
        String sidecar = it == null ? null : it.subtitleSidecar();
        List<String> labels = new ArrayList<>();
        List<Runnable> acts = new ArrayList<>();
        if (sidecar != null) {
            labels.add("Auto: " + new java.io.File(sidecar).getName());
            acts.add(() -> sendSubtitle(sidecar));
        }
        labels.add("Choose .srt file…");
        acts.add(() -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("application/x-subrip");
            try { startActivityForResult(i, 500); } catch (Exception e) { Ui.toast(this, "No file picker"); }
        });
        labels.add("Turn off subtitles");
        acts.add(() -> sendSubtitle(null));
        Ui.menu(this, "Subtitles", labels, null, null, acts);
    }

    private void sendSubtitle(String path) {
        Intent i = new Intent(this, PlaybackService.class).setAction(PlaybackService.ACTION_SUBTITLE);
        i.putExtra("uri", path);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
        else startService(i);
        Player.subtitleText = "";
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 500 && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri u = data.getData();
            // copy to cache so MediaPlayer can read it repeatedly
            new Thread(() -> {
                try {
                    java.io.File tmp = new java.io.File(getCacheDir(), "sub_" + System.currentTimeMillis() + ".srt");
                    java.io.InputStream in = getContentResolver().openInputStream(u);
                    java.io.FileOutputStream out = new java.io.FileOutputStream(tmp);
                    Utils.copy(in, out);
                    in.close();
                    out.close();
                    runOnUiThread(() -> sendSubtitle(tmp.getAbsolutePath()));
                } catch (Exception ignored) {}
            }).start();
        }
    }

    // ---------------------------------------------------------------- PiP

    private void enterPip() {
        if (Build.VERSION.SDK_INT < 26) { Ui.toast(this, "Picture-in-picture needs Android 8+"); return; }
        try {
            android.app.PictureInPictureParams p = new android.app.PictureInPictureParams.Builder()
                    .setAspectRatio(new android.util.Rational(16, 9)).build();
            enterPictureInPictureMode(p);
        } catch (Exception e) {
            Ui.toast(this, "PiP not available");
        }
    }

    @Override
    public void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (Player.isPlaying() && !isFinishing() && Prefs.autoPip()
                && Build.VERSION.SDK_INT >= 26) {
            enterPip();
        }
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        if (isInPictureInPictureMode) {
            controls.setVisibility(View.GONE);
            surface.setVisibility(View.VISIBLE);
        } else {
            controls.setVisibility(View.VISIBLE);
            setControlsVisible(true);
        }
    }

    // ---------------------------------------------------------------- player glue

    @Override public void onStateChanged() {
        boolean playing = Player.isPlaying();
        playBtn.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play);
        if (playing) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        subView.setText(Player.subtitleText == null ? "" : Player.subtitleText);
    }
    @Override public void onTrackChanged() {
        updateTitle();
        subView.setText("");
    }
    @Override public void onProgress(long posMs, long durMs) {
        if (!dragging) updateProgress(posMs, durMs);
        if (Player.subtitleText != null && !Player.subtitleText.isEmpty()) {
            subView.setText(Player.subtitleText);
        }
    }
    @Override public void onQueueChanged() {}
    @Override public void onSleepTick(int remainSec) {}
    @Override public void onWave(byte[] wave, int samplingRate) {}

    private void updateTitle() {
        MediaItem it = Player.current;
        title.setText(it == null ? "QxPlays" : it.name);
    }

    private void updateProgress(long pos, long dur) {
        int max = (int) Math.max(1, dur);
        seek.setMax(max);
        seek.setProgress((int) Math.min(max, pos));
        posLabel.setText(C.fmtDurClock(pos));
        durLabel.setText(C.fmtDurClock(dur));
    }

    private void applyBrightness(float b) {
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = b;
        getWindow().setAttributes(lp);
        Prefs.sp().edit().putFloat("brightness_value", b).apply();
    }

    // ---------------------------------------------------------------- lifecycle

    private void close(boolean keepPlaying) {
        Player.notifyVideoClosed(this, keepPlaying);
        finish();
    }

    @Override
    public void onBackPressed() {
        if (controlsVisible && !locked) close(false);
        else setControlsVisible(true);
    }

    @Override
    protected void onStop() {
        super.onStop();
        Player.unsubscribe(this);
    }

    @Override
    protected void onDestroy() {
        hideHandler.removeCallbacksAndMessages(null);
        if (savedBrightness < 0 && Prefs.rememberBrightness()) {
            // leave stored brightness for next session
        }
        super.onDestroy();
    }
}
