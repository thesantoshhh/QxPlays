package com.qxplays.player;

import android.graphics.Color;
import android.graphics.Typeface;

/** Global constants: colors, dimensions, fonts. */
public final class C {
    private C() {}

    // ---- Palette (dark ocean theme; adjustable accent) ----
    public static final int BG          = 0xFF0B1220;
    public static final int BG_DEEP     = 0xFF070D18;
    public static final int SURFACE     = 0xFF131C2E;
    public static final int SURFACE_2   = 0xFF1B2740;
    public static final int LINE        = 0xFF22304C;
    public static final int TEXT        = 0xFFEAF1FC;
    public static final int TEXT_DIM    = 0xFF93A5C4;
    public static final int DANGER      = 0xFFFF5C6C;
    public static final int OK          = 0xFF38D39F;
    public static final int WARN        = 0xFFFFB547;
    public static final int BLACK       = 0xFF000000;

    public static final int[] ACCENTS = {
        0xFF3E8BFF, // Ocean Blue (default)
        0xFF35E0E0, // Electric Cyan
        0xFF8E6BFF, // Violet
        0xFFFFA14E, // Amber
        0xFFFF5C7A, // Crimson
        0xFF4ADE80, // Neon Green
    };
    public static final String[] ACCENT_NAMES = {
        "Ocean Blue", "Electric Cyan", "Violet", "Amber", "Crimson", "Neon Green"
    };

    public static int accent() { return Prefs.getAccent(); }

    public static final Typeface REGULAR = Typeface.create("sans-serif", Typeface.NORMAL);
    public static final Typeface MEDIUM  = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    public static final Typeface BOLD    = Typeface.create("sans-serif", Typeface.BOLD);
    public static final Typeface BLACK_T = Typeface.create("sans-serif-black", Typeface.NORMAL);
    public static final Typeface LIGHT   = Typeface.create("sans-serif-light", Typeface.NORMAL);

    public static int alpha(int color, int a) {
        return (color & 0x00FFFFFF) | ((a & 0xFF) << 24);
    }

    public static int mix(int from, int to, float f) {
        int r = (int) (Color.red(from) + (Color.red(to) - Color.red(from)) * f);
        int g = (int) (Color.green(from) + (Color.green(to) - Color.green(from)) * f);
        int b = (int) (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * f);
        return Color.rgb(r, g, b);
    }

    public static boolean isDarkTheme() { return Prefs.getTheme() != 2; }
    public static int bg()        { return Prefs.getTheme() == 2 ? 0xFFF2F5FA : Prefs.getTheme() == 1 ? BLACK : BG; }
    public static int bgDeep()    { return Prefs.getTheme() == 2 ? 0xFFE6EBF4 : Prefs.getTheme() == 1 ? 0xFF05070C : BG_DEEP; }
    public static int surface()   { return Prefs.getTheme() == 2 ? 0xFFFFFFFF : Prefs.getTheme() == 1 ? 0xFF0D1117 : SURFACE; }
    public static int surface2()  { return Prefs.getTheme() == 2 ? 0xFFEFF3FA : Prefs.getTheme() == 1 ? 0xFF141A24 : SURFACE_2; }
    public static int line()      { return Prefs.getTheme() == 2 ? 0xFFD9E1EE : Prefs.getTheme() == 1 ? 0xFF1D2636 : LINE; }
    public static int text()      { return Prefs.getTheme() == 2 ? 0xFF101828 : TEXT; }
    public static int textDim()   { return Prefs.getTheme() == 2 ? 0xFF5B6B84 : TEXT_DIM; }

    public static String fmtSize(long bytes) {
        if (bytes < 0) return "—";
        if (bytes < 1024) return bytes + " B";
        float v = bytes;
        String[] u = {"KB", "MB", "GB", "TB"};
        int i = -1;
        while (v >= 1024 && i < u.length - 1) { v /= 1024; i++; }
        return (v >= 100 ? String.format("%.0f", v) : String.format("%.1f", v)) + " " + u[i];
    }

    public static String fmtDur(long ms) {
        if (ms < 0) return "--:--";
        long s = ms / 1000;
        long h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        if (h > 0) return String.format("%d:%02d:%02d", h, m, sec);
        return String.format("%d:%02d", m, sec);
    }

    public static String fmtDurClock(long ms) {
        long s = ms / 1000;
        long h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        if (h > 0) return String.format("%d:%02d:%02d", h, m, sec);
        return String.format("%02d:%02d", m, sec);
    }

    public static String fmtDate(long unixSeconds) {
        if (unixSeconds <= 0) return "";
        java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault());
        return f.format(new java.util.Date(unixSeconds * 1000L));
    }
}
