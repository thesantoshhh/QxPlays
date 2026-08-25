package com.qxplays.player;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** List/grid views for the six home tabs. */
public class MediaLists {

    /** Callbacks implemented by MainActivity. */
    public interface Bridge {
        Activity getActivity();
        void play(List<MediaItem> queue, int index);
        void itemMenu(MediaItem item);
        void openFolder(String folder);
        void openPlaylist(String name);
        void openStorageBrowser();
        void startSafPicker();
        void openSafRoot(String treeUri);
    }

    // ---------------------------------------------------------------- shared parts

    static View headerRow(Context ctx, String countText, Runnable onSort) {
        LinearLayout row = new LinearLayout(ctx);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(ctx, 18), Ui.dp(ctx, 8), Ui.dp(ctx, 10), Ui.dp(ctx, 2));
        TextView t = Ui.tv(ctx, countText, 13, C.textDim(), 0);
        row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView sort = Ui.tv(ctx, "Sort", 13, C.accent(), 1);
        sort.setPadding(Ui.dp(ctx, 12), Ui.dp(ctx, 6), Ui.dp(ctx, 4), Ui.dp(ctx, 6));
        Ui.setBg(sort, Ui.ripple(ctx, Ui.rect(Ui.dp(ctx, 100), Color.TRANSPARENT)));
        sort.setOnClickListener(v -> { if (onSort != null) onSort.run(); });
        row.addView(sort);
        return row;
    }

    static View emptyState(Context ctx, String title, String sub) {
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(Ui.dp(ctx, 32), Ui.dp(ctx, 64), Ui.dp(ctx, 32), Ui.dp(ctx, 32));
        TextView t = Ui.tv(ctx, title, 17, C.text(), 1);
        t.setGravity(Gravity.CENTER);
        TextView s = Ui.tv(ctx, sub, 14, C.textDim(), 0);
        s.setGravity(Gravity.CENTER);
        s.setPadding(0, Ui.dp(ctx, 8), 0, 0);
        s.setLineSpacing(0, 1.2f);
        box.addView(t);
        box.addView(s);
        return box;
    }

    static View progressBar(Context ctx) {
        android.widget.ProgressBar p = new android.widget.ProgressBar(ctx);
        p.setIndeterminate(true);
        p.getIndeterminateDrawable().setColorFilter(C.accent(), android.graphics.PorterDuff.Mode.SRC_IN);
        FrameLayout f = new FrameLayout(ctx);
        f.addView(p, new FrameLayout.LayoutParams(Ui.dp(ctx, 40), Ui.dp(ctx, 40), Gravity.CENTER));
        return f;
    }

    /** Long-press → item menu, via bridge. */
    static void wireLongPress(View v, MediaItem item, Bridge bridge) {
        v.setOnLongClickListener(x -> {
            Ui.vib(v.getContext(), 20);
            bridge.itemMenu(item);
            return true;
        });
    }

    // ---------------------------------------------------------------- video grid

    public static class MediaGrid extends FrameLayout {
        public static final int MODE_VIDEOS = 0;
        private final Bridge bridge;
        private final int mode;
        private final GridView grid;
        private final View empty;
        private int sortMode = 0;
        private final List<MediaItem> items = new ArrayList<>();

        public MediaGrid(Context ctx, int mode, Bridge bridge) {
            super(ctx);
            this.bridge = bridge;
            this.mode = mode;
            setBackgroundColor(C.bg());

            LinearLayout col = new LinearLayout(ctx);
            col.setOrientation(LinearLayout.VERTICAL);
            addView(col, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            col.addView(headerRow(ctx, "", () ->
                    Sheets.sort(ctx, sortMode, m -> { sortMode = m; refresh(); })));

            grid = new GridView(ctx);
            grid.setNumColumns(2);
            grid.setVerticalSpacing(Ui.dp(ctx, 10));
            grid.setHorizontalSpacing(Ui.dp(ctx, 10));
            grid.setPadding(Ui.dp(ctx, 14), Ui.dp(ctx, 6), Ui.dp(ctx, 14), Ui.dp(ctx, 90));
            grid.setSelector(android.R.color.transparent);
            grid.setAdapter(new BaseAdapter() {
                @Override public int getCount() { return items.size(); }
                @Override public Object getItem(int position) { return items.get(position); }
                @Override public long getItemId(int position) { return position; }
                @Override public View getView(int position, View convertView, ViewGroup parent) {
                    return cell(parent.getContext(), items.get(position));
                }
            });
            grid.setOnItemClickListener((p, v, pos, id) -> {
                List<MediaItem> q = new ArrayList<>(items);
                bridge.play(q, pos);
            });
            col.addView(grid, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

            empty = emptyState(ctx, "No videos found",
                    "Videos on your device will appear here.\nTap to grant media access if the library is empty.");
            empty.setVisibility(GONE);
            addView(empty, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }

        private View cell(Context ctx, MediaItem item) {
            LinearLayout cell = new LinearLayout(ctx);
            cell.setOrientation(LinearLayout.VERTICAL);

            FrameLayout imgBox = new FrameLayout(ctx);
            Thumbs.ThumbView thumb = new Thumbs.ThumbView(ctx);
            thumb.setRounded(true);
            imgBox.addView(thumb, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(ctx, 105)));
            Thumbs.request(ctx, item, thumb);

            TextView dur = Ui.tv(ctx, C.fmtDur(item.durationMs), 11.5f, Color.WHITE, 1);
            dur.setPadding(Ui.dp(ctx, 7), Ui.dp(ctx, 3), Ui.dp(ctx, 7), Ui.dp(ctx, 3));
            Ui.setBg(dur, Ui.rect(Ui.dp(ctx, 6), 0xCC000000));
            FrameLayout.LayoutParams dlp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM | Gravity.END);
            dlp.setMargins(0, 0, Ui.dp(ctx, 6), Ui.dp(ctx, 6));
            imgBox.addView(dur, dlp);

            if (item.width > 0 && item.height > 0) {
                TextView res = Ui.tv(ctx, item.width + "×" + item.height, 10.5f, Color.WHITE, 1);
                res.setPadding(Ui.dp(ctx, 6), Ui.dp(ctx, 2), Ui.dp(ctx, 6), Ui.dp(ctx, 2));
                Ui.setBg(res, Ui.rect(Ui.dp(ctx, 5), 0xB3000000));
                FrameLayout.LayoutParams rlp = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.TOP | Gravity.END);
                rlp.setMargins(0, Ui.dp(ctx, 6), Ui.dp(ctx, 6), 0);
                imgBox.addView(res, rlp);
            }
            cell.addView(imgBox);

            TextView name = Ui.tv(ctx, item.name, 13.5f, C.text(), 0);
            name.setSingleLine(true);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            name.setPadding(Ui.dp(ctx, 2), Ui.dp(ctx, 7), Ui.dp(ctx, 2), 0);
            cell.addView(name);

            TextView meta = Ui.tv(ctx, C.fmtSize(item.size) + " · " + C.fmtDate(item.dateAdded), 11.5f, C.textDim(), 0);
            meta.setPadding(Ui.dp(ctx, 2), Ui.dp(ctx, 2), Ui.dp(ctx, 2), 0);
            cell.addView(meta);

            wireLongPress(cell, item, bridge);
            return cell;
        }

        public void refresh() {
            items.clear();
            items.addAll(LibraryData.videos);
            Library.sort(items, sortMode);
            ((TextView) ((LinearLayout) getChildAt(0)).getChildAt(0))
                    .setText(items.size() + " videos");
            boolean has = !items.isEmpty();
            empty.setVisibility(has ? GONE : VISIBLE);
            grid.setVisibility(has ? VISIBLE : GONE);
            if (has) ((BaseAdapter) grid.getAdapter()).notifyDataSetChanged();
        }
    }

    // ---------------------------------------------------------------- list rows

    static class RowHolder {
        Thumbs.ThumbView thumb;
        TextView title, meta;
        FrameLayout thumbBox;
        View progress;
    }

    static View listRow(Context ctx, MediaItem item, boolean showHeart, boolean showMenu,
                        Bridge bridge, RowHolder holder, android.view.View.OnClickListener onTap) {
        LinearLayout row = new LinearLayout(ctx);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(ctx, 14), Ui.dp(ctx, 7), Ui.dp(ctx, 10), Ui.dp(ctx, 7));

        FrameLayout imgBox = new FrameLayout(ctx);
        Thumbs.ThumbView thumb = new Thumbs.ThumbView(ctx);
        thumb.setRounded(true);
        imgBox.addView(thumb, new FrameLayout.LayoutParams(Ui.dp(ctx, 52), Ui.dp(ctx, 52)));
        Thumbs.request(ctx, item, thumb);
        row.addView(imgBox);

        LinearLayout labels = new LinearLayout(ctx);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(Ui.dp(ctx, 12), 0, Ui.dp(ctx, 8), 0);
        TextView title = Ui.tv(ctx, item.name, 15, C.text(), 0);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        TextView meta = Ui.tv(ctx, C.fmtDur(item.durationMs) + " · " + C.fmtSize(item.size), 12.5f, C.textDim(), 0);
        labels.addView(title);
        labels.addView(meta);
        row.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        if (showHeart) {
            boolean fav = Favorites.has(item.uri);
            TextView heart = Ui.tv(ctx, "♥", 18, fav ? C.DANGER : C.textDim(), 1);
            heart.setPadding(Ui.dp(ctx, 10), Ui.dp(ctx, 8), Ui.dp(ctx, 10), Ui.dp(ctx, 8));
            heart.setOnClickListener(v -> {
                Favorites.toggle(item);
                Ui.toast(ctx, Favorites.has(item.uri) ? "Added to favorites" : "Removed from favorites");
                LibraryData.refresh(bridge.getActivity(), true);
            });
            row.addView(heart);
        }
        if (showMenu) {
            TextView more = Ui.tv(ctx, "⋮", 20, C.textDim(), 1);
            more.setPadding(Ui.dp(ctx, 8), Ui.dp(ctx, 8), Ui.dp(ctx, 4), Ui.dp(ctx, 8));
            more.setOnClickListener(v -> bridge.itemMenu(item));
            row.addView(more);
        }

        row.setOnClickListener(onTap);
        wireLongPress(row, item, bridge);

        if (holder != null) {
            holder.thumb = thumb;
            holder.title = title;
            holder.meta = meta;
            holder.thumbBox = imgBox;
            holder.progress = null;
        }
        return row;
    }

    // ---------------------------------------------------------------- audio / favorites list

    public static class MediaList extends FrameLayout {
        public static final int MODE_AUDIO = 0, MODE_FAVORITES = 1;
        private final Bridge bridge;
        private final int mode;
        private final ListView list;
        private final View empty;
        private int sortMode = 0;
        private final List<MediaItem> items = new ArrayList<>();

        public MediaList(Context ctx, int mode, Bridge bridge) {
            super(ctx);
            this.bridge = bridge;
            this.mode = mode;
            setBackgroundColor(C.bg());

            LinearLayout col = new LinearLayout(ctx);
            col.setOrientation(LinearLayout.VERTICAL);
            addView(col, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            col.addView(headerRow(ctx, "", () -> Sheets.sort(ctx, sortMode, m -> { sortMode = m; refresh(); })));

            list = new ListView(ctx);
            list.setDivider(null);
            list.setSelector(android.R.color.transparent);
            list.setPadding(0, 0, 0, Ui.dp(ctx, 90));
            list.setAdapter(new BaseAdapter() {
                @Override public int getCount() { return items.size(); }
                @Override public Object getItem(int position) { return items.get(position); }
                @Override public long getItemId(int position) { return position; }
                @Override public View getView(int position, View convertView, ViewGroup parent) {
                    MediaItem item = items.get(position);
                    View row = listRow(parent.getContext(), item, mode == MODE_AUDIO, true, bridge, null, null);
                    row.setOnClickListener(v -> {
                        List<MediaItem> q = new ArrayList<>(items);
                        bridge.play(q, position);
                    });
                    wireLongPress(row, item, bridge);
                    return row;
                }
            });
            list.setOnItemClickListener((p, v, pos, id) -> {
                List<MediaItem> q = new ArrayList<>(items);
                bridge.play(q, pos);
            });
            col.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

            empty = emptyState(ctx,
                    mode == MODE_AUDIO ? "No music found" : "Nothing here yet",
                    mode == MODE_AUDIO
                            ? "Audio files on your device will appear here."
                            : "Tap ♥ on any track to keep it in your favorites.");
            empty.setVisibility(GONE);
            addView(empty, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }

        public void refresh() {
            items.clear();
            if (mode == MODE_AUDIO) {
                items.addAll(LibraryData.audio);
            } else {
                items.addAll(Favorites.all());
                if (sortMode == 0) sortMode = 2; // favorites newest-first by default
            }
            Library.sort(items, sortMode);
            ((TextView) ((LinearLayout) getChildAt(0)).getChildAt(0))
                    .setText(items.size() + (mode == MODE_AUDIO ? " tracks" : " favorites"));
            boolean has = !items.isEmpty();
            empty.setVisibility(has ? GONE : VISIBLE);
            list.setVisibility(has ? VISIBLE : GONE);
            if (has) ((BaseAdapter) list.getAdapter()).notifyDataSetChanged();
        }
    }

    // ---------------------------------------------------------------- folders

    public static class FolderList extends FrameLayout {
        private final Bridge bridge;
        private final ListView list;
        private final List<String> rows = new ArrayList<>();

        public FolderList(Context ctx, Bridge bridge) {
            super(ctx);
            this.bridge = bridge;
            setBackgroundColor(C.bg());

            LinearLayout col = new LinearLayout(ctx);
            col.setOrientation(LinearLayout.VERTICAL);
            addView(col, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            LinearLayout actions = new LinearLayout(ctx);
            actions.setPadding(Ui.dp(ctx, 12), Ui.dp(ctx, 10), Ui.dp(ctx, 12), Ui.dp(ctx, 4));
            TextView browse = Ui.tv(ctx, "Browse device storage", 14, C.accent(), 1);
            browse.setGravity(Gravity.CENTER);
            browse.setPadding(Ui.dp(ctx, 10), Ui.dp(ctx, 11), Ui.dp(ctx, 10), Ui.dp(ctx, 11));
            Ui.setBg(browse, Ui.ripple(ctx, Ui.rect(Ui.dp(ctx, 14), C.surface())));
            browse.setOnClickListener(v -> bridge.openStorageBrowser());
            TextView saf = Ui.tv(ctx, "+ Add folder", 14, C.accent(), 1);
            saf.setGravity(Gravity.CENTER);
            saf.setPadding(Ui.dp(ctx, 10), Ui.dp(ctx, 11), Ui.dp(ctx, 10), Ui.dp(ctx, 11));
            Ui.setBg(saf, Ui.ripple(ctx, Ui.rect(Ui.dp(ctx, 14), C.surface())));
            saf.setOnClickListener(v -> bridge.startSafPicker());
            LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            alp.setMargins(0, 0, Ui.dp(ctx, 6), 0);
            actions.addView(browse, alp);
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            slp.setMargins(Ui.dp(ctx, 6), 0, 0, 0);
            actions.addView(saf, slp);
            col.addView(actions);

            list = new ListView(ctx);
            list.setDivider(null);
            list.setSelector(android.R.color.transparent);
            list.setPadding(0, 0, 0, Ui.dp(ctx, 90));
            list.setAdapter(new BaseAdapter() {
                @Override public int getCount() { return rows.size(); }
                @Override public Object getItem(int position) { return rows.get(position); }
                @Override public long getItemId(int position) { return position; }
                @Override public View getView(int position, View convertView, ViewGroup parent) {
                    final String name = rows.get(position);
                    final String treeUri = Prefs.safRoots().containsKey(name) ? Prefs.safRoots().get(name) : null;
                    LinearLayout row = new LinearLayout(parent.getContext());
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    row.setPadding(Ui.dp(ctx, 16), Ui.dp(ctx, 10), Ui.dp(ctx, 14), Ui.dp(ctx, 10));
                    int count = 0;
                    for (MediaItem it : LibraryData.videos) if (name.equals(it.folder)) count++;
                    for (MediaItem it : LibraryData.audio) if (name.equals(it.folder)) count++;
                    row.addView(Ui.icon(ctx, treeUri != null ? R.drawable.ic_folder : R.drawable.ic_folder, 22, treeUri != null ? C.WARN : C.accent()));
                    row.addView(Ui.space(ctx, 14));
                    TextView t = Ui.tv(ctx, name, 15, C.text(), 0);
                    row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                    TextView c = Ui.tv(ctx, String.valueOf(count), 13.5f, C.textDim(), 0);
                    row.addView(c);
                    row.addView(Ui.space(ctx, 8));
                    row.addView(Ui.icon(ctx, R.drawable.ic_chevron, 18, C.textDim()));
                    row.setOnClickListener(v -> {
                        if (treeUri != null) bridge.openSafRoot(treeUri);
                        else bridge.openFolder(name);
                    });
                    return row;
                }
            });
            col.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        }

        public void refresh() {
            rows.clear();
            rows.addAll(Library.folders(LibraryData.videos));
            rows.addAll(Library.folders(LibraryData.audio));
            java.util.LinkedHashSet<String> set = new java.util.LinkedHashSet<>(rows);
            rows.clear();
            rows.addAll(set);
            for (String tree : Prefs.safRoots().keySet()) if (!rows.contains(tree)) rows.add(tree);
            if (getChildCount() > 0 && getChildAt(0) instanceof ListView) {
                ((BaseAdapter) list.getAdapter()).notifyDataSetChanged();
            }
        }
    }

    // ---------------------------------------------------------------- playlists

    public static class PlaylistList extends FrameLayout {
        private final Bridge bridge;
        private final ListView list;
        private final List<Playlists.Playlist> rows = new ArrayList<>();

        public PlaylistList(Context ctx, Bridge bridge) {
            super(ctx);
            this.bridge = bridge;
            setBackgroundColor(C.bg());
            LinearLayout col = new LinearLayout(ctx);
            col.setOrientation(LinearLayout.VERTICAL);
            addView(col, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            TextView create = Ui.tv(ctx, "+ Create playlist", 14, C.accent(), 1);
            create.setGravity(Gravity.CENTER);
            create.setPadding(Ui.dp(ctx, 10), Ui.dp(ctx, 11), Ui.dp(ctx, 10), Ui.dp(ctx, 11));
            Ui.setBg(create, Ui.ripple(ctx, Ui.rect(Ui.dp(ctx, 14), C.surface())));
            LinearLayout wrap = new LinearLayout(ctx);
            wrap.setPadding(Ui.dp(ctx, 12), Ui.dp(ctx, 10), Ui.dp(ctx, 12), Ui.dp(ctx, 4));
            wrap.addView(create, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            create.setOnClickListener(v -> Ui.input(ctx, "New playlist", "Playlist name", null, false,
                    android.text.InputType.TYPE_CLASS_TEXT, name -> {
                        try {
                            Playlists.create(name);
                            refresh();
                            Ui.toast(ctx, "Playlist created");
                        } catch (Exception e) {
                            Ui.toast(ctx, e.getMessage());
                        }
                    }));
            col.addView(wrap);

            list = new ListView(ctx);
            list.setDivider(null);
            list.setSelector(android.R.color.transparent);
            list.setPadding(0, 0, 0, Ui.dp(ctx, 90));
            list.setAdapter(new BaseAdapter() {
                @Override public int getCount() { return rows.size(); }
                @Override public Object getItem(int position) { return rows.get(position); }
                @Override public long getItemId(int position) { return position; }
                @Override public View getView(int position, View convertView, ViewGroup parent) {
                    Playlists.Playlist p = rows.get(position);
                    LinearLayout row = new LinearLayout(parent.getContext());
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    row.setPadding(Ui.dp(ctx, 16), Ui.dp(ctx, 12), Ui.dp(ctx, 14), Ui.dp(ctx, 12));
                    row.addView(Ui.icon(ctx, R.drawable.ic_playlist, 22, C.accent()));
                    row.addView(Ui.space(ctx, 14));
                    TextView t = Ui.tv(ctx, p.name, 15, C.text(), 0);
                    row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                    TextView c = Ui.tv(ctx, p.items.size() + " items", 13.5f, C.textDim(), 0);
                    row.addView(c);
                    row.addView(Ui.space(ctx, 8));
                    row.addView(Ui.icon(ctx, R.drawable.ic_chevron, 18, C.textDim()));
                    row.setOnClickListener(v -> bridge.openPlaylist(p.name));
                    row.setOnLongClickListener(v -> {
                        Ui.vib(ctx, 20);
                        List<String> labels = new ArrayList<>();
                        List<Runnable> acts = new ArrayList<>();
                        labels.add("Rename");
                        acts.add(() -> Ui.input(ctx, "Rename playlist", "New name", p.name, false,
                                android.text.InputType.TYPE_CLASS_TEXT, name -> {
                                    try {
                                        Playlists.rename(p.name, name);
                                        refresh();
                                    } catch (Exception e) {
                                        Ui.toast(ctx, e.getMessage());
                                    }
                                }));
                        labels.add("Delete playlist");
                        acts.add(() -> Ui.confirm(ctx, "Delete playlist",
                                "Delete \"" + p.name + "\"? The files themselves are not deleted.", "Delete", () -> {
                                    Playlists.delete(p.name);
                                    refresh();
                                }));
                        Ui.menu(ctx, p.name, labels, null, null, acts);
                        return true;
                    });
                    return row;
                }
            });
            col.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        }

        public void refresh() {
            rows.clear();
            rows.addAll(Playlists.all());
            if (getChildCount() > 0 && getChildAt(0) instanceof ListView) {
                ((BaseAdapter) list.getAdapter()).notifyDataSetChanged();
            }
        }
    }

    // ---------------------------------------------------------------- recent

    public static class RecentList extends FrameLayout {
        private final Bridge bridge;
        private final ListView list;
        private final List<History.Entry> rows = new ArrayList<>();

        public RecentList(Context ctx, Bridge bridge) {
            super(ctx);
            this.bridge = bridge;
            setBackgroundColor(C.bg());
            LinearLayout col = new LinearLayout(ctx);
            col.setOrientation(LinearLayout.VERTICAL);
            addView(col, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            LinearLayout bar = new LinearLayout(ctx);
            bar.setGravity(Gravity.CENTER_VERTICAL);
            bar.setPadding(Ui.dp(ctx, 18), Ui.dp(ctx, 8), Ui.dp(ctx, 12), Ui.dp(ctx, 2));
            TextView t = Ui.tv(ctx, "Recently played", 13, C.textDim(), 0);
            bar.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            TextView clear = Ui.tv(ctx, "Clear", 13, C.accent(), 1);
            clear.setPadding(Ui.dp(ctx, 12), Ui.dp(ctx, 6), Ui.dp(ctx, 4), Ui.dp(ctx, 6));
            clear.setOnClickListener(v -> Ui.confirm(ctx, "Clear history", "Remove all recent items?", "Clear", () -> {
                History.clear();
                refresh();
            }));
            bar.addView(clear);
            col.addView(bar);

            list = new ListView(ctx);
            list.setDivider(null);
            list.setSelector(android.R.color.transparent);
            list.setPadding(0, 0, 0, Ui.dp(ctx, 90));
            list.setAdapter(new BaseAdapter() {
                @Override public int getCount() { return rows.size(); }
                @Override public Object getItem(int position) { return rows.get(position); }
                @Override public long getItemId(int position) { return position; }
                @Override public View getView(int position, View convertView, ViewGroup parent) {
                    History.Entry e = rows.get(position);
                    View row = listRow(parent.getContext(), e.item, false, true, bridge, null, null);
                    // add resume progress on the thumb
                    LinearLayout lr = (LinearLayout) row;
                    FrameLayout imgBox = (FrameLayout) lr.getChildAt(0);
                    View prog = new View(parent.getContext());
                    prog.setBackgroundColor(C.alpha(C.accent(), 220));
                    float frac = 0;
                    if (e.item.durationMs > 0) frac = Math.min(1f, e.posMs / (float) e.item.durationMs);
                    FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            Math.max(Ui.dp(ctx, 2), (int) (Ui.dp(ctx, 52) * frac)),
                            Gravity.BOTTOM);
                    imgBox.addView(prog, plp);
                    row.setOnClickListener(v -> {
                        List<MediaItem> q = new ArrayList<>();
                        q.add(e.item);
                        bridge.play(q, 0);
                    });
                    return row;
                }
            });
            col.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        }

        public void refresh() {
            rows.clear();
            rows.addAll(History.all());
            if (getChildCount() > 0 && getChildAt(0) instanceof ListView) {
                ((BaseAdapter) list.getAdapter()).notifyDataSetChanged();
            }
        }
    }
}
