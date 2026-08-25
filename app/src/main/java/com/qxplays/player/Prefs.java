package com.qxplays.player;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Central settings store (SharedPreferences). No network, no analytics. */
public final class Prefs {
    private static SharedPreferences sp;

    private Prefs() {}

    public static SharedPreferences sp() { return sp; }

    public static void init(Context ctx) {
        sp = ctx.getSharedPreferences("qxplays", Context.MODE_PRIVATE);
    }

    // ---------------- Appearance ----------------
    public static final String KEY_THEME = "theme";            // 0 dark, 1 amoled, 2 light
    public static final String KEY_ACCENT = "accent";          // index into C.ACCENTS

    public static int getTheme() { return sp.getInt(KEY_THEME, 0); }
    public static void setTheme(int t) { sp.edit().putInt(KEY_THEME, t).apply(); }
    public static int getAccent() { return C.ACCENTS[Math.min(Math.max(sp.getInt(KEY_ACCENT, 0), 0), C.ACCENTS.length - 1)]; }
    public static void setAccentIndex(int i) { sp.edit().putInt(KEY_ACCENT, i).apply(); }
    public static int getAccentIndex() { return Math.min(Math.max(sp.getInt(KEY_ACCENT, 0), 0), C.ACCENTS.length - 1); }

    // ---------------- Playback ----------------
    public static final String KEY_RESUME = "resume";          // remember position
    public static final String KEY_AUTO_ROTATE = "auto_rotate";
    public static final String KEY_DEFAULT_SPEED = "speed";
    public static final String KEY_KEEP_SCREEN_ON = "keep_screen_on";
    public static final String KEY_REMEMBER_BRIGHTNESS = "remember_brightness";
    public static final String KEY_SWIPE_BRIGHTNESS = "swipe_brightness";
    public static final String KEY_SWIPE_VOLUME = "swipe_volume";
    public static final String KEY_DOUBLE_TAP_SEEK = "double_tap_seek";
    public static final String KEY_SEEK_STEP = "seek_step";    // seconds
    public static final String KEY_PLAY_NEXT_AUTO = "play_next_auto";
    public static final String KEY_AUDIO_SESSION_KEEP = "keep_session";

    public static boolean resume() { return sp.getBoolean(KEY_RESUME, true); }
    public static boolean autoRotate() { return sp.getBoolean(KEY_AUTO_ROTATE, true); }
    public static float defaultSpeed() { return sp.getFloat(KEY_DEFAULT_SPEED, 1.0f); }
    public static void setDefaultSpeed(float s) { sp.edit().putFloat(KEY_DEFAULT_SPEED, s).apply(); }
    public static boolean keepScreenOn() { return sp.getBoolean(KEY_KEEP_SCREEN_ON, true); }
    public static boolean rememberBrightness() { return sp.getBoolean(KEY_REMEMBER_BRIGHTNESS, true); }
    public static boolean swipeBrightness() { return sp.getBoolean(KEY_SWIPE_BRIGHTNESS, true); }
    public static boolean swipeVolume() { return sp.getBoolean(KEY_SWIPE_VOLUME, true); }
    public static boolean doubleTapSeek() { return sp.getBoolean(KEY_DOUBLE_TAP_SEEK, true); }
    public static int seekStep() { return sp.getInt(KEY_SEEK_STEP, 10); }
    public static boolean playNextAuto() { return sp.getBoolean(KEY_PLAY_NEXT_AUTO, true); }

    public static final String KEY_AUTO_PIP = "auto_pip";
    public static boolean autoPip() { return sp.getBoolean(KEY_AUTO_PIP, true); }

    // ---------------- Audio effects ----------------
    public static final String KEY_EQ_ENABLED = "eq_on";
    public static final String KEY_EQ_PRESET = "eq_preset";    // 0..n-1 or custom = -1
    public static final String KEY_EQ_BANDS = "eq_bands";      // comma separated band levels
    public static final String KEY_BASS_ENABLED = "bass_on";
    public static final String KEY_BASS_STRENGTH = "bass_strength";
    public static final String KEY_VIRT_ENABLED = "virt_on";
    public static final String KEY_VIRT_STRENGTH = "virt_strength";
    public static final String KEY_LOUD_ENABLED = "loud_on";
    public static final String KEY_LOUD_GAIN = "loud_gain";
    public static final String KEY_VISUALIZER = "visualizer";

