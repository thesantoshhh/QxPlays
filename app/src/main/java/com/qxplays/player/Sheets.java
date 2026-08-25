package com.qxplays.player;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Bottom sheets: item menus, details, sleep timer, speed, sort, playlist picker. */
public class Sheets {
    public interface OnItemMenu {
        void play(MediaItem item);
        void playNext(MediaItem item);
        void addToQueue(MediaItem item);
        void addToPlaylist(MediaItem item);
        void favorite(MediaItem item);
        void hide(MediaItem item);
        void details(MediaItem item);
        void share(MediaItem item);
        void delete(MediaItem item);
    }

    public static void itemMenu(Context ctx, MediaItem item, boolean isVaultItem, OnItemMenu cb) {
        List<String> labels = new ArrayList<>();
        List<Integer> icons = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();

        labels.add("Play");
        icons.add(R.drawable.ic_play);
        colors.add(C.accent());
        actions.add(() -> cb.play(item));

        if (!isVaultItem) {
            labels.add("Play next");
            icons.add(R.drawable.ic_next);
            colors.add(C.textDim());
            actions.add(() -> cb.playNext(item));
            labels.add("Add to queue");
            icons.add(R.drawable.ic_queue);
            colors.add(C.textDim());
            actions.add(() -> cb.addToQueue(item));
            labels.add("Add to playlist");
            icons.add(R.drawable.ic_playlist);
            colors.add(C.textDim());
            actions.add(() -> cb.addToPlaylist(item));
            labels.add(Favorites.has(item.uri) ? "Remove from favorites" : "Add to favorites");
            icons.add(Favorites.has(item.uri) ? R.drawable.ic_heart : R.drawable.ic_heart_off);
            colors.add(Favorites.has(item.uri) ? C.DANGER : C.textDim());
            actions.add(() -> cb.favorite(item));
            labels.add("Move to Private Space");
            icons.add(R.drawable.ic_shield);
            colors.add(C.WARN);
            actions.add(() -> cb.hide(item));
        }
        labels.add("Details");
        icons.add(R.drawable.ic_info);
        colors.add(C.textDim());
        actions.add(() -> cb.details(item));
        if (!isVaultItem) {
            labels.add("Share");
            icons.add(R.drawable.ic_share);
            colors.add(C.textDim());
            actions.add(() -> cb.share(item));
            labels.add("Delete");
            icons.add(R.drawable.ic_delete);
            colors.add(C.DANGER);
            actions.add(() -> cb.delete(item));
        }
        Ui.menu(ctx, item.name, labels, icons, colors, actions);
    }

    public static void details(Context ctx, MediaItem item, Runnable extra) {
        Ui.Sheet s = new Ui.Sheet(ctx, true, null);
        s.title("Details");
        int pad = Ui.dp(ctx, 14);
        LinearLayout cols = new LinearLayout(ctx);
        cols.setOrientation(LinearLayout.VERTICAL);

        String[][] base = {
                {"Name", item.name},
                {"Type", (item.isVideo ? "Video" : "Audio") + " · " + (item.mime == null ? "?" : item.mime)},
                {"Duration", C.fmtDur(item.durationMs)},
                {"Size", C.fmtSize(item.size)},
                {"Added", item.dateAdded > 0 ? C.fmtDate(item.dateAdded) : "—"},
        };
        if (item.width > 0 && item.height > 0) {
            base = new String[][]{
                    {"Name", item.name},
                    {"Type", (item.isVideo ? "Video" : "Audio") + " · " + (item.mime == null ? "?" : item.mime)},
                    {"Resolution", item.width + " × " + item.height},
                    {"Duration", C.fmtDur(item.durationMs)},
                    {"Size", C.fmtSize(item.size)},
                    {"Added", item.dateAdded > 0 ? C.fmtDate(item.dateAdded) : "—"},
            };
        }
        for (String[] kv : base) addRow(ctx, cols, kv[0], kv[1]);

        Map<String, String> probe = Library.probe(ctx, item.uri);
        for (Map.Entry<String, String> e : probe.entrySet()) {
            String k = e.getKey();
            String v = e.getValue();
            if (k.equals("Duration")) v = C.fmtDur(Long.parseLong(v));
            if (k.equals("Bitrate")) v = (Long.parseLong(v) / 1000) + " kbps";
            addRow(ctx, cols, k, v);
        }
        s.add(cols);
        if (extra != null) {
            s.add(Ui.space(ctx, 12));
            s.add(Ui.btnGhost(ctx, "Open with another app", () -> { s.dismiss(null); extra.run(); }));
        }
        s.add(Ui.space(ctx, 6));
        s.show();
    }

