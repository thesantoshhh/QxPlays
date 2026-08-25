package com.qxplays.player;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Pushed detail screens: folder contents, playlist contents, storage & SAF browsers. */
public class Browsers {

    static LinearLayout base(Context ctx, String title, String actionLabel, Runnable action) {
        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setBackgroundColor(C.bg());

        LinearLayout header = new LinearLayout(ctx);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(Ui.dp(ctx, 4), Ui.dp(ctx, 8), Ui.dp(ctx, 12), Ui.dp(ctx, 8));
        header.addView(Ui.iconBtn(ctx, R.drawable.ic_back, C.text(), v -> {
            if (ctx instanceof MainActivity) ((MainActivity) ctx).pop();
        }));
        TextView t = Ui.tv(ctx, title, 18, C.text(), 2);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        header.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        if (actionLabel != null) {
            TextView a = Ui.tv(ctx, actionLabel, 14, C.accent(), 1);
            a.setPadding(Ui.dp(ctx, 10), Ui.dp(ctx, 8), Ui.dp(ctx, 6), Ui.dp(ctx, 8));
            a.setOnClickListener(v -> { if (action != null) action.run(); });
            header.addView(a);
        }
        col.addView(header);
        return col;
    }

    static View mediaRow(Context ctx, MediaItem item, MediaLists.Bridge bridge) {
        View row = MediaLists.listRow(ctx, item, false, true, bridge, null, null);
        return row;
    }

    // ---------------------------------------------------------------- folder detail

    public static class FolderDetailView extends LinearLayout {
        private final Context ctx;
        private final String folder;
        private final List<MediaItem> items = new ArrayList<>();
        private final ListView list;

        public FolderDetailView(Context c, String folder) {
            super(c);
            ctx = c;
            this.folder = folder;
            setOrientation(VERTICAL);
            addView(base(ctx, folder, "Play all", this::playAll));
            list = new ListView(ctx);
            list.setDivider(null);
            list.setSelector(android.R.color.transparent);
            list.setPadding(0, 0, 0, Ui.dp(ctx, 16));
            list.setAdapter(new BaseAdapter() {
                @Override public int getCount() { return items.size(); }
                @Override public Object getItem(int position) { return items.get(position); }
                @Override public long getItemId(int position) { return position; }
                @Override public View getView(int position, View convertView, ViewGroup parent) {
                    MediaItem item = items.get(position);
                    View row = mediaRow(ctx, item, bridge());
                    row.setOnClickListener(v -> play(position));
                    return row;
                }
            });
            addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
            refresh();
        }

        private MediaLists.Bridge bridge() { return (MediaLists.Bridge) ctx; }

        private void refresh() {
            items.clear();
            items.addAll(Library.inFolder(LibraryData.videos, folder));
            items.addAll(Library.inFolder(LibraryData.audio, folder));
            Library.sort(items, 0);
            ((BaseAdapter) list.getAdapter()).notifyDataSetChanged();
        }

        private void playAll() {
            if (items.isEmpty()) { Ui.toast(ctx, "Folder is empty"); return; }
            bridge().play(new ArrayList<>(items), 0);
        }

        private void play(int index) {
            bridge().play(new ArrayList<>(items), index);
        }
    }

    // ---------------------------------------------------------------- playlist detail

    public static class PlaylistDetailView extends LinearLayout {
        private final Context ctx;
        private final String name;
        private final List<MediaItem> items = new ArrayList<>();
        private final ListView list;

        public PlaylistDetailView(Context c, String name) {
            super(c);
            ctx = c;
            this.name = name;
            setOrientation(VERTICAL);
            addView(base(ctx, name, "Play all", this::playAll));
            list = new ListView(ctx);
            list.setDivider(null);
            list.setSelector(android.R.color.transparent);
            list.setPadding(0, 0, 0, Ui.dp(ctx, 16));
            list.setAdapter(new BaseAdapter() {
                @Override public int getCount() { return items.size(); }
                @Override public Object getItem(int position) { return items.get(position); }
                @Override public long getItemId(int position) { return position; }
                @Override public View getView(int position, View convertView, ViewGroup parent) {
                    MediaItem item = items.get(position);
                    View row = mediaRow(ctx, item, bridge());
                    row.setOnClickListener(v -> play(position));
                    row.setOnLongClickListener(v -> {
                        Ui.vib(ctx, 20);
                        Ui.confirm(ctx, "Remove from playlist",
                                "Remove \"" + item.name + "\" from \"" + name + "\"?", "Remove", () -> {
                                    Playlists.remove(name, item.uri);
                                    refresh();
                                });
                        return true;
                    });
                    return row;
                }
            });
            addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
            refresh();
        }

