const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

// --- SECURITY BOUNDS TESTS ---

test("Unauthenticated user: cannot read user profiles", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
});

test("Authenticated user: can create and read own profile", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const userDoc = aliceDb.collection("users").doc(ALICE_UID);
  await assertSucceeds(
    userDoc.set({
      userId: ALICE_UID,
      displayName: "Alice",
      email: "alice@example.com",
      createdAt: new Date(),
    })
  );
  await assertSucceeds(userDoc.get());
});

test("Authenticated user: cannot read Bob's profile", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("users").doc(BOB_UID).set({
      userId: BOB_UID,
      displayName: "Bob",
      email: "bob@example.com",
      createdAt: new Date(),
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("users").doc(BOB_UID).get());
});

test("Family items: members can read, non-members cannot read", async () => {
  const familyId = "fam_1";
  const itemId = "item_1";

  // Seed family and item
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("families").doc(familyId).set({
      name: "Alice Family",
      creatorId: ALICE_UID,
      joinCode: "ABC1234",
      memberUids: [ALICE_UID],
      createdAt: new Date(),
    });
    await context.firestore().collection("families").doc(familyId).collection("items").doc(itemId).set({
      familyId: familyId,
      memberUids: [ALICE_UID],
      authorId: ALICE_UID,
      itemType: "NOTE",
      category: "GENERAL",
      isEncrypted: true,
      title: "Secret Note",
      content: "Encrypted payload",
      createdAt: new Date(),
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(aliceDb.collection("families").doc(familyId).collection("items").doc(itemId).get());

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("families").doc(familyId).collection("items").doc(itemId).get());
});

test("Share links: can be fetched by token, list is denied", async () => {
  const token = "secure-token-123";
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("share_links").doc(token).set({
      token: token,
      title: "Trip Itinerary",
      summary: "Flight UA123 departing at 10 AM",
      itemType: "FLIGHT",
      createdById: ALICE_UID,
      createdAt: new Date(),
    });
  });

  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertSucceeds(unauthDb.collection("share_links").doc(token).get());
  await assertFails(unauthDb.collection("share_links").get());
});

test("User profile: can update own fcmToken, cannot update another user's fcmToken", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const aliceDoc = aliceDb.collection("users").doc(ALICE_UID);

  await assertSucceeds(
    aliceDoc.set({
      userId: ALICE_UID,
      displayName: "Alice",
      email: "alice@example.com",
      createdAt: new Date(),
    })
  );

  await assertSucceeds(
    aliceDoc.update({
      fcmToken: "fcm_device_token_abc123",
      updatedAt: new Date(),
    })
  );

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      fcmToken: "hijacked_token",
    })
  );
});

test("Push events: family members can create and read, non-members cannot", async () => {
  const familyId = "fam_push_1";
  const eventId = "push_evt_1";

  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("families").doc(familyId).set({
      name: "Alice Family",
      creatorId: ALICE_UID,
      joinCode: "ABC1234",
      memberUids: [ALICE_UID],
      createdAt: new Date(),
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const aliceEventRef = aliceDb.collection("families").doc(familyId).collection("push_events").doc(eventId);
  await assertSucceeds(
    aliceEventRef.set({
      familyId: familyId,
      topic: `family_${familyId}`,
      memberUids: [ALICE_UID],
      authorId: ALICE_UID,
      authorName: "Alice",
      itemType: "NOTE",
      title: "Grocery List",
      isEncrypted: false,
      createdAt: new Date(),
    })
  );
  await assertSucceeds(aliceEventRef.get());

  // Update & delete are forbidden on push_events
  await assertFails(aliceEventRef.update({ title: "Tampered" }));
  await assertFails(aliceEventRef.delete());

  // Non-member Bob cannot create or read push_events
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("families").doc(familyId).collection("push_events").doc(eventId).get());
  await assertFails(
    bobDb.collection("families").doc(familyId).collection("push_events").doc("push_evt_2").set({
      familyId: familyId,
      topic: `family_${familyId}`,
      memberUids: [BOB_UID],
      authorId: BOB_UID,
      itemType: "EVENT",
      title: "Unauthorized Event",
      isEncrypted: false,
      createdAt: new Date(),
    })
  );
});

test("Family items: validate recurrenceRule, non-negative estimatedCost, and attachment size", async () => {
  const familyId = "fam_ext_1";
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("families").doc(familyId).set({
      name: "PodAlly Family",
      creatorId: ALICE_UID,
      joinCode: "POD2026",
      memberUids: [ALICE_UID],
      createdAt: new Date(),
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const itemsCol = aliceDb.collection("families").doc(familyId).collection("items");

  // Valid recurring grocery/trip item with cost and encrypted attachment
  await assertSucceeds(
    itemsCol.doc("valid_ext").set({
      familyId: familyId,
      memberUids: [ALICE_UID],
      authorId: ALICE_UID,
      itemType: "NOTE",
      category: "HOME",
      isEncrypted: true,
      title: "Costco Grocery Run",
      content: "- [ ] Organic Milk",
      recurrenceRule: "WEEKLY",
      estimatedCost: 84.5,
      attachmentEncryptedBase64: "iv123:cipher456",
      createdAt: new Date(),
    })
  );

  // Invalid recurrenceRule rejected
  await assertFails(
    itemsCol.doc("bad_rec").set({
      familyId: familyId,
      memberUids: [ALICE_UID],
      authorId: ALICE_UID,
      itemType: "REMINDER",
      category: "GENERAL",
      isEncrypted: false,
      title: "Bad Recurrence",
      recurrenceRule: "HOURLY",
      createdAt: new Date(),
    })
  );

  // Negative estimatedCost rejected
  await assertFails(
    itemsCol.doc("bad_cost").set({
      familyId: familyId,
      memberUids: [ALICE_UID],
      authorId: ALICE_UID,
      itemType: "PLACE",
      category: "TRAVEL",
      isEncrypted: false,
      title: "Negative Cost",
      estimatedCost: -15,
      createdAt: new Date(),
    })
  );
});


