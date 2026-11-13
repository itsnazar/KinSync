package com.example.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.FamilyItem
import com.example.data.model.ItemCategory
import com.example.data.model.ItemType
import com.example.data.repository.FamilyRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RoomOfflineCacheTest : FirestoreEmulatorTestBase() {

    private lateinit var localDb: KinSyncDatabase
    private lateinit var itemDao: FamilyItemDao

    @Before
    fun setUpRoom() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        localDb = Room.inMemoryDatabaseBuilder(context, KinSyncDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        itemDao = localDb.familyItemDao()
    }

    @After
    fun tearDownRoom() {
        localDb.close()
    }

    @Test
    fun offlineAddUpdateAndReconnectSync_persistsInRoomAndSyncsToFirestore() = runBlocking {
        val uid = signInTestUser("offline_sync@test.com")
        val repo = FamilyRepository(firestore, itemDao)
        val family = withTimeout(6000L) {
            repo.createFamily("Offline Sync Family", "SYNC99").getOrThrow()
        }

        // 1. Create note and calendar event while offline
        val offlineNote = FamilyItem(
            familyId = family.id,
            memberUids = listOf(uid),
            authorId = uid,
            authorName = "Tester",
            itemType = ItemType.NOTE.name,
            category = ItemCategory.HOME.name,
            title = "Offline Grocery Note",
            content = "[ ] Milk\n[ ] Bread",
            isEncrypted = false
        )
        val offlineEvent = FamilyItem(
            familyId = family.id,
            memberUids = listOf(uid),
            authorId = uid,
            authorName = "Tester",
            itemType = ItemType.EVENT.name,
            category = ItemCategory.TRAVEL.name,
            title = "Flight Check-In",
            content = "Terminal 2",
            eventDateMillis = System.currentTimeMillis() + 86_400_000L,
            isEncrypted = false
        )

        val noteId = repo.addFamilyItem(family.id, offlineNote, isOnline = false).getOrThrow()
        val eventId = repo.addFamilyItem(family.id, offlineEvent, isOnline = false).getOrThrow()

        // Verify both items are immediately readable from Room cache and pending count is 2
        val cachedItems = itemDao.observeItemsForFamily(family.id).first()
        assertEquals(2, cachedItems.size)
        assertEquals(2, itemDao.observePendingCount(family.id).first())

        // Update offline note before reconnecting
        repo.updateFamilyItem(
            family.id,
            offlineNote.copy(id = noteId, title = "Updated Offline Grocery Note"),
            isOnline = false
        )

        // 2. Restore connectivity and sync pending Room changes to Firestore
        val syncedCount = withTimeout(8000L) {
            repo.syncPendingItems(family.id)
        }
        assertEquals(2, syncedCount)
        assertEquals(0, itemDao.observePendingCount(family.id).first())

        // 3. Verify items exist in Firestore remotely
        val remoteSnapshot = withTimeout(6000L) {
            repo.observeFamilyItems(family.id).first { list ->
                list.any { it.id == noteId } && list.any { it.id == eventId }
            }
        }
        assertTrue(remoteSnapshot.any { it.id == noteId && it.title == "Updated Offline Grocery Note" })
        assertTrue(remoteSnapshot.any { it.id == eventId && it.title == "Flight Check-In" })
    }

    @Test
    fun mergeChecklistUpdate_andAttachmentPersistence_syncCorrectly() = runBlocking {
        val uid = signInTestUser("checklist_photo@test.com")
        val repo = FamilyRepository(firestore, itemDao)
        val family = withTimeout(6000L) {
            repo.createFamily("Checklist Family", "LIST88").getOrThrow()
        }

        val groceryList = FamilyItem(
            familyId = family.id,
            memberUids = listOf(uid),
            authorId = uid,
            authorName = "Shopper",
            itemType = ItemType.NOTE.name,
            category = ItemCategory.HOME.name,
            title = "Weekend Groceries",
            content = "- [ ] Milk\n- [ ] Eggs",
            estimatedCost = 42.50,
            attachmentEncryptedBase64 = "sampleIv:samplePhotoCipher",
            isEncrypted = false
        )

        val itemId = withTimeout(6000L) {
            repo.addFamilyItem(family.id, groceryList, isOnline = true).getOrThrow()
        }
        val saved = groceryList.copy(id = itemId)

        withTimeout(6000L) {
            repo.mergeChecklistUpdate(
                familyId = family.id,
                localItem = saved,
                toggledCleanItemText = "Milk",
                toggleToChecked = true,
                addedLineText = "Strawberries",
                passkey = "TestPasskey",
                isOnline = true
            ).getOrThrow()
        }

        val remote = withTimeout(6000L) {
            repo.observeFamilyItems(family.id).first { list ->
                list.any { it.id == itemId && it.content.contains("- [x] Milk") && it.content.contains("- [ ] Strawberries") }
            }
        }
        val fetched = remote.first { it.id == itemId }
        assertEquals("sampleIv:samplePhotoCipher", fetched.attachmentEncryptedBase64)
        assertEquals(42.50, fetched.estimatedCost ?: 0.0, 0.01)

        // Verify direct Firestore document reflects the transaction merge as well
        val directSnap = withTimeout(6000L) {
            firestore.collection("families").document(family.id).collection("items").document(itemId).get().await()
        }
        val remoteContent = directSnap.getString("content") ?: ""
        assertTrue(remoteContent.contains("- [x] Milk"))
        assertTrue(remoteContent.contains("- [ ] Strawberries"))

        // Verify nextRecurrenceMillis advances timestamps properly
        val now = 1_700_000_000_000L
        assertEquals(now + 86_400_000L, FamilyRepository.nextRecurrenceMillis(now, com.example.data.model.RecurrenceRule.DAILY.name))
        assertEquals(now + 7 * 86_400_000L, FamilyRepository.nextRecurrenceMillis(now, com.example.data.model.RecurrenceRule.WEEKLY.name))
    }
}