        private MediaLists.Bridge bridge() { return (MediaLists.Bridge) ctx; }

        private void refresh() {
            items.clear();
            Playlists.Playlist p = Playlists.get(name);
            if (p != null) items.addAll(p.items);
            ((BaseAdapter) list.getAdapter()).notifyDataSetChanged();
        }

        private void playAll() {
            if (items.isEmpty()) { Ui.toast(ctx, "Playlist is empty"); return; }
            bridge().play(new ArrayList<>(items), 0);
        }

        private void play(int index) {
            bridge().play(new ArrayList<>(items), index);
        }
    }

    // ---------------------------------------------------------------- storage browser

    public static class StorageBrowserView extends LinearLayout {
        private final Context ctx;
        private final List<MediaItem> items = new ArrayList<>();
        private final ListView list;
        private final TextView status;
        private final android.widget.ProgressBar progress;
        private boolean scanning;

        public StorageBrowserView(Context c) {
            super(c);
            ctx = c;
            setOrientation(VERTICAL);
            addView(base(ctx, "Device storage", null, null));

            status = Ui.tv(ctx, "Scanning storage…", 13.5f, C.textDim(), 0);
            status.setPadding(Ui.dp(ctx, 18), Ui.dp(ctx, 6), Ui.dp(ctx, 18), Ui.dp(ctx, 4));
            addView(status);
            progress = new android.widget.ProgressBar(ctx);
            progress.setIndeterminate(true);
            progress.getIndeterminateDrawable().setColorFilter(C.accent(), android.graphics.PorterDuff.Mode.SRC_IN);
            LinearLayout progRow = new LinearLayout(ctx);
            progRow.setGravity(Gravity.CENTER_HORIZONTAL);
            progRow.addView(progress, new LinearLayout.LayoutParams(Ui.dp(ctx, 28), Ui.dp(ctx, 28)));
            addView(progRow);

            list = new ListView(ctx);
            list.setDivider(null);
            list.setSelector(android.R.color.transparent);
            list.setPadding(0, 0, 0, Ui.dp(ctx, 16));
            list.setAdapter(new BaseAdapter() {
                @Override public int getCount() { return items.size(); }
                @Override public Object getItem(int position) { return items.get(position); }
                @Override public long getItemId(int position) { return position; }
                @Override public View getView(int position, View convertView, ViewGroup parent) {
                    MediaItem item = items.get(position);
                    View row = mediaRow(ctx, item, bridge());
                    row.setOnClickListener(v -> play(position));
                    return row;
                }
            });
            addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
            startScan();
        }

        private MediaLists.Bridge bridge() { return (MediaLists.Bridge) ctx; }

        private void startScan() {
            if (scanning) return;
            scanning = true;
            ExecutorService pool = Executors.newSingleThreadExecutor();
            pool.execute(() -> {
                List<MediaItem> found = new ArrayList<>();
                java.io.File root = android.os.Environment.getExternalStorageDirectory();
                found.addAll(Library.browseFiles(root, 0));
                // also scan secondary volumes quickly
                java.io.File storage = root.getParentFile();
                if (storage != null) {
                    java.io.File[] vols = storage.listFiles();
                    if (vols != null) {
                        for (java.io.File vol : vols) {
                            if (!vol.getAbsolutePath().equals(root.getAbsolutePath())
                                    && vol.isDirectory() && vol.canRead()) {
                                found.addAll(Library.browseFiles(vol, 0));
                            }
                        }
                    }
                }
                Library.sort(found, 0);
                post(() -> {
                    items.clear();
                    items.addAll(found);
                    scanning = false;
                    progress.setVisibility(GONE);
                    status.setText(found.size() + " media files found");
                    ((BaseAdapter) list.getAdapter()).notifyDataSetChanged();
                });
                pool.shutdown();
            });
        }

