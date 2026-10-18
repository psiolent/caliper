package psiolent.caliper.integrations

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * A photo attached to a turn: the full image sent to the model, and a small copy.
 * The full image is never persisted. The small copy is kept on the message, so the
 * transcript can show what was sent for about as little as a thumbnail costs.
 */
data class Attachment(val dataUrl: String, val thumbnail: String)

object Images {

    private const val MAX_EDGE = 1024
    private const val THUMB_EDGE = 160
    private const val QUALITY = 80

    /** Where a camera capture is written before it is read back. */
    fun newCaptureFile(context: Context): File {
        val directory = File(context.cacheDir, "captures").apply { mkdirs() }
        return File(directory, "meal-${System.currentTimeMillis()}.jpg")
    }

    /** Remove a capture once it has been read, so the cache does not accumulate meals. */
    fun discardCapture(context: Context, uri: Uri) {
        runCatching {
            if (uri.authority == "${context.packageName}.fileprovider") {
                File(context.cacheDir, "captures/${File(uri.path ?: "").name}").delete()
            }
        }
    }

    /**
     * Read a picked or captured photo. Photos are the most expensive thing we send,
     * so the full copy is capped in size before it ever reaches the model.
     */
    suspend fun toAttachment(context: Context, uri: Uri): Attachment? = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return@withContext null
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@withContext null
        val upright = applyExifOrientation(bytes, decoded)
        try {
            Attachment(dataUrl = encode(upright, MAX_EDGE), thumbnail = encode(upright, THUMB_EDGE))
        } finally {
            upright.recycle()
        }
    }

    /**
     * Cameras store photos in the sensor's orientation and record how to turn them
     * upright in an EXIF tag that decoding ignores, so a portrait shot decodes on
     * its side. Apply the tag before anything else sees the pixels.
     */
    private fun applyExifOrientation(bytes: ByteArray, bitmap: Bitmap): Bitmap {
        val orientation = runCatching {
            ExifInterface(ByteArrayInputStream(bytes))
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return bitmap
        }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }

    /** Decode a small copy for display. */
    fun decodeThumbnail(dataUrl: String): Bitmap? = runCatching {
        val bytes = Base64.decode(dataUrl.substringAfter(','), Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()

    private fun encode(source: Bitmap, maxEdge: Int): String {
        val scale = maxEdge.toFloat() / maxOf(source.width, source.height, 1)
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(source, (source.width * scale).toInt(), (source.height * scale).toInt(), true)
        } else {
            source
        }
        val bytes = ByteArrayOutputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
            out.toByteArray()
        }
        if (scaled !== source) scaled.recycle()
        return "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
}
