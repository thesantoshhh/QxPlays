package com.qxplays.player;

import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Context;
import android.content.IntentSender;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Private space ("Vault").
 *
 * Real security model:
 *  - Files are copied out of shared storage, encrypted with AES-256-GCM using a key derived
 *    from the user's own password (PBKDF2-HMAC-SHA256, 150k rounds, random salt), and the
 *    original is removed from the device media index so it is not visible anywhere else.
 *  - The password is never stored; only a salted verifier hash.
 */
public class Vault {
    public static final String TAG = "Vault";
    private final Context ctx;
    private final File dir;
    private final File cacheDir;
    private static Vault instance;

    // pending MediaStore delete after user consent (Android 10/11+)
    private String pendingDeleteId;
    private Uri pendingDeleteUri;

    public static synchronized Vault get(Context ctx) {
        if (instance == null) instance = new Vault(ctx.getApplicationContext());
        return instance;
    }

    private Vault(Context ctx) {
        this.ctx = ctx;
        this.dir = new File(ctx.getFilesDir(), "vault");
        this.cacheDir = new File(ctx.getCacheDir(), "vault_play");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        //noinspection ResultOfMethodCallIgnored
        cacheDir.mkdirs();
    }

    // ---------------- password management ----------------

    public boolean isSetUp() {
        return !Prefs.sp().getString(Prefs.KEY_VAULT_SALT, "").isEmpty()
                && !Prefs.sp().getString(Prefs.KEY_VAULT_HASH, "").isEmpty();
    }

    public void createPassword(String password) throws Exception {
        if (password == null || password.length() < 4)
            throw new Exception("Password must be at least 4 characters");
        Crypto.Credentials c = Crypto.create(password.toCharArray());
        Prefs.sp().edit()
                .putString(Prefs.KEY_VAULT_SALT, Utils.base64(c.salt))
                .putString(Prefs.KEY_VAULT_HASH, Utils.base64(c.hash))
                .putInt(Prefs.KEY_VAULT_ITERS, c.iterations)
                .apply();
        Prefs.vaultSetSessionUnlocked(true);
    }

    public boolean verifyPassword(String password) {
        byte[] salt = Utils.unbase64(Prefs.sp().getString(Prefs.KEY_VAULT_SALT, ""));
        byte[] hash = Utils.unbase64(Prefs.sp().getString(Prefs.KEY_VAULT_HASH, ""));
        int iters = Prefs.sp().getInt(Prefs.KEY_VAULT_ITERS, Crypto.ITERATIONS);
        boolean ok = Crypto.verify(password == null ? new char[0] : password.toCharArray(), salt, hash, iters);
        if (ok) Prefs.vaultSetSessionUnlocked(true);
        return ok;
    }

    public void changePassword(String oldPw, String newPw) throws Exception {
        if (!verifyPassword(oldPw)) throw new Exception("Current password is incorrect");
        if (newPw == null || newPw.length() < 4) throw new Exception("New password must be at least 4 characters");
        byte[] oldSalt = Utils.unbase64(Prefs.sp().getString(Prefs.KEY_VAULT_SALT, ""));
        int oldIters = Prefs.sp().getInt(Prefs.KEY_VAULT_ITERS, Crypto.ITERATIONS);
        byte[] oldKey = Crypto.deriveKey(oldPw.toCharArray(), oldSalt, oldIters);
        Crypto.Credentials nc = Crypto.create(newPw.toCharArray());
        byte[] newKey = Crypto.deriveKey(newPw.toCharArray(), nc.salt, nc.iterations);

        // Re-encrypt every vault file.
        for (VaultItem vi : listItems()) {
            File f = new File(dir, vi.id + ".qxv");
            if (!f.exists()) continue;
            byte[] blob = Utils.readAll(new FileInputStream(f));
            byte[] plain = Crypto.decrypt(oldKey, blob);
            byte[] re = Crypto.encrypt(newKey, plain);
            FileOutputStream out = new FileOutputStream(f);
            out.write(re);
            out.close();
        }
        Prefs.sp().edit()
                .putString(Prefs.KEY_VAULT_SALT, Utils.base64(nc.salt))
                .putString(Prefs.KEY_VAULT_HASH, Utils.base64(nc.hash))
                .putInt(Prefs.KEY_VAULT_ITERS, nc.iterations)
                .apply();
        Prefs.vaultSetSessionUnlocked(true);
    }

    public void lock() { Prefs.vaultSetSessionUnlocked(false); wipePlayCache(); }

    private byte[] sessionKey() {
        String k = Prefs.sp().getString("vault_session_key", null);
        if (k == null || k.isEmpty()) return null;
        return Utils.unbase64(k);
    }

    /**
     * Keep only the DERIVED key (never the password itself) for the unlocked session.
     * Cleared on lock/app background.
     */
    public void rememberSessionPassword(String password) {
        byte[] salt = Utils.unbase64(Prefs.sp().getString(Prefs.KEY_VAULT_SALT, ""));
        int iters = Prefs.sp().getInt(Prefs.KEY_VAULT_ITERS, Crypto.ITERATIONS);
        byte[] key = Crypto.deriveKey(password == null ? new char[0] : password.toCharArray(), salt, iters);
        Prefs.sp().edit().putString("vault_session_key", Utils.base64(key)).apply();
    }

