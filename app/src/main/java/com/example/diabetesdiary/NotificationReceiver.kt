package com.example.diabetesdiary

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import android.widget.Toast

class NotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action

        if (action == "FINISH_ACTION") {
            val noteText = intent.getStringExtra("noteText") ?: ""
            Toast.makeText(context, "Zakończono: $noteText", Toast.LENGTH_SHORT).show()
            return
        }

        val noteText = intent.getStringExtra("noteText") ?: "Masz przypomnienie!"
        val noteDate = intent.getStringExtra("noteDate") ?: ""
        val noteTime = intent.getStringExtra("noteTime") ?: ""
        val notificationId = intent.getStringExtra("notificationId")?.hashCode() ?: System.currentTimeMillis().toInt()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val openIntent = Intent(context, CalendarActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingOpenIntent = PendingIntent.getActivity(
            context,
            notificationId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val finishIntent = Intent(context, NotificationReceiver::class.java).apply {
            this.action = "FINISH_ACTION"
            putExtra("noteText", noteText)
        }

        val pendingFinishIntent = PendingIntent.getBroadcast(
            context,
            notificationId,
            finishIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, "diabetes_channel")
            .setSmallIcon(R.mipmap.ic_launcher)  // lub inna ikona Twojej aplikacji
            .setContentTitle("Przypomnienie na $noteDate $noteTime")
            .setContentText(noteText)
            .setContentIntent(pendingOpenIntent)
            .setAutoCancel(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,  // ikona dla akcji "Zakończ"
                "Zakończ",
                pendingFinishIntent
            )
            .build()

        notificationManager.notify(notificationId, notification)
    }
}
