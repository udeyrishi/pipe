package com.udeyrishi.pipesample

import android.graphics.Bitmap
import com.udeyrishi.pipe.Job
import com.udeyrishi.pipe.Pipeline
import com.udeyrishi.pipe.buildPipeline
import com.udeyrishi.pipe.repository.MutableRepository

class ImagePipelineMember(private val url: String? = null, private val image: Bitmap? = null) {
    fun getBitmap(): Bitmap = image ?: throw IllegalStateException("image must've been read.")
    fun getUrl(): String = url ?: throw IllegalStateException("URL must've been non-null.")
}

private const val SCALED_IMAGE_SIZE = 400

fun makePipeline(repository: MutableRepository<Job<ImagePipelineMember>>): Pipeline<ImagePipelineMember> {
    return buildPipeline {
        setRepository(repository)

        addStep("download", attempts = 4) {
            ImagePipelineMember(image = downloadImage(it.getUrl()))
        }

        addStep("rotate") {
            ImagePipelineMember(image = rotateBitmap(it.getBitmap(), 90.0f))
        }

        addStep("scale") {
            ImagePipelineMember(image = scale(it.getBitmap(), SCALED_IMAGE_SIZE, SCALED_IMAGE_SIZE))
        }

        addCountedBarrier("overdraw", capacity = Int.MAX_VALUE) { allMembers ->
            allMembers.mapIndexed { index, member ->
                val siblingIndex = if (index == 0) allMembers.lastIndex else index - 1
                val resultingImage = overdraw(member.getBitmap(), allMembers[siblingIndex].getBitmap())
                ImagePipelineMember(image = resultingImage)
            }
        }
    }
}
