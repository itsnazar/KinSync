package com.example.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.data.model.ItemType
import com.example.data.model.NotificationPreferences

object NotificationHelper {
    private const val CHANNEL_ID = "podally_family_updates"
    private const val CHANNEL_NAME = "Family Activity & Updates"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = "Push notifications for new family notes, calendar events, reminders, and travel updates."
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun sendNotification(
        context: Context,
        title: String,
        body: String,
        itemType: ItemType,
        authorId: String,
        prefs: NotificationPreferences,
        isEncrypted: Boolean = false
    ) {
        // Filter by category
        val shouldNotify = when (itemType) {
            ItemType.NOTE -> prefs.notifyNotes
            ItemType.REMINDER -> prefs.notifyReminders
            ItemType.EVENT -> prefs.notifyEvents
            ItemType.FLIGHT, ItemType.PLACE -> prefs.notifyTravel
        }
        if (!shouldNotify) return

        // Filter by member if configured
        if (prefs.memberFilter.isNotEmpty() && authorId.isNotBlank() && !prefs.memberFilter.contains(authorId)) {
            return
        }

        // Check POST_NOTIFICATIONS permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            )
            if (permission != PackageManager.PERMISSION_GRANTED) {
                return
            }
        }

        val finalTitle = if (isEncrypted && !prefs.showEncryptedPreviewLocally) {
            "PodAlly Encrypted ${itemType.name.lowercase().replaceFirstChar { it.uppercase() }}"
        } else {
            title
        }

        val finalBody = if (isEncrypted && !prefs.showEncryptedPreviewLocally) {
            "Unlock PodAlly with your family passkey to read this update."
        } else {
            body
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(finalTitle)
            .setContentText(finalBody)
            .setStyle(NotificationCompat.BigTextStyle().bigText(finalBody))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify((System.currentTimeMillis() % 100000).toInt(), builder.build())
    }
}
