package it.marrone.goodscaring.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import it.marrone.goodscaring.MainActivity
import it.marrone.goodscaring.R

object Notifications {
  private const val CHANNEL_ID = "stock_alerts"

  fun createChannel(context: Context) {
    val channel =
      NotificationChannel(CHANNEL_ID, "Scorte in esaurimento", NotificationManager.IMPORTANCE_DEFAULT)
        .apply { description = "Avvisi quando un bene sta per finire" }
    context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
  }

  fun canNotify(context: Context): Boolean =
    (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
      ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED) &&
      NotificationManagerCompat.from(context).areNotificationsEnabled()

  fun show(context: Context, notificationId: Int, title: String, text: String) {
    if (!canNotify(context)) return
    val intent =
      Intent(context, MainActivity::class.java).addFlags(
        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
      )
    val pendingIntent =
      PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    val notification =
      NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(title)
        .setContentText(text)
        .setStyle(NotificationCompat.BigTextStyle().bigText(text))
        .setContentIntent(pendingIntent)
        .setAutoCancel(true)
        .build()
    try {
      NotificationManagerCompat.from(context).notify(notificationId, notification)
    } catch (_: SecurityException) {
      // Permission revoked between the check and the call.
    }
  }
}