        private void play(int index) {
            bridge().play(new ArrayList<>(items), index);
        }
    }

    // ---------------------------------------------------------------- SAF browser

    public static class SafBrowserView extends LinearLayout {
        private final Context ctx;
        private final String treeUri;
        private final List<MediaItem> items = new ArrayList<>();
        private final ListView list;
        private final TextView status;

        public SafBrowserView(Context c, String treeUri) {
            super(c);
            ctx = c;
            this.treeUri = treeUri;
            setOrientation(VERTICAL);
            addView(base(ctx, "Folder", "Remove folder", () -> {
                Ui.confirm(ctx, "Remove folder", "Stop showing this folder in QxPlays?", "Remove", () -> {
                    for (java.util.Map.Entry<String, String> e : Prefs.safRoots().entrySet()) {
                        if (e.getValue().equals(treeUri)) {
                            Prefs.removeSafRoot(e.getKey());
                            break;
                        }
                    }
                    if (ctx instanceof MainActivity) ((MainActivity) ctx).pop();
                });
            }));
            status = Ui.tv(ctx, "Reading folder…", 13.5f, C.textDim(), 0);
            status.setPadding(Ui.dp(ctx, 18), Ui.dp(ctx, 6), Ui.dp(ctx, 18), Ui.dp(ctx, 4));
            addView(status);
            list = new ListView(ctx);
            list.setDivider(null);
            list.setSelector(android.R.color.transparent);
            list.setPadding(0, 0, 0, Ui.dp(ctx, 16));
            list.setAdapter(new BaseAdapter() {
                @Override public int getCount() { return items.size(); }
                @Override public Object getItem(int position) { return items.get(position); }
                @Override public long getItemId(int position) { return position; }
                @Override public View getView(int position, View convertView, ViewGroup parent) {
                    MediaItem item = items.get(position);
                    View row = mediaRow(ctx, item, bridge());
                    row.setOnClickListener(v -> play(position));
                    return row;
                }
            });
            addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
            scan();
        }

        private MediaLists.Bridge bridge() { return (MediaLists.Bridge) ctx; }

        private void scan() {
            ExecutorService pool = Executors.newSingleThreadExecutor();
            pool.execute(() -> {
                List<MediaItem> found = new ArrayList<>();
                try {
                    android.net.Uri tree = android.net.Uri.parse(treeUri);
                    String rootId = android.provider.DocumentsContract.getTreeDocumentId(tree);
                    walk(tree, rootId, found, 0);
                } catch (Exception ignored) {}
                Library.sort(found, 0);
                post(() -> {
                    items.clear();
                    items.addAll(found);
                    status.setText(found.size() + " media files found");
                    ((BaseAdapter) list.getAdapter()).notifyDataSetChanged();
                });
                pool.shutdown();
            });
        }

        private void walk(android.net.Uri tree, String docId, List<MediaItem> out, int depth) {
            if (depth > 6 || docId == null) return;
            try {
                android.net.Uri childrenUri = android.provider.DocumentsContract
                        .buildChildDocumentsUriUsingTree(tree, docId);
                android.database.Cursor c = ctx.getContentResolver().query(childrenUri,
                        new String[]{
                                android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                                android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                                android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE,
                                android.provider.DocumentsContract.Document.COLUMN_SIZE,
                                android.provider.DocumentsContract.Document.COLUMN_LAST_MODIFIED
                        }, null, null, null);
                if (c == null) return;
                while (c.moveToNext()) {
                    String id = c.getString(0);
                    String name = c.getString(1);
                    String mime = c.getString(2);
                    long size = c.isNull(3) ? 0 : c.getLong(3);
                    long mod = c.isNull(4) ? 0 : c.getLong(4);
                    if (android.provider.DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                        walk(tree, id, out, depth + 1);
                    } else if (name != null && Utils.isSupportedMedia(name)) {
                        android.net.Uri docUri = android.provider.DocumentsContract
                                .buildDocumentUriUsingTree(tree, id);
                        MediaItem it = MediaItem.fromSaf(ctx, docUri, mime, size, mod, name);
                        out.add(it);
                    }
                }
                c.close();
            } catch (Exception ignored) {}
        }

        private void play(int index) {
            bridge().play(new ArrayList<>(items), index);
        }
    }
}
