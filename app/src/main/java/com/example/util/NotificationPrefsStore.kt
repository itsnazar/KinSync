package com.example.util

import android.content.Context
import com.example.data.model.NotificationPreferences

object NotificationPrefsStore {
    private const val PREFS_NAME = "podally_prefs"
    private const val KEY_NOTES = "notif_notes"
    private const val KEY_EVENTS = "notif_events"
    private const val KEY_REMINDERS = "notif_reminders"
    private const val KEY_TRAVEL = "notif_travel"
    private const val KEY_SHARE_LINKS = "notif_share_links"
    private const val KEY_MEMBER_FILTER = "notif_member_filter"
    private const val KEY_SHOW_ENCRYPTED_PREVIEW = "notif_show_encrypted_preview"

    fun load(context: Context): NotificationPreferences {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val filterSet = prefs.getStringSet(KEY_MEMBER_FILTER, emptySet()) ?: emptySet()
        return NotificationPreferences(
            notifyNotes = prefs.getBoolean(KEY_NOTES, true),
            notifyEvents = prefs.getBoolean(KEY_EVENTS, true),
            notifyReminders = prefs.getBoolean(KEY_REMINDERS, true),
            notifyTravel = prefs.getBoolean(KEY_TRAVEL, true),
            notifyShareLinks = prefs.getBoolean(KEY_SHARE_LINKS, true),
            memberFilter = filterSet.toSet(),
            showEncryptedPreviewLocally = prefs.getBoolean(KEY_SHOW_ENCRYPTED_PREVIEW, true)
        )
    }

    fun save(context: Context, preferences: NotificationPreferences) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_NOTES, preferences.notifyNotes)
            .putBoolean(KEY_EVENTS, preferences.notifyEvents)
            .putBoolean(KEY_REMINDERS, preferences.notifyReminders)
            .putBoolean(KEY_TRAVEL, preferences.notifyTravel)
            .putBoolean(KEY_SHARE_LINKS, preferences.notifyShareLinks)
            .putStringSet(KEY_MEMBER_FILTER, preferences.memberFilter)
            .putBoolean(KEY_SHOW_ENCRYPTED_PREVIEW, preferences.showEncryptedPreviewLocally)
            .apply()
    }
}
