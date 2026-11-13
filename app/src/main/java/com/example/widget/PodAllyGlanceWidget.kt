package com.example.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.example.MainActivity
import com.example.data.local.KinSyncDatabase
import com.example.data.model.FamilyItem
import com.example.data.model.ItemType
import com.example.security.CryptoManager
import com.example.security.SecurePasskeyStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class PodAllyGlanceWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val items = loadWidgetItems(context)

        provideContent {
            GlanceTheme {
                WidgetContent(context = context, items = items)
            }
        }
    }

    private suspend fun loadWidgetItems(context: Context): List<FamilyItem> = withContext(Dispatchers.IO) {
        val familyId = SecurePasskeyStore.loadActiveFamilyId(context)
        if (familyId.isBlank()) return@withContext emptyList()
        val passkey = SecurePasskeyStore.loadPasskey(context)

        return@withContext try {
            val raw = KinSyncDatabase.getInstance(context)
                .familyItemDao()
                .observeItemsForFamily(familyId)
                .first()
            raw.map { entity ->
                val item = entity.toFamilyItem()
                if (item.isEncrypted && item.encryptedPayload.isNotBlank()) {
                    val decrypted = CryptoManager.decrypt(item.encryptedPayload, passkey)
                    if (decrypted.startsWith("TIT:")) {
                        val titlePart = decrypted.substringAfter("TIT:").substringBefore("||CON:")
                        val contentPart = decrypted.substringAfter("||CON:")
                        item.copy(title = titlePart, content = contentPart)
                    } else {
                        item.copy(content = decrypted)
                    }
                } else {
                    item
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Composable
    private fun WidgetContent(context: Context, items: List<FamilyItem>) {
        val upcomingEvents = items
            .filter { it.itemType == ItemType.EVENT.name }
            .sortedBy { it.eventDateMillis ?: Long.MAX_VALUE }
            .take(2)
        val pendingReminders = items
            .filter { it.itemType == ItemType.REMINDER.name && !it.isCompleted }
            .take(2)

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.surface)
                .padding(12.dp)
                .clickable(actionStartActivity(launchIntent)),
            verticalAlignment = Alignment.Top
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "PodAlly Family Hub",
                    style = TextStyle(
                        color = GlanceTheme.colors.primary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            Spacer(modifier = GlanceModifier.height(6.dp))

            if (upcomingEvents.isEmpty() && pendingReminders.isEmpty()) {
                Text(
                    text = "Open PodAlly to add events, reminders, or grocery lists.",
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                )
            } else {
                upcomingEvents.forEach { ev ->
                    Text(
                        text = "• Event: ${ev.title}",
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurface,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        maxLines = 1
                    )
                }
                pendingReminders.forEach { rem ->
                    Text(
                        text = "☐ ${rem.title}",
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurfaceVariant,
                            fontSize = 12.sp
                        ),
                        maxLines = 1
                    )
                }
            }
        }
    }

    companion object {
        suspend fun requestUpdate(context: Context) {
            try {
                val manager = GlanceAppWidgetManager(context)
                val glanceIds = manager.getGlanceIds(PodAllyGlanceWidget::class.java)
                val widget = PodAllyGlanceWidget()
                glanceIds.forEach { glanceId ->
                    widget.update(context, glanceId)
                }
            } catch (_: Exception) {
            }
        }
    }
}

class PodAllyWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PodAllyGlanceWidget()
}
