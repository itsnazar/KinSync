package com.example.data.remote

import android.content.Context
import android.content.Intent
import android.util.Base64
import com.example.BuildConfig
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

@JsonClass(generateAdapter = true)
data class GmailMessageListResponse(
    val messages: List<GmailMessageRef>? = null
)

@JsonClass(generateAdapter = true)
data class GmailMessageRef(
    val id: String,
    val threadId: String? = null
)

@JsonClass(generateAdapter = true)
data class GmailMessageDetail(
    val id: String,
    val snippet: String? = null,
    val payload: GmailPayload? = null
)

@JsonClass(generateAdapter = true)
data class GmailPayload(
    val mimeType: String? = null,
    val headers: List<GmailHeader>? = null,
    val body: GmailBody? = null,
    val parts: List<GmailPayload>? = null
)

@JsonClass(generateAdapter = true)
data class GmailHeader(
    val name: String,
    val value: String
)

@JsonClass(generateAdapter = true)
data class GmailBody(
    val data: String? = null
)

data class GmailEmailSummary(
    val id: String,
    val subject: String,
    val from: String,
    val date: String,
    val snippet: String,
    val bodyText: String
)

interface GmailApiService {
    @GET("gmail/v1/users/me/messages")
    suspend fun listMessages(
        @Header("Authorization") authHeader: String,
        @Query("q") query: String,
        @Query("maxResults") maxResults: Int = 8,
        @Query("fields") fields: String = "messages(id)"
    ): GmailMessageListResponse

    @GET("gmail/v1/users/me/messages/{id}")
    suspend fun getMessage(
        @Header("Authorization") authHeader: String,
        @Path("id") messageId: String,
        @Query("format") format: String = "full",
        @Query("fields") fields: String = "id,snippet,payload(mimeType,headers,body,parts)"
    ): GmailMessageDetail
}

object GmailBookingFetcher {
    const val GMAIL_READONLY_SCOPE = "https://www.googleapis.com/auth/gmail.readonly"

    private val moshi: Moshi by lazy {
        Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
    }

    private val okHttpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
        }
        OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()
    }

    private val api: GmailApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://gmail.googleapis.com/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GmailApiService::class.java)
    }

    /**
     * Requests in-app user permission to read Gmail travel confirmations using
     * Google Identity Services AuthorizationClient.
     */
    suspend fun authorizeGmailReadOnly(context: Context): Result<AuthorizationResult> {
        return try {
            val request = AuthorizationRequest.builder()
                .setRequestedScopes(listOf(Scope(GMAIL_READONLY_SCOPE)))
                .build()
            val result = Identity.getAuthorizationClient(context)
                .authorize(request)
                .await()
            Result.success(result)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getAuthorizationResultFromIntent(context: Context, intent: Intent?): AuthorizationResult? {
        return if (intent != null) {
            Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(intent)
        } else {
            null
        }
    }

    suspend fun fetchTravelEmails(accessToken: String): Result<List<GmailEmailSummary>> {
        return try {
            val authHeader = "Bearer $accessToken"
            val query = "(flight OR itinerary OR airline OR hotel OR reservation OR booking) newer_than:120d"
            val listResp = api.listMessages(authHeader = authHeader, query = query, maxResults = 8)
            val refs = listResp.messages.orEmpty()
            if (refs.isEmpty()) {
                return Result.success(emptyList())
            }

            val summaries = refs.mapNotNull { ref ->
                try {
                    val detail = api.getMessage(authHeader = authHeader, messageId = ref.id)
                    val headers = detail.payload?.headers.orEmpty()
                    val subject = headers.firstOrNull { it.name.equals("Subject", ignoreCase = true) }?.value
                        ?: "Travel Confirmation"
                    val from = headers.firstOrNull { it.name.equals("From", ignoreCase = true) }?.value.orEmpty()
                    val date = headers.firstOrNull { it.name.equals("Date", ignoreCase = true) }?.value.orEmpty()
                    val plainBody = extractPlainText(detail.payload).ifBlank { detail.snippet.orEmpty() }
                    GmailEmailSummary(
                        id = detail.id,
                        subject = subject,
                        from = from,
                        date = date,
                        snippet = detail.snippet.orEmpty(),
                        bodyText = plainBody.take(6000)
                    )
                } catch (_: Exception) {
                    null
                }
            }
            Result.success(summaries)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun extractPlainText(payload: GmailPayload?): String {
        if (payload == null) return ""
        val mime = payload.mimeType.orEmpty()
        if (mime.equals("text/plain", ignoreCase = true)) {
            val encoded = payload.body?.data
            if (!encoded.isNullOrBlank()) {
                return decodeBase64Url(encoded)
            }
        }
        payload.parts?.forEach { part ->
            val nested = extractPlainText(part)
            if (nested.isNotBlank()) return nested
        }
        // Fallback to text/html stripped of tags if no text/plain part was found
        if (mime.equals("text/html", ignoreCase = true)) {
            val encoded = payload.body?.data
            if (!encoded.isNullOrBlank()) {
                return decodeBase64Url(encoded)
                    .replace(Regex("<style[^>]*>[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), " ")
                    .replace(Regex("<[^>]+>"), " ")
                    .replace(Regex("\\s+"), " ")
                    .trim()
            }
        }
        return ""
    }

    private fun decodeBase64Url(data: String): String {
        return try {
            val bytes = Base64.decode(data, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
            String(bytes, Charsets.UTF_8)
        } catch (_: Exception) {
            ""
        }
    }
}
