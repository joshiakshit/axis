package com.ash.axis.ui.qr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File

@Suppress("TopLevelPropertyNaming")
internal const val EXTENDED_MAX_ZOOM = 10f

@Suppress("TopLevelPropertyNaming")
private const val SELFIE_MAX_DIMENSION = 640

@Suppress("TopLevelPropertyNaming")
private const val SELFIE_JPEG_QUALITY = 65

internal fun File.toJpegDataUri(): String {
    val compressed = compressImage(readBytes())
    return "data:image/jpeg;base64," + Base64.encodeToString(compressed, Base64.NO_WRAP)
}

internal fun Uri.toDataUri(context: Context): String? {
    val bytes =
        runCatching {
            context.contentResolver.openInputStream(this)?.use { it.readBytes() }
        }.getOrNull() ?: return null
    val compressed = compressImage(bytes)
    return "data:image/jpeg;base64," + Base64.encodeToString(compressed, Base64.NO_WRAP)
}

private fun compressImage(raw: ByteArray): ByteArray {
    val original = BitmapFactory.decodeByteArray(raw, 0, raw.size) ?: return raw
    val scaled = scaleDown(original, SELFIE_MAX_DIMENSION)
    val out = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, SELFIE_JPEG_QUALITY, out)
    if (scaled !== original) scaled.recycle()
    original.recycle()
    return out.toByteArray()
}

private fun scaleDown(
    bitmap: Bitmap,
    maxDim: Int,
): Bitmap {
    val w = bitmap.width
    val h = bitmap.height
    if (w <= maxDim && h <= maxDim) return bitmap
    val scale = maxDim.toFloat() / maxOf(w, h)
    return Bitmap.createScaledBitmap(bitmap, (w * scale).toInt(), (h * scale).toInt(), true)
}
