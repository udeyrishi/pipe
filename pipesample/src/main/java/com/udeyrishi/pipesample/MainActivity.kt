package com.udeyrishi.pipesample

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import com.udeyrishi.pipe.Job
import com.udeyrishi.pipe.Pipeline
import com.udeyrishi.pipe.State
import kotlinx.android.synthetic.main.activity_main.image_container as imageContainer
import kotlinx.android.synthetic.main.activity_main.root_view as rootView

class MainActivity : AppCompatActivity() {
    companion object {
        private const val JOB_TAG = "IMAGE_TRANSFORM_JOBS"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        rootView.setOnRefreshListener {
            App.jobsRepo[JOB_TAG].forEach { (job, _, _) ->
                job.state.removeObservers(this)
                job.interrupt()
            }

            App.jobsRepo.removeIf {
                it.tag == JOB_TAG
            }

            val pipeline = runPipeline()
            pipeline.manualBarriers.first().lift()
        }

        val pipeline = runPipeline()
        rootView.findViewById<Button>(R.id.start_button).setOnClickListener {
            it.visibility = View.GONE
            rootView.isRefreshing = true
            pipeline.manualBarriers.first().lift()
        }
    }

    private fun runPipeline(): Pipeline<ImagePipelineMember> {
        val imageUrls = listOf(
                "http://pre00.deviantart.net/ac5d/th/pre/f/2013/020/1/d/cat_stock_3_by_manonvr-d5s437u.jpg",
                "http://i1.wp.com/ivereadthis.com/wp-content/uploads/2018/03/IMG_0340.jpg",
                "http://i2.wp.com/debsrandomwritings.com/wp-content/uploads/2015/09/Ugg-with-stitches1.jpg",
                "http://5v9xs33wvow7hck7-zippykid.netdna-ssl.com/wp-content/uploads/2011/05/Cat-Urine-300x293.jpg"
        )

        val pipeline = makePipeline()
        val jobs = pipeline.createImageJobs(imageUrls)

        jobs.forEachIndexed { index, job ->
            job.state.observe(this, Observer {
                onStateChanged(it, index, job)
            })
        }

        return pipeline
    }

    private fun onStateChanged(state: State?, index: Int, job: Job<ImagePipelineMember>) {
        val imageView = (imageContainer.getChildAt(index + 1) as ImageView)
        when (state) {
            is State.Running, State.Scheduled -> {
                val drawable = ContextCompat.getDrawable(this, R.drawable.ic_arrow_downward_black_24dp)
                imageView.setImageDrawable(drawable)
            }
            is State.Terminal.Success -> {
                rootView.isRefreshing = false
                imageView.setImageBitmap(job.result?.image)
            }
            is State.Terminal.Failure -> {
                rootView.isRefreshing = false
                val drawable = ContextCompat.getDrawable(this, R.drawable.ic_error_black_24dp)
                imageView.setImageDrawable(drawable)
            }
        }
    }

    private fun Pipeline<ImagePipelineMember>.createImageJobs(imageUrls: List<String>): List<Job<ImagePipelineMember>> {
        countedBarriers.forEach {
            it.setCapacity(imageUrls.size)
        }

        return imageUrls.map { url ->
            push(ImagePipelineMember(url = url), tag = JOB_TAG)
        }
    }
}