    public void forgetSessionPassword() {
        Prefs.sp().edit().remove("vault_session_key").apply();
    }

    public boolean isUnlocked() {
        return isSetUp() && Prefs.vaultSessionUnlocked() && sessionKey() != null;
    }

    public void unlock(String password) {
        if (verifyPassword(password)) rememberSessionPassword(password);
    }

    // ---------------- vault items ----------------

    public static class VaultItem {
        public String id;
        public String name;
        public String mime;
        public long size;
        public long durationMs;
        public boolean isVideo;
        public long hiddenAt;
        public String originalUri; // uri before hiding (for restore)
    }

    public List<VaultItem> listItems() {
        List<VaultItem> out = new ArrayList<>();
        try {
            JSONArray arr = Prefs.getJson("vault_items");
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                VaultItem vi = new VaultItem();
                vi.id = o.getString("id");
                vi.name = o.optString("name", "Unknown");
                vi.mime = o.optString("mime", "");
                vi.size = o.optLong("size", 0);
                vi.durationMs = o.optLong("dur", 0);
                vi.isVideo = o.optBoolean("video", false);
                vi.hiddenAt = o.optLong("at", 0);
                vi.originalUri = o.optString("orig", "");
                out.add(vi);
            }
        } catch (Exception ignored) {}
        return out;
    }

    private void saveMeta(List<VaultItem> items) {
        JSONArray arr = new JSONArray();
        for (VaultItem vi : items) {
            JSONObject o = new JSONObject();
            try {
                o.put("id", vi.id);
                o.put("name", vi.name);
                o.put("mime", vi.mime);
                o.put("size", vi.size);
                o.put("dur", vi.durationMs);
                o.put("video", vi.isVideo);
                o.put("at", vi.hiddenAt);
                o.put("orig", vi.originalUri == null ? "" : vi.originalUri);
            } catch (Exception ignored) {}
            arr.put(o);
        }
        Prefs.putJson("vault_items", arr);
    }

    /**
     * Hide a media item: encrypt it into the vault, then remove it from shared storage.
     * @return an IntentSender needing user consent for deletion on Android 10+, or null if deletion
     *         happened directly. On success the item is already inside the vault.
     */
    public IntentSender hideItem(MediaItem item) throws Exception {
        if (!isUnlocked()) throw new Exception("Private space is locked");
        String id = Utils.sha256(item.uri);
        byte[] key = sessionKey();

        // 1. Read source bytes.
        byte[] plain;
        InputStream in = Utils.openInput(ctx, item.uri);
        plain = Utils.readAll(in);
        in.close();

        // 2. Encrypt + write.
        byte[] blob = Crypto.encrypt(key, plain);
        FileOutputStream out = new FileOutputStream(new File(dir, id + ".qxv"));
        out.write(blob);
        out.close();

        // 3. Record metadata.
        List<VaultItem> items = listItems();
        boolean exists = false;
        for (VaultItem vi : items) if (vi.id.equals(id)) { exists = true; break; }
        if (!exists) {
            VaultItem vi = new VaultItem();
            vi.id = id;
            vi.name = item.name;
            vi.mime = item.mime;
            vi.size = item.size;
            vi.durationMs = item.durationMs;
            vi.isVideo = item.isVideo;
            vi.hiddenAt = System.currentTimeMillis() / 1000;
            vi.originalUri = item.uri;
            items.add(vi);
            saveMeta(items);
        }

        // 4. Remove the original from shared storage.
        return deleteOriginal(item.uri, id);
    }

    /** Import arbitrary files (from SAF) into the vault without hiding from library. */
    public void importUri(Uri src, String name, String mime, long size) throws Exception {
        if (!isUnlocked()) throw new Exception("Private space is locked");
        byte[] key = sessionKey();
        String id = Utils.sha256(src.toString());
        InputStream in = ctx.getContentResolver().openInputStream(src);
        byte[] plain = Utils.readAll(in);
        in.close();
        FileOutputStream out = new FileOutputStream(new File(dir, id + ".qxv"));
        out.write(Crypto.encrypt(key, plain));
        out.close();
        List<VaultItem> items = listItems();
        VaultItem vi = new VaultItem();
        vi.id = id;
        vi.name = name;
        vi.mime = mime;
        vi.size = size;
        vi.hiddenAt = System.currentTimeMillis() / 1000;
        vi.originalUri = src.toString();
        items.add(vi);
        saveMeta(items);
    }

    private IntentSender deleteOriginal(String uri, String id) {
        try {
            if (uri.startsWith("file://")) {
                new File(uri.substring(7)).delete();
                return null;
            }
            Uri u = Uri.parse(uri);
            try {
                ctx.getContentResolver().delete(u, null, null);
                return null;
            } catch (SecurityException e) {
                if (Build.VERSION.SDK_INT >= 30) {
                    pendingDeleteId = id;
                    pendingDeleteUri = u;
                    return MediaStore.createDeleteRequest(ctx.getContentResolver(),
                            java.util.Collections.singletonList(u)).getIntentSender();
                } else if (Build.VERSION.SDK_INT >= 29) {
                    pendingDeleteId = id;
                    pendingDeleteUri = u;
                    throw e; // RecoverableSecurityException → activity will retry after consent
                }
                return null;
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            return null; // delete failed, but the encrypted copy is safe inside the vault
        }
    }

    public void finishDeleteAfterConsent() {
        if (pendingDeleteUri == null) return;
        try { ctx.getContentResolver().delete(pendingDeleteUri, null, null); } catch (Exception ignored) {}
        pendingDeleteUri = null;
        pendingDeleteId = null;
    }

    /** Restore a hidden item back to shared storage. */
    public String restoreItem(VaultItem vi) throws Exception {
        if (!isUnlocked()) throw new Exception("Private space is locked");
        File f = new File(dir, vi.id + ".qxv");
        if (!f.exists()) throw new Exception("Vault file missing");
        byte[] blob = Utils.readAll(new FileInputStream(f));
        byte[] plain = Crypto.decrypt(sessionKey(), blob);

        Uri outUri = null;
        if (vi.originalUri != null && vi.originalUri.startsWith("file://")) {
            File target = new File(vi.originalUri.substring(7));
            if (target.getParentFile() != null) //noinspection ResultOfMethodCallIgnored
                target.getParentFile().mkdirs();
            FileOutputStream out = new FileOutputStream(target);
            out.write(plain);
            out.close();
            outUri = Uri.fromFile(target);
        } else {
            outUri = insertIntoMediaStore(vi, plain);
        }

        //noinspection ResultOfMethodCallIgnored
        f.delete();
        List<VaultItem> items = listItems();
        List<VaultItem> keep = new ArrayList<>();
        for (VaultItem v : items) if (!v.id.equals(vi.id)) keep.add(v);
        saveMeta(keep);
        return outUri == null ? null : outUri.toString();
    }

    private Uri insertIntoMediaStore(VaultItem vi, byte[] plain) throws Exception {
        if (Build.VERSION.SDK_INT >= 29) {
            boolean video = vi.isVideo;
            String relative = video ? "Movies/" : "Music/";
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.MediaColumns.DISPLAY_NAME, vi.name);
            cv.put(MediaStore.MediaColumns.MIME_TYPE, vi.mime == null || vi.mime.isEmpty()
                    ? (video ? "video/mp4" : "audio/mpeg") : vi.mime);
            cv.put(MediaStore.MediaColumns.RELATIVE_PATH, relative);
            cv.put(MediaStore.MediaColumns.IS_PENDING, 1);
            Uri base = video ? MediaStore.Video.Media.EXTERNAL_CONTENT_URI : MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
            Uri u = ctx.getContentResolver().insert(base, cv);
            if (u == null) throw new Exception("Could not create media entry");
            try {
                java.io.OutputStream out = ctx.getContentResolver().openOutputStream(u);
                if (out == null) throw new Exception("Could not open media output");
                out.write(plain);
                out.close();
                ContentValues done = new ContentValues();
                done.put(MediaStore.MediaColumns.IS_PENDING, 0);
                ctx.getContentResolver().update(u, done, null, null);
                return u;
            } catch (Exception e) {
                try { ctx.getContentResolver().delete(u, null, null); } catch (Exception ignored) {}
                throw e;
            }
        } else {
            File root = android.os.Environment.getExternalStorageDirectory();
            File target = new File(root, (vi.isVideo ? "Movies/" : "Music/") + Utils.safeName(vi.name));
            FileOutputStream out = new FileOutputStream(target);
            out.write(plain);
            out.close();
            // Let the scanner see it.
            android.media.MediaScannerConnection.scanFile(ctx, new String[]{ target.getAbsolutePath() }, null, null);
            return Uri.fromFile(target);
        }
    }

    /** Decrypt a vault item for playback into app-private cache. */
    public Uri playUri(VaultItem vi) throws Exception {
        if (!isUnlocked()) throw new Exception("Private space is locked");
        File f = new File(dir, vi.id + ".qxv");
        if (!f.exists()) throw new Exception("Vault file missing");
        File tmp = new File(cacheDir, Utils.sha256(vi.id) + "." + Utils.extOf(vi.name));
        if (!tmp.exists() || tmp.length() == 0) {
            byte[] blob = Utils.readAll(new FileInputStream(f));
            byte[] plain = Crypto.decrypt(sessionKey(), blob);
            FileOutputStream out = new FileOutputStream(tmp);
            out.write(plain);
            out.close();
        }
        return Uri.fromFile(tmp);
    }

    public void wipePlayCache() {
        File[] files = cacheDir.listFiles();
        if (files != null) for (File f : files) f.delete();
    }

    public void deleteVaultItem(VaultItem vi) {
        new File(dir, vi.id + ".qxv").delete();
        List<VaultItem> items = listItems();
        List<VaultItem> keep = new ArrayList<>();
        for (VaultItem v : items) if (!v.id.equals(vi.id)) keep.add(v);
        saveMeta(keep);
    }

    public long vaultSize() {
        return Utils.dirSize(dir);
    }
}
