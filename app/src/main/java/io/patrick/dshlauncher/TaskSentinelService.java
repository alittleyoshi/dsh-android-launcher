package io.patrick.dshlauncher;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public class TaskSentinelService extends Service {
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_NOT_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        try {
            TermuxBridge.stopBackend(this);
        } catch (Exception ignored) {
        }
        stopSelf();
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
