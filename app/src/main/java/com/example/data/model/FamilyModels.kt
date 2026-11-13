package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

enum class ItemType {
    NOTE,
    REMINDER,
    EVENT,
    FLIGHT,
    PLACE
}

enum class ItemCategory {
    GENERAL,
    SCHOOL,
    CHORES,
    MEDICAL,
    TRAVEL,
    CELEBRATION,
    HOME
}

enum class RecurrenceRule(val label: String) {
    NONE("Once"),
    DAILY("Daily"),
    WEEKLY("Weekly"),
    MONTHLY("Monthly")
}

@IgnoreExtraProperties
data class UserProfile(
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val activeFamilyId: String = "",
    val fcmToken: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class Family(
    @DocumentId val id: String = "",
    val name: String = "",
    val creatorId: String = "",
    val joinCode: String = "",
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FamilyItem(
    @DocumentId val id: String = "",
    val familyId: String = "",
    val memberUids: List<String> = emptyList(),
    val authorId: String = "",
    val authorName: String = "",
    val itemType: String = ItemType.NOTE.name,
    val category: String = ItemCategory.GENERAL.name,
    @get:PropertyName("isEncrypted")
    @field:PropertyName("isEncrypted")
    val isEncrypted: Boolean = true,
    val encryptedPayload: String = "",
    val title: String = "",
    val content: String = "",
    val eventDateMillis: Long? = null,
    @get:PropertyName("isCompleted")
    @field:PropertyName("isCompleted")
    val isCompleted: Boolean = false,
    val recurrenceRule: String = RecurrenceRule.NONE.name,
    val estimatedCost: Double? = null,
    val attachmentEncryptedBase64: String = "",
    val flightNumber: String = "",
    val flightDeparture: String = "",
    val flightArrival: String = "",
    val flightStatus: String = "On Time",
    val placeName: String = "",
    val placeAddress: String = "",
    val placeGeoQuery: String = "",
    val shareToken: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ShareLink(
    val token: String = "",
    val title: String = "",
    val summary: String = "",
    val itemType: String = ItemType.NOTE.name,
    val createdById: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)
