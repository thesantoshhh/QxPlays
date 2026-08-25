package com.qxplays.player;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/** First-launch flow: welcome → permissions → private-space password. */
public class OnboardingView extends FrameLayout {
    public interface Callback {
        void onDone();
        void onRequestPermissions(String[] perms);
    }

    private final Activity act;
    private final Callback cb;
    private final LinearLayout page;

    public OnboardingView(Activity a, Callback c) {
        super(a);
        act = a;
        cb = c;
        setBackgroundColor(C.bg());
        page = new LinearLayout(a);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER_HORIZONTAL);
        addView(page, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        welcome();
    }

    private void clear() { page.removeAllViews(); }

    private TextView title(String t) {
        TextView v = Ui.tv(act, t, 26, C.text(), 3);
        v.setGravity(Gravity.CENTER);
        return v;
    }

    private TextView body(String t) {
        TextView v = Ui.tv(act, t, 15, C.textDim(), 0);
        v.setGravity(Gravity.CENTER);
        v.setLineSpacing(0, 1.25f);
        v.setPadding(Ui.dp(act, 24), Ui.dp(act, 10), Ui.dp(act, 24), Ui.dp(act, 6));
        return v;
    }

    // ---------------------------------------------------------------- step 1: welcome

    private void welcome() {
        clear();
        TextView logo = Ui.tv(act, "▶", 64, C.accent(), 3);
        logo.setGravity(Gravity.CENTER);
        page.addView(logo);
        page.addView(title("Welcome to QxPlays"));
        page.addView(body("A powerful, fully offline video & music player.\n\n"
                + "• Plays virtually every format your device supports\n"
                + "• Gestures, speed, subtitles, equalizer, sleep timer\n"
                + "• A private space encrypted with your own password\n\n"
                + "No ads. No network. No tracking."));
        page.addView(Ui.space(act, 14));
        TextView next = Ui.btnPrimary(act, "Get started", () -> permissionsStep());
        page.addView(next);
    }

    // ---------------------------------------------------------------- step 2: permissions

