package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Note
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import com.example.ai.AiResult
import com.example.ai.GeminiFamilyAdvisor
import com.example.data.model.Family
import com.example.data.model.FamilyItem
import com.example.data.model.ItemCategory
import com.example.data.model.ItemType
import com.example.data.model.RecurrenceRule
import com.example.data.model.UserProfile
import com.example.data.repository.FamilyRepository
import com.example.security.CryptoManager
import com.example.security.SecurePasskeyStore
import com.example.ui.calendar.SocialCalendarScreen
import com.example.ui.dashboard.FamilyDashboardScreen
import com.example.ui.dialogs.AddItemDialog
import com.example.ui.dialogs.GeminiInsightsDialog
import com.example.ui.dialogs.GmailBookingImportDialog
import com.example.ui.dialogs.ShareLinkDialog
import com.example.ui.family.FamilyWorkspaceScreen
import com.example.ui.notes.NotesAndRemindersScreen
import com.example.ui.settings.SettingsAndSecurityScreen
import com.example.ui.travel.TravelPlanningScreen
import com.example.util.NetworkMonitor
import com.example.util.NotificationHelper
import com.example.util.NotificationPrefsStore
import com.example.worker.ReminderNotificationWorker
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.messaging.messaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppContent(
    currentUserId: String,
    sharedBookingText: String? = null,
    onConsumeSharedBookingText: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { FamilyRepository(context) }
    val aiAdvisor = remember { GeminiFamilyAdvisor() }

    val networkMonitor = remember { NetworkMonitor(context) }
    DisposableEffect(networkMonitor) {
        onDispose { networkMonitor.unregister() }
    }
    val isOnline by networkMonitor.isOnline.collectAsState()

    var familyPasskey by remember {
        mutableStateOf(SecurePasskeyStore.loadPasskey(context))
    }
    var biometricLockEnabled by remember {
        mutableStateOf(SecurePasskeyStore.isBiometricLockEnabled(context))
    }

    var notificationPrefs by remember {
        mutableStateOf(NotificationPrefsStore.load(context))
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    // Initialize notification channels & request Android 13+ notification permission
    LaunchedEffect(Unit) {
        NotificationHelper.createNotificationChannel(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // Observe user profile & families
    val userProfile by repository.observeUserProfile(currentUserId).collectAsState(initial = null)
    val userFamilies by repository.observeUserFamilies(currentUserId).collectAsState(initial = emptyList())

    var activeFamily by remember { mutableStateOf<Family?>(null) }
    var isWorkspaceLoading by remember { mutableStateOf(false) }
    var workspaceError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(userFamilies, userProfile) {
        if (activeFamily == null && userFamilies.isNotEmpty()) {
            val preferredId = userProfile?.activeFamilyId
            activeFamily = userFamilies.find { it.id == preferredId } ?: userFamilies.first()
        } else if (activeFamily != null) {
            userFamilies.find { it.id == activeFamily!!.id }?.let { updated ->
                activeFamily = updated
            }
        }
    }

    // Persist active family ID for Glance widget & background workers
    LaunchedEffect(activeFamily?.id) {
        val famId = activeFamily?.id.orEmpty()
        if (famId.isNotBlank()) {
            SecurePasskeyStore.saveActiveFamilyId(context, famId)
            try {
                Firebase.messaging.subscribeToTopic("family_$famId").await()
            } catch (_: Exception) {
            }
        }
    }

    // Save profile and FCM registration token
    LaunchedEffect(currentUserId) {
        val user = Firebase.auth.currentUser
        if (user != null) {
            val token = try {
                Firebase.messaging.token.await()
            } catch (_: Exception) {
                ""
            }
            repository.saveUserProfile(
                UserProfile(
                    userId = currentUserId,
                    displayName = user.displayName ?: "Family Member",
                    email = user.email ?: "",
                    activeFamilyId = activeFamily?.id ?: "",
                    fcmToken = token
                )
            )
            if (token.isNotBlank()) {
                repository.updateFcmToken(token)
            }
        }
    }

    // Observe pending Room offline sync items & auto-sync when connection is restored
    val activeFamilyId = activeFamily?.id.orEmpty()
    val pendingSyncCount by repository.observePendingSyncCount(activeFamilyId).collectAsState(initial = 0)

    LaunchedEffect(isOnline, activeFamilyId) {
        if (isOnline && activeFamilyId.isNotBlank()) {
            val synced = repository.syncPendingItems(activeFamilyId)
            if (synced > 0) {
                Toast.makeText(context, "Synced $synced offline change(s) to cloud", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Observe active family items (offline-first Room + bounded real-time Firestore)
    val rawItems by repository.observeFamilyItems(activeFamilyId).collectAsState(initial = emptyList())

    // Decrypt items off the main UI thread on Dispatchers.Default
    val decryptedItems by produceState(initialValue = emptyList<FamilyItem>(), rawItems, familyPasskey) {
        value = withContext(Dispatchers.Default) {
            rawItems.map { item ->
                if (item.isEncrypted && item.encryptedPayload.isNotBlank()) {
                    val decrypted = CryptoManager.decrypt(item.encryptedPayload, familyPasskey)
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
        }
    }

    val memberDisplayNames = remember(activeFamily, decryptedItems, currentUserId) {
        val map = linkedMapOf<String, String>()
        map[currentUserId] = Firebase.auth.currentUser?.displayName ?: "You"
        decryptedItems.forEach { item ->
            if (item.authorId.isNotBlank() && item.authorName.isNotBlank()) {
                map.putIfAbsent(item.authorId, item.authorName)
            }
        }
        activeFamily?.memberUids?.forEachIndexed { idx, uid ->
            map.putIfAbsent(uid, "Member ${idx + 1}")
        }
        map
    }

    // Modals state
    var showAddItemDialog by remember { mutableStateOf(false) }
    var addItemInitialType by remember { mutableStateOf(ItemType.NOTE) }

    var showShareLinkDialog by remember { mutableStateOf(false) }
    var itemToShare by remember { mutableStateOf<FamilyItem?>(null) }

    var showGeminiDialog by remember { mutableStateOf(false) }
    var geminiDialogTitle by remember { mutableStateOf("Gemini Family Briefing") }
    var geminiResult by remember { mutableStateOf<AiResult>(AiResult.Loading) }

    var showGmailImportDialog by remember { mutableStateOf(false) }
    var gmailImportSeedText by remember { mutableStateOf("") }

    var selectedNavTab by remember { mutableIntStateOf(0) }

    // Helper for recurring reminders: advances date & reschedules offline reminder worker
    fun handleToggleReminder(item: FamilyItem) {
        scope.launch {
            val famId = activeFamily?.id ?: return@launch
            val isRecurring = item.recurrenceRule != RecurrenceRule.NONE.name && item.recurrenceRule.isNotBlank()
            val updated = if (isRecurring && !item.isCompleted) {
                val nextDate = FamilyRepository.nextRecurrenceMillis(item.eventDateMillis, item.recurrenceRule)
                Toast.makeText(context, "Advanced recurring reminder to next cycle", Toast.LENGTH_SHORT).show()
                item.copy(isCompleted = false, eventDateMillis = nextDate)
            } else {
                item.copy(isCompleted = !item.isCompleted)
            }
            val itemToSave = if (updated.isEncrypted) {
                val cipher = CryptoManager.encrypt("TIT:${updated.title}||CON:${updated.content}", familyPasskey)
                updated.copy(encryptedPayload = cipher)
            } else {
                updated
            }
            repository.updateFamilyItem(famId, itemToSave, isOnline = isOnline)
            if (!itemToSave.isCompleted && itemToSave.eventDateMillis != null) {
                ReminderNotificationWorker.scheduleReminder(context, itemToSave)
            }
        }
    }

    // Open booking import dialog automatically when email text is shared into PodAlly via ACTION_SEND
    LaunchedEffect(sharedBookingText, activeFamily?.id) {
        if (!sharedBookingText.isNullOrBlank() && activeFamily != null) {
            gmailImportSeedText = sharedBookingText
            selectedNavTab = 3
            showGmailImportDialog = true
            onConsumeSharedBookingText()
        }
    }

    BackHandler(enabled = selectedNavTab != 0) {
        selectedNavTab = 0
    }

    // If user has no active family workspace yet, show FamilyWorkspaceScreen
    if (activeFamily == null) {
        FamilyWorkspaceScreen(
            userFamilies = userFamilies,
            onSelectFamily = { fam ->
                activeFamily = fam
                SecurePasskeyStore.saveActiveFamilyId(context, fam.id)
                scope.launch {
                    repository.saveUserProfile(
                        UserProfile(
                            userId = currentUserId,
                            displayName = Firebase.auth.currentUser?.displayName ?: "Member",
                            email = Firebase.auth.currentUser?.email ?: "",
                            activeFamilyId = fam.id
                        )
                    )
                }
            },
            onCreateFamily = { name, joinCode ->
                isWorkspaceLoading = true
                workspaceError = null
                scope.launch {
                    val result = repository.createFamily(name, joinCode)
                    if (result.isSuccess) {
                        val created = result.getOrThrow()
                        activeFamily = created
                        SecurePasskeyStore.saveActiveFamilyId(context, created.id)
                        repository.saveUserProfile(
                            UserProfile(
                                userId = currentUserId,
                                displayName = Firebase.auth.currentUser?.displayName ?: "Member",
                                email = Firebase.auth.currentUser?.email ?: "",
                                activeFamilyId = created.id
                            )
                        )
                    } else {
                        workspaceError = result.exceptionOrNull()?.localizedMessage ?: "Failed to create family"
                    }
                    isWorkspaceLoading = false
                }
            },
            onJoinFamily = { familyId, joinCode ->
                isWorkspaceLoading = true
                workspaceError = null
                scope.launch {
                    val result = repository.joinFamilyByCode(joinCode, familyId)
                    if (result.isSuccess) {
                        Toast.makeText(context, "Joined family space!", Toast.LENGTH_SHORT).show()
                    } else {
                        workspaceError = result.exceptionOrNull()?.localizedMessage ?: "Invalid family ID or code"
                    }
                    isWorkspaceLoading = false
                }
            },
            isLoading = isWorkspaceLoading,
            errorMessage = workspaceError
        )
        return
    }

    val familyMembers = activeFamily!!.memberUids.ifEmpty { listOf(currentUserId) }

    // Main Scaffold UI
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (selectedNavTab) {
                            0 -> activeFamily?.name ?: "Family Dashboard"
                            1 -> "Notes & Grocery Lists"
                            2 -> "Family Social Calendar"
                            3 -> "Travel & Flight Tracking"
                            else -> "Settings & Privacy"
                        },
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    // Online / Offline / Pending Sync Pill
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = when {
                            !isOnline -> MaterialTheme.colorScheme.errorContainer
                            pendingSyncCount > 0 -> MaterialTheme.colorScheme.secondaryContainer
                            else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                        },
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .clickable(enabled = isOnline && pendingSyncCount > 0) {
                                scope.launch {
                                    val synced = repository.syncPendingItems(activeFamily!!.id)
                                    Toast.makeText(context, "Synced $synced item(s)", Toast.LENGTH_SHORT).show()
                                }
                            }
                            .testTag("sync_status_badge")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = when {
                                    !isOnline -> Icons.Default.CloudOff
                                    pendingSyncCount > 0 -> Icons.Default.Sync
                                    else -> Icons.Default.CloudDone
                                },
                                contentDescription = if (isOnline) "Online and synced" else "Offline cache active",
                                tint = when {
                                    !isOnline -> MaterialTheme.colorScheme.onErrorContainer
                                    pendingSyncCount > 0 -> MaterialTheme.colorScheme.onSecondaryContainer
                                    else -> MaterialTheme.colorScheme.primary
                                },
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = when {
                                    !isOnline -> if (pendingSyncCount > 0) "Offline ($pendingSyncCount)" else "Offline"
                                    pendingSyncCount > 0 -> "Sync ($pendingSyncCount)"
                                    else -> "Synced"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = when {
                                    !isOnline -> MaterialTheme.colorScheme.onErrorContainer
                                    pendingSyncCount > 0 -> MaterialTheme.colorScheme.onSecondaryContainer
                                    else -> MaterialTheme.colorScheme.primary
                                }
                            )
                        }
                    }

                    // Small E2EE Status Badge
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "E2EE Active",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(end = 16.dp)
                            .size(20.dp)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.testTag("main_navigation_bar")
            ) {
                NavigationBarItem(
                    selected = selectedNavTab == 0,
                    onClick = { selectedNavTab = 0 },
                    icon = { Icon(Icons.Default.Dashboard, contentDescription = "Dashboard") },
                    label = { Text("Dashboard") }
                )
                NavigationBarItem(
                    selected = selectedNavTab == 1,
                    onClick = { selectedNavTab = 1 },
                    icon = { Icon(Icons.Default.Note, contentDescription = "Notes") },
                    label = { Text("Notes & Lists") }
                )
                NavigationBarItem(
                    selected = selectedNavTab == 2,
                    onClick = { selectedNavTab = 2 },
                    icon = { Icon(Icons.Default.CalendarMonth, contentDescription = "Calendar") },
                    label = { Text("Calendar") }
                )
                NavigationBarItem(
                    selected = selectedNavTab == 3,
                    onClick = { selectedNavTab = 3 },
                    icon = { Icon(Icons.Default.Flight, contentDescription = "Travel") },
                    label = { Text("Travel") }
                )
                NavigationBarItem(
                    selected = selectedNavTab == 4,
                    onClick = { selectedNavTab = 4 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedNavTab) {
                0 -> {
                    FamilyDashboardScreen(
                        familyName = activeFamily?.name ?: "Family Space",
                        items = decryptedItems,
                        onToggleReminder = { item -> handleToggleReminder(item) },
                        onOpenItemDetail = { item ->
                            itemToShare = item
                            showShareLinkDialog = true
                        },
                        onAddAction = { type ->
                            addItemInitialType = type
                            showAddItemDialog = true
                        },
                        onRequestGeminiSummary = {
                            geminiDialogTitle = "Gemini Family Briefing"
                            geminiResult = AiResult.Loading
                            showGeminiDialog = true
                            scope.launch {
                                val summaryContent = decryptedItems.take(8).joinToString("\n") { itm ->
                                    "- [${itm.itemType}] ${itm.title}: ${itm.content}"
                                }
                                val result = aiAdvisor.summarizeFamilyActivities(
                                    if (summaryContent.isBlank()) "No scheduled events yet." else summaryContent
                                )
                                geminiResult = result
                            }
                        }
                    )
                }
                1 -> {
                    NotesAndRemindersScreen(
                        items = decryptedItems,
                        familyPasskey = familyPasskey,
                        onToggleReminder = { item -> handleToggleReminder(item) },
                        onUpdateItem = { updatedItem ->
                            scope.launch {
                                val itemToSave = if (updatedItem.isEncrypted) {
                                    val plain = "TIT:${updatedItem.title}||CON:${updatedItem.content}"
                                    val cipher = CryptoManager.encrypt(plain, familyPasskey)
                                    updatedItem.copy(encryptedPayload = cipher)
                                } else {
                                    updatedItem
                                }
                                repository.updateFamilyItem(activeFamily!!.id, itemToSave, isOnline = isOnline)
                            }
                        },
                        onMergeChecklistToggle = { item, cleanLine, toChecked ->
                            scope.launch {
                                repository.mergeChecklistUpdate(
                                    familyId = activeFamily!!.id,
                                    localItem = item,
                                    toggledCleanItemText = cleanLine,
                                    toggleToChecked = toChecked,
                                    passkey = familyPasskey,
                                    isOnline = isOnline
                                )
                            }
                        },
                        onMergeChecklistAdd = { item, newLine ->
                            scope.launch {
                                repository.mergeChecklistUpdate(
                                    familyId = activeFamily!!.id,
                                    localItem = item,
                                    addedLineText = newLine,
                                    passkey = familyPasskey,
                                    isOnline = isOnline
                                )
                            }
                        },
                        onDeleteItem = { item ->
                            scope.launch {
                                repository.deleteFamilyItem(activeFamily!!.id, item.id, isOnline = isOnline)
                            }
                        },
                        onShareItem = { item ->
                            itemToShare = item
                            showShareLinkDialog = true
                        },
                        onAddNew = { type ->
                            addItemInitialType = type
                            showAddItemDialog = true
                        }
                    )
                }
                2 -> {
                    SocialCalendarScreen(
                        items = decryptedItems,
                        onAddEvent = {
                            addItemInitialType = ItemType.EVENT
                            showAddItemDialog = true
                        },
                        onDeleteEvent = { event ->
                            scope.launch {
                                repository.deleteFamilyItem(activeFamily!!.id, event.id, isOnline = isOnline)
                            }
                        },
                        onShareEvent = { event ->
                            itemToShare = event
                            showShareLinkDialog = true
                        },
                        onRequestGeminiScheduleAnalysis = {
                            geminiDialogTitle = "Gemini Conflict & Schedule Check"
                            geminiResult = AiResult.Loading
                            showGeminiDialog = true
                            scope.launch {
                                val eventsText = decryptedItems.filter { it.itemType == ItemType.EVENT.name }
                                    .joinToString("\n") { ev ->
                                        "- ${ev.title} (${ev.category}) on date: ${ev.eventDateMillis}"
                                    }
                                val prompt = if (eventsText.isBlank()) "No events." else eventsText
                                geminiResult = aiAdvisor.summarizeFamilyActivities("Calendar Schedule:\n$prompt")
                            }
                        }
                    )
                }
                3 -> {
                    TravelPlanningScreen(
                        items = decryptedItems,
                        familyPasskey = familyPasskey,
                        onAddFlight = { fNum, dep, arr, cost ->
                            scope.launch {
                                val flightItem = FamilyItem(
                                    familyId = activeFamily!!.id,
                                    memberUids = familyMembers,
                                    authorId = currentUserId,
                                    authorName = Firebase.auth.currentUser?.displayName ?: "Family",
                                    itemType = ItemType.FLIGHT.name,
                                    category = ItemCategory.TRAVEL.name,
                                    title = "Flight $fNum",
                                    flightNumber = fNum,
                                    flightDeparture = dep,
                                    flightArrival = arr,
                                    flightStatus = "On Time",
                                    estimatedCost = cost,
                                    isEncrypted = false
                                )
                                repository.addFamilyItem(activeFamily!!.id, flightItem, isOnline = isOnline)
                            }
                        },
                        onAddPlace = { pName, pAddr, cost ->
                            scope.launch {
                                val placeItem = FamilyItem(
                                    familyId = activeFamily!!.id,
                                    memberUids = familyMembers,
                                    authorId = currentUserId,
                                    authorName = Firebase.auth.currentUser?.displayName ?: "Family",
                                    itemType = ItemType.PLACE.name,
                                    category = ItemCategory.TRAVEL.name,
                                    title = pName,
                                    placeName = pName,
                                    placeAddress = pAddr,
                                    estimatedCost = cost,
                                    isEncrypted = false
                                )
                                repository.addFamilyItem(activeFamily!!.id, placeItem, isOnline = isOnline)
                            }
                        },
                        onAddTripExpense = { title, cost, encryptedReceipt ->
                            scope.launch {
                                val expenseItem = FamilyItem(
                                    familyId = activeFamily!!.id,
                                    memberUids = familyMembers,
                                    authorId = currentUserId,
                                    authorName = Firebase.auth.currentUser?.displayName ?: "Family",
                                    itemType = ItemType.NOTE.name,
                                    category = ItemCategory.TRAVEL.name,
                                    title = title,
                                    content = "Trip Expense",
                                    estimatedCost = cost,
                                    attachmentEncryptedBase64 = encryptedReceipt,
                                    isEncrypted = true,
                                    encryptedPayload = CryptoManager.encrypt("TIT:$title||CON:Trip Expense", familyPasskey)
                                )
                                repository.addFamilyItem(activeFamily!!.id, expenseItem, isOnline = isOnline)
                            }
                        },
                        onUpdateFlightStatus = { flight, newStatus ->
                            scope.launch {
                                repository.updateFamilyItem(
                                    activeFamily!!.id,
                                    flight.copy(flightStatus = newStatus),
                                    isOnline = isOnline
                                )
                                Toast.makeText(
                                    context,
                                    "Flight ${flight.flightNumber} updated to $newStatus",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        onOpenGmailImport = {
                            gmailImportSeedText = ""
                            showGmailImportDialog = true
                        },
                        onDeleteItem = { item ->
                            scope.launch {
                                repository.deleteFamilyItem(activeFamily!!.id, item.id, isOnline = isOnline)
                            }
                        },
                        onShareItem = { item ->
                            itemToShare = item
                            showShareLinkDialog = true
                        },
                        onRequestGeminiTripPlan = { destination, dates ->
                            geminiDialogTitle = "Gemini Trip Packing & Shopping Plan"
                            geminiResult = AiResult.Loading
                            showGeminiDialog = true
                            scope.launch {
                                val placesSummary = decryptedItems.filter {
                                    it.itemType == ItemType.PLACE.name || it.itemType == ItemType.FLIGHT.name
                                }.joinToString(", ") { it.title }
                                geminiResult = aiAdvisor.planTripSuppliesAndShopping(destination, dates, placesSummary)
                            }
                        }
                    )
                }
                4 -> {
                    SettingsAndSecurityScreen(
                        currentFamily = activeFamily,
                        currentPasskey = familyPasskey,
                        notificationPrefs = notificationPrefs,
                        memberDisplayNames = memberDisplayNames,
                        isOnline = isOnline,
                        pendingSyncCount = pendingSyncCount,
                        biometricLockEnabled = biometricLockEnabled,
                        userEmail = Firebase.auth.currentUser?.email ?: "",
                        userDisplayName = Firebase.auth.currentUser?.displayName ?: "Family Member",
                        onSavePasskey = { newKey ->
                            familyPasskey = newKey
                            SecurePasskeyStore.savePasskey(context, newKey)
                            Toast.makeText(context, "Encryption passkey saved in Android Keystore!", Toast.LENGTH_SHORT).show()
                        },
                        onToggleBiometricLock = { enabled ->
                            biometricLockEnabled = enabled
                            SecurePasskeyStore.setBiometricLockEnabled(context, enabled)
                            Toast.makeText(
                                context,
                                if (enabled) "Biometric app lock enabled" else "Biometric app lock disabled",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        onPruneOldItems = {
                            scope.launch {
                                val pruned = repository.pruneOldCompletedItems(activeFamily!!.id, olderThanDays = 60)
                                Toast.makeText(
                                    context,
                                    if (pruned > 0) "Pruned $pruned old completed item(s)" else "No completed items older than 60 days",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        onUpdateNotificationPrefs = { newPrefs ->
                            notificationPrefs = newPrefs
                            NotificationPrefsStore.save(context, newPrefs)
                        },
                        onTriggerManualSync = {
                            scope.launch {
                                val synced = repository.syncPendingItems(activeFamily!!.id)
                                Toast.makeText(context, "Synced $synced pending item(s)", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onSignOut = {
                            CryptoManager.clearKeyCache()
                            val credManager = CredentialManager.create(context)
                            scope.launch {
                                withContext(NonCancellable) {
                                    try {
                                        credManager.clearCredentialState(ClearCredentialStateRequest())
                                    } catch (_: Exception) {}
                                    Firebase.auth.signOut()
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    // Unified Add Item Dialog
    if (showAddItemDialog) {
        AddItemDialog(
            initialType = addItemInitialType,
            authorName = Firebase.auth.currentUser?.displayName ?: "Family Member",
            familyPasskey = familyPasskey,
            onDismiss = { showAddItemDialog = false },
            onConfirm = { newItem ->
                scope.launch {
                    val finalItem = if (newItem.isEncrypted) {
                        val plain = "TIT:${newItem.title}||CON:${newItem.content}"
                        val cipher = CryptoManager.encrypt(plain, familyPasskey)
                        newItem.copy(
                            familyId = activeFamily!!.id,
                            memberUids = familyMembers,
                            encryptedPayload = cipher
                        )
                    } else {
                        newItem.copy(
                            familyId = activeFamily!!.id,
                            memberUids = familyMembers
                        )
                    }

                    val result = repository.addFamilyItem(activeFamily!!.id, finalItem, isOnline = isOnline)
                    val savedId = result.getOrNull().orEmpty()
                    if (finalItem.itemType == ItemType.REMINDER.name || finalItem.itemType == ItemType.EVENT.name) {
                        ReminderNotificationWorker.scheduleReminder(
                            context,
                            finalItem.copy(id = savedId.ifBlank { finalItem.id })
                        )
                    }
                    showAddItemDialog = false

                    // Dispatch local confirmation alert respecting category, member, and E2EE preview settings
                    NotificationHelper.sendNotification(
                        context = context,
                        title = "New ${finalItem.itemType}: ${newItem.title}",
                        body = "Added to ${activeFamily?.name}",
                        itemType = ItemType.valueOf(finalItem.itemType),
                        authorId = currentUserId,
                        prefs = notificationPrefs,
                        isEncrypted = finalItem.isEncrypted
                    )
                }
            }
        )
    }

    // Gmail & Email Booking Import Dialog
    if (showGmailImportDialog) {
        GmailBookingImportDialog(
            initialEmailText = gmailImportSeedText,
            aiAdvisor = aiAdvisor,
            onImportBookings = { flights, places ->
                scope.launch {
                    val authorName = Firebase.auth.currentUser?.displayName ?: "Family"
                    flights.forEach { f ->
                        val item = FamilyItem(
                            familyId = activeFamily!!.id,
                            memberUids = familyMembers,
                            authorId = currentUserId,
                            authorName = authorName,
                            itemType = ItemType.FLIGHT.name,
                            category = ItemCategory.TRAVEL.name,
                            title = "Flight ${f.flightNumber}",
                            content = f.summary,
                            flightNumber = f.flightNumber,
                            flightDeparture = f.departure,
                            flightArrival = f.arrival,
                            flightStatus = f.status,
                            isEncrypted = false
                        )
                        repository.addFamilyItem(activeFamily!!.id, item, isOnline = isOnline)
                    }
                    places.forEach { p ->
                        val item = FamilyItem(
                            familyId = activeFamily!!.id,
                            memberUids = familyMembers,
                            authorId = currentUserId,
                            authorName = authorName,
                            itemType = ItemType.PLACE.name,
                            category = ItemCategory.TRAVEL.name,
                            title = p.name,
                            placeName = p.name,
                            placeAddress = p.address,
                            isEncrypted = false
                        )
                        repository.addFamilyItem(activeFamily!!.id, item, isOnline = isOnline)
                    }
                    showGmailImportDialog = false
                    Toast.makeText(
                        context,
                        "Imported ${flights.size} flight(s) and ${places.size} place(s)",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            onDismiss = { showGmailImportDialog = false }
        )
    }

    // Share Link Dialog
    if (showShareLinkDialog && itemToShare != null) {
        ShareLinkDialog(
            item = itemToShare!!,
            onCreateLink = { link ->
                repository.createShareLink(link)
            },
            onDismiss = {
                showShareLinkDialog = false
                itemToShare = null
            }
        )
    }

    // Gemini Insights Dialog
    if (showGeminiDialog) {
        GeminiInsightsDialog(
            title = geminiDialogTitle,
            aiResult = geminiResult,
            onDismiss = { showGeminiDialog = false }
        )
    }
}
