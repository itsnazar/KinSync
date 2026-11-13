package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.example.security.SecurePasskeyStore
import com.example.ui.MainAppContent
import com.example.ui.auth.AuthScreen
import com.example.ui.dialogs.SharedItemViewerDialog
import com.example.ui.theme.PodAllyTheme
import com.example.util.BiometricLockHelper
import com.example.util.configureAppCheck
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth

class MainActivity : FragmentActivity() {

    private var sharedEmailText by mutableStateOf<String?>(null)
    private var sharedLinkToken by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // MANDATED: Configure Firebase App Check with launch Intent before UI or Firebase calls
        configureAppCheck(this, intent)
        extractIncomingIntents(intent)

        enableEdgeToEdge()

        setContent {
            PodAllyTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppRoot(
                        sharedBookingText = sharedEmailText,
                        onConsumeSharedBookingText = { sharedEmailText = null },
                        sharedLinkToken = sharedLinkToken,
                        onConsumeSharedLinkToken = { sharedLinkToken = null }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractIncomingIntents(intent)
    }

    private fun extractIncomingIntents(intent: Intent?) {
        if (intent == null) return
        if (intent.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT).orEmpty()
            val text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
            val combined = listOf(subject, text).filter { it.isNotBlank() }.joinToString("\n\n")
            if (combined.isNotBlank()) {
                sharedEmailText = combined
            }
        } else if (intent.action == Intent.ACTION_VIEW) {
            val dataUri = intent.data ?: return
            val pathSegments = dataUri.pathSegments
            val token = when {
                dataUri.scheme == "podally" && dataUri.host == "share" -> pathSegments.firstOrNull()
                dataUri.host == "podally.family" && pathSegments.firstOrNull() == "share" -> pathSegments.getOrNull(1)
                else -> null
            }
            if (!token.isNullOrBlank()) {
                sharedLinkToken = token
            }
        }
    }
}

@Composable
fun AppRoot(
    sharedBookingText: String? = null,
    onConsumeSharedBookingText: () -> Unit = {},
    sharedLinkToken: String? = null,
    onConsumeSharedLinkToken: () -> Unit = {}
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }

    val biometricRequired = remember(currentUser?.uid) {
        SecurePasskeyStore.isBiometricLockEnabled(context) &&
            activity != null &&
            BiometricLockHelper.isAvailable(activity)
    }
    var isBiometricUnlocked by remember(currentUser?.uid) { mutableStateOf(!biometricRequired) }
    var biometricError by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose {
            Firebase.auth.removeAuthStateListener(listener)
        }
    }

    LaunchedEffect(currentUser?.uid, biometricRequired) {
        if (currentUser != null && biometricRequired && !isBiometricUnlocked && activity != null) {
            BiometricLockHelper.authenticate(
                activity = activity,
                onSuccess = {
                    biometricError = null
                    isBiometricUnlocked = true
                },
                onError = { msg ->
                    biometricError = msg
                }
            )
        }
    }

    if (currentUser == null) {
        AuthScreen(
            onAuthSuccess = {
                currentUser = Firebase.auth.currentUser
            }
        )
    } else if (!isBiometricUnlocked && activity != null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "PodAlly Locked",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(20.dp)
                            .size(40.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "PodAlly is Locked",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = biometricError ?: "Authenticate with biometrics or device PIN to view your family hub.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = {
                        biometricError = null
                        BiometricLockHelper.authenticate(
                            activity = activity,
                            onSuccess = { isBiometricUnlocked = true },
                            onError = { msg -> biometricError = msg }
                        )
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Fingerprint,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Unlock PodAlly")
                }
            }
        }
    } else {
        val uid = currentUser!!.uid
        // Key by uid so switching accounts completely resets UI and scoped listeners
        key(uid) {
            MainAppContent(
                currentUserId = uid,
                sharedBookingText = sharedBookingText,
                onConsumeSharedBookingText = onConsumeSharedBookingText
            )
        }
    }

    if (!sharedLinkToken.isNullOrBlank()) {
        SharedItemViewerDialog(
            token = sharedLinkToken,
            firestoreDatabaseId = stringResource(R.string.firestore_database_id),
            onDismiss = onConsumeSharedLinkToken
        )
    }
}
