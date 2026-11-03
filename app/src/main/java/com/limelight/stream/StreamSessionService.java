package com.limelight.stream;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import com.limelight.Game;
import com.limelight.R;
import com.limelight.LimeLog;

/**
 * User-visible foreground lifetime for an active remote-desktop session.
 *
 * The Game Activity keeps the live decoder/transport while it is stopped but
 * not destroyed; the foreground service reduces process eviction risk. It
 * cannot override Android force-stop or process-kill behavior.
 */
public final class StreamSessionService extends Service {
    public static final String ACTION_STOP = "com.limelight.stream.STOP";
    public static final String ACTION_RECONNECTING = "com.limelight.stream.RECONNECTING";
    public static final String ACTION_STREAMING = "com.limelight.stream.STREAMING";
    private static final String CHANNEL_ID = "artemis_stream_session";
    private static final int NOTIFICATION_ID = 7801;
    private static volatile boolean running;

    public static void start(Context context) {
        send(context, ACTION_STREAMING);
    }

    public static void reconnecting(Context context) {
        send(context, ACTION_RECONNECTING);
    }

    private static void send(Context context, String action) {
        Intent intent = new Intent(context, StreamSessionService.class).setAction(action);
        try {
            if (running) {
                context.startService(intent);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
        } catch (RuntimeException e) {
            // Android can deny foreground-service starts from the background.
            LimeLog.warning("Unable to start streaming foreground service: " + e);
        }
    }

    public static void stop(Context context) {
        context.stopService(new Intent(context, StreamSessionService.class));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        running = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    "Remote desktop session", NotificationManager.IMPORTANCE_LOW);
            ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE))
                    .createNotificationChannel(channel);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_STREAMING : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            new Handler(Looper.getMainLooper()).post(() -> {
                Game activity = Game.instance;
                if (activity != null) {
                    activity.disconnect();
                }
                stopSelf();
            });
            return START_NOT_STICKY;
        }

        // Return to the existing streaming Activity, not the computer list.
        // The launch extras also preserve the host/app target if Android has
        // to recreate Game in the same process.
        Game activeGame = Game.instance;
        Intent openIntent;
        if (activeGame != null && !activeGame.isFinishing()) {
            openIntent = new Intent(this, Game.class);
            Intent originalIntent = activeGame.getIntent();
            if (originalIntent != null && originalIntent.getExtras() != null) {
                openIntent.putExtras(originalIntent.getExtras());
            }
        } else {
            openIntent = getPackageManager().getLaunchIntentForPackage(getPackageName());
            if (openIntent == null) openIntent = new Intent(this, Game.class);
        }
        openIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent open = PendingIntent.getActivity(this, 0, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent stopIntent = new Intent(this, StreamSessionService.class).setAction(ACTION_STOP);
        PendingIntent stop = PendingIntent.getService(this, 1, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        Notification notification = builder.setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(getString(R.string.app_label) + " remote desktop")
                .setContentText(ACTION_RECONNECTING.equals(action) ? "Reconnecting to the host" : "Streaming session active")
                .setContentIntent(open)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Disconnect", stop)
                .build();
        startForeground(NOTIFICATION_ID, notification);
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
