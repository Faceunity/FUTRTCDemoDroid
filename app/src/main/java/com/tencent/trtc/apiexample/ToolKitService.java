package com.tencent.trtc.apiexample;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.blankj.utilcode.util.ServiceUtils;

/**
 * 该 Service 用于应用保活，勿删
 * 9.0 及之后的系统，应用退后台后摄像头和麦克风将停止工作（https://developer.android.com/about/versions/pie/android-9.0-changes-all）
 * 该 Service 是为了保证应用退后台摄像头和麦克风依旧可以正常工作。
 */
public class ToolKitService extends Service {

    private static final int NOTIFICATION_ID = 1001;

    @Override
    public void onCreate() {
        super.onCreate();
        Notification notification = createForegroundNotification();
        startForeground(NOTIFICATION_ID, notification);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return new Binder();
    }

    public static void start(Context context) {
        if (ServiceUtils.isServiceRunning(ToolKitService.class)) {
            return;
        }
        Intent service = new Intent(context, ToolKitService.class);
        /*
         * 9.0 及之后的系统，应用退后台后摄像头和麦克风将停止工作（https://developer.android.com/about/versions/pie/android-9.0-changes-all）
         * 该 Service 是为了保证应用退后台摄像头和麦克风依旧可以正常工作，故仅在 9.0 及之后的系统启动。
         */
        if (Build.VERSION.SDK_INT >= 28) {
            context.startForegroundService(service);
        }
    }

    private Notification createForegroundNotification() {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        String notificationChannelId = "notification_channel_id_01";
        // Android8.0以上的系统，新建消息通道
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            //用户可见的通道名称
            String channelName = "TRTC Foreground Service Notification";
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel notificationChannel =
                    new NotificationChannel(notificationChannelId, channelName, importance);
            notificationChannel.setDescription("Channel description");
            notificationChannel.setVibrationPattern(new long[]{0, 1000, 500, 1000});
            notificationChannel.enableVibration(true);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(notificationChannel);
            }
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, notificationChannelId);
        builder.setSmallIcon(R.mipmap.ic_appicon);
        builder.setContentTitle(getString(R.string.app_name));
        builder.setContentText(getString(R.string.app_running));
        builder.setWhen(System.currentTimeMillis());
        return builder.build();
    }
}