    public static boolean eqEnabled() { return sp.getBoolean(KEY_EQ_ENABLED, false); }
    public static int eqPreset() { return sp.getInt(KEY_EQ_PRESET, -1); }
    public static int[] eqBands() {
        String s = sp.getString(KEY_EQ_BANDS, "");
        if (s.isEmpty()) return null;
        String[] parts = s.split(",");
        int[] out = new int[parts.length];
        try {
            for (int i = 0; i < parts.length; i++) out[i] = Integer.parseInt(parts[i]);
        } catch (Exception e) { return null; }
        return out;
    }
    public static boolean bassEnabled() { return sp.getBoolean(KEY_BASS_ENABLED, false); }
    public static int bassStrength() { return sp.getInt(KEY_BASS_STRENGTH, 500); }
    public static boolean virtEnabled() { return sp.getBoolean(KEY_VIRT_ENABLED, false); }
    public static int virtStrength() { return sp.getInt(KEY_VIRT_STRENGTH, 500); }
    public static boolean loudEnabled() { return sp.getBoolean(KEY_LOUD_ENABLED, false); }
    public static int loudGain() { return sp.getInt(KEY_LOUD_GAIN, 400); }
    public static boolean visualizer() { return sp.getBoolean(KEY_VISUALIZER, true); }

    // ---------------- Sleep timer ----------------
    public static final String KEY_SLEEP_MIN = "sleep_min";
    public static int sleepMinutes() { return sp.getInt(KEY_SLEEP_MIN, 0); }
    public static void setSleepMinutes(int m) { sp.edit().putInt(KEY_SLEEP_MIN, m).apply(); }

    // ---------------- Private space ----------------
    public static final String KEY_VAULT_SALT = "vault_salt";
    public static final String KEY_VAULT_HASH = "vault_hash";
    public static final String KEY_VAULT_ITERS = "vault_iters";
    public static final String KEY_VAULT_AUTOLOCK = "vault_autolock";   // lock when app backgrounds
    public static final String KEY_VAULT_UNLOCKED = "vault_unlocked";   // session unlock
    public static final String KEY_VAULT_UNLOCK_TS = "vault_unlock_ts";

    public static boolean vaultAutoLock() { return sp.getBoolean(KEY_VAULT_AUTOLOCK, true); }
    public static boolean vaultSessionUnlocked() {
        if (!sp.getBoolean(KEY_VAULT_UNLOCKED, false)) return false;
        long ts = sp.getLong(KEY_VAULT_UNLOCK_TS, 0);
        if (System.currentTimeMillis() - ts > 15 * 60 * 1000L) { // session max 15 min
            sp.edit().putBoolean(KEY_VAULT_UNLOCKED, false).apply();
            return false;
        }
        return true;
    }
    public static void vaultSetSessionUnlocked(boolean v) {
        sp.edit().putBoolean(KEY_VAULT_UNLOCKED, v).putLong(KEY_VAULT_UNLOCK_TS, System.currentTimeMillis()).apply();
    }

    // ---------------- Onboarding ----------------
    public static final String KEY_ONBOARDED = "onboarded";
    public static boolean onboarded() { return sp.getBoolean(KEY_ONBOARDED, false); }
    public static void setOnboarded(boolean v) { sp.edit().putBoolean(KEY_ONBOARDED, v).apply(); }

    // ---------------- SAF folder roots ----------------
    public static final String KEY_SAF_ROOTS = "saf_roots";

    public static java.util.Map<String, String> safRoots() {
        java.util.Map<String, String> out = new java.util.LinkedHashMap<>();
        try {
            JSONObject o = new JSONObject(sp.getString(KEY_SAF_ROOTS, "{}"));
            java.util.Iterator<String> keys = o.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                out.put(k, o.getString(k));
            }
        } catch (Exception ignored) {}
        return out;
    }

    public static void addSafRoot(String name, String treeUri) {
        try {
            JSONObject o = new JSONObject(sp.getString(KEY_SAF_ROOTS, "{}"));
            o.put(name, treeUri);
            sp.edit().putString(KEY_SAF_ROOTS, o.toString()).apply();
        } catch (Exception ignored) {}
    }

    public static void removeSafRoot(String name) {
        try {
            JSONObject o = new JSONObject(sp.getString(KEY_SAF_ROOTS, "{}"));
            o.remove(name);
            sp.edit().putString(KEY_SAF_ROOTS, o.toString()).apply();
        } catch (Exception ignored) {}
    }

    // ---------------- JSON helpers ----------------
    public static void putJson(String key, JSONArray arr) { sp.edit().putString(key, arr.toString()).apply(); }
    public static JSONArray getJson(String key) {
        String s = sp.getString(key, "[]");
        try { return new JSONArray(s); } catch (Exception e) { return new JSONArray(); }
    }

    public static void putList(String key, List<String> list) {
        JSONArray a = new JSONArray();
        for (String s : list) a.put(s);
        putJson(key, a);
    }
    public static List<String> getList(String key) {
        List<String> out = new ArrayList<>();
        JSONArray a = getJson(key);
        for (int i = 0; i < a.length(); i++) {
            try { out.add(a.getString(i)); } catch (Exception ignored) {}
        }
        return out;
    }
}
