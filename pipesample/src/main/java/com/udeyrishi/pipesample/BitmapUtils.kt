package com.udeyrishi.pipesample

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.URL

internal fun downloadImage(stringUrl: String): Bitmap {
    val url = URL(stringUrl)
    return (url.content as InputStream).use { inputStream ->
        val bytes = inputStream.readBytes()
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }
}

private fun InputStream.readBytes(): ByteArray {
    return ByteArrayOutputStream().use {
        val buffer = ByteArray(8192)
        var bytesRead = read(buffer)
        while (bytesRead != -1) {
            it.write(buffer, 0, bytesRead)
            bytesRead = read(buffer)
        }

        it.toByteArray()
    }
}

internal fun rotateBitmap(source: Bitmap, angle: Float): Bitmap {
    val matrix = Matrix()
    matrix.postRotate(angle)
    return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
}

internal fun scale(image: Bitmap, width: Int, height: Int): Bitmap {
    return Bitmap.createScaledBitmap(image, width, height, true)
}

internal fun overdraw(firstImage: Bitmap, secondImage: Bitmap): Bitmap {
    val result = Bitmap.createBitmap(firstImage.width, firstImage.height, firstImage.config)
    val scaledSecond = scale(secondImage, firstImage.width / 3, firstImage.height / 3)

    val canvas = Canvas(result)
    canvas.drawBitmap(firstImage, 0f, 0f, null)
    canvas.drawBitmap(scaledSecond, 0f, 0f, null)

    return result
}
