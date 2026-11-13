package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.example.security.CryptoManager
import java.io.ByteArrayOutputStream

object ImageAttachmentHelper {
    private const val MAX_DIMENSION_PX = 640

    /**
     * Downsamples and compresses a picked photo/receipt (< 85 KB), then encrypts with AES-256-GCM.
     */
    fun compressAndEncryptUri(context: Context, uri: Uri, passkey: String): String? {
        return try {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }

            var sampleSize = 1
            val maxEdge = maxOf(bounds.outWidth, bounds.outHeight)
            while (maxEdge / sampleSize > MAX_DIMENSION_PX * 2) {
                sampleSize *= 2
            }

            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val rawBitmap = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOpts)
            } ?: return null

            val scaled = scaleDown(rawBitmap, MAX_DIMENSION_PX)
            if (scaled != rawBitmap) rawBitmap.recycle()

            val out = ByteArrayOutputStream()
            val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP
            }
            var quality = 72
            do {
                out.reset()
                scaled.compress(format, quality, out)
                quality -= 12
            } while (out.size() > 85_000 && quality >= 30)

            scaled.recycle()
            val encrypted = CryptoManager.encryptBytes(out.toByteArray(), passkey)
            encrypted.takeIf { it.length <= 145_000 }
        } catch (_: Exception) {
            null
        }
    }

    fun decryptToImageBitmap(encryptedPayload: String, passkey: String): ImageBitmap? {
        if (encryptedPayload.isBlank()) return null
        val bytes = CryptoManager.decryptBytes(encryptedPayload, passkey) ?: return null
        return try {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }

    private fun scaleDown(bitmap: Bitmap, maxEdge: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxEdge && height <= maxEdge) return bitmap
        val ratio = width.toFloat() / height.toFloat()
        val (targetW, targetH) = if (width >= height) {
            maxEdge to (maxEdge / ratio).toInt().coerceAtLeast(1)
        } else {
            (maxEdge * ratio).toInt().coerceAtLeast(1) to maxEdge
        }
        return Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
    }
}
