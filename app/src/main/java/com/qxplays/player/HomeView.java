package com.qxplays.player;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Home scaffold: header, tab strip, tab content, mini player. */
public class HomeView extends FrameLayout implements Player.Listener, LibraryData.Listener, MediaLists.Bridge {

    public interface Host {
        void openAudioPlayer();
        void openVideo(List<MediaItem> queue, int index);
        void openSettings();
        void openPrivate();
        void openFolder(String folder);
        void openPlaylist(String name);
        void openSearch();
        void push(View v);
        void onPermissionNeeded();
        void itemMenu(MediaItem item);
        void openStorageBrowser();
        void startSafPicker();
        void openSafRoot(String treeUri);
    }

    private final Context ctx;
    private final Host host;

    private final LinearLayout root;
    private final LinearLayout header;
    private final View tabs;
    private final FrameLayout content;
    private final LinearLayout miniPlayer;
    private ImageView miniPlayBtn;

    private final List<TextView> tabChips = new ArrayList<>();
    private final List<View> tabViews = new ArrayList<>();
    private int currentTab = 0;

    private MediaLists.MediaGrid videoTab;
    private MediaLists.MediaList audioTab;
    private MediaLists.FolderList folderTab;
    private MediaLists.PlaylistList playlistTab;
    private MediaLists.MediaList favoritesTab;
    private MediaLists.RecentList recentTab;

