package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.FamilyItem
import com.example.data.model.ItemCategory
import com.example.data.model.ItemType
import com.example.data.model.ShareLink
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class FamilyRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun createFamily_authenticatedUser_success() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repo = FamilyRepository(firestore)

        val createResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.createFamily("Miller Family", "MIL1234")
        }
        assertTrue("Expected family creation to succeed", createResult.isSuccess)
        val family = createResult.getOrThrow()
        assertEquals("Miller Family", family.name)
        assertTrue(family.memberUids.contains(aliceUid))
    }

    @Test
    fun addFamilyItem_and_observe_authenticatedMember_emitsUpdates() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repo = FamilyRepository(firestore)

        val family = repo.createFamily("Smith Family", "SMI1234").getOrThrow()
        val familyId = family.id

        val item = FamilyItem(
            familyId = familyId,
            memberUids = listOf(aliceUid),
            authorId = aliceUid,
            authorName = "Alice",
            itemType = ItemType.NOTE.name,
            category = ItemCategory.GENERAL.name,
            isEncrypted = true,
            title = "Doctor Appointment",
            content = "Encrypted reminder"
        )

        val addResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.addFamilyItem(familyId, item)
        }
        assertTrue("Expected add item to succeed", addResult.isSuccess)
        val itemId = addResult.getOrThrow()

        val emitted = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeFamilyItems(familyId).first { list -> list.any { it.id == itemId } }
        }
        assertTrue(emitted.any { it.id == itemId })
    }

    @Test
    fun observeFamilyItems_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repo = FamilyRepository(firestore)
        val family = repo.createFamily("Brown Family", "BRO1234").getOrThrow()
        val familyId = family.id

        auth.signOut()

        var caughtException: Throwable? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeFamilyItems(familyId).first()
            }
        } catch (e: Exception) {
            caughtException = e
        }

        assertNotNull("Expected exception for unauthenticated observer", caughtException)
        val isFailFastAuth = caughtException is IllegalStateException
        val firestoreEx = caughtException?.unwrapFirestoreException()
        val isPermissionDenied = firestoreEx?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
        assertTrue("Expected fail-fast auth check or PERMISSION_DENIED", isFailFastAuth || isPermissionDenied)
    }

    @Test
    fun createShareLink_and_fetchUnauthenticated_success() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repo = FamilyRepository(firestore)
        val token = "token-" + UUID.randomUUID().toString()

        val shareLink = ShareLink(
            token = token,
            title = "Vacation Flight Itinerary",
            summary = "Flight UA456 departing SFO at 9:00 AM",
            itemType = ItemType.FLIGHT.name,
            createdById = aliceUid
        )

        val createResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.createShareLink(shareLink)
        }
        assertTrue(createResult.isSuccess)

        auth.signOut()

        val fetchResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.getShareLink(token)
        }
        assertTrue(fetchResult.isSuccess)
        val fetched = fetchResult.getOrThrow()
        assertNotNull(fetched)
        assertEquals("Vacation Flight Itinerary", fetched?.title)
    }

    @Test
    fun updateFcmToken_authenticatedUser_success() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repo = FamilyRepository(firestore)

        val saveResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.saveUserProfile(
                com.example.data.model.UserProfile(
                    userId = aliceUid,
                    displayName = "Alice",
                    email = ALICE_EMAIL
                )
            )
        }
        assertTrue(saveResult.isSuccess)

        val tokenResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.updateFcmToken("fcm-test-token-987")
        }
        assertTrue("Expected FCM token update to succeed", tokenResult.isSuccess)
    }

    private companion object {
        const val ALICE_EMAIL = "alice_rules@test.com"
        const val DEFAULT_TIMEOUT_MS = 6000L
        const val FLOW_TIMEOUT_MS = 4000L
    }
}
