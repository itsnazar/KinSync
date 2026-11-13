package com.example.ui.dialogs

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.FamilyItem
import com.example.data.model.ItemCategory
import com.example.data.model.ItemType
import com.example.data.model.RecurrenceRule
import com.example.util.ImageAttachmentHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

@Composable
fun AddItemDialog(
    initialType: ItemType,
    authorName: String,
    familyPasskey: String,
    onDismiss: () -> Unit,
    onConfirm: (FamilyItem) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val typeOptions = listOf(
        ItemType.NOTE to "Note",
        ItemType.REMINDER to "Reminder",
        ItemType.EVENT to "Event",
        ItemType.FLIGHT to "Flight",
        ItemType.PLACE to "Place"
    )

    var selectedTypeIndex by remember {
        val initialIndex = typeOptions.indexOfFirst { it.first == initialType }.coerceAtLeast(0)
        mutableIntStateOf(initialIndex)
    }

    val selectedType = typeOptions[selectedTypeIndex].first

    var title by remember { mutableStateOf("") }
    var content by remember {
        val defaultContent = if (selectedType == ItemType.NOTE) {
            "- [ ] Organic Whole Milk\n- [ ] Fresh Eggs (1 Dozen)\n- [ ] Sourdough Bread\n- [ ] Avocados"
        } else ""
        mutableStateOf(defaultContent)
    }
    var selectedCategory by remember { mutableStateOf(ItemCategory.GENERAL) }
    var isEncrypted by remember { mutableStateOf(true) }
    var recurrenceRule by remember { mutableStateOf(RecurrenceRule.NONE) }
    var estimatedCostText by remember { mutableStateOf("") }
    var encryptedAttachment by remember { mutableStateOf("") }
    var isProcessingPhoto by remember { mutableStateOf(false) }

    // Event & Travel specific
    var eventLocation by remember { mutableStateOf("") }
    var flightNum by remember { mutableStateOf("") }
    var flightDeparture by remember { mutableStateOf("") }
    var flightArrival by remember { mutableStateOf("") }

    var placeName by remember { mutableStateOf("") }
    var placeAddress by remember { mutableStateOf("") }

    // Date
    var daysFromNow by remember { mutableIntStateOf(1) }

    // Voice to text target: 0 for title, 1 for content
    var voiceTarget by remember { mutableIntStateOf(0) }

    val speechRecognizerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                if (voiceTarget == 0) {
                    title = if (title.isBlank()) spokenText else "$title $spokenText"
                } else {
                    content = if (content.isBlank()) spokenText else "$content\n$spokenText"
                }
            }
        }
    }

    // Zero-permission Android Photo Picker
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            isProcessingPhoto = true
            scope.launch {
                val cipher = withContext(Dispatchers.IO) {
                    ImageAttachmentHelper.compressAndEncryptUri(context, uri, familyPasskey)
                }
                isProcessingPhoto = false
                if (cipher != null) {
                    encryptedAttachment = cipher
                } else {
                    Toast.makeText(context, "Could not compress image under limit.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val previewBitmap by produceState<ImageBitmap?>(initialValue = null, encryptedAttachment, familyPasskey) {
        value = if (encryptedAttachment.isBlank()) {
            null
        } else {
            withContext(Dispatchers.Default) {
                ImageAttachmentHelper.decryptToImageBitmap(encryptedAttachment, familyPasskey)
            }
        }
    }

    fun startVoiceInput(target: Int) {
        voiceTarget = target
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, if (target == 0) "Speak title..." else "Speak details or note...")
        }
        try {
            speechRecognizerLauncher.launch(intent)
        } catch (_: Exception) {}
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "New Family Item",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Type selector tabs
                ScrollableTabRow(
                    selectedTabIndex = selectedTypeIndex,
                    edgePadding = 0.dp
                ) {
                    typeOptions.forEachIndexed { index, pair ->
                        Tab(
                            selected = selectedTypeIndex == index,
                            onClick = { selectedTypeIndex = index },
                            text = { Text(pair.second, fontWeight = FontWeight.SemiBold) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Title with Voice-to-Text Button
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = {
                        Text(
                            when (selectedType) {
                                ItemType.FLIGHT -> "Flight Title / Route"
                                ItemType.PLACE -> "Place or Attraction Name"
                                else -> "Title"
                            }
                        )
                    },
                    trailingIcon = {
                        IconButton(onClick = { startVoiceInput(0) }) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Voice to Text Title",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("item_title_input"),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp)
                )

                // Category selector
                Text(
                    text = "Label Category",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(ItemCategory.GENERAL, ItemCategory.HOME, ItemCategory.CHORES, ItemCategory.SCHOOL, ItemCategory.TRAVEL).forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat.name.lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                // Type-specific inputs
                when (selectedType) {
                    ItemType.NOTE -> {
                        OutlinedTextField(
                            value = content,
                            onValueChange = { content = it },
                            label = { Text("Note details or Grocery checklist") },
                            trailingIcon = {
                                IconButton(onClick = { startVoiceInput(1) }) {
                                    Icon(
                                        imageVector = Icons.Default.Mic,
                                        contentDescription = "Voice to Text Note",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            shape = RoundedCornerShape(10.dp)
                        )
                        Text(
                            text = "Tip: Use lines starting with '- [ ]' for interactive grocery shopping strike-offs!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 11.sp
                        )
                    }
                    ItemType.EVENT -> {
                        OutlinedTextField(
                            value = content,
                            onValueChange = { content = it },
                            label = { Text("Event Details & Highlights") },
                            trailingIcon = {
                                IconButton(onClick = { startVoiceInput(1) }) {
                                    Icon(Icons.Default.Mic, contentDescription = "Voice to Text", tint = MaterialTheme.colorScheme.primary)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp)
                        )

                        // Google Maps Event Location field
                        OutlinedTextField(
                            value = eventLocation,
                            onValueChange = { eventLocation = it },
                            label = { Text("Venue / Address (Google Maps Navigation)") },
                            leadingIcon = {
                                Icon(Icons.Default.Place, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp)
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Scheduled: ", style = MaterialTheme.typography.bodySmall)
                            Button(
                                onClick = { daysFromNow = (daysFromNow + 1) % 14 },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("In $daysFromNow day(s)", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    ItemType.REMINDER -> {
                        OutlinedTextField(
                            value = content,
                            onValueChange = { content = it },
                            label = { Text("Reminder Details") },
                            trailingIcon = {
                                IconButton(onClick = { startVoiceInput(1) }) {
                                    Icon(Icons.Default.Mic, contentDescription = "Voice to Text", tint = MaterialTheme.colorScheme.primary)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Due: ", style = MaterialTheme.typography.bodySmall)
                            Button(
                                onClick = { daysFromNow = (daysFromNow + 1) % 14 },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("In $daysFromNow day(s)", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    ItemType.FLIGHT -> {
                        OutlinedTextField(
                            value = flightNum,
                            onValueChange = { flightNum = it },
                            label = { Text("Flight Number (e.g. UA 450)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp)
                        )
                        OutlinedTextField(
                            value = flightDeparture,
                            onValueChange = { flightDeparture = it },
                            label = { Text("Departure (e.g. SFO)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp)
                        )
                        OutlinedTextField(
                            value = flightArrival,
                            onValueChange = { flightArrival = it },
                            label = { Text("Arrival (e.g. OGG)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                    ItemType.PLACE -> {
                        OutlinedTextField(
                            value = placeAddress,
                            onValueChange = { placeAddress = it },
                            label = { Text("Address / City (for Google Maps navigation)") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }

                // Recurrence rule chips for Events & Reminders
                if (selectedType == ItemType.EVENT || selectedType == ItemType.REMINDER) {
                    Text(
                        text = "Repeat Schedule",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        RecurrenceRule.entries.forEach { rule ->
                            FilterChip(
                                selected = recurrenceRule == rule,
                                onClick = { recurrenceRule = rule },
                                label = { Text(rule.label, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }

                // Optional Estimated Cost / Budget
                OutlinedTextField(
                    value = estimatedCostText,
                    onValueChange = { input ->
                        if (input.isEmpty() || input.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                            estimatedCostText = input
                        }
                    },
                    label = {
                        Text(
                            if (selectedType == ItemType.NOTE) "Estimated Budget / Receipt Cost ($, optional)"
                            else "Estimated Cost ($, optional)"
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp)
                )

                // Encrypted Photo / Receipt Picker (Zero-permission PickVisualMedia)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        enabled = !isProcessingPhoto,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("attach_photo_button"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        if (isProcessingPhoto) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Encrypting photo...", style = MaterialTheme.typography.labelMedium)
                        } else {
                            Icon(
                                imageVector = Icons.Default.AddPhotoAlternate,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (encryptedAttachment.isBlank()) {
                                    if (selectedType == ItemType.NOTE) "Attach Grocery / Product Picture" else "Attach Photo / Receipt"
                                } else {
                                    "Replace Encrypted Picture"
                                },
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }

                    if (previewBitmap != null) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(modifier = Modifier.fillMaxWidth()) {
                                Image(
                                    bitmap = previewBitmap!!,
                                    contentDescription = "Encrypted picture preview",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(120.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                )
                                IconButton(
                                    onClick = { encryptedAttachment = "" },
                                    modifier = Modifier.align(Alignment.TopEnd)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Remove picture",
                                            modifier = Modifier.padding(4.dp).size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // E2EE Checkbox
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { isEncrypted = !isEncrypted }
                ) {
                    Checkbox(
                        checked = isEncrypted,
                        onCheckedChange = { isEncrypted = it }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "End-to-End Encrypt with Family Passkey",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isProcessingPhoto,
                onClick = {
                    if (title.isNotBlank() || flightNum.isNotBlank() || placeName.isNotBlank() || eventLocation.isNotBlank() || encryptedAttachment.isNotBlank()) {
                        val cal = Calendar.getInstance().apply {
                            add(Calendar.DAY_OF_YEAR, daysFromNow)
                            set(Calendar.HOUR_OF_DAY, 10)
                            set(Calendar.MINUTE, 0)
                        }

                        val resolvedTitle = when {
                            title.isNotBlank() -> title.trim()
                            flightNum.isNotBlank() -> "Flight $flightNum"
                            placeName.isNotBlank() -> placeName.trim()
                            encryptedAttachment.isNotBlank() && selectedType == ItemType.NOTE -> "Grocery / Shopping List"
                            else -> "Family Activity"
                        }

                        val parsedCost = estimatedCostText.toDoubleOrNull()

                        val item = FamilyItem(
                            authorName = authorName,
                            itemType = selectedType.name,
                            category = selectedCategory.name,
                            isEncrypted = isEncrypted,
                            title = resolvedTitle,
                            content = content.trim(),
                            eventDateMillis = cal.timeInMillis,
                            recurrenceRule = if (selectedType == ItemType.EVENT || selectedType == ItemType.REMINDER) {
                                recurrenceRule.name
                            } else {
                                RecurrenceRule.NONE.name
                            },
                            estimatedCost = parsedCost,
                            attachmentEncryptedBase64 = encryptedAttachment,
                            flightNumber = flightNum.trim(),
                            flightDeparture = flightDeparture.trim(),
                            flightArrival = flightArrival.trim(),
                            flightStatus = "On Time",
                            placeName = if (placeName.isNotBlank()) placeName.trim() else resolvedTitle,
                            placeAddress = if (eventLocation.isNotBlank()) eventLocation.trim() else placeAddress.trim()
                        )
                        onConfirm(item)
                    }
                },
                modifier = Modifier.testTag("confirm_add_item_button")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
