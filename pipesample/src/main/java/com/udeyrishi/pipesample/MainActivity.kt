package com.udeyrishi.pipesample

import android.arch.lifecycle.Observer
import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.widget.ImageView
import com.udeyrishi.pipe.Job
import com.udeyrishi.pipe.State
import kotlinx.android.synthetic.main.activity_main.root

class MainActivity : AppCompatActivity() {
    companion object {
        private const val JOB_TAG = "IMAGE_TRANSFORM_JOBS"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val imageUrls = listOf(
                "http://pre00.deviantart.net/ac5d/th/pre/f/2013/020/1/d/cat_stock_3_by_manonvr-d5s437u.jpg",
                "http://i1.wp.com/ivereadthis.com/wp-content/uploads/2018/03/IMG_0340.jpg",
                "http://i2.wp.com/debsrandomwritings.com/wp-content/uploads/2015/09/Ugg-with-stitches1.jpg",
                "http://5v9xs33wvow7hck7-zippykid.netdna-ssl.com/wp-content/uploads/2011/05/Cat-Urine-300x293.jpg",
                "http://g77v3827gg2notadhhw9pew7-wpengine.netdna-ssl.com/wp-content/uploads/2017/03/what-to-do-cat-seizure_canna-pet-e1490730066628-1024x681.jpg"
        )

        val jobs = if (App.INSTANCE.jobsRepo[JOB_TAG].isEmpty()) {
            createImageJobs(imageUrls)
        } else {
            App.INSTANCE.jobsRepo[JOB_TAG].map { it.identifiableObject }
        }

        jobs.forEachIndexed { index, job ->
            job.state.observe(this, Observer {
                onStateChanged(it, index, job)
            })

            job.start()
        }
    }

    private fun onStateChanged(state: State?, index: Int, job: Job<ImagePipelineMember>) {
        when (state) {
            is State.Running, State.Scheduled -> {
                val drawable = ContextCompat.getDrawable(this, R.drawable.ic_arrow_downward_black_24dp)
                (root.getChildAt(index) as ImageView).setImageDrawable(drawable)
            }
            is State.Terminal.Success -> {
                (root.getChildAt(index) as ImageView).setImageBitmap(job.result?.getBitmap())
            }
            is State.Terminal.Failure -> {
                val drawable = ContextCompat.getDrawable(this, R.drawable.ic_error_black_24dp)
                (root.getChildAt(index) as ImageView).setImageDrawable(drawable)
            }
        }
    }

    private fun createImageJobs(imageUrls: List<String>): List<Job<ImagePipelineMember>> {
        val pipeline = makePipeline(App.INSTANCE.jobsRepo)
        pipeline.aggregators.forEach {
            it.capacity = imageUrls.size
        }

        return imageUrls.map { url ->
            pipeline.push(ImagePipelineMember(url = url), tag = JOB_TAG).also { job ->
                job.logger = App.INSTANCE.logger
            }
        }
    }
}
