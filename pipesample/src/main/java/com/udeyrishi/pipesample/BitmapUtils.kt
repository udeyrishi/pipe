package com.udeyrishi.pipesample

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.URL

private const val SCALED_IMAGE_SIZE = 400

internal fun downloadImage(stringUrl: String): Bitmap {
    val url = URL(stringUrl)
    val inputStream = url.content as InputStream
    val buffer = ByteArray(8192)
    var bytesRead: Int
    val bytes = ByteArrayOutputStream().use {
        bytesRead = inputStream.read(buffer)
        while (bytesRead != -1) {
            it.write(buffer, 0, bytesRead)
            bytesRead = inputStream.read(buffer)
        }

        it.toByteArray()
    }

    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}

internal fun rotateBitmap(source: Bitmap, angle: Float): Bitmap {
    val matrix = Matrix()
    matrix.postRotate(angle)
    return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
}

internal fun join(firstImage: Bitmap, secondImage: Bitmap): Bitmap {
    val result = Bitmap.createBitmap(SCALED_IMAGE_SIZE, SCALED_IMAGE_SIZE, firstImage.config)
    val scaledFirst = Bitmap.createScaledBitmap(firstImage, SCALED_IMAGE_SIZE, SCALED_IMAGE_SIZE, true)
    val scaledSecond = Bitmap.createScaledBitmap(secondImage, SCALED_IMAGE_SIZE/3, SCALED_IMAGE_SIZE/3, true)

    val canvas = Canvas(result)
    canvas.drawBitmap(scaledFirst, 0f, 0f, null)
    canvas.drawBitmap(scaledSecond, 0f, 0f, null)

    return result
}
