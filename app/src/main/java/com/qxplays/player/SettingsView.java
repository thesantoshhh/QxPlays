package com.qxplays.player;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import java.util.List;

/** Settings screen. Every control is wired to real preferences. */
public class SettingsView extends LinearLayout {

    private final Activity act;

    public SettingsView(Context c) {
        super(c);
        act = (Activity) c;
        setOrientation(VERTICAL);
        setBackgroundColor(C.bg());
        addView(Browsers.base(act, "Settings", null, null));

        ScrollView sc = new ScrollView(act);
        sc.setFillViewport(true);
        LinearLayout col = new LinearLayout(act);
        col.setOrientation(VERTICAL);
        col.setPadding(Ui.dp(act, 16), Ui.dp(act, 4), Ui.dp(act, 16), Ui.dp(act, 24));
        sc.addView(col, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        addView(sc, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        section(col, "Appearance");
        themePicker(col);
        accentPicker(col);

        section(col, "Playback");
        toggle(col, "Resume where you left off", Prefs.resume(),
                v -> Prefs.sp().edit().putBoolean(Prefs.KEY_RESUME, v).apply());
        toggle(col, "Auto-rotate video", Prefs.autoRotate(),
                v -> Prefs.sp().edit().putBoolean(Prefs.KEY_AUTO_ROTATE, v).apply());
        toggle(col, "Keep screen on while playing", Prefs.keepScreenOn(),
                v -> Prefs.sp().edit().putBoolean(Prefs.KEY_KEEP_SCREEN_ON, v).apply());
        toggle(col, "Remember brightness changes", Prefs.rememberBrightness(),
                v -> Prefs.sp().edit().putBoolean(Prefs.KEY_REMEMBER_BRIGHTNESS, v).apply());
        toggle(col, "Swipe to change brightness", Prefs.swipeBrightness(),
                v -> Prefs.sp().edit().putBoolean(Prefs.KEY_SWIPE_BRIGHTNESS, v).apply());
        toggle(col, "Swipe to change volume", Prefs.swipeVolume(),
                v -> Prefs.sp().edit().putBoolean(Prefs.KEY_SWIPE_VOLUME, v).apply());
        toggle(col, "Double-tap to seek ±10s", Prefs.doubleTapSeek(),
                v -> Prefs.sp().edit().putBoolean(Prefs.KEY_DOUBLE_TAP_SEEK, v).apply());
        toggle(col, "Automatically play next", Prefs.playNextAuto(),
                v -> Prefs.sp().edit().putBoolean(Prefs.KEY_PLAY_NEXT_AUTO, v).apply());
        slider(col, "Seek step (seconds)", 5, 60, Prefs.seekStep(),
                v -> Prefs.sp().edit().putInt(Prefs.KEY_SEEK_STEP, v).apply());
        sliderF(col, "Default speed", 0.5f, 2f, Prefs.defaultSpeed(),
                v -> Prefs.setDefaultSpeed(v));

        section(col, "Audio & effects");
        toggle(col, "Live music visualizer", Prefs.visualizer(),
                v -> Prefs.sp().edit().putBoolean(Prefs.KEY_VISUALIZER, v).apply());

        section(col, "Private Space");
        String vaultState = Vault.get(act).isSetUp()
                ? (Vault.get(act).isUnlocked() ? "Unlocked" : "Locked") : "Not set up";
        TextView vaultInfo = Ui.tv(act, "Status: " + vaultState + " · "
                + C.fmtSize(Vault.get(act).vaultSize()) + " stored", 13.5f, C.textDim(), 0);
        vaultInfo.setPadding(Ui.dp(act, 4), Ui.dp(act, 4), Ui.dp(act, 4), Ui.dp(act, 4));
        col.addView(vaultInfo);
        toggle(col, "Lock when app is closed", Prefs.vaultAutoLock(),
                v -> Prefs.sp().edit().putBoolean(Prefs.KEY_VAULT_AUTOLOCK, v).apply());
        TextView changePw = Ui.tv(act, "Change password", 15, C.accent(), 1);
        changePw.setPadding(Ui.dp(act, 4), Ui.dp(act, 12), Ui.dp(act, 4), Ui.dp(act, 12));
        changePw.setOnClickListener(v -> {
            Vault vault = Vault.get(act);
            if (!vault.isSetUp()) { Ui.toast(act, "Private Space is not set up yet"); return; }
            if (!vault.isUnlocked()) { Ui.toast(act, "Unlock the Private Space first"); return; }
            Ui.input(act, "Change password", "Current password", null, true,
                    android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD,
                    oldPw -> Ui.input(act, "Change password", "New password (min 4 characters)", null, true,
                            android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD,
                            newPw -> Ui.input(act, "Change password", "Repeat new password", null, true,
                                    android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD,
                                    newPw2 -> {
                                        if (!newPw.equals(newPw2)) {
                                            Ui.toast(act, "New passwords do not match");
                                            return;
                                        }
                                        new Thread(() -> {
                                            try {
                                                Vault.get(act).changePassword(oldPw, newPw);
                                                act.runOnUiThread(() -> Ui.toast(act, "Password changed"));
                                            } catch (Exception e) {
                                                final String msg = e.getMessage();
                                                act.runOnUiThread(() -> Ui.toast(act, msg));
                                            }
                                        }).start();
                                    })));
        });
        col.addView(changePw);

        section(col, "Library & permissions");
        TextView permStatus = Ui.tv(act, "Media access: " + (Perms.hasMediaRead(act) ? "granted" : "not granted")
                + " · Notifications: " + (Perms.hasNotifications(act) ? "granted" : "not granted")
                + " · Microphone: " + (Perms.hasRecordAudio(act) ? "granted" : "not granted")
                + " · All files: " + (Perms.hasAllFiles(act) ? "granted" : "not granted"), 13.5f, C.textDim(), 0);
        permStatus.setPadding(Ui.dp(act, 4), Ui.dp(act, 4), Ui.dp(act, 4), Ui.dp(act, 8));
        permStatus.setLineSpacing(0, 1.2f);
        col.addView(permStatus);
        TextView openPerms = Ui.tv(act, "Open app permissions", 15, C.accent(), 1);
        openPerms.setPadding(Ui.dp(act, 4), Ui.dp(act, 8), Ui.dp(act, 4), Ui.dp(act, 8));
        openPerms.setOnClickListener(v -> Perms.openAppSettings(act));
        col.addView(openPerms);
        TextView refresh = Ui.tv(act, "Rescan media library", 15, C.accent(), 1);
        refresh.setPadding(Ui.dp(act, 4), Ui.dp(act, 8), Ui.dp(act, 4), Ui.dp(act, 8));
        refresh.setOnClickListener(v -> {
            LibraryData.refresh(act, true);
            Ui.toast(act, "Library refreshed");
        });
        col.addView(refresh);

        section(col, "About");
        TextView about = Ui.tv(act, "QxPlays 1.0.0\n\nA powerful, fully offline video & music player.\n"
                + "No ads · no network · no tracking.\n"
                + "Private Space uses AES-256-GCM with PBKDF2-HMAC-SHA256 (150k rounds).\n\n"
                + "Built with pure Android platform APIs.", 13.5f, C.textDim(), 0);
        about.setPadding(Ui.dp(act, 4), Ui.dp(act, 6), Ui.dp(act, 4), Ui.dp(act, 6));
        about.setLineSpacing(0, 1.3f);
        col.addView(about);
    }

    private void section(LinearLayout col, String name) {
        TextView t = Ui.tv(act, name.toUpperCase(), 12, C.accent(), 2);
        t.setPadding(Ui.dp(act, 4), Ui.dp(act, 18), Ui.dp(act, 4), Ui.dp(act, 4));
        col.addView(t);
    }

    private void toggle(LinearLayout col, String label, boolean initial, OnBool cb) {
        LinearLayout row = new LinearLayout(act);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(act, 4), Ui.dp(act, 9), Ui.dp(act, 4), Ui.dp(act, 9));
        TextView t = Ui.tv(act, label, 15, C.text(), 0);
        row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Switch sw = new Switch(act);
        sw.setChecked(initial);
        sw.setOnCheckedChangeListener((b, checked) -> cb.on(checked));
        row.addView(sw);
        col.addView(row);
    }

    private void slider(LinearLayout col, String label, int min, int max, int initial, OnInt cb) {
        LinearLayout wrap = new LinearLayout(act);
        wrap.setOrientation(VERTICAL);
        wrap.setPadding(Ui.dp(act, 4), Ui.dp(act, 8), Ui.dp(act, 4), Ui.dp(act, 4));
        LinearLayout row = new LinearLayout(act);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = Ui.tv(act, label, 15, C.text(), 0);
        row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView val = Ui.tv(act, String.valueOf(initial), 15, C.accent(), 1);
        row.addView(val);
        wrap.addView(row);
        SeekBar sb = new SeekBar(act);
        sb.setMax(max - min);
        sb.setProgress(initial - min);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int v = progress + min;
                val.setText(String.valueOf(v));
                if (fromUser) cb.on(v);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        wrap.addView(sb);
        col.addView(wrap);
    }

    private void sliderF(LinearLayout col, String label, float min, float max, float initial, OnFloat cb) {
        LinearLayout wrap = new LinearLayout(act);
        wrap.setOrientation(VERTICAL);
        wrap.setPadding(Ui.dp(act, 4), Ui.dp(act, 8), Ui.dp(act, 4), Ui.dp(act, 4));
        LinearLayout row = new LinearLayout(act);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = Ui.tv(act, label, 15, C.text(), 0);
        row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView val = Ui.tv(act, String.format("%.2f×", initial), 15, C.accent(), 1);
        row.addView(val);
        wrap.addView(row);
        SeekBar sb = new SeekBar(act);
        int steps = 30;
        sb.setMax(steps);
        sb.setProgress(Math.round((initial - min) / (max - min) * steps));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float v = min + (max - min) * progress / steps;
                v = Math.round(v * 20) / 20f;
                val.setText(String.format("%.2f×", v));
                if (fromUser) cb.on(v);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        wrap.addView(sb);
        col.addView(wrap);
    }

    private void themePicker(LinearLayout col) {
        LinearLayout row = new LinearLayout(act);
        String[] names = {"Dark", "AMOLED", "Light"};
        int[] vals = {0, 1, 2};
        int current = Prefs.getTheme();
        for (int i = 0; i < names.length; i++) {
            final int v = vals[i];
            TextView chip = Ui.tv(act, names[i], 13.5f, current == v ? 0xFF101828 : C.text(), 1);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(Ui.dp(act, 10), Ui.dp(act, 10), Ui.dp(act, 10), Ui.dp(act, 10));
            Ui.setBg(chip, Ui.ripple(act, Ui.pill(current == v ? C.accent() : C.surface2())));
            chip.setOnClickListener(x -> {
                Prefs.setTheme(v);
                act.recreate();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            lp.setMargins(Ui.dp(act, 3), 0, Ui.dp(act, 3), 0);
            row.addView(chip, lp);
        }
        LinearLayout pad = new LinearLayout(act);
        pad.setPadding(Ui.dp(act, 0), Ui.dp(act, 8), Ui.dp(act, 0), Ui.dp(act, 4));
        pad.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        col.addView(pad);
    }

    private void accentPicker(LinearLayout col) {
        LinearLayout row = new LinearLayout(act);
        int current = Prefs.getAccentIndex();
        for (int i = 0; i < C.ACCENTS.length; i++) {
            final int idx = i;
            View dot = new View(act);
            int s = Ui.dp(act, 34);
            dot.setLayoutParams(new LinearLayout.LayoutParams(s, s));
            Ui.setBg(dot, Ui.rect(Ui.dp(act, 50), C.ACCENTS[i],
                    i == current ? 0xFFFFFFFF : android.graphics.Color.TRANSPARENT, Ui.dp(act, 3)));
            dot.setOnClickListener(v -> {
                Prefs.setAccentIndex(idx);
                act.recreate();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(s, s);
            lp.setMargins(Ui.dp(act, 4), Ui.dp(act, 6), Ui.dp(act, 4), Ui.dp(act, 6));
            row.addView(dot, lp);
        }
        LinearLayout pad = new LinearLayout(act);
        pad.setPadding(Ui.dp(act, 0), Ui.dp(act, 6), Ui.dp(act, 0), Ui.dp(act, 4));
        pad.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        col.addView(pad);
        TextView hint = Ui.tv(act, C.ACCENT_NAMES[current] + " accent", 12.5f, C.textDim(), 0);
        hint.setPadding(Ui.dp(act, 4), Ui.dp(act, 2), 0, 0);
        col.addView(hint);
    }

    private interface OnBool { void on(boolean v); }
    private interface OnInt { void on(int v); }
    private interface OnFloat { void on(float v); }
}
