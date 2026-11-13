package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.FamilyItem
import com.example.data.model.RecurrenceRule

@Entity(tableName = "cached_family_items")
data class FamilyItemEntity(
    @PrimaryKey val id: String,
    val familyId: String,
    val memberUidsCsv: String,
    val authorId: String,
    val authorName: String,
    val itemType: String,
    val category: String,
    val isEncrypted: Boolean,
    val encryptedPayload: String,
    val title: String,
    val content: String,
    val eventDateMillis: Long?,
    val isCompleted: Boolean,
    val recurrenceRule: String = RecurrenceRule.NONE.name,
    val estimatedCost: Double? = null,
    val attachmentEncryptedBase64: String = "",
    val flightNumber: String,
    val flightDeparture: String,
    val flightArrival: String,
    val flightStatus: String,
    val placeName: String,
    val placeAddress: String,
    val placeGeoQuery: String,
    val shareToken: String,
    val pendingSyncAction: String? = null, // null = synced, "CREATE", "UPDATE", "DELETE"
    val updatedAtMillis: Long = System.currentTimeMillis()
) {
    fun toFamilyItem(): FamilyItem {
        return FamilyItem(
            id = id,
            familyId = familyId,
            memberUids = memberUidsCsv.split(",").filter { it.isNotBlank() },
            authorId = authorId,
            authorName = authorName,
            itemType = itemType,
            category = category,
            isEncrypted = isEncrypted,
            encryptedPayload = encryptedPayload,
            title = title,
            content = content,
            eventDateMillis = eventDateMillis,
            isCompleted = isCompleted,
            recurrenceRule = recurrenceRule,
            estimatedCost = estimatedCost,
            attachmentEncryptedBase64 = attachmentEncryptedBase64,
            flightNumber = flightNumber,
            flightDeparture = flightDeparture,
            flightArrival = flightArrival,
            flightStatus = flightStatus,
            placeName = placeName,
            placeAddress = placeAddress,
            placeGeoQuery = placeGeoQuery,
            shareToken = shareToken
        )
    }

    companion object {
        const val SYNC_CREATE = "CREATE"
        const val SYNC_UPDATE = "UPDATE"
        const val SYNC_DELETE = "DELETE"

        fun fromFamilyItem(
            item: FamilyItem,
            pendingSyncAction: String? = null
        ): FamilyItemEntity {
            return FamilyItemEntity(
                id = item.id,
                familyId = item.familyId,
                memberUidsCsv = item.memberUids.joinToString(","),
                authorId = item.authorId,
                authorName = item.authorName,
                itemType = item.itemType,
                category = item.category,
                isEncrypted = item.isEncrypted,
                encryptedPayload = item.encryptedPayload,
                title = item.title,
                content = item.content,
                eventDateMillis = item.eventDateMillis,
                isCompleted = item.isCompleted,
                recurrenceRule = item.recurrenceRule,
                estimatedCost = item.estimatedCost,
                attachmentEncryptedBase64 = item.attachmentEncryptedBase64,
                flightNumber = item.flightNumber,
                flightDeparture = item.flightDeparture,
                flightArrival = item.flightArrival,
                flightStatus = item.flightStatus,
                placeName = item.placeName,
                placeAddress = item.placeAddress,
                placeGeoQuery = item.placeGeoQuery,
                shareToken = item.shareToken,
                pendingSyncAction = pendingSyncAction,
                updatedAtMillis = item.updatedAt?.toDate()?.time ?: System.currentTimeMillis()
            )
        }
    }
}
