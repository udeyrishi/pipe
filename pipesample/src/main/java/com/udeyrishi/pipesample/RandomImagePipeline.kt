package com.udeyrishi.pipesample

import android.graphics.Bitmap
import com.udeyrishi.pipe.buildPipeline

data class ImagePipelineMember(val url: String? = null, val image: Bitmap? = null)

private const val SCALED_IMAGE_SIZE = 400

fun makePipeline() = buildPipeline<ImagePipelineMember> {
        setRepository(App.jobsRepo)
        setLogger(App.logger)

        addStep("download", attempts = 4) {
            ImagePipelineMember(image = downloadImage(it.url!!))
        }

        addStep("rotate") {
            ImagePipelineMember(image = rotateBitmap(it.image!!, 90.0f))
        }

        addStep("scale") {
            ImagePipelineMember(image = scale(it.image!!, SCALED_IMAGE_SIZE, SCALED_IMAGE_SIZE))
        }

        addCountedBarrier("overdraw", capacity = Int.MAX_VALUE) { allMembers ->
            allMembers.mapIndexed { index, member ->
                val siblingIndex = if (index == 0) allMembers.lastIndex else index - 1
                val resultingImage = overdraw(member.image!!, allMembers[siblingIndex].image!!)
                ImagePipelineMember(image = resultingImage)
            }
        }
    }