    private void permissionsStep() {
        clear();
        page.addView(title("Permissions"));
        page.addView(body("QxPlays needs a few permissions to find and play your media. "
                + "Everything is optional — features gracefully switch off when denied."));

        List<Perms.PermInfo> perms = Perms.allPermissions(act);
        LinearLayout list = new LinearLayout(act);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(Ui.dp(act, 22), Ui.dp(act, 6), Ui.dp(act, 22), 0);
        for (Perms.PermInfo p : perms) {
            LinearLayout row = new LinearLayout(act);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, Ui.dp(act, 9), 0, Ui.dp(act, 9));
            TextView check = Ui.tv(act, p.granted ? "✓" : "•", 16, p.granted ? C.OK : C.textDim(), 1);
            row.addView(check);
            row.addView(Ui.space(act, 12));
            LinearLayout labels = new LinearLayout(act);
            labels.setOrientation(LinearLayout.VERTICAL);
            TextView name = Ui.tv(act, p.label, 15, C.text(), 0);
            TextView why = Ui.tv(act, p.rationale, 12.5f, C.textDim(), 0);
            why.setLineSpacing(0, 1.15f);
            labels.addView(name);
            labels.addView(why);
            row.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            if (!p.granted && p.permission != null) {
                TextView grant = Ui.tv(act, "Allow", 13, C.accent(), 1);
                grant.setPadding(Ui.dp(act, 10), Ui.dp(act, 8), 0, Ui.dp(act, 8));
                grant.setOnClickListener(v -> cb.onRequestPermissions(new String[]{ p.permission }));
                row.addView(grant);
            }
            if (!p.granted && p.permission == null) {
                TextView grant = Ui.tv(act, "Enable", 13, C.accent(), 1);
                grant.setPadding(Ui.dp(act, 10), Ui.dp(act, 8), 0, Ui.dp(act, 8));
                grant.setOnClickListener(v -> Perms.openAllFilesSettings(act));
                row.addView(grant);
            }
            list.addView(row);
        }
        page.addView(list);
        page.addView(Ui.space(act, 10));
        LinearLayout btns = new LinearLayout(act);
        btns.setGravity(Gravity.CENTER);
        TextView later = Ui.btnGhost(act, "Skip", this::vaultStep);
        btns.addView(later);
        btns.addView(Ui.space(act, 12));
        TextView next = Ui.btnPrimary(act, "Continue", this::vaultStep);
        btns.addView(next);
        page.addView(btns);
        page.addView(Ui.space(act, 16));
    }

    // ---------------------------------------------------------------- step 3: vault password

    private EditText pw1, pw2;
    private TextView strength;
    private TextView[] bars = new TextView[4];

    private void vaultStep() {
        clear();
        page.addView(title("Private Space"));
        page.addView(body("Protect private videos and music with your own password. "
                + "Files are encrypted with AES-256 and disappear from your library until you unlock them. "
                + "QxPlays never stores the password itself — only a secure verifier."));

        LinearLayout form = new LinearLayout(act);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(Ui.dp(act, 24), Ui.dp(act, 8), Ui.dp(act, 24), 0);

        pw1 = input("Create a password (min 4 characters)");
        form.addView(pw1);
        form.addView(Ui.space(act, 10));
        pw2 = input("Repeat the password");
        form.addView(pw2);

        // strength meter
        form.addView(Ui.space(act, 12));
        LinearLayout meter = new LinearLayout(act);
        for (int i = 0; i < 4; i++) {
            TextView b = Ui.tv(act, "", 1, Color.TRANSPARENT, 0);
            int w = Ui.dp(act, 34), h = Ui.dp(act, 5);
            b.setLayoutParams(new LinearLayout.LayoutParams(w, h));
            Ui.setBg(b, Ui.rect(Ui.dp(act, 3), C.surface2()));
            meter.addView(b);
            meter.addView(Ui.space(act, 6));
            bars[i] = b;
        }
        form.addView(meter);
        strength = Ui.tv(act, "Password strength", 12.5f, C.textDim(), 0);
        strength.setPadding(0, Ui.dp(act, 6), 0, 0);
        form.addView(strength);

        View.OnFocusChangeListener strengthUpdate = (v, has) -> updateStrength();
        pw1.setOnFocusChangeListener(strengthUpdate);

        page.addView(form);
        page.addView(Ui.space(act, 14));
        LinearLayout btns = new LinearLayout(act);
        btns.setGravity(Gravity.CENTER);
        TextView later = Ui.btnGhost(act, "Skip for now", this::finish);
        btns.addView(later);
        btns.addView(Ui.space(act, 12));
        TextView next = Ui.btnPrimary(act, "Set password", () -> {
            String a = pw1.getText().toString();
            String b = pw2.getText().toString();
            if (a.length() < 4) {
                Ui.toast(act, "Password must be at least 4 characters");
                return;
            }
            if (!a.equals(b)) {
                Ui.toast(act, "Passwords do not match");
                return;
            }
            try {
                Vault.get(act).createPassword(a);
                Vault.get(act).rememberSessionPassword(a);
                Ui.toast(act, "Private Space secured");
                finish();
            } catch (Exception e) {
                Ui.toast(act, e.getMessage());
            }
        });
        btns.addView(next);
        page.addView(btns);
        page.addView(Ui.space(act, 16));
    }

    private EditText input(String hint) {
        EditText e = new EditText(act);
        e.setHint(hint);
        e.setHintTextColor(C.textDim());
        e.setTextColor(C.text());
        e.setTextSize(15);
        e.setSingleLine(true);
        e.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        e.setPadding(Ui.dp(act, 14), Ui.dp(act, 13), Ui.dp(act, 14), Ui.dp(act, 13));
        Ui.setBg(e, Ui.rect(Ui.dp(act, 12), C.surface2(), C.line(), Ui.dp(act, 1)));
        return e;
    }

    private void updateStrength() {
        String p = pw1.getText().toString();
        int score = 0;
        if (p.length() >= 4) score++;
        if (p.length() >= 8) score++;
        boolean mixed = p.matches(".*[A-Za-z].*") && p.matches(".*[0-9].*");
        if (mixed) score++;
        if (p.length() >= 12) score++;
        for (int i = 0; i < 4; i++) {
            int color = i < score ? (score <= 1 ? C.DANGER : score == 2 ? C.WARN : C.OK) : C.surface2();
            Ui.setBg(bars[i], Ui.rect(Ui.dp(act, 3), color));
        }
        String[] labels = {"", "Weak", "Fair", "Good", "Strong"};
        strength.setText("Password strength" + (score > 0 ? ": " + labels[score] : ""));
    }

    private void finish() {
        try {
            InputMethodManager imm = (InputMethodManager) act.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(getWindowToken(), 0);
        } catch (Exception ignored) {}
        cb.onDone();
    }
}
