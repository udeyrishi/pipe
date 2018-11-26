<p align="center"><img src="docs/assets/logo.png" width="300px"/></p>

-----------------
[![Build Status](https://travis-ci.org/udeyrishi/pipe.svg?branch=master)](https://travis-ci.org/udeyrishi/pipe)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Download](https://api.bintray.com/packages/udeyrishi/pipe/pipe/images/download.svg)](https://bintray.com/udeyrishi/pipe/pipe/_latestVersion)


Pipe is an Android library for building pipelines for executing background tasks.

* **Declarative:** Uses a Kotlin DSL or builder to declare pipeline schematics.
* **Powerful:** Supports both simple steps and complex synchronization structures like barriers and aggregators.
* **Resilient:** Pipe has resiliency baked-in. Has support for step retries in case of failures.
* **Android arch-friendly:** The API is designed with the Android architecture components in mind, making integration into your apps easy.


### Example

Consider a use case where we want to construct the following pipeline:

1. Wait for a UI signal to start the pipeline (i.e., button click to lift the manual barrier)
1. Download an image from the given URL
1. Rotate the downloaded image by 90 degrees
1. Scale the image to a 400px x 400px size
1. Overdraw the scaled image on top if its sibling

<img src="docs/assets/sample_app_demo.gif" width=300/>

Pipe uses a Kotlin DSL to declare pipeline schematics. We can express the above pipeline as:

```kt
data class ImagePipelineMember(val url: String? = null, val image: Bitmap? = null)
val JOBS_REPO = InMemoryRepository<Job<ImagePipelineMember>>()
val LOGGER = AndroidLogger("Pipe")

fun makePipeline() = buildPipeline<ImagePipelineMember> {
    setRepository(JOBS_REPO)
    setLogger(LOGGER)

    addManualBarrier("start_barrier")

    addStep("download", attempts = 4) {
        ImagePipelineMember(image = downloadImage(it.url!!))
    }

    addStep("rotate") {
        ImagePipelineMember(image = rotateBitmap(it.image!!, 90.0f))
    }

    addStep("scale") {
        ImagePipelineMember(image = scale(it.image!!, 400, 400))
    }

    addCountedBarrier("overdraw", capacity = Int.MAX_VALUE) { allMembers ->
        allMembers.mapIndexed { index, member ->
            val siblingIndex = if (index == 0) allMembers.lastIndex else index - 1
            val resultingImage = overdraw(member.image!!, allMembers[siblingIndex].image!!)
            ImagePipelineMember(image = resultingImage)
        }
    }
}
```

We can then use this factory function to create an instance of the pipeline:

```kt
val pipeline = makePipeline()
```

And then schedule jobs into it:

```kt
const val JOB_TAG = "IMAGE_TRANSFORM_JOBS"

fun Pipeline<ImagePipelineMember>.createImageJobs(imageUrls: List<String>): List<Job<ImagePipelineMember>> {
    countedBarriers.forEach {
        it.setCapacity(imageUrls.size)
    }

    return imageUrls.map { url ->
        push(ImagePipelineMember(url = url), tag = JOB_TAG)
    }
}
```

We can subscribe to these jobs' state changes in our activities/fragments via `LiveData`:

```kt
val jobs = pipeline.createImageJobs(urls)
jobs.forEach { job ->
    job.state.observe(this, Observer {
        // Update UI in response to state changes
    })
}
```

See the [state machine](#state-machine) for the details.

We can also fetch any ongoing jobs from the repository (e.g., if the fragment is re-created):


```kt
JOBS_REPO[JOB_TAG].forEach { (job, _, _) ->
    // Perform actions on the jobs, such as interrupting them, unsubscribing from them, etc.
}
```

And finally, we can start the pipeline when a UI button is clicked:

```kt
someButton.setOnClickListener {
    pipeline.manualBarriers.first().lift()
}
```

### State machine

The progress of a job is encoded via a state machine:

<img src="docs/assets/state_machine.svg"/>

### Learn more

To see a full Android app example using the above pipeline, see the [sample app](pipesample/). Or see the javadocs [here](https://www.udeyrishi.com/pipe/).
