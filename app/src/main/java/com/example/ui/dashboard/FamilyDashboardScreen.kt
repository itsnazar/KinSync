package com.example.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Note
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.FamilyItem
import com.example.data.model.ItemCategory
import com.example.data.model.ItemType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun FamilyDashboardScreen(
    familyName: String,
    items: List<FamilyItem>,
    onToggleReminder: (FamilyItem) -> Unit,
    onOpenItemDetail: (FamilyItem) -> Unit,
    onAddAction: (ItemType) -> Unit,
    onRequestGeminiSummary: () -> Unit
) {
    // 1. Next 3-5 upcoming calendar events
    val upcomingEvents = items
        .filter { it.itemType == ItemType.EVENT.name }
        .sortedBy { it.eventDateMillis ?: Long.MAX_VALUE }
        .take(5)

    // 2. Latest 2-3 shared notes
    val latestNotes = items
        .filter { it.itemType == ItemType.NOTE.name }
        .take(3)

    // 3. Pending reminders
    val pendingReminders = items
        .filter { it.itemType == ItemType.REMINDER.name && !it.isCompleted }
        .take(5)

    val activeFlights = items
        .filter { it.itemType == ItemType.FLIGHT.name }
        .take(2)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("family_dashboard_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Welcome and Gemini AI Action Banner
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = familyName.ifBlank { "Family Space" },
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "End-to-End Encrypted Hub",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Encrypted",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = onRequestGeminiSummary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("gemini_summary_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Gemini Smart Family Briefing",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // Quick Actions Row
        item {
            Text(
                text = "Quick Actions",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    ActionChip(
                        icon = Icons.Default.CalendarMonth,
                        label = "+ Event",
                        onClick = { onAddAction(ItemType.EVENT) }
                    )
                }
                item {
                    ActionChip(
                        icon = Icons.Default.Note,
                        label = "+ Note",
                        onClick = { onAddAction(ItemType.NOTE) }
                    )
                }
                item {
                    ActionChip(
                        icon = Icons.Default.NotificationsActive,
                        label = "+ Reminder",
                        onClick = { onAddAction(ItemType.REMINDER) }
                    )
                }
                item {
                    ActionChip(
                        icon = Icons.Default.Flight,
                        label = "+ Flight",
                        onClick = { onAddAction(ItemType.FLIGHT) }
                    )
                }
                item {
                    ActionChip(
                        icon = Icons.Default.Place,
                        label = "+ Place",
                        onClick = { onAddAction(ItemType.PLACE) }
                    )
                }
            }
        }

        // SECTION 1: Next 3-5 Upcoming Calendar Events
        item {
            SectionHeader(
                title = "Upcoming Events",
                count = upcomingEvents.size,
                icon = Icons.Default.CalendarMonth
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (upcomingEvents.isEmpty()) {
                EmptyStateCard(message = "No upcoming family events. Tap '+ Event' to add.")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    upcomingEvents.forEach { event ->
                        EventDashboardCard(
                            item = event,
                            onClick = { onOpenItemDetail(event) }
                        )
                    }
                }
            }
        }

        // SECTION 2: Pending Reminders
        item {
            SectionHeader(
                title = "Pending Reminders",
                count = pendingReminders.size,
                icon = Icons.Default.NotificationsActive
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (pendingReminders.isEmpty()) {
                EmptyStateCard(message = "All family reminders are complete! 🎉")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    pendingReminders.forEach { reminder ->
                        ReminderDashboardCard(
                            item = reminder,
                            onToggle = { onToggleReminder(reminder) },
                            onClick = { onOpenItemDetail(reminder) }
                        )
                    }
                }
            }
        }

        // SECTION 3: Latest 2-3 Shared Notes
        item {
            SectionHeader(
                title = "Latest Shared Notes",
                count = latestNotes.size,
                icon = Icons.Default.Note
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (latestNotes.isEmpty()) {
                EmptyStateCard(message = "No shared notes yet. Tap '+ Note' to post a recipe, memo, or note.")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    latestNotes.forEach { note ->
                        NoteDashboardCard(
                            item = note,
                            onClick = { onOpenItemDetail(note) }
                        )
                    }
                }
            }
        }

        // Active Flights Section (if any)
        if (activeFlights.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Tracked Flights",
                    count = activeFlights.size,
                    icon = Icons.Default.Flight
                )
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    activeFlights.forEach { flight ->
                        FlightDashboardCard(item = flight, onClick = { onOpenItemDetail(flight) })
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    count: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        if (count > 0) {
            Text(
                text = "$count items",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ActionChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EventDashboardCard(
    item: FamilyItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Date Badge
            val formattedDate = item.eventDateMillis?.let {
                SimpleDateFormat("MMM d\nh:mm a", Locale.getDefault()).format(Date(it))
            } ?: "Upcoming"

            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontSize = 11.sp,
                    lineHeight = 14.sp
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title.ifBlank { "Untitled Event" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.content.isNotBlank()) {
                    Text(
                        text = item.content,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                CategoryTag(category = item.category)
            }

            if (item.isEncrypted) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Encrypted",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun ReminderDashboardCard(
    item: FamilyItem,
    onToggle: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onToggle) {
                Icon(
                    imageVector = if (item.isCompleted) Icons.Default.CheckCircle else Icons.Default.CheckCircleOutline,
                    contentDescription = "Complete Reminder",
                    tint = if (item.isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(24.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title.ifBlank { "Untitled Reminder" },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    textDecoration = if (item.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                    color = if (item.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CategoryTag(category = item.category)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "by ${item.authorName.ifBlank { "Member" }}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (item.isEncrypted) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Encrypted",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun NoteDashboardCard(
    item: FamilyItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.title.ifBlank { "Note" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.isEncrypted) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Encrypted",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            if (item.content.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CategoryTag(category = item.category)
                Text(
                    text = item.authorName.ifBlank { "Family Member" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun FlightDashboardCard(
    item: FamilyItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Flight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Flight ${item.flightNumber.ifBlank { item.title }}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${item.flightDeparture} → ${item.flightArrival}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = item.flightStatus.ifBlank { "On Time" },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
fun CategoryTag(category: String) {
    val (bgColor, textColor) = when (category.uppercase()) {
        ItemCategory.SCHOOL.name -> Color(0xFFE0E7FF) to Color(0xFF3730A3)
        ItemCategory.CHORES.name -> Color(0xFFDCFCE7) to Color(0xFF166534)
        ItemCategory.MEDICAL.name -> Color(0xFFFFE4E6) to Color(0xFF9F1239)
        ItemCategory.TRAVEL.name -> Color(0xFFE0F2FE) to Color(0xFF0369A1)
        ItemCategory.CELEBRATION.name -> Color(0xFFFEF3C7) to Color(0xFF92400E)
        ItemCategory.HOME.name -> Color(0xFFF3E8FF) to Color(0xFF6B21A8)
        else -> Color(0xFFF1F5F9) to Color(0xFF334155)
    }

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = bgColor
    ) {
        Text(
            text = category.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun EmptyStateCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp)
        )
    }
}
