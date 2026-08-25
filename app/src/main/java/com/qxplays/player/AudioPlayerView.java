package com.qxplays.player;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Full-screen audio player with visualizer, queue, equalizer and sleep timer. */
public class AudioPlayerView extends FrameLayout implements Player.Listener {

    private final Activity act;
    private final TextView title, sub, posLabel, durLabel;
    private final ImageView playBtn, repeatBtn, shuffleBtn, favBtn;
    private final SeekBar seek;
    private final VisualizerView viz;
    private Thumbs.ThumbView cover;
    private boolean dragging;
    private TextView sleepBadge;

    public AudioPlayerView(Context c) {
        super(c);
        act = (Activity) c;
        setBackgroundColor(C.bg());

        LinearLayout col = new LinearLayout(act);
        col.setOrientation(LinearLayout.VERTICAL);
        addView(col, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // header
        LinearLayout header = new LinearLayout(act);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(Ui.dp(act, 4), Ui.dp(act, 8), Ui.dp(act, 12), Ui.dp(act, 4));
        header.addView(Ui.iconBtn(act, R.drawable.ic_back, C.text(), v -> ((MainActivity) act).pop()));
        TextView hTitle = Ui.tv(act, "Now playing", 17, C.text(), 2);
        header.addView(hTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        header.addView(Ui.iconBtn(act, R.drawable.ic_queue, C.text(), v -> queueSheet()));
        header.addView(Ui.iconBtn(act, R.drawable.ic_more, C.textDim(), v -> moreMenu()));
        col.addView(header);

        // cover + visualizer
        FrameLayout stage = new FrameLayout(act);
        viz = new VisualizerView(act);
        stage.addView(viz, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(act, 250), Gravity.BOTTOM));
        cover = new Thumbs.ThumbView(act);
        cover.setRounded(true);
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(Ui.dp(act, 210), Ui.dp(act, 210), Gravity.CENTER);
        clp.topMargin = Ui.dp(act, 8);
        stage.addView(cover, clp);
        col.addView(stage);

        // labels
        title = Ui.tv(act, "", 18, C.text(), 2);
        title.setGravity(Gravity.CENTER);
        title.setPadding(Ui.dp(act, 24), Ui.dp(act, 10), Ui.dp(act, 24), 0);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        col.addView(title);
        sub = Ui.tv(act, "", 13.5f, C.textDim(), 0);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(Ui.dp(act, 24), Ui.dp(act, 3), Ui.dp(act, 24), 0);
        col.addView(sub);

        // seek
        LinearLayout seekRow = new LinearLayout(act);
        seekRow.setGravity(Gravity.CENTER_VERTICAL);
        seekRow.setPadding(Ui.dp(act, 20), Ui.dp(act, 10), Ui.dp(act, 20), 0);
        posLabel = Ui.tv(act, "0:00", 12, C.textDim(), 0);
        durLabel = Ui.tv(act, "0:00", 12, C.textDim(), 0);
        seek = new SeekBar(act);
        seek.getProgressDrawable().setColorFilter(C.accent(), android.graphics.PorterDuff.Mode.SRC_IN);
        seek.getThumb().setColorFilter(C.accent(), android.graphics.PorterDuff.Mode.SRC_IN);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                posLabel.setText(C.fmtDurClock(progress));
                if (fromUser) Player.seek(act, progress);
            }
            @Override public void onStartTrackingTouch(SeekBar sb) { dragging = true; }
            @Override public void onStopTrackingTouch(SeekBar sb) { dragging = false; }
        });
        seekRow.addView(posLabel);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        slp.setMargins(Ui.dp(act, 10), 0, Ui.dp(act, 10), 0);
        seekRow.addView(seek, slp);
        seekRow.addView(durLabel);
        col.addView(seekRow);

        // controls
        LinearLayout controls = new LinearLayout(act);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        controls.setPadding(Ui.dp(act, 24), Ui.dp(act, 12), Ui.dp(act, 24), 0);
        shuffleBtn = Ui.iconBtn(act, R.drawable.ic_shuffle, Player.shuffle ? C.accent() : C.textDim(), v -> {
            Player.setShuffle(act, !Player.shuffle);
            updateControls();
        });
        controls.addView(shuffleBtn);
        controls.addView(Ui.space(act, 14));
        controls.addView(Ui.iconBtn(act, R.drawable.ic_prev, C.text(), 28, v -> Player.prev(act)));
        controls.addView(Ui.space(act, 10));
        playBtn = Ui.iconBtn(act, R.drawable.ic_play, C.text(), 64, v -> Player.toggle(act));
        playBtn.setColorFilter(0xFFFFFFFF, android.graphics.PorterDuff.Mode.SRC_IN);
        Ui.setBg(playBtn, Ui.rippleCircle(act, C.accent()));
        controls.addView(playBtn);
        controls.addView(Ui.space(act, 10));
        controls.addView(Ui.iconBtn(act, R.drawable.ic_next, C.text(), 28, v -> Player.next(act)));
        controls.addView(Ui.space(act, 14));
        repeatBtn = Ui.iconBtn(act, R.drawable.ic_repeat, Player.repeatMode == Player.REPEAT_OFF ? C.textDim() : C.accent(), v -> {
            Player.setRepeat(act, (Player.repeatMode + 1) % 3);
            updateControls();
        });
        controls.addView(repeatBtn);
        LinearLayout center = new LinearLayout(act);
        center.setGravity(Gravity.CENTER_HORIZONTAL);
        center.addView(controls);
        col.addView(center);

        // action row
        LinearLayout actions = new LinearLayout(act);
        actions.setGravity(Gravity.CENTER);
        actions.setPadding(Ui.dp(act, 12), Ui.dp(act, 14), Ui.dp(act, 12), 0);
        favBtn = Ui.iconBtn(act, R.drawable.ic_heart_off, C.textDim(), v -> {
            if (Player.current != null) {
                Favorites.toggle(Player.current);
                updateControls();
                Ui.toast(act, Favorites.has(Player.current.uri) ? "Added to favorites" : "Removed from favorites");
            }
        });
        actions.addView(favBtn);
        actions.addView(actionBtn(R.drawable.ic_speed, () -> Sheets.speed(act, sp -> {
            Player.setSpeed(act, sp);
            updateControls();
        })));
        actions.addView(actionBtn(R.drawable.ic_equalizer, this::equalizerSheet));
        actions.addView(actionBtn(R.drawable.ic_sleep, () -> Sheets.sleepTimer(act, min -> {
            Player.setSleepTimer(act, min);
            updateSleepBadge();
        })));
        actions.addView(actionBtn(R.drawable.ic_queue, this::queueSheet));
        actions.addView(actionBtn(R.drawable.ic_share, () -> {
            if (Player.current != null) Sheets.share(act, Player.current);
        }));
        col.addView(actions);

        sleepBadge = Ui.tv(act, "", 12, C.WARN, 1);
        sleepBadge.setGravity(Gravity.CENTER);
        sleepBadge.setVisibility(GONE);
        col.addView(sleepBadge);

        Player.subscribe(this);
        updateTrack();
        updateControls();
        updateSleepBadge();
    }

    private View actionBtn(int icon, Runnable r) {
        return Ui.iconBtn(act, icon, C.textDim(), v -> r.run());
    }

    private void moreMenu() {
        if (Player.current == null) return;
        List<String> labels = new ArrayList<>();
        List<Runnable> acts = new ArrayList<>();
        labels.add("Details");
        acts.add(() -> Sheets.details(act, Player.current, null));
        labels.add("Add to playlist");
        acts.add(() -> Sheets.playlistPicker(act, Player.current, null));
        labels.add("Stop playback");
        acts.add(() -> { Player.stop(act); ((MainActivity) act).pop(); });
        Ui.menu(act, Player.current.name, labels, null, null, acts);
    }

    // ---------------------------------------------------------------- queue sheet

    private void queueSheet() {
        Ui.Sheet s = new Ui.Sheet(act, true, null);
        s.title("Queue");
        if (Player.queueSize() == 0) {
            s.add(Ui.tv(act, "Queue is empty", 15, C.textDim(), 0));
            s.add(Ui.space(act, 10));
            s.show();
            return;
        }
        LinearLayout wrap = new LinearLayout(act);
        wrap.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < Player.queueSize(); i++) {
            final int idx = i;
            MediaItem it = Player.queueAt(i);
            boolean current = idx == Player.index;
            LinearLayout row = new LinearLayout(act);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(Ui.dp(act, 6), Ui.dp(act, 6), Ui.dp(act, 6), Ui.dp(act, 6));
            TextView num = Ui.tv(act, current ? "▶" : String.valueOf(idx + 1), 13, current ? C.accent() : C.textDim(), 1);
            num.setGravity(Gravity.CENTER);
            row.addView(num, new LinearLayout.LayoutParams(Ui.dp(act, 24), ViewGroup.LayoutParams.WRAP_CONTENT));
            TextView name = Ui.tv(act, it.name, 14.5f, current ? C.accent() : C.text(), current ? 1 : 0);
            name.setSingleLine(true);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            row.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            TextView dur = Ui.tv(act, C.fmtDur(it.durationMs), 12, C.textDim(), 0);
            row.addView(dur);
            TextView rm = Ui.tv(act, "✕", 15, C.textDim(), 1);
            rm.setPadding(Ui.dp(act, 10), Ui.dp(act, 6), Ui.dp(act, 4), Ui.dp(act, 6));
            rm.setOnClickListener(v -> Player.removeFromQueue(act, idx));
            row.addView(rm);
            row.setOnClickListener(v -> { Player.playIndex(act, idx); });
            wrap.addView(row);
        }
        s.add(wrap);
        s.add(Ui.space(act, 8));
        s.show();
    }

    // ---------------------------------------------------------------- equalizer

    private void equalizerSheet() {
        Ui.Sheet s = new Ui.Sheet(act, true, null);
        s.title("Equalizer");
        if (!Player.hasCurrent() || Player.state == Player.STATE_IDLE) {
            s.add(Ui.tv(act, "Start playback to tune the sound.\nAll settings are saved.", 15, C.textDim(), 0));
            s.add(Ui.space(act, 10));
            s.show();
            return;
        }
        if (PlaybackService.eqBandLevels() == null) {
            s.add(Ui.tv(act, "Audio effects are unavailable for this stream.", 15, C.textDim(), 0));
            s.add(Ui.space(act, 10));
            s.show();
            return;
        }

        // enable
        LinearLayout onRow = new LinearLayout(act);
        onRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView onLabel = Ui.tv(act, "Equalizer", 15, C.text(), 0);
        onRow.addView(onLabel, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Switch on = new Switch(act);
        on.setChecked(Prefs.eqEnabled());
        on.setOnCheckedChangeListener((b, checked) -> {
            PlaybackService.eqSetEnabled(checked);
            Ui.toast(act, checked ? "Equalizer on" : "Equalizer off");
        });
        onRow.addView(on);
        s.add(onRow);

        // presets
        int presetCount = PlaybackService.eqPresetCount();
        if (presetCount > 0) {
            HorizontalScrollViewWrap presets = new HorizontalScrollViewWrap(act);
            LinearLayout strip = new LinearLayout(act);
            for (int i = 0; i < presetCount; i++) {
                final int idx = i;
                String presetName = PlaybackService.eqPresetName(i);
                TextView chip = Ui.tv(act, presetName, 12.5f, C.text(), 0);
                chip.setPadding(Ui.dp(act, 10), Ui.dp(act, 8), Ui.dp(act, 10), Ui.dp(act, 8));
                Ui.setBg(chip, Ui.ripple(act, Ui.pill(C.surface2())));
                chip.setOnClickListener(v -> {
                    PlaybackService.eqUsePreset(idx);
                    Prefs.sp().edit().putInt(Prefs.KEY_EQ_PRESET, idx).apply();
                });
                strip.addView(chip);
                strip.addView(Ui.space(act, 8));
            }
            presets.addView(strip);
            s.add(presets);
        }

        // band sliders
        int[] levels = PlaybackService.eqBandLevels();
        short[] range = PlaybackService.eqBandRange();
        LinearLayout bands = new LinearLayout(act);
        bands.setGravity(Gravity.CENTER);
        for (int i = 0; i < levels.length; i++) {
            final int band = i;
            VerticalSeek v = new VerticalSeek(act, levels[i], range[0], range[1], val -> {
                PlaybackService.eqSetBand(band, val);
                Prefs.sp().edit().putInt(Prefs.KEY_EQ_PRESET, -1).apply();
                saveBands();
            });
            bands.addView(v);
            bands.addView(Ui.space(act, 12));
        }
        s.add(bands);

        // bass / virtualizer / loudness
        fxRow(s, "Bass boost", Prefs.bassEnabled(), Prefs.bassStrength(), 0, 1000,
                (on2, v2) -> PlaybackService.bassSetEnabled(on2, v2));
        fxRow(s, "3D surround", Prefs.virtEnabled(), Prefs.virtStrength(), 0, 1000,
                (on2, v2) -> PlaybackService.virtSetEnabled(on2, v2));
        fxRow(s, "Loudness", Prefs.loudEnabled(), Prefs.loudGain(), 0, 1500,
                (on2, v2) -> PlaybackService.loudSetEnabled(on2, v2));
        s.add(Ui.space(act, 10));
        s.show();
    }

    private void saveBands() {
        int[] levels = PlaybackService.eqBandLevels();
        if (levels == null) return;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < levels.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(levels[i]);
        }
        Prefs.sp().edit().putString(Prefs.KEY_EQ_BANDS, sb.toString()).apply();
    }

    private interface FxCallback { void on(boolean enabled, int value); }

    private void fxRow(Ui.Sheet s, String label, boolean enabled, int value, int min, int max, FxCallback cb) {
        LinearLayout wrap = new LinearLayout(act);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(0, Ui.dp(act, 8), 0, Ui.dp(act, 2));
        LinearLayout row = new LinearLayout(act);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = Ui.tv(act, label, 14.5f, C.text(), 0);
        row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Switch sw = new Switch(act);
        sw.setChecked(enabled);
        row.addView(sw);
        wrap.addView(row);
        SeekBar sb = new SeekBar(act);
        sb.setMax(max - min);
        sb.setProgress(Math.max(0, Math.min(sb.getMax(), value - min)));
        final boolean[] state = { enabled };
        sw.setOnCheckedChangeListener((b, checked) -> {
            state[0] = checked;
            cb.on(checked, sb.getProgress() + min);
        });
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) cb.on(state[0], progress + min);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        wrap.addView(sb);
        s.add(wrap);
    }

    private class HorizontalScrollViewWrap extends android.widget.HorizontalScrollView {
        HorizontalScrollViewWrap(Context ctx) {
            super(ctx);
            setHorizontalScrollBarEnabled(false);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, Ui.dp(act, 10), 0, Ui.dp(act, 4));
            setLayoutParams(lp);
        }
    }

    private static class VerticalSeek extends LinearLayout {
        VerticalSeek(Context ctx, int initial, int min, int max, OnInt cb) {
            super(ctx);
            setOrientation(VERTICAL);
            setGravity(Gravity.CENTER_HORIZONTAL);
            TextView val = Ui.tv(ctx, "", 10, C.textDim(), 0);
            SeekBar sb = new SeekBar(ctx);
            sb.setMax(max - min);
            sb.setProgress(initial - min);
            int h = Ui.dp(ctx, 110);
            sb.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(ctx, 22), h));
            sb.setRotation(270);
            sb.getProgressDrawable().setColorFilter(C.accent(), android.graphics.PorterDuff.Mode.SRC_IN);
            sb.getThumb().setColorFilter(C.accent(), android.graphics.PorterDuff.Mode.SRC_IN);
            sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    int v = progress + min;
                    val.setText((v > 0 ? "+" : "") + (v / 100));
                    if (fromUser) cb.on(v);
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });
            addView(val);
            LinearLayout box = new LinearLayout(ctx);
            box.setGravity(Gravity.CENTER_HORIZONTAL);
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(Ui.dp(ctx, 28), h);
            box.addView(sb, blp);
            addView(box);
        }
    }

    private interface OnInt { void on(int v); }

    // ---------------------------------------------------------------- state updates

    private void updateTrack() {
        MediaItem it = Player.current;
        if (it == null) {
            title.setText("Nothing playing");
            sub.setText("Pick a track from your library");
            return;
        }
        title.setText(it.name);
        sub.setText((it.isVideo ? "Video · background audio" : "Audio") + " · "
                + C.fmtSize(it.size) + (Player.speed != 1f ? " · " + String.format("%.2f×", Player.speed) : ""));
        if (cover.getBoundUri() == null || !cover.getBoundUri().equals(it.uri)) {
            Thumbs.request(act, it, cover);
        }
    }

    private void updateControls() {
        boolean playing = Player.isPlaying();
        playBtn.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play);
        int rep = Player.repeatMode;
        repeatBtn.setImageResource(rep == Player.REPEAT_ONE ? R.drawable.ic_repeat_one : R.drawable.ic_repeat);
        repeatBtn.setColorFilter(rep == Player.REPEAT_OFF ? C.textDim() : C.accent(), android.graphics.PorterDuff.Mode.SRC_IN);
        shuffleBtn.setColorFilter(Player.shuffle ? C.accent() : C.textDim(), android.graphics.PorterDuff.Mode.SRC_IN);
        boolean fav = Player.current != null && Favorites.has(Player.current.uri);
        favBtn.setImageResource(fav ? R.drawable.ic_heart : R.drawable.ic_heart_off);
        favBtn.setColorFilter(fav ? C.DANGER : C.textDim(), android.graphics.PorterDuff.Mode.SRC_IN);
    }

    private void updateSleepBadge() {
        int rem = Player.sleepRemainSec;
        if (rem < 0) {
            sleepBadge.setVisibility(VISIBLE);
            sleepBadge.setText("Sleep after this track · tap to cancel");
            sleepBadge.setOnClickListener(v -> {
                Player.setSleepTimer(act, 0);
                sleepBadge.setVisibility(GONE);
            });
        } else if (rem > 0) {
            sleepBadge.setVisibility(VISIBLE);
            sleepBadge.setText("Sleep timer: " + C.fmtDurClock(rem * 1000L) + " left · tap to cancel");
            sleepBadge.setOnClickListener(v -> {
                Player.setSleepTimer(act, 0);
                sleepBadge.setVisibility(GONE);
            });
        } else {
            sleepBadge.setVisibility(GONE);
            sleepBadge.setOnClickListener(null);
        }
    }

    @Override public void onStateChanged() { updateControls(); }
    @Override public void onTrackChanged() { updateTrack(); updateControls(); }
    @Override public void onProgress(long posMs, long durMs) {
        if (dragging) return;
        int max = (int) Math.max(1, durMs);
        seek.setMax(max);
        seek.setProgress((int) Math.min(max, posMs));
        posLabel.setText(C.fmtDurClock(posMs));
        durLabel.setText(C.fmtDurClock(durMs));
    }
    @Override public void onQueueChanged() { }
    @Override public void onSleepTick(int remainSec) { updateSleepBadge(); }
    @Override public void onWave(byte[] wave, int samplingRate) {
        viz.feed(wave);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        Player.unsubscribe(this);
    }

    // ---------------------------------------------------------------- visualizer

    static class VisualizerView extends View {
        private byte[] wave = new byte[0];
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        VisualizerView(Context ctx) {
            super(ctx);
            setLayerType(LAYER_TYPE_HARDWARE, null);
        }

        void feed(byte[] w) {
            wave = w;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;
            int bars = 44;
            float gap = w / bars;
            paint.setStyle(Paint.Style.FILL);
            paint.setStrokeCap(Paint.Cap.ROUND);
            float base = h * 0.72f;
            for (int i = 0; i < bars; i++) {
                float amp = 0.06f;
                if (wave.length > 0) {
                    int idx = Math.min(wave.length - 1, (int) ((float) i / bars * wave.length));
                    byte b = wave[idx];
                    amp = 0.06f + 0.5f * ((b & 0xFF) / 128f);
                }
                float bh = Math.max(4, h * amp * (0.4f + 0.6f * Math.abs((float) Math.sin(i * 0.7))));
                float x = i * gap + gap * 0.25f;
                paint.setColor(C.alpha(C.accent(), 42 + (int) (60 * amp)));
                canvas.drawRoundRect(x, base - bh, x + gap * 0.5f, base, gap * 0.28f, gap * 0.28f, paint);
            }
        }
    }
}
