package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.R
import com.example.data.local.FamilyItemDao
import com.example.data.local.FamilyItemEntity
import com.example.data.local.KinSyncDatabase
import com.example.data.model.Family
import com.example.data.model.FamilyItem
import com.example.data.model.ItemType
import com.example.data.model.RecurrenceRule
import com.example.data.model.ShareLink
import com.example.data.model.UserProfile
import com.example.security.CryptoManager
import com.example.widget.PodAllyGlanceWidget
import com.example.worker.SyncPendingWorker
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import java.util.UUID

class FamilyRepository(
    private val db: FirebaseFirestore,
    private val itemDao: FamilyItemDao? = null,
    private val appContext: Context? = null
) {

    constructor(context: Context) : this(
        db = createMemoryCachedFirestore(context.applicationContext),
        itemDao = KinSyncDatabase.getInstance(context).familyItemDao(),
        appContext = context.applicationContext
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    // --- User Profile ---

    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        val path = "users/$userId"
        val docRef = db.collection("users").document(userId)
        docRef.snapshots().map { snapshot ->
            if (snapshot.exists()) snapshot.toObject(UserProfile::class.java) else null
        }.catch { error ->
            if (error is CancellationException) throw error
            handleFirestoreError(error, OperationType.GET, path)
            throw error
        }.collect { emit(it) }
    }

    suspend fun saveUserProfile(profile: UserProfile): Result<Unit> {
        val uid = requireUserId()
        val path = "users/$uid"
        val docRef = db.collection("users").document(uid)
        return try {
            val payload = mutableMapOf<String, Any>(
                "userId" to uid,
                "displayName" to profile.displayName,
                "email" to profile.email,
                "activeFamilyId" to profile.activeFamilyId,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (profile.fcmToken.isNotBlank()) {
                payload["fcmToken"] = profile.fcmToken
            }
            val existing = docRef.get().await()
            if (!existing.exists()) {
                payload["createdAt"] = FieldValue.serverTimestamp()
            }
            docRef.set(payload, SetOptions.merge()).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun updateFcmToken(token: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users/$uid"
        return try {
            db.collection("users").document(uid)
                .set(
                    mapOf(
                        "fcmToken" to token,
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                )
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- Family Management ---

    fun observeUserFamilies(userId: String): Flow<List<Family>> = flow {
        val path = "families"
        db.collection("families")
            .whereArrayContains("memberUids", userId)
            .snapshots()
            .map { snapshot -> snapshot.toObjects(Family::class.java) }
            .catch { error ->
                if (error is CancellationException) throw error
                handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
            .collect { emit(it) }
    }

    suspend fun createFamily(name: String, joinCode: String): Result<Family> {
        val uid = requireUserId()
        val docRef = db.collection("families").document()
        val familyId = docRef.id
        val payload = mapOf(
            "name" to name.trim(),
            "creatorId" to uid,
            "joinCode" to joinCode.trim().uppercase(),
            "memberUids" to listOf(uid),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            val created = Family(
                id = familyId,
                name = name.trim(),
                creatorId = uid,
                joinCode = joinCode.trim().uppercase(),
                memberUids = listOf(uid)
            )
            Result.success(created)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "families/$familyId")
            Result.failure(e)
        }
    }

    suspend fun joinFamilyByCode(joinCode: String, familyId: String): Result<Unit> {
        val uid = requireUserId()
        val path = "families/$familyId"
        val docRef = db.collection("families").document(familyId)
        return try {
            val snapshot = docRef.get().await()
            if (!snapshot.exists()) {
                return Result.failure(IllegalArgumentException("Family workspace not found."))
            }
            val existingCode = snapshot.getString("joinCode") ?: ""
            if (!existingCode.equals(joinCode.trim(), ignoreCase = true)) {
                return Result.failure(IllegalArgumentException("Invalid family join code."))
            }
            docRef.update(
                "memberUids", FieldValue.arrayUnion(uid),
                "updatedAt", FieldValue.serverTimestamp()
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- Offline-First Room Cache + Firestore Real-Time Sync ---

    fun observePendingSyncCount(familyId: String): Flow<Int> {
        if (familyId.isBlank() || itemDao == null) return flowOf(0)
        return itemDao.observePendingCount(familyId)
    }

    fun observeFamilyItems(familyId: String): Flow<List<FamilyItem>> = flow {
        if (familyId.isBlank()) {
            emit(emptyList())
            return@flow
        }
        val uid = requireUserId()
        val path = "families/$familyId/items"

        if (itemDao == null) {
            // Direct Firestore stream (used in rule tests or without Room)
            emitAll(
                db.collection("families").document(familyId).collection("items")
                    .whereArrayContains("memberUids", uid)
                    .limit(200)
                    .snapshots()
                    .map { snapshot -> snapshot.toObjects(FamilyItem::class.java) }
                    .catch { error ->
                        if (error is CancellationException) throw error
                        handleFirestoreError(error, OperationType.LIST, path)
                        throw error
                    }
            )
            return@flow
        }

        // Offline-first Room cache backed by bounded real-time Firestore listener
        coroutineScope {
            launch {
                try {
                    db.collection("families").document(familyId).collection("items")
                        .whereArrayContains("memberUids", uid)
                        .limit(200)
                        .snapshots()
                        .catch { error ->
                            if (error is CancellationException) throw error
                            Log.w("FamilyRepository", "Firestore stream paused/offline: ${error.message}")
                            handleFirestoreError(error, OperationType.LIST, path)
                        }
                        .collect { snapshot ->
                            val remoteItems = snapshot.toObjects(FamilyItem::class.java)
                            val pendingIds = itemDao.getPendingItemIds(familyId).toSet()
                            val toCache = remoteItems
                                .filter { it.id !in pendingIds }
                                .map { FamilyItemEntity.fromFamilyItem(it, pendingSyncAction = null) }

                            if (toCache.isNotEmpty()) {
                                itemDao.upsertAll(toCache)
                            }
                            val activeRemoteIds = remoteItems.map { it.id }
                            if (activeRemoteIds.isEmpty()) {
                                itemDao.clearSyncedItemsForFamily(familyId)
                            } else {
                                itemDao.deleteStaleSyncedItems(familyId, activeRemoteIds)
                            }
                            appContext?.let { PodAllyGlanceWidget.requestUpdate(it) }
                        }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w("FamilyRepository", "Offline mode active; serving Room cache: ${e.message}")
                }
            }

            emitAll(
                itemDao.observeItemsForFamily(familyId)
                    .map { entities -> entities.map { it.toFamilyItem() } }
            )
        }
    }

    suspend fun addFamilyItem(
        familyId: String,
        item: FamilyItem,
        isOnline: Boolean = true
    ): Result<String> {
        val uid = requireUserId()
        val docRef = if (item.id.isNotBlank()) {
            db.collection("families").document(familyId).collection("items").document(item.id)
        } else {
            db.collection("families").document(familyId).collection("items").document()
        }
        val itemId = docRef.id
        val normalizedItem = item.copy(
            id = itemId,
            familyId = familyId,
            memberUids = item.memberUids.ifEmpty { listOf(uid) },
            authorId = uid,
            authorName = item.authorName.ifEmpty { "Family Member" }
        )

        itemDao?.upsert(
            FamilyItemEntity.fromFamilyItem(
                normalizedItem,
                pendingSyncAction = if (isOnline) null else FamilyItemEntity.SYNC_CREATE
            )
        )
        appContext?.let { PodAllyGlanceWidget.requestUpdate(it) }

        if (!isOnline && itemDao != null) {
            appContext?.let { SyncPendingWorker.scheduleSync(it, familyId) }
            return Result.success(itemId)
        }

        val path = docRef.path
        val payload = buildCreatePayload(normalizedItem, uid, familyId)

        return try {
            docRef.set(payload).await()
            if (normalizedItem.itemType == ItemType.NOTE.name || normalizedItem.itemType == ItemType.EVENT.name) {
                publishPushEvent(familyId, normalizedItem)
            }
            Result.success(itemId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            if (itemDao != null) {
                itemDao.upsert(
                    FamilyItemEntity.fromFamilyItem(
                        normalizedItem,
                        pendingSyncAction = FamilyItemEntity.SYNC_CREATE
                    )
                )
                appContext?.let { SyncPendingWorker.scheduleSync(it, familyId) }
                Result.success(itemId)
            } else {
                Result.failure(e)
            }
        }
    }

    suspend fun updateFamilyItem(
        familyId: String,
        item: FamilyItem,
        isOnline: Boolean = true
    ): Result<Unit> {
        val uid = requireUserId()
        require(item.id.isNotBlank()) { "Item ID must not be blank for update" }

        val existingLocal = itemDao?.getById(item.id)
        val wasPendingCreate = existingLocal?.pendingSyncAction == FamilyItemEntity.SYNC_CREATE
        val nextOfflineAction = if (wasPendingCreate) {
            FamilyItemEntity.SYNC_CREATE
        } else {
            FamilyItemEntity.SYNC_UPDATE
        }

        itemDao?.upsert(
            FamilyItemEntity.fromFamilyItem(
                item,
                pendingSyncAction = if (isOnline) null else nextOfflineAction
            )
        )
        appContext?.let { PodAllyGlanceWidget.requestUpdate(it) }

        if (!isOnline && itemDao != null) {
            appContext?.let { SyncPendingWorker.scheduleSync(it, familyId) }
            return Result.success(Unit)
        }

        val docRef = db.collection("families").document(familyId).collection("items").document(item.id)
        val path = docRef.path

        return try {
            if (wasPendingCreate) {
                val payload = buildCreatePayload(item, uid, familyId)
                docRef.set(payload).await()
            } else {
                val updates = buildUpdatePayload(item)
                docRef.update(updates).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            if (itemDao != null) {
                itemDao.upsert(
                    FamilyItemEntity.fromFamilyItem(
                        item,
                        pendingSyncAction = nextOfflineAction
                    )
                )
                appContext?.let { SyncPendingWorker.scheduleSync(it, familyId) }
                Result.success(Unit)
            } else {
                Result.failure(e)
            }
        }
    }

    /**
     * Atomically merges grocery checklist toggles or additions against the latest Firestore state
     * so concurrent shoppers don't overwrite each other's checkmarks.
     */
    suspend fun mergeChecklistUpdate(
        familyId: String,
        localItem: FamilyItem,
        toggledCleanItemText: String? = null,
        toggleToChecked: Boolean = true,
        addedLineText: String? = null,
        passkey: String,
        isOnline: Boolean = true
    ): Result<Unit> {
        if (!isOnline) {
            val merged = applyChecklistDelta(localItem.content, toggledCleanItemText, toggleToChecked, addedLineText)
            val updated = if (localItem.isEncrypted) {
                val plain = "TIT:${localItem.title}||CON:$merged"
                localItem.copy(content = merged, encryptedPayload = CryptoManager.encrypt(plain, passkey))
            } else {
                localItem.copy(content = merged)
            }
            return updateFamilyItem(familyId, updated, isOnline = false)
        }

        val docRef = db.collection("families").document(familyId).collection("items").document(localItem.id)
        return try {
            val mergedItem = db.runTransaction { tx ->
                val snap = tx.get(docRef)
                val remote = snap.toObject(FamilyItem::class.java) ?: localItem
                val remoteEncrypted = snap.getBoolean("isEncrypted") ?: remote.isEncrypted
                val remoteRawTitle = snap.getString("title") ?: remote.title
                val remoteRawContent = snap.getString("content") ?: remote.content
                val remotePayload = snap.getString("encryptedPayload") ?: remote.encryptedPayload

                val (remoteTitle, remotePlainContent) = if (remoteEncrypted && remotePayload.isNotBlank()) {
                    val dec = CryptoManager.decrypt(remotePayload, passkey)
                    if (dec.startsWith("TIT:")) {
                        dec.substringAfter("TIT:").substringBefore("||CON:") to dec.substringAfter("||CON:")
                    } else {
                        remoteRawTitle to dec
                    }
                } else {
                    remoteRawTitle to remoteRawContent
                }

                val mergedContent = applyChecklistDelta(
                    remotePlainContent,
                    toggledCleanItemText,
                    toggleToChecked,
                    addedLineText
                )

                val updates = mutableMapOf<String, Any>(
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                val nextPayload = if (remoteEncrypted) {
                    val cipher = CryptoManager.encrypt("TIT:$remoteTitle||CON:$mergedContent", passkey)
                    updates["encryptedPayload"] = cipher
                    cipher
                } else {
                    updates["content"] = mergedContent
                    remotePayload
                }
                tx.update(docRef, updates)
                remote.copy(
                    id = localItem.id,
                    familyId = familyId,
                    isEncrypted = remoteEncrypted,
                    title = remoteTitle,
                    content = mergedContent,
                    encryptedPayload = nextPayload
                )
            }.await()

            itemDao?.upsert(FamilyItemEntity.fromFamilyItem(mergedItem, pendingSyncAction = null))
            appContext?.let { PodAllyGlanceWidget.requestUpdate(it) }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("FamilyRepository", "Checklist transaction failed, queueing offline update: ${e.message}")
            // Fallback to queued update if offline or transient failure
            val merged = applyChecklistDelta(localItem.content, toggledCleanItemText, toggleToChecked, addedLineText)
            val updated = if (localItem.isEncrypted) {
                localItem.copy(
                    content = merged,
                    encryptedPayload = CryptoManager.encrypt("TIT:${localItem.title}||CON:$merged", passkey)
                )
            } else {
                localItem.copy(content = merged)
            }
            updateFamilyItem(familyId, updated, isOnline = false)
        }
    }

    private fun applyChecklistDelta(
        baseContent: String,
        toggledCleanItemText: String?,
        toggleToChecked: Boolean,
        addedLineText: String?
    ): String {
        val lines = baseContent.lines().filter { it.isNotBlank() }.toMutableList()
        if (!toggledCleanItemText.isNullOrBlank()) {
            val targetIdx = lines.indexOfFirst { line ->
                val clean = line.removePrefix("- [x] ").removePrefix("- [ ] ").trim()
                clean.equals(toggledCleanItemText.trim(), ignoreCase = true)
            }
            if (targetIdx >= 0) {
                lines[targetIdx] = if (toggleToChecked) {
                    "- [x] ${toggledCleanItemText.trim()}"
                } else {
                    "- [ ] ${toggledCleanItemText.trim()}"
                }
            }
        }
        if (!addedLineText.isNullOrBlank()) {
            val cleanAdd = addedLineText.trim()
            val alreadyExists = lines.any {
                it.removePrefix("- [x] ").removePrefix("- [ ] ").trim().equals(cleanAdd, ignoreCase = true)
            }
            if (!alreadyExists) {
                lines.add("- [ ] $cleanAdd")
            }
        }
        return lines.joinToString("\n")
    }

    suspend fun deleteFamilyItem(
        familyId: String,
        itemId: String,
        isOnline: Boolean = true
    ): Result<Unit> {
        requireUserId()

        if (!isOnline && itemDao != null) {
            val existingLocal = itemDao.getById(itemId)
            if (existingLocal?.pendingSyncAction == FamilyItemEntity.SYNC_CREATE) {
                itemDao.deleteById(itemId)
            } else if (existingLocal != null) {
                itemDao.upsert(existingLocal.copy(pendingSyncAction = FamilyItemEntity.SYNC_DELETE))
            } else {
                itemDao.upsert(
                    FamilyItemEntity(
                        id = itemId,
                        familyId = familyId,
                        memberUidsCsv = "",
                        authorId = "",
                        authorName = "",
                        itemType = ItemType.NOTE.name,
                        category = "GENERAL",
                        isEncrypted = false,
                        encryptedPayload = "",
                        title = "",
                        content = "",
                        eventDateMillis = null,
                        isCompleted = false,
                        flightNumber = "",
                        flightDeparture = "",
                        flightArrival = "",
                        flightStatus = "",
                        placeName = "",
                        placeAddress = "",
                        placeGeoQuery = "",
                        shareToken = "",
                        pendingSyncAction = FamilyItemEntity.SYNC_DELETE
                    )
                )
            }
            appContext?.let {
                SyncPendingWorker.scheduleSync(it, familyId)
                PodAllyGlanceWidget.requestUpdate(it)
            }
            return Result.success(Unit)
        }

        val docRef = db.collection("families").document(familyId).collection("items").document(itemId)
        val path = docRef.path
        return try {
            docRef.delete().await()
            itemDao?.deleteById(itemId)
            appContext?.let { PodAllyGlanceWidget.requestUpdate(it) }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    /**
     * Prunes completed reminders older than [olderThanDays] to keep local Room & Firestore storage compact.
     */
    suspend fun pruneOldCompletedItems(familyId: String, olderThanDays: Int = 60): Int {
        if (familyId.isBlank() || itemDao == null) return 0
        val cutoffMillis = System.currentTimeMillis() - olderThanDays * 86_400_000L
        return try {
            val uid = requireUserId()
            val snap = db.collection("families").document(familyId).collection("items")
                .whereArrayContains("memberUids", uid)
                .whereEqualTo("isCompleted", true)
                .get()
                .await()
            var pruned = 0
            for (doc in snap.documents) {
                val updatedAt = doc.getTimestamp("updatedAt")?.toDate()?.time ?: continue
                if (updatedAt < cutoffMillis && doc.getString("recurrenceRule") in listOf(null, RecurrenceRule.NONE.name)) {
                    doc.reference.delete().await()
                    itemDao.deleteById(doc.id)
                    pruned++
                }
            }
            pruned
        } catch (_: Exception) {
            0
        }
    }

    /**
     * Synchronizes any queued offline CREATE / UPDATE / DELETE Room operations to Firestore
     * once connectivity is restored.
     */
    suspend fun syncPendingItems(familyId: String): Int {
        val dao = itemDao ?: return 0
        if (familyId.isBlank()) return 0
        val uid = auth.currentUser?.uid ?: return 0
        val pendingList = dao.getPendingSyncItems(familyId)
        var syncedCount = 0

        for (entity in pendingList) {
            val docRef = db.collection("families").document(familyId).collection("items").document(entity.id)
            try {
                when (entity.pendingSyncAction) {
                    FamilyItemEntity.SYNC_CREATE -> {
                        val item = entity.toFamilyItem()
                        val payload = buildCreatePayload(item, uid, familyId)
                        docRef.set(payload).await()
                        dao.upsert(entity.copy(pendingSyncAction = null))
                        if (item.itemType == ItemType.NOTE.name || item.itemType == ItemType.EVENT.name) {
                            publishPushEvent(familyId, item)
                        }
                        syncedCount++
                    }
                    FamilyItemEntity.SYNC_UPDATE -> {
                        val item = entity.toFamilyItem()
                        val updates = buildUpdatePayload(item)
                        docRef.update(updates).await()
                        dao.upsert(entity.copy(pendingSyncAction = null))
                        syncedCount++
                    }
                    FamilyItemEntity.SYNC_DELETE -> {
                        docRef.delete().await()
                        dao.deleteById(entity.id)
                        syncedCount++
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("FamilyRepository", "Failed to sync item ${entity.id}: ${e.message}")
            }
        }
        if (syncedCount > 0) {
            appContext?.let { PodAllyGlanceWidget.requestUpdate(it) }
        }
        return syncedCount
    }

    private suspend fun publishPushEvent(familyId: String, item: FamilyItem) {
        val uid = auth.currentUser?.uid ?: return
        val eventId = "push_" + UUID.randomUUID().toString().take(16)
        val docRef = db.collection("families").document(familyId).collection("push_events").document(eventId)
        val payload = mapOf(
            "familyId" to familyId,
            "topic" to "family_$familyId",
            "memberUids" to item.memberUids.ifEmpty { listOf(uid) },
            "authorId" to uid,
            "authorName" to item.authorName.ifBlank { "Family Member" },
            "itemType" to item.itemType,
            "title" to if (item.isEncrypted) "Encrypted ${item.itemType.lowercase()}" else item.title.take(200),
            "isEncrypted" to item.isEncrypted,
            "createdAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
        } catch (e: Exception) {
            Log.w("FamilyRepository", "Push event queue skipped: ${e.message}")
        }
    }

    private fun buildCreatePayload(item: FamilyItem, uid: String, familyId: String): MutableMap<String, Any> {
        val payload = mutableMapOf<String, Any>(
            "familyId" to familyId,
            "memberUids" to item.memberUids.ifEmpty { listOf(uid) },
            "authorId" to uid,
            "authorName" to item.authorName.ifEmpty { "Family Member" },
            "itemType" to item.itemType,
            "category" to item.category,
            "isEncrypted" to item.isEncrypted,
            "isCompleted" to item.isCompleted,
            "recurrenceRule" to item.recurrenceRule.ifBlank { RecurrenceRule.NONE.name },
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (item.encryptedPayload.isNotBlank()) payload["encryptedPayload"] = item.encryptedPayload
        if (item.title.isNotBlank()) payload["title"] = item.title
        if (item.content.isNotBlank()) payload["content"] = item.content
        if (item.eventDateMillis != null) payload["eventDateMillis"] = item.eventDateMillis
        if (item.estimatedCost != null) payload["estimatedCost"] = item.estimatedCost
        if (item.attachmentEncryptedBase64.isNotBlank()) {
            payload["attachmentEncryptedBase64"] = item.attachmentEncryptedBase64
        }
        if (item.flightNumber.isNotBlank()) payload["flightNumber"] = item.flightNumber
        if (item.flightDeparture.isNotBlank()) payload["flightDeparture"] = item.flightDeparture
        if (item.flightArrival.isNotBlank()) payload["flightArrival"] = item.flightArrival
        if (item.flightStatus.isNotBlank()) payload["flightStatus"] = item.flightStatus
        if (item.placeName.isNotBlank()) payload["placeName"] = item.placeName
        if (item.placeAddress.isNotBlank()) payload["placeAddress"] = item.placeAddress
        if (item.placeGeoQuery.isNotBlank()) payload["placeGeoQuery"] = item.placeGeoQuery
        if (item.shareToken.isNotBlank()) payload["shareToken"] = item.shareToken
        return payload
    }

    private fun buildUpdatePayload(item: FamilyItem): MutableMap<String, Any> {
        val updates = mutableMapOf<String, Any>(
            "category" to item.category,
            "isEncrypted" to item.isEncrypted,
            "isCompleted" to item.isCompleted,
            "recurrenceRule" to item.recurrenceRule.ifBlank { RecurrenceRule.NONE.name },
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (item.authorName.isNotBlank()) updates["authorName"] = item.authorName
        if (item.encryptedPayload.isNotBlank()) updates["encryptedPayload"] = item.encryptedPayload
        if (item.title.isNotBlank()) updates["title"] = item.title
        if (item.content.isNotBlank()) updates["content"] = item.content
        if (item.eventDateMillis != null) updates["eventDateMillis"] = item.eventDateMillis
        if (item.estimatedCost != null) updates["estimatedCost"] = item.estimatedCost
        updates["attachmentEncryptedBase64"] = item.attachmentEncryptedBase64
        if (item.flightNumber.isNotBlank()) updates["flightNumber"] = item.flightNumber
        if (item.flightDeparture.isNotBlank()) updates["flightDeparture"] = item.flightDeparture
        if (item.flightArrival.isNotBlank()) updates["flightArrival"] = item.flightArrival
        if (item.flightStatus.isNotBlank()) updates["flightStatus"] = item.flightStatus
        if (item.placeName.isNotBlank()) updates["placeName"] = item.placeName
        if (item.placeAddress.isNotBlank()) updates["placeAddress"] = item.placeAddress
        if (item.placeGeoQuery.isNotBlank()) updates["placeGeoQuery"] = item.placeGeoQuery
        if (item.shareToken.isNotBlank()) updates["shareToken"] = item.shareToken
        return updates
    }

    // --- Secure Share Links ---

    suspend fun createShareLink(link: ShareLink): Result<String> {
        val uid = requireUserId()
        val docRef = db.collection("share_links").document(link.token)
        val payload = mapOf(
            "token" to link.token,
            "title" to link.title,
            "summary" to link.summary,
            "itemType" to link.itemType,
            "createdById" to uid,
            "createdAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            Result.success(link.token)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "share_links/${link.token}")
            Result.failure(e)
        }
    }

    suspend fun getShareLink(token: String): Result<ShareLink?> {
        val path = "share_links/$token"
        return try {
            val snapshot = db.collection("share_links").document(token).get().await()
            if (snapshot.exists()) {
                Result.success(snapshot.toObject(ShareLink::class.java))
            } else {
                Result.success(null)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }

    companion object {
        private fun createMemoryCachedFirestore(context: Context): FirebaseFirestore {
            val db = FirebaseFirestore.getInstance(context.getString(R.string.firestore_database_id))
            try {
                db.firestoreSettings = FirebaseFirestoreSettings.Builder()
                    .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                    .build()
            } catch (_: IllegalStateException) {
                // Settings already frozen after initial Firestore usage; safe to ignore
            }
            return db
        }

        fun nextRecurrenceMillis(currentMillis: Long?, recurrenceRule: String): Long {
            val cal = Calendar.getInstance().apply {
                timeInMillis = currentMillis ?: System.currentTimeMillis()
            }
            when (recurrenceRule) {
                RecurrenceRule.DAILY.name -> cal.add(Calendar.DAY_OF_YEAR, 1)
                RecurrenceRule.WEEKLY.name -> cal.add(Calendar.WEEK_OF_YEAR, 1)
                RecurrenceRule.MONTHLY.name -> cal.add(Calendar.MONTH, 1)
            }
            return cal.timeInMillis
        }
    }
}
