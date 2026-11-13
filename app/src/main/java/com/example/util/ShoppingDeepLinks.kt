package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.net.URLEncoder

enum class Retailer(val displayName: String, val badgeColorHex: Long) {
    AMAZON("Amazon", 0xFFFF9900),
    WALMART("Walmart", 0xFF0071DC),
    COSTCO("Costco", 0xFFE31837),
    SAMS_CLUB("Sam's Club", 0xFF0067A0),
    TARGET("Target", 0xFFCC0000)
}

object ShoppingDeepLinks {

    fun getSearchUrl(retailer: Retailer, query: String): String {
        val encodedQuery = URLEncoder.encode(query.trim(), "UTF-8")
        return when (retailer) {
            Retailer.AMAZON -> "https://www.amazon.com/s?k=$encodedQuery"
            Retailer.WALMART -> "https://www.walmart.com/search?q=$encodedQuery"
            Retailer.COSTCO -> "https://www.costco.com/CatalogSearch?dept=All&keyword=$encodedQuery"
            Retailer.SAMS_CLUB -> "https://www.samsclub.com/s/$encodedQuery"
            Retailer.TARGET -> "https://www.target.com/s?searchTerm=$encodedQuery"
        }
    }

    fun openRetailer(context: Context, retailer: Retailer, query: String) {
        val url = getSearchUrl(retailer, query)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            // Fallback to browser
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            context.startActivity(webIntent)
        }
    }
}
