package com.example.ai

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "GeminiFamilyAdvisor"

sealed interface AiResult {
    data object Loading : AiResult
    data class Success(val text: String) : AiResult
    data class Error(val message: String) : AiResult
}

data class ExtractedFlight(
    val flightNumber: String,
    val departure: String,
    val arrival: String,
    val status: String = "On Time",
    val summary: String = ""
)

data class ExtractedPlace(
    val name: String,
    val address: String,
    val category: String = "TRAVEL"
)

data class ExtractedBooking(
    val flights: List<ExtractedFlight>,
    val places: List<ExtractedPlace>
)

class GeminiFamilyAdvisor {

    // Mandated: Lazy initialization to prevent startup crashes before App Check initializes
    private val model by lazy {
        Firebase.ai.generativeModel("gemini-3.8-flash")
    }

    private val bookingSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "flights" to Schema.array(
                    items = Schema.obj(
                        properties = mapOf(
                            "flightNumber" to Schema.string(description = "Airline code and number e.g. UA 248"),
                            "departure" to Schema.string(description = "Departure airport code, city, or time"),
                            "arrival" to Schema.string(description = "Arrival airport code, city, or time"),
                            "status" to Schema.string(description = "Flight status such as On Time or Confirmed"),
                            "summary" to Schema.string(description = "Confirmation code or seat details")
                        ),
                        optionalProperties = listOf("status", "summary")
                    ),
                    description = "Flights found in the booking email"
                ),
                "places" to Schema.array(
                    items = Schema.obj(
                        properties = mapOf(
                            "name" to Schema.string(description = "Hotel, rental car, or attraction name"),
                            "address" to Schema.string(description = "Street address or city for Google Maps"),
                            "category" to Schema.string(description = "Category such as Hotel, Attraction, or Car Rental")
                        ),
                        optionalProperties = listOf("category")
                    ),
                    description = "Hotels or travel places found in the booking email"
                )
            )
        )
    }

    private val bookingExtractorModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = bookingSchema
            }
        )
    }

    /**
     * Generates a holistic family summary of upcoming events, notes, and reminders.
     */
    suspend fun summarizeFamilyActivities(activitiesSummary: String): AiResult {
        return try {
            val prompt = """
                You are PodAlly AI, a thoughtful family assistant.
                Analyze the following upcoming family events, notes, and reminders:
                
                $activitiesSummary
                
                Provide a clear, warm, and concise family briefing formatted in 3 short sections:
                1. 🗓️ Upcoming Highlights (Key events & schedule overview)
                2. ⚠️ Important Deadlines & Reminders (Things not to forget)
                3. 💡 Smart Family Tip (Suggestions for smooth coordination, logistics, or conflict resolution)
                Keep it helpful, minimalist, and easy for busy parents and family members to read.
            """.trimIndent()

            val response = withTimeout(60_000L) {
                model.generateContent(prompt)
            }
            val text = response.text
            if (!text.isNullOrBlank()) {
                AiResult.Success(text)
            } else {
                AiResult.Error("Gemini returned an empty briefing.")
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI summary timed out", e)
            AiResult.Error("Generation timed out. Please try again.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AI summary failed", e)
            AiResult.Error(e.localizedMessage ?: "Failed to generate family summary.")
        }
    }

    /**
     * Recommends shopping items, deals, and packing lists for family trips and activities.
     */
    suspend fun planTripSuppliesAndShopping(destination: String, dates: String, items: String): AiResult {
        return try {
            val prompt = """
                You are a family travel & smart shopping planner.
                Trip Destination: $destination
                Dates / Season: $dates
                Current Planned Items: $items
                
                Provide:
                1. 🎒 Essential Packing Checklist (tailored to family travel)
                2. 🛒 Recommended Shopping & Supplies (e.g. travel snacks, sunscreen, adapters, travel gear)
                3. 🏷️ Best Retailer Value Guide (highlighting whether to buy in bulk at Costco/Sam's Club or pick up quickly at Amazon/Walmart)
                Keep it bulleted, practical, and concise.
            """.trimIndent()

            val response = withTimeout(60_000L) {
                model.generateContent(prompt)
            }
            val text = response.text
            if (!text.isNullOrBlank()) {
                AiResult.Success(text)
            } else {
                AiResult.Error("Gemini returned an empty shopping suggestion.")
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Trip shopping planning timed out", e)
            AiResult.Error("Trip shopping generation timed out.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Trip shopping failed", e)
            AiResult.Error(e.localizedMessage ?: "Failed to generate shopping tips.")
        }
    }

    /**
     * Extracts structured flights and travel places/hotels from a Gmail confirmation or pasted email.
     */
    suspend fun extractBookingsFromText(rawEmailText: String): Result<ExtractedBooking> {
        return try {
            val prompt = """
                Extract all airline flights and hotel/travel place bookings from the email text below.
                Return empty arrays if no flights or places are present. Do not invent data not in the email.
                
                EMAIL CONTENT:
                ${rawEmailText.take(8000)}
            """.trimIndent()

            val response = withTimeout(60_000L) {
                bookingExtractorModel.generateContent(prompt)
            }
            val jsonText = response.text ?: throw IllegalStateException("Gemini returned empty response.")
            val root = JSONObject(jsonText)

            val flightsJson = root.optJSONArray("flights")
            val flights = buildList {
                if (flightsJson != null) {
                    for (i in 0 until flightsJson.length()) {
                        val obj = flightsJson.getJSONObject(i)
                        val number = obj.optString("flightNumber").trim()
                        if (number.isNotBlank()) {
                            add(
                                ExtractedFlight(
                                    flightNumber = number,
                                    departure = obj.optString("departure").trim(),
                                    arrival = obj.optString("arrival").trim(),
                                    status = obj.optString("status", "On Time").ifBlank { "On Time" },
                                    summary = obj.optString("summary").trim()
                                )
                            )
                        }
                    }
                }
            }

            val placesJson = root.optJSONArray("places")
            val places = buildList {
                if (placesJson != null) {
                    for (i in 0 until placesJson.length()) {
                        val obj = placesJson.getJSONObject(i)
                        val name = obj.optString("name").trim()
                        if (name.isNotBlank()) {
                            add(
                                ExtractedPlace(
                                    name = name,
                                    address = obj.optString("address").trim(),
                                    category = obj.optString("category", "TRAVEL").ifBlank { "TRAVEL" }
                                )
                            )
                        }
                    }
                }
            }

            Result.success(ExtractedBooking(flights = flights, places = places))
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Booking extraction timed out", e)
            Result.failure(Exception("Booking extraction timed out. Please try again."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Booking extraction failed", e)
            Result.failure(e)
        }
    }
}
