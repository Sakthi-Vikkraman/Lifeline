package com.lifeline.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** Camera captures land in cache first, then get downscaled, rotated upright, and moved into app storage. */
object Photos {
    private const val MAX_EDGE = 1600

    fun newCaptureUri(context: Context): Uri {
        val dir = File(context.cacheDir, "captures").apply { mkdirs() }
        val file = File(dir, "capture-${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }

    suspend fun importToStorage(context: Context, source: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_EDGE) sample *= 2
            val decoded = resolver.openInputStream(source)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return@runCatching null

            val rotation = resolver.openInputStream(source)?.use { ExifInterface(it).rotationDegrees } ?: 0
            val scale = minOf(1f, MAX_EDGE.toFloat() / maxOf(decoded.width, decoded.height))
            val matrix = Matrix().apply { postScale(scale, scale); postRotate(rotation.toFloat()) }
            val upright = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)

            val dir = File(context.filesDir, "photos").apply { mkdirs() }
            val out = File(dir, "${UUID.randomUUID()}.jpg")
            out.outputStream().use { upright.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            out.absolutePath
        }.getOrNull()
    }

    fun clearCaptures(context: Context) {
        File(context.cacheDir, "captures").listFiles()?.forEach { it.delete() }
    }

    fun delete(path: String?) {
        if (path != null) File(path).delete()
    }
}
