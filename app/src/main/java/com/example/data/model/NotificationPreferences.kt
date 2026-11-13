package com.example.data.model

data class NotificationPreferences(
    val notifyNotes: Boolean = true,
    val notifyEvents: Boolean = true,
    val notifyReminders: Boolean = true,
    val notifyTravel: Boolean = true,
    val notifyShareLinks: Boolean = true,
    val memberFilter: Set<String> = emptySet(), // Empty means all members
    val showEncryptedPreviewLocally: Boolean = true
)
