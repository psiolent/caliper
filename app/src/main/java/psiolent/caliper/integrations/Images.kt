package psiolent.caliper.integrations

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * A photo held for the current turn: the full image sent to the model, and a small
 * one kept for the thumbnail strip. Neither is ever persisted.
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
        val decoded = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            ?: return@withContext null
        try {
            Attachment(dataUrl = encode(decoded, MAX_EDGE), thumbnail = encode(decoded, THUMB_EDGE))
        } finally {
            decoded.recycle()
        }
    }

    /** Decode the small copy for display. */
    fun decodeThumbnail(attachment: Attachment): Bitmap? = runCatching {
        val bytes = Base64.decode(attachment.thumbnail.substringAfter(','), Base64.DEFAULT)
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
