package com.example.ui.notes

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.FamilyItem
import com.example.data.model.ItemCategory
import com.example.data.model.ItemType
import com.example.data.model.RecurrenceRule
import com.example.ui.dashboard.CategoryTag
import com.example.util.ImageAttachmentHelper
import com.example.util.Retailer
import com.example.util.ShoppingDeepLinks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun NotesAndRemindersScreen(
    items: List<FamilyItem>,
    familyPasskey: String,
    onToggleReminder: (FamilyItem) -> Unit,
    onUpdateItem: (FamilyItem) -> Unit,
    onMergeChecklistToggle: (item: FamilyItem, cleanLine: String, toChecked: Boolean) -> Unit,
    onMergeChecklistAdd: (item: FamilyItem, newLine: String) -> Unit,
    onDeleteItem: (FamilyItem) -> Unit,
    onShareItem: (FamilyItem) -> Unit,
    onAddNew: (ItemType) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedCategoryFilter by remember { mutableStateOf("ALL") }
    var inspectingAttachmentItem by remember { mutableStateOf<FamilyItem?>(null) }

    val categories = listOf("ALL") + ItemCategory.entries.map { it.name }

    val allFilteredItems = items.filter { item ->
        val typeMatch = if (selectedTab == 0) {
            item.itemType == ItemType.NOTE.name
        } else {
            item.itemType == ItemType.REMINDER.name
        }
        val categoryMatch = if (selectedCategoryFilter == "ALL") true else item.category == selectedCategoryFilter
        typeMatch && categoryMatch
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Tabs: Notes & Lists vs Reminders
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Notes & Grocery Lists", fontWeight = FontWeight.SemiBold) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Reminders", fontWeight = FontWeight.SemiBold) }
                )
            }

            // Category Filter Chips
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(categories) { cat ->
                    FilterChip(
                        selected = selectedCategoryFilter == cat,
                        onClick = { selectedCategoryFilter = cat },
                        label = {
                            Text(
                                text = cat.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    )
                }
            }

            if (allFilteredItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (selectedTab == 0) "No notes or lists found" else "No reminders found",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tap the + button to create one for your family.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(allFilteredItems, key = { it.id }) { item ->
                        if (selectedTab == 0) {
                            val isChecklist = item.content.contains("- [ ]") || item.content.contains("- [x]") || item.category == ItemCategory.CHORES.name || item.category == ItemCategory.HOME.name
                            if (isChecklist) {
                                GroceryListCard(
                                    item = item,
                                    familyPasskey = familyPasskey,
                                    onToggleLine = { cleanLine, toChecked ->
                                        onMergeChecklistToggle(item, cleanLine, toChecked)
                                    },
                                    onAddLine = { newLine ->
                                        onMergeChecklistAdd(item, newLine)
                                    },
                                    onUpdate = onUpdateItem,
                                    onInspectPhoto = { inspectingAttachmentItem = item },
                                    onArchive = { onDeleteItem(item) },
                                    onShare = { onShareItem(item) }
                                )
                            } else {
                                NoteItemCard(
                                    item = item,
                                    familyPasskey = familyPasskey,
                                    onUpdate = onUpdateItem,
                                    onInspectPhoto = { inspectingAttachmentItem = item },
                                    onDelete = { onDeleteItem(item) },
                                    onShare = { onShareItem(item) }
                                )
                            }
                        } else {
                            ReminderItemCard(
                                item = item,
                                onToggle = { onToggleReminder(item) },
                                onDelete = { onDeleteItem(item) },
                                onShare = { onShareItem(item) }
                            )
                        }
                    }
                }
            }
        }

        // Floating Action Button
        FloatingActionButton(
            onClick = {
                if (selectedTab == 0) onAddNew(ItemType.NOTE) else onAddNew(ItemType.REMINDER)
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("add_note_or_reminder_fab"),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Icon(imageVector = Icons.Default.Add, contentDescription = "Add Item")
        }
    }

    // Full-screen encrypted photo/receipt preview dialog
    if (inspectingAttachmentItem != null) {
        val targetItem = inspectingAttachmentItem!!
        val fullBitmap by produceState<ImageBitmap?>(
            initialValue = null,
            targetItem.attachmentEncryptedBase64,
            familyPasskey
        ) {
            value = withContext(Dispatchers.Default) {
                ImageAttachmentHelper.decryptToImageBitmap(targetItem.attachmentEncryptedBase64, familyPasskey)
            }
        }
        AlertDialog(
            onDismissRequest = { inspectingAttachmentItem = null },
            title = { Text(targetItem.title.ifBlank { "Attached Picture" }, fontWeight = FontWeight.Bold) },
            text = {
                if (fullBitmap != null) {
                    Image(
                        bitmap = fullBitmap!!,
                        contentDescription = "Full encrypted attachment",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                } else {
                    Text(
                        text = "Unable to decrypt picture with current family passkey.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            confirmButton = {
                Button(onClick = { inspectingAttachmentItem = null }) {
                    Text("Close")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        onUpdateItem(targetItem.copy(attachmentEncryptedBase64 = ""))
                        inspectingAttachmentItem = null
                    }
                ) {
                    Text("Remove Photo", color = MaterialTheme.colorScheme.error)
                }
            }
        )
    }
}

/**
 * Interactive Grocery Shopping List Card where family members can strike off picked-up items,
 * add new items transactionally, attach grocery/product pictures, compare prices with
 * Costco/Walmart/Amazon tags, and archive when complete!
 */
@Composable
fun GroceryListCard(
    item: FamilyItem,
    familyPasskey: String,
    onToggleLine: (cleanText: String, toChecked: Boolean) -> Unit,
    onAddLine: (newLine: String) -> Unit,
    onUpdate: (FamilyItem) -> Unit,
    onInspectPhoto: () -> Unit,
    onArchive: () -> Unit,
    onShare: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var newItemText by remember { mutableStateOf("") }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val cipher = withContext(Dispatchers.IO) {
                    ImageAttachmentHelper.compressAndEncryptUri(context, uri, familyPasskey)
                }
                if (cipher != null) {
                    onUpdate(item.copy(attachmentEncryptedBase64 = cipher))
                    Toast.makeText(context, "Encrypted picture attached!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Image too large to encrypt cleanly.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val attachmentBitmap by produceState<ImageBitmap?>(
        initialValue = null,
        item.attachmentEncryptedBase64,
        familyPasskey
    ) {
        value = if (item.attachmentEncryptedBase64.isBlank()) {
            null
        } else {
            withContext(Dispatchers.Default) {
                ImageAttachmentHelper.decryptToImageBitmap(item.attachmentEncryptedBase64, familyPasskey)
            }
        }
    }

    // Parse checklist lines
    val lines = item.content.lines().filter { it.isNotBlank() }
    val allChecked = lines.isNotEmpty() && lines.all { it.startsWith("- [x]") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ShoppingCart,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = item.title.ifBlank { "Grocery Shopping List" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (item.estimatedCost != null && item.estimatedCost > 0.0) {
                            Text(
                                text = String.format(Locale.US, "Budget: $%.2f", item.estimatedCost),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (item.isEncrypted) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Encrypted",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(16.dp)
                                .padding(end = 4.dp)
                        )
                    }
                    IconButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("grocery_add_photo_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddPhotoAlternate,
                            contentDescription = "Add picture to grocery list",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(onClick = onShare, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Inline encrypted picture preview on grocery/shopping list
            if (attachmentBitmap != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onInspectPhoto() }
                ) {
                    Image(
                        bitmap = attachmentBitmap!!,
                        contentDescription = "Grocery list attached picture",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                    ) {
                        Text(
                            text = "Tap to view / edit photo",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Checklist items with strike-through (uses transactional merge)
            lines.forEach { line ->
                val isDone = line.startsWith("- [x]")
                val cleanText = line.removePrefix("- [x] ").removePrefix("- [ ] ")

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onToggleLine(cleanText, !isDone)
                        }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isDone) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                        contentDescription = null,
                        tint = if (isDone) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = cleanText,
                        style = MaterialTheme.typography.bodyMedium,
                        textDecoration = if (isDone) TextDecoration.LineThrough else TextDecoration.None,
                        color = if (isDone) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )

                    // Quick retail price check tags
                    RetailPill(name = "Amazon", onClick = { ShoppingDeepLinks.openRetailer(context, Retailer.AMAZON, cleanText) })
                    Spacer(modifier = Modifier.width(4.dp))
                    RetailPill(name = "Walmart", onClick = { ShoppingDeepLinks.openRetailer(context, Retailer.WALMART, cleanText) })
                    Spacer(modifier = Modifier.width(4.dp))
                    RetailPill(name = "Costco", onClick = { ShoppingDeepLinks.openRetailer(context, Retailer.COSTCO, cleanText) })
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Add item to list inline
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = newItemText,
                    onValueChange = { newItemText = it },
                    placeholder = { Text("Add grocery item...", style = MaterialTheme.typography.bodySmall) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (newItemText.isNotBlank()) {
                            onAddLine(newItemText.trim())
                            newItemText = ""
                        }
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("+")
                }
            }

            // Archive Banner when all items picked up
            if (allChecked) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "All items picked up!",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }

                        Button(
                            onClick = onArchive,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Archive,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Archive", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RetailPill(name: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 9.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun NoteItemCard(
    item: FamilyItem,
    familyPasskey: String,
    onUpdate: (FamilyItem) -> Unit,
    onInspectPhoto: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val cipher = withContext(Dispatchers.IO) {
                    ImageAttachmentHelper.compressAndEncryptUri(context, uri, familyPasskey)
                }
                if (cipher != null) {
                    onUpdate(item.copy(attachmentEncryptedBase64 = cipher))
                    Toast.makeText(context, "Encrypted picture attached!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val attachmentBitmap by produceState<ImageBitmap?>(
        initialValue = null,
        item.attachmentEncryptedBase64,
        familyPasskey
    ) {
        value = if (item.attachmentEncryptedBase64.isBlank()) {
            null
        } else {
            withContext(Dispatchers.Default) {
                ImageAttachmentHelper.decryptToImageBitmap(item.attachmentEncryptedBase64, familyPasskey)
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.title.ifBlank { "Note" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (item.isEncrypted) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Encrypted",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(16.dp)
                                .padding(end = 4.dp)
                        )
                    }
                    IconButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddPhotoAlternate,
                            contentDescription = "Attach picture",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = onShare, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            if (item.content.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = item.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (attachmentBitmap != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Image(
                    bitmap = attachmentBitmap!!,
                    contentDescription = "Note attached picture",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onInspectPhoto() }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CategoryTag(category = item.category)
                Text(
                    text = "Posted by ${item.authorName.ifBlank { "Member" }}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ReminderItemCard(
    item: FamilyItem,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit
) {
    val recurrenceLabel = RecurrenceRule.entries
        .firstOrNull { it.name == item.recurrenceRule && it != RecurrenceRule.NONE }
        ?.label

    Card(
        modifier = Modifier.fillMaxWidth(),
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
            IconButton(onClick = onToggle) {
                Icon(
                    imageVector = if (item.isCompleted) Icons.Default.CheckCircle else Icons.Default.CheckCircleOutline,
                    contentDescription = "Toggle completion",
                    tint = if (item.isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title.ifBlank { "Untitled Reminder" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    textDecoration = if (item.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                    color = if (item.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface
                )
                if (item.content.isNotBlank()) {
                    Text(
                        text = item.content,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CategoryTag(category = item.category)
                    if (recurrenceLabel != null) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Repeat,
                                    contentDescription = null,
                                    modifier = Modifier.size(11.dp),
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = recurrenceLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                    if (item.eventDateMillis != null) {
                        Text(
                            text = "Due: " + SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(item.eventDateMillis)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            IconButton(onClick = onShare, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "Share",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }

            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
