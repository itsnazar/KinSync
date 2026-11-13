package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.net.URLEncoder

object GoogleMapsHelper {

    /**
     * Opens location in Google Maps using geo URI or Google Maps Web URL fallback.
     */
    fun openInGoogleMaps(context: Context, query: String, address: String = "") {
        val fullQuery = if (address.isNotBlank()) "$query, $address" else query
        val encoded = URLEncoder.encode(fullQuery.trim(), "UTF-8")

        // Try Google Maps app first with geo URI
        val gmmIntentUri = Uri.parse("geo:0,0?q=$encoded")
        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
            setPackage("com.google.android.apps.maps")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        try {
            context.startActivity(mapIntent)
        } catch (_: Exception) {
            // Fallback to universal Google Maps Search URL in browser or default handler
            val webUrl = "https://www.google.com/maps/search/?api=1&query=$encoded"
            val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse(webUrl)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(fallbackIntent)
        }
    }
}