    public HomeView(Context c, Host h) {
        super(c);
        ctx = c;
        host = h;
        setBackgroundColor(C.bg());

        root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        addView(root, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        header = buildHeader();
        root.addView(header);

        tabs = buildTabs();
        root.addView(tabs);

        content = new FrameLayout(ctx);
        root.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        buildTabViews();

        miniPlayer = buildMiniPlayer();
        root.addView(miniPlayer);

        Player.subscribe(this);
        LibraryData.subscribe(this);
        updateMiniPlayer();
    }

    // ---------------------------------------------------------------- header

    private LinearLayout buildHeader() {
        LinearLayout bar = new LinearLayout(ctx);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(Ui.dp(ctx, 18), Ui.dp(ctx, 14), Ui.dp(ctx, 10), Ui.dp(ctx, 6));

        LinearLayout logo = new LinearLayout(ctx);
        logo.setGravity(Gravity.CENTER_VERTICAL);
        ImageView tri = Ui.icon(ctx, R.drawable.ic_play, 26, C.accent());
        logo.addView(tri);
        logo.addView(Ui.space(ctx, 8));
        TextView name = Ui.tv(ctx, "QxPlays", 23, C.text(), 3);
        logo.addView(name);
        bar.addView(logo, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        bar.addView(Ui.iconBtn(ctx, R.drawable.ic_search, C.text(), v -> host.openSearch()));
        bar.addView(Ui.space(ctx, 2));
        bar.addView(Ui.iconBtn(ctx, R.drawable.ic_shield, C.accent(), v -> host.openPrivate()));
        bar.addView(Ui.space(ctx, 2));
        bar.addView(Ui.iconBtn(ctx, R.drawable.ic_settings, C.textDim(), v -> host.openSettings()));
        return bar;
    }

    private View buildTabs() {
        LinearLayout strip = new LinearLayout(ctx);
        strip.setPadding(Ui.dp(ctx, 8), Ui.dp(ctx, 2), Ui.dp(ctx, 8), Ui.dp(ctx, 8));
        String[] names = {"Videos", "Audio", "Folders", "Playlists", "Favorites", "Recent"};
        int[] icons = {R.drawable.ic_video, R.drawable.ic_music, R.drawable.ic_folder,
                R.drawable.ic_playlist, R.drawable.ic_heart, R.drawable.ic_history};
        for (int i = 0; i < names.length; i++) {
            final int idx = i;
            TextView chip = Ui.tv(ctx, names[i], 14, C.textDim(), 1);
            chip.setGravity(Gravity.CENTER_VERTICAL);
            chip.setPadding(Ui.dp(ctx, 13), Ui.dp(ctx, 9), Ui.dp(ctx, 13), Ui.dp(ctx, 9));
            LinearLayout wrap = new LinearLayout(ctx);
            wrap.setGravity(Gravity.CENTER_VERTICAL);
            wrap.setPadding(Ui.dp(ctx, 2), 0, Ui.dp(ctx, 2), 0);
            wrap.addView(Ui.icon(ctx, icons[i], 17, C.textDim()));
            wrap.addView(Ui.space(ctx, 6));
            wrap.addView(chip);
            Ui.setBg(wrap, Ui.ripple(ctx, Ui.rect(Ui.dp(ctx, 100), Color.TRANSPARENT)));
            wrap.setOnClickListener(v -> selectTab(idx));
            strip.addView(wrap);
            tabChips.add(chip);
        }
        HorizontalScrollView sc = new HorizontalScrollView(ctx);
        sc.setHorizontalScrollBarEnabled(false);
        sc.addView(strip, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        FrameLayout holder = new FrameLayout(ctx);
        holder.addView(sc);
        return holder;
    }

    private void buildTabViews() {
        videoTab = new MediaLists.MediaGrid(ctx, MediaLists.MediaGrid.MODE_VIDEOS, this);
        audioTab = new MediaLists.MediaList(ctx, MediaLists.MediaList.MODE_AUDIO, this);
        folderTab = new MediaLists.FolderList(ctx, this);
        playlistTab = new MediaLists.PlaylistList(ctx, this);
        favoritesTab = new MediaLists.MediaList(ctx, MediaLists.MediaList.MODE_FAVORITES, this);
        recentTab = new MediaLists.RecentList(ctx, this);

        tabViews.add(videoTab);
        tabViews.add(audioTab);
        tabViews.add(folderTab);
        tabViews.add(playlistTab);
        tabViews.add(favoritesTab);
        tabViews.add(recentTab);
        selectTab(0);
    }

    public void selectTab(int idx) {
        currentTab = idx;
        for (int i = 0; i < tabChips.size(); i++) {
            tabChips.get(i).setTextColor(i == idx ? C.accent() : C.textDim());
        }
        content.removeAllViews();
        View v = tabViews.get(idx);
        content.addView(v, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        refreshCurrentTab();
    }

    public void refreshCurrentTab() {
        switch (currentTab) {
            case 0: videoTab.refresh(); break;
            case 1: audioTab.refresh(); break;
            case 2: folderTab.refresh(); break;
            case 3: playlistTab.refresh(); break;
            case 4: favoritesTab.refresh(); break;
            case 5: recentTab.refresh(); break;
        }
    }

    private void onPlay(List<MediaItem> queue, int index, boolean video) {
        if (video) host.openVideo(queue, index);
        else {
            Player.playQueue(ctx, queue, index);
            host.openAudioPlayer();
        }
    }

    // ---------------------------------------------------------------- MediaLists.Bridge

    @Override public android.app.Activity getActivity() { return (android.app.Activity) getContext(); }
    @Override public void play(List<MediaItem> queue, int index) {
        if (queue == null || queue.isEmpty() || index < 0 || index >= queue.size()) return;
        onPlay(queue, index, queue.get(index).isVideo);
    }
    @Override public void itemMenu(MediaItem item) { host.itemMenu(item); }
    @Override public void openFolder(String folder) { host.openFolder(folder); }
    @Override public void openPlaylist(String name) { host.openPlaylist(name); }
    @Override public void openStorageBrowser() { host.openStorageBrowser(); }
    @Override public void startSafPicker() { host.startSafPicker(); }
    @Override public void openSafRoot(String treeUri) { host.openSafRoot(treeUri); }

    // ---------------------------------------------------------------- mini player

    private LinearLayout buildMiniPlayer() {
        LinearLayout bar = new LinearLayout(ctx);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(Ui.dp(ctx, 12), Ui.dp(ctx, 8), Ui.dp(ctx, 8), Ui.dp(ctx, 8));
        bar.setVisibility(GONE);
        Ui.setBg(bar, Ui.rect(0, C.surface()));
        bar.setOnClickListener(v -> {
            if (Player.current != null && Player.current.isVideo) {
                List<MediaItem> q = new ArrayList<>(Player.queue);
                host.openVideo(q, Math.max(0, Player.index));
            } else {
                host.openAudioPlayer();
            }
        });

        Thumbs.ThumbView thumb = new Thumbs.ThumbView(ctx);
        thumb.setRounded(true);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(Ui.dp(ctx, 44), Ui.dp(ctx, 44));
        bar.addView(thumb, tlp);

        LinearLayout labels = new LinearLayout(ctx);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(Ui.dp(ctx, 12), 0, Ui.dp(ctx, 8), 0);
        TextView title = Ui.tv(ctx, "", 15, C.text(), 1);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.MARQUEE);
        title.setMarqueeRepeatLimit(-1);
        title.setSelected(true);
        TextView sub = Ui.tv(ctx, "", 12.5f, C.textDim(), 0);
        labels.addView(title);
        labels.addView(sub);
        bar.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        miniPlayBtn = Ui.iconBtn(ctx, R.drawable.ic_play, C.accent(), v -> Player.toggle(ctx));
        bar.addView(miniPlayBtn);
        bar.addView(Ui.iconBtn(ctx, R.drawable.ic_next, C.textDim(), v -> Player.next(ctx)));
        bar.addView(Ui.iconBtn(ctx, R.drawable.ic_close, C.textDim(), v -> Player.stop(ctx)));

        // keep refs for updates
        bar.setTag(new Object[]{thumb, title, sub});
        return bar;
    }

    private void updateMiniPlayer() {
        Object[] refs = (Object[]) miniPlayer.getTag();
        Thumbs.ThumbView thumb = (Thumbs.ThumbView) refs[0];
        TextView title = (TextView) refs[1];
        TextView sub = (TextView) refs[2];
        if (Player.hasCurrent() && Player.state != Player.STATE_IDLE) {
            miniPlayer.setVisibility(VISIBLE);
            if (Player.current != null) {
                Thumbs.request(ctx, Player.current, thumb);
                title.setText(Player.current.name);
                sub.setText((Player.current.isVideo ? "Video in background · " : "Audio · ") + C.fmtDur(Player.durationMs));
            }
            miniPlayBtn.setImageResource(Player.isPlaying() ? R.drawable.ic_pause : R.drawable.ic_play);
        } else {
            miniPlayer.setVisibility(GONE);
        }
    }

    // ---------------------------------------------------------------- listener glue

    @Override public void onStateChanged() { updateMiniPlayer(); }
    @Override public void onTrackChanged() { updateMiniPlayer(); }
    @Override public void onProgress(long posMs, long durMs) { }
    @Override public void onQueueChanged() { }
    @Override public void onSleepTick(int remainSec) { }
    @Override public void onWave(byte[] wave, int samplingRate) { }
    @Override public void onLibraryChanged() { refreshCurrentTab(); }

    public void onDestroy() {
        Player.unsubscribe(this);
        LibraryData.unsubscribe(this);
    }
}
