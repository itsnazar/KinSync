package com.example.ui.travel

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.FamilyItem
import com.example.data.model.ItemCategory
import com.example.data.model.ItemType
import com.example.util.GoogleMapsHelper
import com.example.util.ImageAttachmentHelper
import com.example.util.Retailer
import com.example.util.ShoppingDeepLinks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.util.Locale

@Composable
fun TravelPlanningScreen(
    items: List<FamilyItem>,
    familyPasskey: String,
    onAddFlight: (String, String, String, Double?) -> Unit,
    onAddPlace: (String, String, Double?) -> Unit,
    onAddTripExpense: (title: String, cost: Double, encryptedReceiptBase64: String) -> Unit,
    onUpdateFlightStatus: (FamilyItem, String) -> Unit,
    onOpenGmailImport: () -> Unit,
    onDeleteItem: (FamilyItem) -> Unit,
    onShareItem: (FamilyItem) -> Unit,
    onRequestGeminiTripPlan: (destination: String, dates: String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val flights = items.filter { it.itemType == ItemType.FLIGHT.name }
    val places = items.filter { it.itemType == ItemType.PLACE.name }
    val travelExpenses = items.filter {
        it.category == ItemCategory.TRAVEL.name &&
            it.itemType != ItemType.FLIGHT.name &&
            it.itemType != ItemType.PLACE.name &&
            (it.estimatedCost != null || it.attachmentEncryptedBase64.isNotBlank())
    }

    val totalTripBudget = remember(items) {
        items.filter {
            it.itemType == ItemType.FLIGHT.name ||
                it.itemType == ItemType.PLACE.name ||
                it.category == ItemCategory.TRAVEL.name
        }.sumOf { it.estimatedCost ?: 0.0 }
    }

    var showAddFlightDialog by remember { mutableStateOf(false) }
    var showAddPlaceDialog by remember { mutableStateOf(false) }
    var showAddExpenseDialog by remember { mutableStateOf(false) }
    var inspectingReceiptItem by remember { mutableStateOf<FamilyItem?>(null) }

    var tripDestinationInput by remember { mutableStateOf("Family Summer Vacation") }
    var tripDatesInput by remember { mutableStateOf("July 15 - July 24") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("travel_planning_screen"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // SECTION 1: Travel Destinations & Dates Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
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
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Family Travel & Itinerary",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Encrypted Travel Plans, Flights & Bookings",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.Map,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = tripDestinationInput,
                            onValueChange = { tripDestinationInput = it },
                            label = { Text("Destination") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp)
                        )
                        OutlinedTextField(
                            value = tripDatesInput,
                            onValueChange = { tripDatesInput = it },
                            label = { Text("Dates") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            onRequestGeminiTripPlan(tripDestinationInput, tripDatesInput)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Gemini Trip Packing & Shopping Advice")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = onOpenGmailImport,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("import_gmail_booking_button"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Email,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Import Bookings from Email Text")
                    }
                }
            }
        }

        // SECTION 1B: Trip Budget Tracker Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AccountBalanceWallet,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Trip Budget Tracker",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = String.format(Locale.US, "Planned Total: $%.2f", totalTripBudget),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Button(
                            onClick = { showAddExpenseDialog = true },
                            modifier = Modifier.testTag("log_trip_expense_button"),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("+ Log Expense", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    if (travelExpenses.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        travelExpenses.forEach { exp ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = exp.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    if (exp.attachmentEncryptedBase64.isNotBlank()) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            modifier = Modifier.clickable { inspectingReceiptItem = exp }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Receipt,
                                                    contentDescription = "View receipt",
                                                    modifier = Modifier.size(12.dp),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                                Spacer(modifier = Modifier.width(3.dp))
                                                Text(
                                                    text = "Receipt",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (exp.estimatedCost != null) {
                                        Text(
                                            text = String.format(Locale.US, "$%.2f", exp.estimatedCost),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    IconButton(onClick = { onDeleteItem(exp) }, modifier = Modifier.size(28.dp)) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete expense",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // SECTION 2: Flight Tracking
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Flight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Flight Tracking",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Button(
                    onClick = { showAddFlightDialog = true },
                    modifier = Modifier.testTag("add_flight_button"),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("+ Track Flight", style = MaterialTheme.typography.labelSmall)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (flights.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No flights tracked yet. Tap '+ Track Flight' or paste confirmation text from Email.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    flights.forEach { flight ->
                        FlightCard(
                            flight = flight,
                            onStatusChange = { newStatus -> onUpdateFlightStatus(flight, newStatus) },
                            onOpenLiveStatus = {
                                val flightCode = flight.flightNumber.ifBlank { flight.title }.trim()
                                val encoded = URLEncoder.encode("flight status $flightCode", "UTF-8")
                                val intent = Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://www.google.com/search?q=$encoded")
                                ).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(intent)
                            },
                            onInspectPhoto = { inspectingReceiptItem = flight },
                            onDelete = { onDeleteItem(flight) },
                            onShare = { onShareItem(flight) }
                        )
                    }
                }
            }
        }

        // SECTION 3: Google Maps Import & Navigation for Listed Travel Locations
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Place,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Places & Google Maps Navigation",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Button(
                    onClick = { showAddPlaceDialog = true },
                    modifier = Modifier.testTag("add_place_button"),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("+ Add Place", style = MaterialTheme.typography.labelSmall)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (places.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Add hotels, parks, and attractions. They can be imported directly into Google Maps with one tap.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    places.forEach { place ->
                        PlaceLocationCard(
                            place = place,
                            onOpenMaps = {
                                GoogleMapsHelper.openInGoogleMaps(
                                    context = context,
                                    query = place.placeName,
                                    address = place.placeAddress
                                )
                            },
                            onInspectPhoto = { inspectingReceiptItem = place },
                            onDelete = { onDeleteItem(place) },
                            onShare = { onShareItem(place) }
                        )
                    }
                }
            }
        }

        // SECTION 4: Shopping Tools & Retail Deal Checker (Amazon, Walmart, Costco, Sam's Club, Target)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.ShoppingCart,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Smart Retail & Supplies Checker",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "Compare travel essentials across major retailers with direct deep-links:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    val sampleItems = listOf(
                        "Sunscreen SPF 50",
                        "Packing Cubes",
                        "Travel Snacks in Bulk",
                        "Noise Canceling Headphones",
                        "Universal Power Adapter",
                        "First Aid Kit"
                    )

                    sampleItems.forEach { itemQuery ->
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Text(
                                text = itemQuery,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(Retailer.entries) { retailer ->
                                    RetailTagBadge(
                                        retailer = retailer,
                                        onClick = { ShoppingDeepLinks.openRetailer(context, retailer, itemQuery) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal to add flight
    if (showAddFlightDialog) {
        var flightNum by remember { mutableStateOf("") }
        var departure by remember { mutableStateOf("") }
        var arrival by remember { mutableStateOf("") }
        var costText by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddFlightDialog = false },
            title = { Text("Track Family Flight", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = flightNum,
                        onValueChange = { flightNum = it },
                        label = { Text("Flight Number (e.g. UA 248)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = departure,
                        onValueChange = { departure = it },
                        label = { Text("Departure Airport / City") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = arrival,
                        onValueChange = { arrival = it },
                        label = { Text("Arrival Airport / City") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = costText,
                        onValueChange = { if (it.isEmpty() || it.matches(Regex("^\\d*\\.?\\d{0,2}$"))) costText = it },
                        label = { Text("Estimated Ticket Cost ($, optional)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (flightNum.isNotBlank()) {
                            onAddFlight(flightNum.trim(), departure.trim(), arrival.trim(), costText.toDoubleOrNull())
                            showAddFlightDialog = false
                        }
                    }
                ) {
                    Text("Start Tracking")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showAddFlightDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Modal to add place
    if (showAddPlaceDialog) {
        var placeName by remember { mutableStateOf("") }
        var placeAddress by remember { mutableStateOf("") }
        var costText by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddPlaceDialog = false },
            title = { Text("Add Travel Location", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = placeName,
                        onValueChange = { placeName = it },
                        label = { Text("Place or Hotel Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = placeAddress,
                        onValueChange = { placeAddress = it },
                        label = { Text("Address or City (optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = costText,
                        onValueChange = { if (it.isEmpty() || it.matches(Regex("^\\d*\\.?\\d{0,2}$"))) costText = it },
                        label = { Text("Estimated Booking Cost ($, optional)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (placeName.isNotBlank()) {
                            onAddPlace(placeName.trim(), placeAddress.trim(), costText.toDoubleOrNull())
                            showAddPlaceDialog = false
                        }
                    }
                ) {
                    Text("Save Place")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showAddPlaceDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Modal to log trip expense + encrypted receipt photo
    if (showAddExpenseDialog) {
        var expenseTitle by remember { mutableStateOf("") }
        var expenseCost by remember { mutableStateOf("") }
        var encryptedReceipt by remember { mutableStateOf("") }
        val receiptPicker = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickVisualMedia()
        ) { uri ->
            if (uri != null) {
                scope.launch {
                    val cipher = withContext(Dispatchers.IO) {
                        ImageAttachmentHelper.compressAndEncryptUri(context, uri, familyPasskey)
                    }
                    if (cipher != null) {
                        encryptedReceipt = cipher
                        Toast.makeText(context, "Receipt encrypted!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        AlertDialog(
            onDismissRequest = { showAddExpenseDialog = false },
            title = { Text("Log Trip Expense", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = expenseTitle,
                        onValueChange = { expenseTitle = it },
                        label = { Text("Expense (e.g. Rental Car, Hotel Deposit)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = expenseCost,
                        onValueChange = { if (it.isEmpty() || it.matches(Regex("^\\d*\\.?\\d{0,2}$"))) expenseCost = it },
                        label = { Text("Amount ($)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedButton(
                        onClick = {
                            receiptPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (encryptedReceipt.isBlank()) "Attach Receipt Photo" else "Receipt Attached")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val costVal = expenseCost.toDoubleOrNull() ?: 0.0
                        if (expenseTitle.isNotBlank() && costVal >= 0.0) {
                            onAddTripExpense(expenseTitle.trim(), costVal, encryptedReceipt)
                            showAddExpenseDialog = false
                        }
                    }
                ) {
                    Text("Save Expense")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showAddExpenseDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Encrypted receipt viewer
    if (inspectingReceiptItem != null) {
        val target = inspectingReceiptItem!!
        val receiptBitmap by produceState<ImageBitmap?>(
            initialValue = null,
            target.attachmentEncryptedBase64,
            familyPasskey
        ) {
            value = withContext(Dispatchers.Default) {
                ImageAttachmentHelper.decryptToImageBitmap(target.attachmentEncryptedBase64, familyPasskey)
            }
        }
        AlertDialog(
            onDismissRequest = { inspectingReceiptItem = null },
            title = { Text(target.title.ifBlank { "Receipt" }, fontWeight = FontWeight.Bold) },
            text = {
                if (receiptBitmap != null) {
                    Image(
                        bitmap = receiptBitmap!!,
                        contentDescription = "Encrypted receipt",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                } else {
                    Text("Unable to decrypt photo with current passkey.")
                }
            },
            confirmButton = {
                Button(onClick = { inspectingReceiptItem = null }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun FlightCard(
    flight: FamilyItem,
    onStatusChange: (String) -> Unit,
    onOpenLiveStatus: () -> Unit,
    onInspectPhoto: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit
) {
    var statusMenuExpanded by remember { mutableStateOf(false) }
    val statusOptions = listOf("On Time", "Boarding", "In Air", "Delayed", "Landed")
    val currentStatus = flight.flightStatus.ifBlank { "On Time" }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Flight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Flight ${flight.flightNumber.ifBlank { flight.title }}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (flight.estimatedCost != null && flight.estimatedCost > 0.0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = String.format(Locale.US, "$%.0f", flight.estimatedCost),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Box {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = when (currentStatus) {
                            "Delayed" -> Color(0xFFFEE2E2)
                            "Boarding", "In Air" -> Color(0xFFFEF3C7)
                            else -> Color(0xFFDCFCE7)
                        },
                        modifier = Modifier.clickable { statusMenuExpanded = true }
                    ) {
                        Text(
                            text = "$currentStatus ▾",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = when (currentStatus) {
                                "Delayed" -> Color(0xFF991B1B)
                                "Boarding", "In Air" -> Color(0xFF92400E)
                                else -> Color(0xFF166534)
                            },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = statusMenuExpanded,
                        onDismissRequest = { statusMenuExpanded = false }
                    ) {
                        statusOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    statusMenuExpanded = false
                                    onStatusChange(option)
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Departure", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(flight.flightDeparture.ifBlank { "Departing" }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
                Text("✈", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text("Arrival", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(flight.flightArrival.ifBlank { "Destination" }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
            }

            if (flight.content.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = flight.content,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = onOpenLiveStatus,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(imageVector = Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Live Flight Status", style = MaterialTheme.typography.labelSmall)
                    }
                    if (flight.attachmentEncryptedBase64.isNotBlank()) {
                        OutlinedButton(
                            onClick = onInspectPhoto,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Receipt, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Pass", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onShare, modifier = Modifier.size(40.dp)) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = "Share flight", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete flight", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaceLocationCard(
    place: FamilyItem,
    onOpenMaps: () -> Unit,
    onInspectPhoto: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Place,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = place.placeName.ifBlank { place.title },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (place.estimatedCost != null && place.estimatedCost > 0.0) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = String.format(Locale.US, "$%.0f", place.estimatedCost),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                if (place.placeAddress.isNotBlank()) {
                    Text(
                        text = place.placeAddress,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFFE8F0FE))
                            .clickable { onOpenMaps() }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Directions,
                            contentDescription = "Navigate in Google Maps",
                            tint = Color(0xFF1A73E8),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Open in Google Maps",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1A73E8)
                        )
                    }

                    if (place.attachmentEncryptedBase64.isNotBlank()) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .clickable { onInspectPhoto() }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Receipt,
                                contentDescription = "View attached photo",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Photo",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onShare, modifier = Modifier.size(40.dp)) {
                    Icon(imageVector = Icons.Default.Share, contentDescription = "Share place", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete place", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun RetailTagBadge(
    retailer: Retailer,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(retailer.badgeColorHex).copy(alpha = 0.12f),
        modifier = Modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = retailer.displayName,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color(retailer.badgeColorHex)
            )
            Spacer(modifier = Modifier.width(2.dp))
            Text("↗", style = MaterialTheme.typography.labelSmall, color = Color(retailer.badgeColorHex))
        }
    }
}
