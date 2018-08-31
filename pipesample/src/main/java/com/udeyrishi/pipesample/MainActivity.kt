package com.udeyrishi.pipesample

import android.annotation.SuppressLint
import android.arch.lifecycle.Observer
import android.graphics.Bitmap
import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.widget.ImageView
import com.udeyrishi.pipe.Job
import com.udeyrishi.pipe.Pipeline
import com.udeyrishi.pipe.State
import com.udeyrishi.pipe.buildPipeline
import com.udeyrishi.pipe.repository.InMemoryRepository
import com.udeyrishi.pipe.steps.Aggregator
import kotlinx.android.synthetic.main.activity_main.root

class MainActivity : AppCompatActivity() {
    companion object {
        private const val JOB_TAG = "IMAGE_TRANSFORM_JOBS"
        private const val LOG_TAG = "Pipe Sample"
    }

    private val imageUrls = listOf(
            "http://www.yevol.com/en/visualstudio/resources/horizon.jpg",
            "http://i1.wp.com/ivereadthis.com/wp-content/uploads/2018/03/IMG_0340.jpg",
            "http://i2.wp.com/debsrandomwritings.com/wp-content/uploads/2015/09/Ugg-with-stitches1.jpg",
            "http://5v9xs33wvow7hck7-zippykid.netdna-ssl.com/wp-content/uploads/2011/05/Cat-Urine-300x293.jpg",
            "http://g77v3827gg2notadhhw9pew7-wpengine.netdna-ssl.com/wp-content/uploads/2017/03/what-to-do-cat-seizure_canna-pet-e1490730066628-1024x681.jpg"
    )

    private val repository = InMemoryRepository<Job<ImagePipelineMember>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }

    @SuppressLint("SetTextI18n")
    override fun onResume() {
        super.onResume()
        val (aggregator, pipeline) = makePipeline(repository)

        aggregator.capacity = imageUrls.size

        val jobs = imageUrls.mapIndexed { index, url ->
            pipeline.push(ImagePipelineMember(index = index, url = url), tag = JOB_TAG)
        }

        jobs.forEachIndexed { index, job ->
            job.state.observe(this, Observer { state ->
                Log.i(LOG_TAG, "${job.uuid} changed to state $state.")

                (state as? State.Running.AttemptFailed)?.cause?.let(::logThrowable)
                (state as? State.Terminal.Failure)?.causes?.forEach(::logThrowable)

                when (state) {
                    is State.Running, State.Scheduled -> {
                        val drawable = ContextCompat.getDrawable(this, R.drawable.ic_arrow_downward_black_24dp)
                        (root.getChildAt(index) as ImageView).setImageDrawable(drawable)
                    }
                    is State.Terminal.Success -> {
                        (root.getChildAt(index) as ImageView).setImageBitmap(job.result?.image)
                    }
                    is State.Terminal.Failure -> {
                        val drawable = ContextCompat.getDrawable(this, R.drawable.ic_error_black_24dp)
                        (root.getChildAt(index) as ImageView).setImageDrawable(drawable)
                    }
                }
            })
        }

        jobs.forEach {
            it.start()
        }
    }

    override fun onPause() {
        super.onPause()
        repository[JOB_TAG].forEach {
            it.identifiableObject.interrupt()
        }

        repository.removeIf {
            true
        }
    }

    private fun logThrowable(throwable: Throwable) {
        var t: Throwable? = throwable
        while (t != null) {
            Log.e(LOG_TAG, t.toString())
            t = t.cause
        }
    }
}

private fun makePipeline(repository: InMemoryRepository<Job<ImagePipelineMember>>): Pair<Aggregator, Pipeline<ImagePipelineMember>> {
    lateinit var aggregator: Aggregator
    val pipeline = buildPipeline(repository) {
        addStep("read-drawable", attempts = 4) {
            val image = downloadImage(it.url ?: throw IllegalStateException("URL must've been non-null."))
            ImagePipelineMember(index = it.index, image = image)
        }

        addStep("rotate") {
            val drawable = it.image ?: throw IllegalStateException("image must've been read.")
            val rotated = rotateBitmap(drawable, 90.0f)
            ImagePipelineMember(index = it.index, image = rotated)
        }

        aggregator = addAggregator(capacity = Int.MAX_VALUE) {
            it.map { (index, _, image) ->
                val siblingIndex = if (index % 2 == 0) (index + 1) else (index - 1)
                if (siblingIndex < it.size) {
                    val joined = join(
                            image ?: throw IllegalStateException("image must've been read."),
                            it[siblingIndex].image ?: throw IllegalStateException("image must've been read.")
                    )
                    ImagePipelineMember(index = index, image = joined)
                } else {
                    ImagePipelineMember(index = index, image = image)
                }
            }
        }
    }

    return aggregator to pipeline
}

private data class ImagePipelineMember(val index: Int, val url: String? = null, val image: Bitmap? = null)
