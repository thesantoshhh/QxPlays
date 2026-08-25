package com.qxplays.player;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;

public class QxApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Prefs.init(this);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(PlaybackService.CHANNEL_ID,
                    getString(R.string.notification_channel_playback), NotificationManager.IMPORTANCE_LOW);
            ch.setDescription(getString(R.string.notification_channel_playback_desc));
            ch.setShowBadge(false);
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.createNotificationChannel(ch);
        }
        // Clear any stale decrypted playback cache from previous sessions.
        Vault.get(this).wipePlayCache();
    }
}
