package com.mgafk.app.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Turns an image picked for a bug report into a screenshot the backend accepts:
 * decoded, downscaled so its longest side is at most [MAX_SIDE], and re-encoded as
 * JPEG until it fits [TARGET_BYTES]. The backend's hard limit is 750 000 decoded
 * bytes per screenshot, and a request body is capped at 1 MB once in base64.
 */
object ScreenshotEncoder {
    private const val MAX_SIDE = 1600
    private const val TARGET_BYTES = 600_000
    private const val THUMBNAIL_SIDE = 192
    private const val MIME_JPEG = "image/jpeg"
    private val QUALITIES = intArrayOf(85, 75, 65, 55)
    /** Times the image is shrunk further when even the lowest quality is too heavy. */
    private const val MAX_SHRINKS = 4
    private const val SHRINK_FACTOR = 0.75f

    /** An encoded screenshot, plus a small bitmap for the form's preview. */
    class Encoded(
        val bytes: ByteArray,
        val mime: String,
        val width: Int,
        val height: Int,
        val thumbnail: Bitmap,
    )

    /**
     * Blocking (decoding and compression): call off the main thread. Null when the
     * image cannot be read, or cannot be made small enough.
     */
    fun encode(context: Context, uri: Uri): Encoded? {
        val resolver = context.contentResolver

        // Size first, to decode a large photo at a fraction of its resolution.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val boundsStream = resolver.openInputStream(uri) ?: return null
        boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
        val longest = max(bounds.outWidth, bounds.outHeight)
        if (longest <= 0) return null
        var sample = 1
        while (longest / (sample * 2) >= MAX_SIDE) sample *= 2

        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val imageStream = resolver.openInputStream(uri) ?: return null
        val decoded: Bitmap = imageStream.use { BitmapFactory.decodeStream(it, null, options) } ?: return null

        // JPEG has no alpha: a transparent PNG would turn black where it is see-through.
        val opaque = if (decoded.hasAlpha()) flattenOnWhite(decoded) else decoded
        var bitmap = scaleToFit(opaque, MAX_SIDE)

        repeat(MAX_SHRINKS) {
            for (quality in QUALITIES) {
                val bytes = compress(bitmap, quality)
                if (bytes.size <= TARGET_BYTES) return encoded(bytes, bitmap)
            }
            bitmap = scaleBy(bitmap, SHRINK_FACTOR)
        }
        val last = compress(bitmap, QUALITIES.last())
        return if (last.size <= BugReportPayload.ATTACHMENT_MAX_BYTES) encoded(last, bitmap) else null
    }

    private fun encoded(bytes: ByteArray, bitmap: Bitmap): Encoded =
        Encoded(
            bytes = bytes,
            mime = MIME_JPEG,
            width = bitmap.width,
            height = bitmap.height,
            thumbnail = scaleToFit(bitmap, THUMBNAIL_SIDE),
        )

    private fun compress(bitmap: Bitmap, quality: Int): ByteArray {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        return out.toByteArray()
    }

    private fun scaleToFit(src: Bitmap, maxSide: Int): Bitmap {
        val longest = max(src.width, src.height)
        if (longest <= maxSide) return src
        return scaleBy(src, maxSide.toFloat() / longest)
    }

    private fun scaleBy(src: Bitmap, factor: Float): Bitmap {
        val width = (src.width * factor).roundToInt().coerceAtLeast(1)
        val height = (src.height * factor).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, width, height, true)
    }

    private fun flattenOnWhite(src: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(src, 0f, 0f, null)
        return out
    }
}
