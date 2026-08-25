package com.qxplays.player;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RoundRectShape;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/** Hand-built Material-style widget toolkit (no external libraries). */
public final class Ui {
    private Ui() {}

    public static int dp(Context ctx, float v) {
        return Math.round(v * ctx.getResources().getDisplayMetrics().density);
    }

    public static float sp(Context ctx, float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, ctx.getResources().getDisplayMetrics());
    }

    // ---------------------------------------------------------------- drawables

    public static GradientDrawable rect(int radius, int fill) {
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(radius);
        d.setColor(fill);
        return d;
    }

    public static GradientDrawable rect(int radius, int fill, int strokeColor, int strokeW) {
        GradientDrawable d = rect(radius, fill);
        if (strokeW > 0) d.setStroke(strokeW, strokeColor);
        return d;
    }

    public static GradientDrawable pill(int fill) { return rect(10000, fill); }

    public static Drawable ripple(Context ctx, Drawable content) {
        if (Build.VERSION.SDK_INT >= 21) {
            return new RippleDrawable(new android.content.res.ColorStateList(
                    new int[][]{new int[]{}},
                    new int[]{C.alpha(C.accent(), 42)}), content, null);
        }
        return content;
    }

    public static Drawable rippleCircle(Context ctx, int fill) {
        GradientDrawable c = new GradientDrawable();
        c.setShape(GradientDrawable.OVAL);
        c.setColor(fill);
        return ripple(ctx, c);
    }

    public static void setBg(View v, Drawable d) {
        if (Build.VERSION.SDK_INT >= 16) v.setBackground(d);
        else v.setBackgroundDrawable(d);
    }

    public static void elevate(View v, float dp) {
        if (Build.VERSION.SDK_INT >= 21) v.setElevation(dp(v.getContext(), dp));
    }

    public static void tint(ImageView iv, int color) {
        iv.setColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN);
    }

    public static void tintDrawable(Drawable d, int color) {
        d.setColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN);
    }

    // ---------------------------------------------------------------- views

    public static TextView tv(Context ctx, String text, float sizeSp, int color, int style) {
        TextView t = new TextView(ctx);
        t.setText(text);
        t.setTextSize(sizeSp);
        t.setTextColor(color);
        t.setTypeface(C.REGULAR);
        switch (style) {
            case 1: t.setTypeface(C.MEDIUM); break;
            case 2: t.setTypeface(C.BOLD); break;
            case 3: t.setTypeface(C.BLACK_T); break;
            case 4: t.setTypeface(C.LIGHT); break;
        }
        t.setIncludeFontPadding(false);
        return t;
    }

    public static ImageView icon(Context ctx, int res, int dpSize, int color) {
        ImageView iv = new ImageView(ctx);
        iv.setImageResource(res);
        iv.setColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN);
        int s = dp(ctx, dpSize);
        iv.setLayoutParams(new LinearLayout.LayoutParams(s, s));
        return iv;
    }

    public static ImageView iconBtn(Context ctx, int res, int color, View.OnClickListener onClick) {
        return iconBtn(ctx, res, color, 40, onClick);
    }

    public static ImageView iconBtn(Context ctx, int res, int color, int dpSize, View.OnClickListener onClick) {
        ImageView iv = icon(ctx, res, 20, color);
        iv.setPadding(dp(ctx, (dpSize - 20) / 2f), dp(ctx, (dpSize - 20) / 2f), dp(ctx, (dpSize - 20) / 2f), dp(ctx, (dpSize - 20) / 2f));
        setBg(iv, rippleCircle(ctx, Color.TRANSPARENT));
        iv.setOnClickListener(onClick);
        return iv;
    }

    /** Primary filled pill button. */
    public static TextView btnPrimary(Context ctx, String text, Runnable onClick) {
        TextView t = tv(ctx, text, 15, C.alpha(C.text(), 255), 1);
        t.setGravity(Gravity.CENTER);
        int padH = dp(ctx, 20), padV = dp(ctx, 13);
        t.setPadding(padH, padV, padH, padV);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(10000);
        bg.setColor(C.accent());
        setBg(t, ripple(ctx, bg));
        elevate(t, 2);
        t.setOnClickListener(v -> {
            if (onClick != null) onClick.run();
        });
        return t;
    }

    public static TextView btnGhost(Context ctx, String text, Runnable onClick) {
        TextView t = tv(ctx, text, 15, C.accent(), 1);
        t.setGravity(Gravity.CENTER);
        int padH = dp(ctx, 20), padV = dp(ctx, 13);
        t.setPadding(padH, padV, padH, padV);
        GradientDrawable bg = rect(10000, Color.TRANSPARENT, C.alpha(C.accent(), 120), dp(ctx, 1.5f));
        setBg(t, ripple(ctx, bg));
        t.setOnClickListener(v -> { if (onClick != null) onClick.run(); });
        return t;
    }

    public static TextView btnDanger(Context ctx, String text, Runnable onClick) {
        TextView t = tv(ctx, text, 15, C.DANGER, 1);
        t.setGravity(Gravity.CENTER);
        int padH = dp(ctx, 20), padV = dp(ctx, 13);
        t.setPadding(padH, padV, padH, padV);
        GradientDrawable bg = rect(10000, Color.TRANSPARENT, C.alpha(C.DANGER, 130), dp(ctx, 1.5f));
        setBg(t, ripple(ctx, bg));
        t.setOnClickListener(v -> { if (onClick != null) onClick.run(); });
        return t;
    }

    public static FrameLayout card(Context ctx) {
        FrameLayout f = new FrameLayout(ctx);
        GradientDrawable bg = rect(dp(ctx, 16), C.surface());
        setBg(f, bg);
        if (Build.VERSION.SDK_INT >= 21) f.setElevation(dp(ctx, 1));
        return f;
    }

    public static View hline(Context ctx) {
        View v = new View(ctx);
        v.setBackgroundColor(C.line());
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(ctx, 1)));
        return v;
    }

    public static View space(Context ctx, int dpH) {
        View v = new View(ctx);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(ctx, dpH)));
        return v;
    }

    public static void vib(Context ctx, int ms) {
        try {
            Vibrator v = (Vibrator) ctx.getSystemService(Context.VIBRATOR_SERVICE);
            if (v == null || !v.hasVibrator()) return;
            if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
            else v.vibrate(ms);
        } catch (Exception ignored) {}
    }

    public static void toast(Context ctx, String msg) {
        try {
            Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show();
        } catch (Exception ignored) {}
    }

    // ---------------------------------------------------------------- overlays

    /** Dim scrim that intercepts taps; used behind sheets. */
    public static View scrim(Context ctx, Runnable onTap) {
        View v = new View(ctx);
        v.setBackgroundColor(0x99000000);
        v.setClickable(true);
        v.setOnClickListener(x -> { if (onTap != null) onTap.run(); });
        return v;
    }

    /**
     * Bottom sheet dialog. content is laid out inside a rounded panel.
     */
    public static class Sheet {
        public final Dialog dialog;
        public final LinearLayout panel;
        private boolean cancelled = false;

        public Sheet(Context ctx, boolean dark, Runnable onDismiss) {
            dialog = new Dialog(ctx, android.R.style.Theme_Translucent_NoTitleBar);
            Window w = dialog.getWindow();
            if (w != null) {
                w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
                lp.copyFrom(w.getAttributes());
                lp.width = WindowManager.LayoutParams.MATCH_PARENT;
                lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
                lp.gravity = Gravity.BOTTOM;
                lp.dimAmount = 0.55f;
                lp.windowAnimations = 0;
                w.setAttributes(lp);
            }
            FrameLayout root = new FrameLayout(ctx);
            root.setOnClickListener(v -> dismiss(onDismiss));
            panel = new LinearLayout(ctx);
            panel.setOrientation(LinearLayout.VERTICAL);
            int pad = dp(ctx, 20);
            panel.setPadding(pad, pad, pad, dp(ctx, 14));
            GradientDrawable bg = rect(dp(ctx, 24), C.surface());
            bg.setCornerRadii(new float[]{dp(ctx, 24), dp(ctx, 24), dp(ctx, 24), dp(ctx, 24), 0, 0, 0, 0});
            setBg(panel, bg);
            FrameLayout.LayoutParams lp2 = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
            root.addView(panel, lp2);
            panel.setOnClickListener(v -> {});
            dialog.setContentView(root);
            dialog.setOnDismissListener(d -> { if (!cancelled && onDismiss != null) onDismiss.run(); });
        }

        public void dismiss(Runnable onDismiss) {
            cancelled = true;
            if (dialog.isShowing()) {
                dialog.dismiss();
                if (onDismiss != null) onDismiss.run();
            }
        }

        public void show() {
            try { dialog.show(); } catch (Exception ignored) {}
            panel.setTranslationY(dp(panel.getContext(), 40));
            panel.animate().translationY(0).setDuration(220)
                    .setInterpolator(new DecelerateInterpolator()).start();
        }

        public Sheet title(String text) {
            LinearLayout row = new LinearLayout(panel.getContext());
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView t = tv(panel.getContext(), text, 18, C.text(), 2);
            row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            ImageView close = iconBtn(panel.getContext(), R.drawable.ic_close, C.textDim(), v -> dismiss(null));
            row.addView(close);
            panel.addView(row);
            panel.addView(hline(panel.getContext()), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(panel.getContext(), 1)));
            panel.addView(space(panel.getContext(), 10));
            return this;
        }

        public Sheet add(View v) {
            panel.addView(v, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return this;
        }
    }

    public static void confirm(Context ctx, String title, String msg, String positive, Runnable onYes) {
        Sheet s = new Sheet(ctx, true, null);
        s.title(title);
        TextView m = tv(ctx, msg, 15, C.textDim(), 0);
        m.setLineSpacing(0, 1.15f);
        s.add(m);
        s.add(space(ctx, 14));
        LinearLayout row = new LinearLayout(ctx);
        row.setGravity(Gravity.END);
        TextView cancel = btnGhost(ctx, "Cancel", () -> s.dismiss(null));
        TextView ok = btnPrimary(ctx, positive, () -> { s.dismiss(null); onYes.run(); });
        row.addView(cancel);
        row.addView(space(ctx, 12));
        row.addView(ok);
        s.add(row);
        s.add(space(ctx, 4));
        s.show();
    }

    /** Text input dialog. */
    public static void input(Context ctx, String title, String hint, String initial,
                             boolean password, int inputType, InputCallback cb) {
        Sheet s = new Sheet(ctx, true, null);
        s.title(title);
        EditText et = new EditText(ctx);
        et.setHint(hint);
        et.setHintTextColor(C.textDim());
        et.setTextColor(C.text());
        et.setTextSize(16);
        et.setSingleLine(true);
        if (initial != null) et.setText(initial);
        if (password) {
            et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        } else {
            et.setInputType(inputType);
        }
        et.setPadding(dp(ctx, 14), dp(ctx, 12), dp(ctx, 14), dp(ctx, 12));
        et.setBackground(rect(dp(ctx, 12), C.surface2(), C.line(), dp(ctx, 1)));
        s.add(et);
        s.add(space(ctx, 14));
        LinearLayout row = new LinearLayout(ctx);
        row.setGravity(Gravity.END);
        TextView cancel = btnGhost(ctx, "Cancel", () -> s.dismiss(null));
        TextView ok = btnPrimary(ctx, "OK", () -> {
            String v = et.getText().toString();
            s.dismiss(null);
            cb.onInput(v);
        });
        row.addView(cancel);
        row.addView(space(ctx, 12));
        row.addView(ok);
        s.add(row);
        s.add(space(ctx, 4));
        s.show();
        et.postDelayed(() -> { et.requestFocus(); }, 200);
    }

    public interface InputCallback { void onInput(String value); }

    /** List menu (used for overflow menus and pickers). */
    public static void menu(Context ctx, String title, List<String> labels,
                            List<Integer> iconRes, List<Integer> iconColors, List<Runnable> actions) {
        Sheet s = new Sheet(ctx, true, null);
        s.title(title);
        for (int i = 0; i < labels.size(); i++) {
            final Runnable action = i < actions.size() ? actions.get(i) : null;
            TextView row = tv(ctx, labels.get(i), 15.5f, C.text(), 0);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(ctx, 14), dp(ctx, 14), dp(ctx, 14), dp(ctx, 14));
            GradientDrawable bg = rect(dp(ctx, 14), Color.TRANSPARENT);
            setBg(row, ripple(ctx, bg));
            row.setOnClickListener(v -> { s.dismiss(null); if (action != null) action.run(); });
            if (iconRes != null && i < iconRes.size() && iconRes.get(i) != 0) {
                LinearLayout wrap = new LinearLayout(ctx);
                wrap.setOrientation(LinearLayout.HORIZONTAL);
                wrap.setGravity(Gravity.CENTER_VERTICAL);
                ImageView ic = icon(ctx, iconRes.get(i), 20,
                        iconColors != null && i < iconColors.size() ? iconColors.get(i) : C.accent());
                wrap.addView(ic);
                wrap.addView(space(ctx, 14));
                wrap.addView(row, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                s.add(wrap);
            } else {
                s.add(row);
            }
        }
        s.add(space(ctx, 8));
        s.show();
    }

    public static void animateAlpha(View v, float from, float to, long ms, int endVisibility) {
        v.setAlpha(from);
        v.setVisibility(View.VISIBLE);
        v.animate().alpha(to).setDuration(ms).setListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                v.setVisibility(endVisibility);
                v.animate().setListener(null);
            }
        }).start();
    }
}
