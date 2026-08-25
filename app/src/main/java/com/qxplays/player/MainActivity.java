package com.qxplays.player;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;

import java.util.ArrayList;
import java.util.List;

/** Single-activity host: home scaffold + pushed screens (search, folders, players, settings, vault). */
public class MainActivity extends Activity implements MediaLists.Bridge {

    private static final int REQ_PERMISSIONS = 100;
    private static final int REQ_SAF_TREE = 300;
    private static final int REQ_DELETE_CONSENT = 2001;

    private FrameLayout root;
    private HomeView home;
    private OnboardingView onboarding;
    private final List<View> stack = new ArrayList<>();

    private boolean vaultLockedOnStop;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        applySystemBars();
        root = new FrameLayout(this);
        root.setBackgroundColor(C.bg());
        setContentView(root);

        if (Prefs.onboarded()) showHome();
        else showOnboarding();
    }

    private void applySystemBars() {
        Window w = getWindow();
        w.setStatusBarColor(C.bgDeep());
        w.setNavigationBarColor(C.bgDeep());
        if (Build.VERSION.SDK_INT >= 23) {
            w.getDecorView().setSystemUiVisibility(0);
        }
    }

    // ---------------------------------------------------------------- flows

    private void showOnboarding() {
        onboarding = new OnboardingView(this, new OnboardingView.Callback() {
            @Override public void onDone() {
                Prefs.setOnboarded(true);
                root.removeView(onboarding);
                onboarding = null;
                showHome();
            }
            @Override public void onRequestPermissions(String[] perms) {
                if (Build.VERSION.SDK_INT >= 23) {
                    MainActivity.this.requestPermissions(perms, REQ_PERMISSIONS);
                }
            }
        });
        root.addView(onboarding, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void showHome() {
        home = new HomeView(this, new HomeView.Host() {
            @Override public void openAudioPlayer() { push(new AudioPlayerView(MainActivity.this)); }
            @Override public void openVideo(List<MediaItem> queue, int index) { openVideoActivity(queue, index); }
            @Override public void openSettings() { push(new SettingsView(MainActivity.this)); }
            @Override public void openPrivate() { push(new PrivateView(MainActivity.this)); }
            @Override public void openFolder(String folder) { push(new Browsers.FolderDetailView(MainActivity.this, folder)); }
            @Override public void openPlaylist(String name) { push(new Browsers.PlaylistDetailView(MainActivity.this, name)); }
            @Override public void openSearch() { push(new SearchView(MainActivity.this)); }
            @Override public void push(View v) { MainActivity.this.push(v); }
            @Override public void onPermissionNeeded() {
                if (Build.VERSION.SDK_INT >= 23) {
                    requestPermissions(Perms.mediaRead(), REQ_PERMISSIONS);
                }
            }
            @Override public void itemMenu(MediaItem item) { MainActivity.this.itemMenu(item); }
            @Override public void openStorageBrowser() { MainActivity.this.openStorageBrowser(); }
            @Override public void startSafPicker() { MainActivity.this.startSafPicker(); }
            @Override public void openSafRoot(String treeUri) { MainActivity.this.openSafRoot(treeUri); }
        });
        root.addView(home, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        LibraryData.refresh(this, true);
    }

    public void openVideoActivity(List<MediaItem> queue, int index) {
        Player.playQueue(this, queue, index);
        startActivity(new Intent(this, PlayerActivity.class));
    }

    public void push(View v) {
        root.addView(v, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        v.setTranslationX(v.getWidth() > 0 ? v.getWidth() : 1200);
        v.animate().translationX(0).setDuration(240).start();
        stack.add(v);
    }

    public void pop() {
        if (stack.isEmpty()) return;
        View v = stack.remove(stack.size() - 1);
        v.animate().translationX(v.getWidth()).setDuration(220).withEndAction(() -> root.removeView(v)).start();
    }

    @Override
    public void onBackPressed() {
        if (!stack.isEmpty()) {
            pop();
            return;
        }
        // moving home tab? handled by HomeView default (videos)
        super.onBackPressed();
    }

    // ---------------------------------------------------------------- lifecycle

    @Override
    protected void onResume() {
        super.onResume();
        if (home != null) {
            if (Perms.hasMediaRead(this)) LibraryData.refresh(this, true);
            home.refreshCurrentTab();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        // security: auto-lock the private space when leaving the app
        if (Prefs.onboarded() && Prefs.vaultAutoLock() && Vault.get(this).isUnlocked()) {
            Vault.get(this).lock();
            vaultLockedOnStop = true;
        }
    }

    // ---------------------------------------------------------------- permission results

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_PERMISSIONS) {
            if (Perms.hasMediaRead(this)) LibraryData.refresh(this, true);
            if (onboarding != null) {
                // rebuild the permissions page
                ((View) onboarding).requestLayout();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_SAF_TREE && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri tree = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(tree,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION);
                String docId = DocumentsContract.getTreeDocumentId(tree);
                String name = docId.contains(":") ? docId.substring(docId.lastIndexOf(':') + 1) : docId;
                Prefs.addSafRoot(name, tree.toString());
                Ui.toast(this, "Folder added: " + name);
                if (home != null) home.refreshCurrentTab();
            } catch (Exception e) {
                Ui.toast(this, "Could not add folder");
            }
        } else if (requestCode == REQ_DELETE_CONSENT) {
            if (resultCode == RESULT_OK) {
                Vault.get(this).finishDeleteAfterConsent();
                LibraryData.refresh(this, true);
                Ui.toast(this, "Moved to Private Space");
            } else {
                Ui.toast(this, "Hidden (delete skipped — the encrypted copy is in your Private Space)");
            }
        } else if (requestCode == PrivateView.REQ_IMPORT) {
            if (resultCode == RESULT_OK) PrivateView.onImportResult(this, data);
        }
    }

    // ---------------------------------------------------------------- MediaLists.Bridge

    @Override public Activity getActivity() { return this; }

    @Override
    public void play(List<MediaItem> queue, int index) {
        if (queue == null || queue.isEmpty() || index < 0 || index >= queue.size()) return;
        MediaItem item = queue.get(index);
        if (item.isVideo) {
            openVideoActivity(queue, index);
        } else {
            Player.playQueue(this, queue, index);
            push(new AudioPlayerView(this));
        }
    }

    @Override
    public void itemMenu(MediaItem item) {
        Sheets.itemMenu(this, item, false, new Sheets.OnItemMenu() {
            @Override public void play(MediaItem it) {
                List<MediaItem> q = new ArrayList<>();
                q.add(it);
                MainActivity.this.play(q, 0);
            }
            @Override public void playNext(MediaItem it) {
                if (!Player.hasCurrent()) { MainActivity.this.play(java.util.Collections.singletonList(it), 0); return; }
                // insert after current index
                List<MediaItem> q = new ArrayList<>(Player.queue);
                int at = Math.min(q.size(), Player.index + 1);
                q.add(at, it);
                Player.playQueue(MainActivity.this, q, Player.index);
                Ui.toast(MainActivity.this, "Will play next");
            }
            @Override public void addToQueue(MediaItem it) {
                if (!Player.hasCurrent()) { MainActivity.this.play(java.util.Collections.singletonList(it), 0); return; }
                List<MediaItem> q = new ArrayList<>(Player.queue);
                q.add(it);
                Player.playQueue(MainActivity.this, q, Player.index);
                Ui.toast(MainActivity.this, "Added to queue");
            }
            @Override public void addToPlaylist(MediaItem it) {
                Sheets.playlistPicker(MainActivity.this, it, null);
            }
            @Override public void favorite(MediaItem it) {
                Favorites.toggle(it);
                Ui.toast(MainActivity.this, Favorites.has(it.uri) ? "Added to favorites" : "Removed from favorites");
                if (home != null) home.refreshCurrentTab();
            }
            @Override public void hide(MediaItem it) {
                Sheets.hideToVault(MainActivity.this, it);
            }
            @Override public void details(MediaItem it) {
                Sheets.details(MainActivity.this, it, () -> openWith(it));
            }
            @Override public void share(MediaItem it) {
                Sheets.share(MainActivity.this, it);
            }
            @Override public void delete(MediaItem it) {
                Sheets.deleteMedia(MainActivity.this, it);
            }
        });
    }

    private void openWith(MediaItem it) {
        try {
            Intent view = new Intent(Intent.ACTION_VIEW);
            view.setDataAndType(Uri.parse(it.uri),
                    (it.mime == null || it.mime.isEmpty()) ? (it.isVideo ? "video/*" : "audio/*") : it.mime);
            view.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(view, "Open with"));
        } catch (Exception e) {
            Ui.toast(this, "No other app can open this file");
        }
    }

    @Override public void openFolder(String folder) { push(new Browsers.FolderDetailView(this, folder)); }
    @Override public void openPlaylist(String name) { push(new Browsers.PlaylistDetailView(this, name)); }
    @Override public void openStorageBrowser() {
        if (Perms.hasAllFiles(this)) {
            push(new Browsers.StorageBrowserView(this));
        } else {
            Ui.confirm(this, "All files access",
                    "The built-in file browser needs \"All files access\" to walk your storage. "
                            + "You can also use \"Add folder\" instead — the library itself works without it.",
                    "Open settings", () -> Perms.openAllFilesSettings(this));
        }
    }
    @Override public void startSafPicker() {
        try {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            startActivityForResult(i, REQ_SAF_TREE);
        } catch (Exception e) {
            Ui.toast(this, "No file picker available on this device");
        }
    }
    @Override public void openSafRoot(String treeUri) { push(new Browsers.SafBrowserView(this, treeUri)); }
}
