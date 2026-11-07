package com.example.coletai;

import android.app.*;
import android.content.Intent;
import android.os.*;

/** Keeps RFCOMM alive while the presenter changes screens or briefly leaves the app. */
public class BluetoothSessionService extends Service {
  static volatile boolean running;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final Runnable update = new Runnable() {
    public void run() {
      if (!BluetoothPlatform.active()) { stopSelf(); return; }
      if (Build.VERSION.SDK_INT < 33 || checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED)
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).notify(7401, notification());
      handler.postDelayed(this, 3000);
    }
  };
  @Override public void onCreate() {
    super.onCreate();
    running = true;
    NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
    if (Build.VERSION.SDK_INT >= 26)
      manager.createNotificationChannel(new NotificationChannel("bluetooth_session", "Sessão Bluetooth offline", NotificationManager.IMPORTANCE_LOW));
    startForeground(7401, notification());
    handler.post(update);
  }
  private Notification notification() {
    PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, OfflineBluetoothActivity.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    Notification.Builder builder = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, "bluetooth_session") : new Notification.Builder(this);
    return builder.setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
        .setContentTitle("Coletaí · apresentação offline").setContentText(BluetoothPlatform.get(this).status())
        .setContentIntent(open).setOngoing(true).build();
  }
  @Override public int onStartCommand(Intent intent, int flags, int startId) {
    BluetoothPlatform.get(this).resume(); return START_NOT_STICKY;
  }
  @Override public IBinder onBind(Intent intent) { return null; }
  @Override public void onDestroy() { running = false; handler.removeCallbacks(update); super.onDestroy(); }
}
