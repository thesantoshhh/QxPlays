package com.qxplays.player;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Private Space: password setup, lock screen, and the encrypted vault browser. */
public class PrivateView extends LinearLayout {

    public static final int REQ_IMPORT = 400;

    private final Activity act;
    private final FrameLayout body = new FrameLayout(getContext());

    public PrivateView(Context c) {
        super(c);
        act = (Activity) c;
        setOrientation(VERTICAL);
        setBackgroundColor(C.bg());
        addView(Browsers.base(act, "Private Space", null, null));
        addView(body, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        show();
    }

    private void show() {
        body.removeAllViews();
        Vault vault = Vault.get(act);
        if (!vault.isSetUp()) {
            body.addView(setupView());
        } else if (!vault.isUnlocked()) {
            body.addView(lockView());
        } else {
            body.addView(listView());
        }
    }

    // ---------------------------------------------------------------- setup

    private View setupView() {
        LinearLayout col = new LinearLayout(act);
        col.setOrientation(VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(Ui.dp(act, 28), Ui.dp(act, 40), Ui.dp(act, 28), Ui.dp(act, 20));

        TextView shield = Ui.tv(act, "⛨", 46, C.accent(), 3);
        shield.setGravity(Gravity.CENTER);
        col.addView(shield);
        TextView t = Ui.tv(act, "Your private space", 20, C.text(), 2);
        t.setGravity(Gravity.CENTER);
        col.addView(t);
        TextView s = Ui.tv(act,
                "Choose your own password. Hidden files are encrypted with AES-256 "
                        + "using a key derived from it (PBKDF2, 150k rounds) and vanish from your library.\n\n"
                        + "There is no backdoor — if you forget the password, the only option is to wipe the space.",
                14, C.textDim(), 0);
        s.setGravity(Gravity.CENTER);
        s.setLineSpacing(0, 1.25f);
        s.setPadding(0, Ui.dp(act, 10), 0, 0);
        col.addView(s);

        EditText pw1 = input("Create a password (min 4 characters)");
        EditText pw2 = input("Repeat the password");
        col.addView(pw1);
        col.addView(Ui.space(act, 10));
        col.addView(pw2);
        col.addView(Ui.space(act, 16));
        col.addView(Ui.btnPrimary(act, "Secure my space", () -> {
            String a = pw1.getText().toString();
            String b = pw2.getText().toString();
            if (a.length() < 4) { Ui.toast(act, "Password must be at least 4 characters"); return; }
            if (!a.equals(b)) { Ui.toast(act, "Passwords do not match"); return; }
            try {
                Vault.get(act).createPassword(a);
                Vault.get(act).rememberSessionPassword(a);
                Ui.toast(act, "Private Space secured");
                show();
            } catch (Exception e) {
                Ui.toast(act, e.getMessage());
            }
        }));
        col.addView(Ui.space(act, 14));
        return col;
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

    // ---------------------------------------------------------------- lock

    private View lockView() {
        LinearLayout col = new LinearLayout(act);
        col.setOrientation(VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(Ui.dp(act, 28), Ui.dp(act, 48), Ui.dp(act, 28), Ui.dp(act, 20));

        TextView lock = Ui.tv(act, "🔒", 44, C.accent(), 3);
        lock.setGravity(Gravity.CENTER);
        col.addView(lock);
        TextView t = Ui.tv(act, "Private Space is locked", 19, C.text(), 2);
        t.setGravity(Gravity.CENTER);
        col.addView(t);

        EditText pw = input("Enter your password");
        col.addView(Ui.space(act, 12));
        col.addView(pw);
        col.addView(Ui.space(act, 16));
        col.addView(Ui.btnPrimary(act, "Unlock", () -> {
            String a = pw.getText().toString();
            Vault vault = Vault.get(act);
            if (vault.verifyPassword(a)) {
                vault.rememberSessionPassword(a);
                hideKeyboard(pw);
                show();
            } else {
                Ui.vib(act, 40);
                pw.setText("");
                Ui.toast(act, "Wrong password");
            }
        }));
        col.addView(Ui.space(act, 14));
        TextView reset = Ui.tv(act, "Forgot password? Wipe the space", 13, C.DANGER, 0);
        reset.setPadding(Ui.dp(act, 12), Ui.dp(act, 10), Ui.dp(act, 12), Ui.dp(act, 10));
        reset.setOnClickListener(v -> Ui.confirm(act, "Wipe Private Space",
                "The password cannot be recovered. Wiping permanently destroys ALL hidden files. Continue?",
                "Wipe everything", () -> {
                    for (Vault.VaultItem vi : Vault.get(act).listItems()) {
                        Vault.get(act).deleteVaultItem(vi);
                    }
                    Prefs.sp().edit()
                            .remove(Prefs.KEY_VAULT_SALT)
                            .remove(Prefs.KEY_VAULT_HASH)
                            .remove(Prefs.KEY_VAULT_ITERS)
                            .putBoolean(Prefs.KEY_VAULT_UNLOCKED, false)
                            .apply();
                    Vault.get(act).forgetSessionPassword();
                    Ui.toast(act, "Private Space wiped");
                    show();
                }));
        col.addView(reset);
        return col;
    }

    private void hideKeyboard(EditText e) {
        try {
            InputMethodManager imm = (InputMethodManager) act.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(e.getWindowToken(), 0);
        } catch (Exception ignored) {}
    }

    // ---------------------------------------------------------------- vault list

    private final List<Vault.VaultItem> items = new ArrayList<>();

    private View listView() {
        LinearLayout col = new LinearLayout(act);
        col.setOrientation(VERTICAL);

        LinearLayout infoBar = new LinearLayout(act);
        infoBar.setGravity(Gravity.CENTER_VERTICAL);
        infoBar.setPadding(Ui.dp(act, 18), Ui.dp(act, 8), Ui.dp(act, 10), Ui.dp(act, 4));
        TextView info = Ui.tv(act, items.size() + " hidden · " + C.fmtSize(Vault.get(act).vaultSize()) + " encrypted", 13, C.textDim(), 0);
        infoBar.addView(info, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView lockBtn = Ui.tv(act, "Lock", 13, C.accent(), 1);
        lockBtn.setPadding(Ui.dp(act, 12), Ui.dp(act, 6), Ui.dp(act, 4), Ui.dp(act, 6));
        lockBtn.setOnClickListener(v -> {
            Vault.get(act).lock();
            show();
        });
        infoBar.addView(lockBtn);
        col.addView(infoBar);

        TextView add = Ui.tv(act, "+ Import files into Private Space", 14, C.accent(), 1);
        add.setGravity(Gravity.CENTER);
        add.setPadding(Ui.dp(act, 10), Ui.dp(act, 11), Ui.dp(act, 10), Ui.dp(act, 11));
        Ui.setBg(add, Ui.ripple(act, Ui.rect(Ui.dp(act, 14), C.surface())));
        LinearLayout addWrap = new LinearLayout(act);
        addWrap.setPadding(Ui.dp(act, 12), Ui.dp(act, 6), Ui.dp(act, 12), Ui.dp(act, 2));
        addWrap.addView(add, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        add.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            try { act.startActivityForResult(i, REQ_IMPORT); } catch (Exception e) { Ui.toast(act, "No file picker available"); }
        });
        col.addView(addWrap);

        ListView list = new ListView(act);
        list.setDivider(null);
        list.setSelector(android.R.color.transparent);
        list.setPadding(0, 0, 0, Ui.dp(act, 16));
        list.setAdapter(new BaseAdapter() {
            @Override public int getCount() { return items.size(); }
            @Override public Object getItem(int position) { return items.get(position); }
            @Override public long getItemId(int position) { return position; }
            @Override public View getView(int position, View convertView, ViewGroup parent) {
                Vault.VaultItem vi = items.get(position);
                LinearLayout row = new LinearLayout(act);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(Ui.dp(act, 14), Ui.dp(act, 8), Ui.dp(act, 10), Ui.dp(act, 8));

                FrameLayout imgBox = new FrameLayout(act);
                Thumbs.ThumbView thumb = new Thumbs.ThumbView(act);
                thumb.setRounded(true);
                imgBox.addView(thumb, new FrameLayout.LayoutParams(Ui.dp(act, 52), Ui.dp(act, 52)));
                MediaItem pseudo = new MediaItem();
                pseudo.uri = "vault://" + vi.id;
                pseudo.name = vi.name;
                pseudo.isVideo = vi.isVideo;
                pseudo.mime = vi.mime;
                pseudo.durationMs = vi.durationMs;
                pseudo.size = vi.size;
                Thumbs.request(act, pseudo, thumb);
                row.addView(imgBox);

                LinearLayout labels = new LinearLayout(act);
                labels.setOrientation(VERTICAL);
                labels.setPadding(Ui.dp(act, 12), 0, Ui.dp(act, 8), 0);
                TextView title = Ui.tv(act, vi.name, 15, C.text(), 0);
                title.setSingleLine(true);
                title.setEllipsize(android.text.TextUtils.TruncateAt.END);
                TextView meta = Ui.tv(act, C.fmtDur(vi.durationMs) + " · " + C.fmtSize(vi.size), 12.5f, C.textDim(), 0);
                labels.addView(title);
                labels.addView(meta);
                row.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

                TextView lock = Ui.tv(act, "🔒", 15, C.accent(), 1);
                lock.setPadding(Ui.dp(act, 6), Ui.dp(act, 6), Ui.dp(act, 4), Ui.dp(act, 6));
                row.addView(lock);

                row.setOnClickListener(v -> playVaultItem(vi));
                row.setOnLongClickListener(v -> {
                    Ui.vib(act, 20);
                    List<String> menuLabels = new ArrayList<>();
                    List<Runnable> menuActs = new ArrayList<>();
                    menuLabels.add("Play");
                    menuActs.add(() -> playVaultItem(vi));
                    menuLabels.add("Restore to library");
                    menuActs.add(() -> Ui.confirm(act, "Restore file",
                            "Decrypt \"" + vi.name + "\" back into your media library?", "Restore", () ->
                                    new Thread(() -> {
                                        try {
                                            Vault.get(act).restoreItem(vi);
                                            act.runOnUiThread(() -> {
                                                LibraryData.refresh(act, true);
                                                Ui.toast(act, "Restored to library");
                                                refresh();
                                            });
                                        } catch (Exception e) {
                                            act.runOnUiThread(() -> Ui.toast(act, e.getMessage()));
                                        }
                                    }).start()));
                    menuLabels.add("Delete forever");
                    menuActs.add(() -> Ui.confirm(act, "Delete forever",
                            "Permanently destroy the encrypted copy of \"" + vi.name + "\"? This cannot be undone.",
                            "Delete", () -> {
                                Vault.get(act).deleteVaultItem(vi);
                                Ui.toast(act, "Deleted");
                                refresh();
                            }));
                    Ui.menu(act, vi.name, menuLabels, null, null, menuActs);
                    return true;
                });
                return row;
            }
        });

        if (items.isEmpty()) {
            TextView empty = Ui.tv(act, "Nothing hidden yet.\n\nHide files with “Move to Private Space” "
                    + "from any video or audio, or import files directly.", 15, C.textDim(), 0);
            empty.setGravity(Gravity.CENTER);
            empty.setLineSpacing(0, 1.3f);
            empty.setPadding(Ui.dp(act, 36), Ui.dp(act, 60), Ui.dp(act, 36), Ui.dp(act, 20));
            col.addView(empty);
        } else {
            col.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        }
        return col;
    }

    private void playVaultItem(Vault.VaultItem vi) {
        new Thread(() -> {
            try {
                Uri u = Vault.get(act).playUri(vi);
                MediaItem it = new MediaItem();
                it.uri = u.toString();
                it.name = vi.name;
                it.mime = vi.mime;
                it.isVideo = vi.isVideo;
                it.durationMs = vi.durationMs;
                it.size = vi.size;
                act.runOnUiThread(() -> {
                    List<MediaItem> q = new ArrayList<>();
                    q.add(it);
                    if (it.isVideo) {
                        Player.playQueue(act, q, 0);
                        act.startActivity(new Intent(act, PlayerActivity.class));
                    } else {
                        Player.playQueue(act, q, 0);
                        ((MainActivity) act).push(new AudioPlayerView(act));
                    }
                });
            } catch (Exception e) {
                act.runOnUiThread(() -> Ui.toast(act, e.getMessage()));
            }
        }).start();
    }

    private void refresh() {
        items.clear();
        items.addAll(Vault.get(act).listItems());
        show();
    }

    /** Called from MainActivity when import picker returns. */
    public static void onImportResult(Activity act, Intent data) {
        if (data == null) return;
        Vault vault = Vault.get(act);
        if (!vault.isUnlocked()) {
            Ui.toast(act, "Private Space is locked");
            return;
        }
        List<Uri> uris = new ArrayList<>();
        if (data.getClipData() != null) {
            for (int i = 0; i < data.getClipData().getItemCount(); i++) {
                uris.add(data.getClipData().getItemAt(i).getUri());
            }
        } else if (data.getData() != null) {
            uris.add(data.getData());
        }
        new Thread(() -> {
            int ok = 0;
            for (Uri u : uris) {
                try {
                    String name = u.getLastPathSegment();
                    String mime = act.getContentResolver().getType(u);
                    long size = -1;
                    try (android.database.Cursor c = act.getContentResolver().query(u, null, null, null, null)) {
                        if (c != null && c.moveToFirst()) {
                            int si = c.getColumnIndex(OpenableColumns.SIZE);
                            if (si >= 0 && !c.isNull(si)) size = c.getLong(si);
                        }
                    } catch (Exception ignored) {}
                    vault.importUri(u, name == null ? "imported_file" : name, mime == null ? "*/*" : mime, size);
                    ok++;
                } catch (Exception ignored) {}
            }
            final int done = ok;
            act.runOnUiThread(() -> Ui.toast(act, done + " file(s) encrypted into Private Space"));
        }).start();
    }
}