    private static void addRow(Context ctx, LinearLayout parent, String k, String v) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, Ui.dp(ctx, 7), 0, Ui.dp(ctx, 7));
        TextView kt = Ui.tv(ctx, k, 14, C.textDim(), 0);
        TextView vt = Ui.tv(ctx, v == null || v.isEmpty() ? "—" : v, 14, C.text(), 0);
        vt.setGravity(Gravity.END);
        row.addView(kt, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.42f));
        row.addView(vt, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.58f));
        parent.addView(row);
    }

    public interface SleepCallback { void onPick(int minutes); }

    public static void sleepTimer(Context ctx, SleepCallback cb) {
        Ui.Sheet s = new Ui.Sheet(ctx, true, null);
        s.title("Sleep timer");
        int[] options = {0, 5, 10, 15, 30, 45, 60, 90};
        String[] labels = {"Off", "5 minutes", "10 minutes", "15 minutes", "30 minutes", "45 minutes", "1 hour", "1.5 hours"};
        LinearLayout grid = new LinearLayout(ctx);
        grid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout row = null;
        int current = Prefs.sleepMinutes();
        for (int i = 0; i < options.length; i++) {
            if (i % 3 == 0) {
                row = new LinearLayout(ctx);
                row.setOrientation(LinearLayout.HORIZONTAL);
                grid.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            final int min = options[i];
            boolean active = current == min;
            TextView chip = Ui.tv(ctx, labels[i], 13.5f, active ? 0xFF101828 : C.text(), 1);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(Ui.dp(ctx, 6), Ui.dp(ctx, 11), Ui.dp(ctx, 6), Ui.dp(ctx, 11));
            Ui.setBg(chip, Ui.ripple(ctx, Ui.pill(active ? C.accent() : C.surface2())));
            chip.setOnClickListener(v -> { s.dismiss(null); cb.onPick(min); });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            lp.setMargins(Ui.dp(ctx, 3), Ui.dp(ctx, 4), Ui.dp(ctx, 3), Ui.dp(ctx, 4));
            row.addView(chip, lp);
        }
        // "end of track" option
        TextView end = Ui.tv(ctx, "End of current track", 13.5f, C.text(), 1);
        end.setGravity(Gravity.CENTER);
        end.setPadding(Ui.dp(ctx, 6), Ui.dp(ctx, 11), Ui.dp(ctx, 6), Ui.dp(ctx, 11));
        Ui.setBg(end, Ui.ripple(ctx, Ui.pill(C.surface2())));
        end.setOnClickListener(v -> { s.dismiss(null); cb.onPick(-1); });
        grid.addView(end, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        s.add(grid);
        s.add(Ui.space(ctx, 8));
        s.show();
    }

    public interface SpeedCallback { void onPick(float speed); }

    public static void speed(Context ctx, SpeedCallback cb) {
        Ui.Sheet s = new Ui.Sheet(ctx, true, null);
        s.title("Playback speed");
        float[] speeds = {0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f, 4f};
        LinearLayout grid = new LinearLayout(ctx);
        grid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout row = null;
        for (int i = 0; i < speeds.length; i++) {
            if (i % 4 == 0) {
                row = new LinearLayout(ctx);
                row.setOrientation(LinearLayout.HORIZONTAL);
                grid.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            final float sp = speeds[i];
            String label = sp == (int) sp ? String.valueOf((int) sp) : String.valueOf(sp);
            if (Math.abs(sp - Player.speed) < 0.01f) label = "×" + label;
            TextView chip = Ui.tv(ctx, label, 14, Math.abs(sp - Player.speed) < 0.01f ? 0xFF101828 : C.text(), 1);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(Ui.dp(ctx, 4), Ui.dp(ctx, 11), Ui.dp(ctx, 4), Ui.dp(ctx, 11));
            Ui.setBg(chip, Ui.ripple(ctx, Ui.pill(Math.abs(sp - Player.speed) < 0.01f ? C.accent() : C.surface2())));
            chip.setOnClickListener(v -> { s.dismiss(null); cb.onPick(sp); });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            lp.setMargins(Ui.dp(ctx, 3), Ui.dp(ctx, 4), Ui.dp(ctx, 3), Ui.dp(ctx, 4));
            row.addView(chip, lp);
        }
        s.add(grid);
        s.add(Ui.space(ctx, 8));
        s.show();
    }

    public interface SortCallback { void onPick(int mode); }

    public static void sort(Context ctx, int current, SortCallback cb) {
        String[] labels = {"Name (A→Z)", "Name (Z→A)", "Newest first", "Oldest first",
                "Largest first", "Smallest first", "Longest first", "Shortest first"};
        int[] modes = {Library.Sort.NAME_ASC, Library.Sort.NAME_DESC, Library.Sort.DATE_NEW, Library.Sort.DATE_OLD,
                Library.Sort.SIZE_BIG, Library.Sort.SIZE_SMALL, Library.Sort.DUR_LONG, Library.Sort.DUR_SHORT};
        Ui.Sheet s = new Ui.Sheet(ctx, true, null);
        s.title("Sort by");
        for (int i = 0; i < labels.length; i++) {
            final int mode = modes[i];
            TextView row = Ui.tv(ctx, labels[i], 15.5f, mode == current ? C.accent() : C.text(), mode == current ? 1 : 0);
            row.setPadding(Ui.dp(ctx, 14), Ui.dp(ctx, 13), Ui.dp(ctx, 14), Ui.dp(ctx, 13));
            Ui.setBg(row, Ui.ripple(ctx, Ui.rect(Ui.dp(ctx, 12), 0)));
            row.setOnClickListener(v -> { s.dismiss(null); cb.onPick(mode); });
            s.add(row);
        }
        s.add(Ui.space(ctx, 8));
        s.show();
    }

    public interface PlaylistCallback { void onPick(String name); }

    public static void playlistPicker(Context ctx, MediaItem item, PlaylistCallback cb) {
        Ui.Sheet s = new Ui.Sheet(ctx, true, null);
        s.title("Add to playlist");
        List<String> names = Playlists.names();
        for (String name : names) {
            TextView row = Ui.tv(ctx, name, 15.5f, C.text(), 0);
            row.setPadding(Ui.dp(ctx, 14), Ui.dp(ctx, 13), Ui.dp(ctx, 14), Ui.dp(ctx, 13));
            Ui.setBg(row, Ui.ripple(ctx, Ui.rect(Ui.dp(ctx, 12), 0)));
            row.setOnClickListener(v -> {
                Playlists.add(name, item);
                s.dismiss(null);
                Ui.toast(ctx, "Added to \"" + name + "\"");
                if (cb != null) cb.onPick(name);
            });
            s.add(row);
        }
        TextView newP = Ui.tv(ctx, "+ New playlist", 15.5f, C.accent(), 1);
        newP.setPadding(Ui.dp(ctx, 14), Ui.dp(ctx, 13), Ui.dp(ctx, 14), Ui.dp(ctx, 13));
        Ui.setBg(newP, Ui.ripple(ctx, Ui.rect(Ui.dp(ctx, 12), 0)));
        newP.setOnClickListener(v -> {
            Ui.input(ctx, "New playlist", "Playlist name", null, false,
                    android.text.InputType.TYPE_CLASS_TEXT, name -> {
                        try {
                            Playlists.Playlist p = Playlists.create(name);
                            Playlists.add(p.name, item);
                            Ui.toast(ctx, "Added to \"" + p.name + "\"");
                            if (cb != null) cb.onPick(p.name);
                        } catch (Exception e) {
                            Ui.toast(ctx, e.getMessage());
                        }
                    });
        });
        s.add(newP);
        s.add(Ui.space(ctx, 8));
        s.show();
    }

    /** Ask the system for delete consent and actually delete. */
    public static void deleteMedia(Activity act, MediaItem item) {
        Uri u = Uri.parse(item.uri);
        if (item.uri.startsWith("file://")) {
            Ui.confirm(act, "Delete file", "Delete \"" + item.name + "\" permanently?", "Delete", () -> {
                new java.io.File(item.uri.substring(7)).delete();
                Thumbs.evict(item.uri);
                LibraryData.refresh(act, true);
                Ui.toast(act, "Deleted");
            });
            return;
        }
        try {
            act.getContentResolver().delete(u, null, null);
            Thumbs.evict(item.uri);
            LibraryData.refresh(act, true);
            Ui.toast(act, "Deleted");
        } catch (SecurityException e) {
            if (Build.VERSION.SDK_INT >= 30) {
                try {
                    IntentSender is = MediaStore.createDeleteRequest(act.getContentResolver(),
                            java.util.Collections.singletonList(u)).getIntentSender();
                    act.startIntentSenderForResult(is, 2001, new Intent(), 0, 0, 0);
                } catch (Exception ex) {
                    Ui.toast(act, "Cannot delete this item");
                }
            } else {
                Ui.toast(act, "Cannot delete this item");
            }
        }
    }

    /** Hide into the private space, handling the system delete-consent flow. */
    public static void hideToVault(Activity act, MediaItem item) {
        Vault vault = Vault.get(act);
        if (!vault.isSetUp()) {
            Ui.toast(act, "Set up the Private Space first");
            return;
        }
        if (!vault.isUnlocked()) {
            Ui.toast(act, "Private Space is locked — unlock it first");
            return;
        }
        Ui.confirm(act, "Move to Private Space",
                "\"" + item.name + "\" will be encrypted with your password and removed from the media library. Continue?",
                "Hide", () -> {
                    new Thread(() -> {
                        try {
                            IntentSender is = vault.hideItem(item);
                            act.runOnUiThread(() -> {
                                Thumbs.evict(item.uri);
                                LibraryData.refresh(act, true);
                                if (is != null) {
                                    try {
                                        act.startIntentSenderForResult(is, 2001, new Intent(), 0, 0, 0);
                                    } catch (Exception e) {
                                        Ui.toast(act, "Hidden (original kept — delete manually)");
                                    }
                                } else {
                                    Ui.toast(act, "Moved to Private Space");
                                }
                            });
                        } catch (Exception e) {
                            act.runOnUiThread(() -> Ui.toast(act, e.getMessage()));
                        }
                    }).start();
                });
    }

    public static void share(Context ctx, MediaItem item) {
        try {
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType(item.mime == null || item.mime.isEmpty() ? "*/*" : item.mime);
            if (item.uri.startsWith("file://")) {
                send.putExtra(Intent.EXTRA_STREAM, Uri.parse(item.uri));
            } else {
                send.putExtra(Intent.EXTRA_STREAM, Uri.parse(item.uri));
                send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }
            send.putExtra(Intent.EXTRA_TEXT, "Shared with QxPlays");
            ctx.startActivity(Intent.createChooser(send, "Share \"" + item.name + "\""));
        } catch (Exception e) {
            Ui.toast(ctx, "Cannot share this file");
        }
    }
}
