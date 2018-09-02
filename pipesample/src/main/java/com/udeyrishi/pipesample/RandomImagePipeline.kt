package com.udeyrishi.pipesample

import android.graphics.Bitmap
import com.udeyrishi.pipe.Job
import com.udeyrishi.pipe.Pipeline
import com.udeyrishi.pipe.buildPipeline
import com.udeyrishi.pipe.repository.MutableRepository

class ImagePipelineMember(val index: Int, private val url: String? = null, private val image: Bitmap? = null) {
    fun getBitmap(): Bitmap = image ?: throw IllegalStateException("image must've been read.")
    fun getUrl(): String = url ?: throw IllegalStateException("URL must've been non-null.")
}

private const val SCALED_IMAGE_SIZE = 400

fun makePipeline(repository: MutableRepository<Job<ImagePipelineMember>>): Pipeline<ImagePipelineMember> {
    return buildPipeline(repository) {
        addStep("download", attempts = 4) {
            ImagePipelineMember(index = it.index, image = downloadImage(it.getUrl()))
        }

        addStep("rotate") {
            ImagePipelineMember(index = it.index, image = rotateBitmap(it.getBitmap(), 90.0f))
        }

        addStep("scale") {
            ImagePipelineMember(index = it.index, image = scale(it.getBitmap(), SCALED_IMAGE_SIZE, SCALED_IMAGE_SIZE))
        }

        addAggregator("overdraw", capacity = Int.MAX_VALUE) { allMembers ->
            allMembers.map { member ->
                val siblingIndex = member.index + (if (member.index % 2 == 0) 1 else -1)
                val resultingImage = if (siblingIndex < allMembers.size) {
                    overdraw(member.getBitmap(), allMembers[siblingIndex].getBitmap())
                } else {
                    member.getBitmap()
                }
                ImagePipelineMember(index = member.index, image = resultingImage)
            }
        }
    }
}
