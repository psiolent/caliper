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

object Images {

    private const val MAX_EDGE = 1024
    private const val QUALITY = 80

    /** Where a camera capture is written before it is read back and downscaled. */
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
     * Downscale a picked image to a JPEG data URL. Photos are the most expensive
     * thing we send, so they are capped in size before they ever reach the model.
     */
    suspend fun toDataUrl(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        val decoded = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            ?: return@withContext null
        val scale = MAX_EDGE.toFloat() / maxOf(decoded.width, decoded.height, 1)
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt(), (decoded.height * scale).toInt(), true)
        } else {
            decoded
        }
        val bytes = ByteArrayOutputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
            out.toByteArray()
        }
        if (scaled !== decoded) scaled.recycle()
        decoded.recycle()
        "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
}
