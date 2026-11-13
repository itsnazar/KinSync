package com.example.ui.dialogs

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ai.ExtractedBooking
import com.example.ai.ExtractedFlight
import com.example.ai.ExtractedPlace
import com.example.ai.GeminiFamilyAdvisor
import com.example.data.remote.GmailBookingFetcher
import com.example.data.remote.GmailEmailSummary
import kotlinx.coroutines.launch

@Composable
fun GmailBookingImportDialog(
    initialEmailText: String = "",
    aiAdvisor: GeminiFamilyAdvisor,
    onImportBookings: (flights: List<ExtractedFlight>, places: List<ExtractedPlace>) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var rawEmailText by remember { mutableStateOf(initialEmailText) }
    var isLoadingGmail by remember { mutableStateOf(false) }
    var isExtracting by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var gmailEmails by remember { mutableStateOf<List<GmailEmailSummary>>(emptyList()) }
    var extractedBooking by remember { mutableStateOf<ExtractedBooking?>(null) }

    val selectedFlights = remember { mutableStateMapOf<Int, Boolean>() }
    val selectedPlaces = remember { mutableStateMapOf<Int, Boolean>() }

    fun runExtraction(text: String) {
        if (text.isBlank()) {
            errorMessage = "Paste a flight or hotel confirmation email, or connect Gmail above."
            return
        }
        scope.launch {
            isExtracting = true
            errorMessage = null
            statusMessage = "Extracting flights & travel places with Gemini..."
            val res = aiAdvisor.extractBookingsFromText(text)
            isExtracting = false
            if (res.isSuccess) {
                val booking = res.getOrThrow()
                extractedBooking = booking
                selectedFlights.clear()
                selectedPlaces.clear()
                booking.flights.indices.forEach { selectedFlights[it] = true }
                booking.places.indices.forEach { selectedPlaces[it] = true }
                statusMessage = if (booking.flights.isEmpty() && booking.places.isEmpty()) {
                    "No flights or hotels detected in that text."
                } else {
                    "Found ${booking.flights.size} flight(s) and ${booking.places.size} place(s)."
                }
            } else {
                statusMessage = null
                errorMessage = res.exceptionOrNull()?.localizedMessage ?: "Failed to extract bookings."
            }
        }
    }

    // Automatically parse if launched with shared email text via ACTION_SEND
    LaunchedEffect(initialEmailText) {
        if (initialEmailText.isNotBlank() && extractedBooking == null) {
            runExtraction(initialEmailText)
        }
    }

    fun loadEmailsWithToken(accessToken: String) {
        scope.launch {
            isLoadingGmail = true
            errorMessage = null
            statusMessage = "Scanning recent travel confirmations in Gmail..."
            val result = GmailBookingFetcher.fetchTravelEmails(accessToken)
            isLoadingGmail = false
            if (result.isSuccess) {
                val list = result.getOrThrow()
                gmailEmails = list
                statusMessage = if (list.isEmpty()) {
                    "No recent flight or hotel confirmation emails found in Gmail."
                } else {
                    "Found ${list.size} travel email(s). Tap one to extract bookings:"
                }
            } else {
                statusMessage = null
                errorMessage = result.exceptionOrNull()?.localizedMessage
                    ?: "Could not fetch Gmail messages. Check OAuth/Play Services configuration or paste email text below."
            }
        }
    }

    val gmailPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { activityResult ->
        if (activityResult.resultCode == Activity.RESULT_OK) {
            val authResult = GmailBookingFetcher.getAuthorizationResultFromIntent(context, activityResult.data)
            val token = authResult?.accessToken
            if (!token.isNullOrBlank()) {
                loadEmailsWithToken(token)
            } else {
                errorMessage = "Gmail access was not granted. You can still paste or share a confirmation email below."
            }
        } else {
            errorMessage = "Gmail permission cancelled. You can paste or share an email below."
        }
    }

    fun requestInAppGmailPermission() {
        scope.launch {
            isLoadingGmail = true
            errorMessage = null
            statusMessage = "Requesting in-app Gmail read-only permission..."
            val authRes = GmailBookingFetcher.authorizeGmailReadOnly(context)
            isLoadingGmail = false
            if (authRes.isSuccess) {
                val result = authRes.getOrThrow()
                if (result.hasResolution()) {
                    val pendingIntent = result.pendingIntent
                    if (pendingIntent != null) {
                        gmailPermissionLauncher.launch(
                            IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                        )
                    } else {
                        errorMessage = "Unable to launch Gmail consent dialog."
                    }
                } else {
                    val token = result.accessToken
                    if (!token.isNullOrBlank()) {
                        loadEmailsWithToken(token)
                    } else {
                        errorMessage = "No Gmail access token returned."
                    }
                }
            } else {
                statusMessage = null
                errorMessage = authRes.exceptionOrNull()?.localizedMessage
                    ?: "In-app Gmail authorization unavailable. Paste or share your email confirmation below."
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("gmail_import_dialog"),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Email,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Import Travel Bookings", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Grant read-only Gmail access inside the app to scan flight & hotel confirmations, or paste any booking email.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Button(
                    onClick = { requestInAppGmailPermission() },
                    enabled = !isLoadingGmail && !isExtracting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("connect_gmail_inapp_button"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (isLoadingGmail) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Email,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Connect Gmail (In-App Permission)")
                }

                if (gmailEmails.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        gmailEmails.take(5).forEach { email ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val combined = "Subject: ${email.subject}\nFrom: ${email.from}\nDate: ${email.date}\n\n${email.bodyText}"
                                        rawEmailText = combined
                                        runExtraction(combined)
                                    },
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                )
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = email.subject,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = email.from,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = email.snippet,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = rawEmailText,
                    onValueChange = { rawEmailText = it },
                    label = { Text("Confirmation Email Text") },
                    placeholder = { Text("Paste flight/hotel confirmation text or share from Gmail...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 100.dp, max = 160.dp)
                        .testTag("booking_email_text_input"),
                    maxLines = 6,
                    shape = RoundedCornerShape(10.dp)
                )

                OutlinedButton(
                    onClick = { runExtraction(rawEmailText) },
                    enabled = rawEmailText.isNotBlank() && !isExtracting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("extract_bookings_button"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (isExtracting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isExtracting) "Analyzing..." else "Extract Bookings with Gemini")
                }

                if (statusMessage != null) {
                    Text(
                        text = statusMessage!!,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (errorMessage != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = errorMessage!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                // Preview extracted flights & places
                extractedBooking?.let { booking ->
                    if (booking.flights.isNotEmpty()) {
                        Text(
                            text = "Flights Detected",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        booking.flights.forEachIndexed { idx, flight ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedFlights[idx] = !(selectedFlights[idx] ?: true)
                                    }
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = selectedFlights[idx] ?: true,
                                    onCheckedChange = { selectedFlights[idx] = it }
                                )
                                Icon(
                                    imageVector = Icons.Default.Flight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "${flight.flightNumber} (${flight.departure} → ${flight.arrival})",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (flight.summary.isNotBlank()) {
                                        Text(
                                            text = flight.summary,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (booking.places.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Hotels & Places Detected",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        booking.places.forEachIndexed { idx, place ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedPlaces[idx] = !(selectedPlaces[idx] ?: true)
                                    }
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = selectedPlaces[idx] ?: true,
                                    onCheckedChange = { selectedPlaces[idx] = it }
                                )
                                Icon(
                                    imageVector = Icons.Default.Place,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = place.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (place.address.isNotBlank()) {
                                        Text(
                                            text = place.address,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            val booking = extractedBooking
            val hasSelection = booking != null && (
                booking.flights.indices.any { selectedFlights[it] == true } ||
                    booking.places.indices.any { selectedPlaces[it] == true }
                )
            Button(
                onClick = {
                    if (booking != null) {
                        val flightsToImport = booking.flights.filterIndexed { i, _ -> selectedFlights[i] == true }
                        val placesToImport = booking.places.filterIndexed { i, _ -> selectedPlaces[i] == true }
                        onImportBookings(flightsToImport, placesToImport)
                    }
                },
                enabled = hasSelection,
                modifier = Modifier.testTag("confirm_import_bookings_button")
            ) {
                Text("Import Selected")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
