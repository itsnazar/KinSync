package com.example.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.model.FamilyItem
import com.example.data.model.ItemType
import com.example.data.repository.FamilyRepository
import com.example.util.NotificationHelper
import com.example.util.NotificationPrefsStore
import java.util.concurrent.TimeUnit

class SyncPendingWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val familyId = inputData.getString(KEY_FAMILY_ID).orEmpty()
        if (familyId.isBlank()) return Result.success()

        return try {
            val repo = FamilyRepository(applicationContext)
            repo.syncPendingItems(familyId)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val KEY_FAMILY_ID = "family_id"

        fun scheduleSync(context: Context, familyId: String) {
            if (familyId.isBlank()) return
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = OneTimeWorkRequestBuilder<SyncPendingWorker>()
                .setConstraints(constraints)
                .setInputData(Data.Builder().putString(KEY_FAMILY_ID, familyId).build())
                .build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(
                    "podally_sync_$familyId",
                    ExistingWorkPolicy.REPLACE,
                    request
                )
        }
    }
}

class ReminderNotificationWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val title = inputData.getString(KEY_TITLE).orEmpty()
        val body = inputData.getString(KEY_BODY).orEmpty()
        val itemTypeRaw = inputData.getString(KEY_ITEM_TYPE) ?: ItemType.REMINDER.name
        val isEncrypted = inputData.getBoolean(KEY_IS_ENCRYPTED, false)
        val authorId = inputData.getString(KEY_AUTHOR_ID).orEmpty()

        val itemType = try {
            ItemType.valueOf(itemTypeRaw)
        } catch (_: Exception) {
            ItemType.REMINDER
        }

        val prefs = NotificationPrefsStore.load(applicationContext)
        NotificationHelper.createNotificationChannel(applicationContext)
        NotificationHelper.sendNotification(
            context = applicationContext,
            title = title.ifBlank { "PodAlly Reminder" },
            body = body.ifBlank { "Scheduled family activity reminder" },
            itemType = itemType,
            authorId = authorId,
            prefs = prefs,
            isEncrypted = isEncrypted
        )
        return Result.success()
    }

    companion object {
        private const val KEY_TITLE = "title"
        private const val KEY_BODY = "body"
        private const val KEY_ITEM_TYPE = "item_type"
        private const val KEY_IS_ENCRYPTED = "is_encrypted"
        private const val KEY_AUTHOR_ID = "author_id"

        fun scheduleReminder(context: Context, item: FamilyItem) {
            val targetMillis = item.eventDateMillis ?: return
            val delayMillis = targetMillis - System.currentTimeMillis()
            if (delayMillis <= 0L) return

            val data = Data.Builder()
                .putString(KEY_TITLE, if (item.isEncrypted) "PodAlly Reminder" else item.title)
                .putString(KEY_BODY, if (item.isEncrypted) "Tap to view encrypted reminder" else item.content)
                .putString(KEY_ITEM_TYPE, item.itemType)
                .putBoolean(KEY_IS_ENCRYPTED, item.isEncrypted)
                .putString(KEY_AUTHOR_ID, item.authorId)
                .build()

            val request = OneTimeWorkRequestBuilder<ReminderNotificationWorker>()
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .setInputData(data)
                .build()

            val workId = "podally_rem_${item.id.ifBlank { item.title.hashCode().toString() }}"
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(workId, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
