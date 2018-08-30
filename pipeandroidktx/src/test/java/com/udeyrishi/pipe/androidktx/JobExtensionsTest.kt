package com.udeyrishi.pipe.androidktx

import android.arch.lifecycle.DefaultLifecycleObserver
import android.arch.lifecycle.Lifecycle
import android.arch.lifecycle.LifecycleObserver
import android.arch.lifecycle.LifecycleOwner
import com.udeyrishi.pipe.buildPipeline
import com.udeyrishi.pipe.repository.InMemoryRepository
import com.udeyrishi.pipe.state.State
import com.udeyrishi.pipe.state.StateChangeListener
import com.udeyrishi.pipe.steps.Barrier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mockito.`when`
import org.mockito.Mockito.any
import org.mockito.Mockito.mock

@Suppress("EXPERIMENTAL_FEATURE_WARNING")
@RunWith(JUnit4::class)
class JobExtensionsTest {
    private val observers = mutableListOf<LifecycleObserver>()

    @Before
    fun setup() {
        observers.clear()
    }

    @Test
    fun subscribeSubscribesOnResume() {
        var currentState = Lifecycle.State.CREATED

        val mockLifecycle = mock(Lifecycle::class.java).apply {
            `when`(this.currentState).thenReturn(currentState)
            `when`(this.addObserver(any())).then {
                observers.add(it.arguments[0] as LifecycleObserver)
            }
            `when`(this.removeObserver(any())).then {
                observers.remove(it.arguments[0] as LifecycleObserver)
            }
        }

        val mockOwner = mock(LifecycleOwner::class.java)
        `when`(mockOwner.lifecycle).thenReturn(mockLifecycle)

        lateinit var barrier: Barrier
        val pipeline = buildPipeline<Int>(InMemoryRepository()) {
            addStep("Step 1") {
                it + 1
            }

            barrier = addBarrier()

            addStep("Step 2") {
                it + 2
            }
        }

        val job = pipeline.push(-3, null)

        assertEquals(0, observers.size)

        var lastSetPrevState: State? = null
        var lastSetNewState: State? = null
        job.subscribe(mockOwner, StateChangeListener { uuid, previousState, newState ->
            assertEquals(job.uuid, uuid)
            assertEquals(Lifecycle.State.RESUMED, currentState)
            lastSetPrevState = previousState
            lastSetNewState = newState
        })

        assertEquals(1, observers.size)

        // Orchestrator not started yet
        assertNull(lastSetPrevState)
        assertNull(lastSetNewState)

        job.start()

        while (barrier.blockedCount < 1) {
            Thread.sleep(100)
        }

        // States changed, but still no notifications, because the lifecycle is not RESUMED
        assertNull(lastSetPrevState)
        assertNull(lastSetNewState)

        currentState = Lifecycle.State.STARTED
        observers.forEach { (it as DefaultLifecycleObserver).onStart(mockOwner) }

        assertNull(lastSetPrevState)
        assertNull(lastSetNewState)

        currentState = Lifecycle.State.RESUMED
        observers.forEach { (it as DefaultLifecycleObserver).onResume(mockOwner) }

        assertNull(lastSetPrevState)
        assertNull(lastSetNewState)

        barrier.lift()

        while (job.state !is State.Terminal) {
            Thread.sleep(100)
        }

        assertTrue(lastSetPrevState is State.Running.AttemptSuccessful)
        assertTrue(lastSetNewState is State.Terminal.Success)
        assertEquals(0, job.result)
    }

    @Test
    fun subscribeUnsubscribesOnPause() {
        var currentState = Lifecycle.State.STARTED

        val mockLifecycle = mock(Lifecycle::class.java).apply {
            `when`(this.currentState).thenReturn(currentState)
            `when`(this.addObserver(any())).then {
                observers.add(it.arguments[0] as LifecycleObserver)
            }
            `when`(this.removeObserver(any())).then {
                observers.remove(it.arguments[0] as LifecycleObserver)
            }
        }

        val mockOwner = mock(LifecycleOwner::class.java)
        `when`(mockOwner.lifecycle).thenReturn(mockLifecycle)

        lateinit var barrier: Barrier
        val pipeline = buildPipeline<Int>(InMemoryRepository()) {
            addStep("Step 1") {
                it + 1
            }

            barrier = addBarrier()

            addStep("Step 2") {
                it + 2
            }
        }

        val job = pipeline.push(-3, null)

        assertEquals(0, observers.size)

        var lastSetPrevState: State? = null
        var lastSetNewState: State? = null
        job.subscribe(mockOwner, StateChangeListener { uuid, previousState, newState ->
            assertEquals(job.uuid, uuid)
            assertEquals(Lifecycle.State.RESUMED, currentState)
            lastSetPrevState = previousState
            lastSetNewState = newState
        })

        assertEquals(1, observers.size)

        observers.forEach { (it as DefaultLifecycleObserver).onStart(mockOwner) }
        currentState = Lifecycle.State.RESUMED
        observers.forEach { (it as DefaultLifecycleObserver).onResume(mockOwner) }

        job.start()

        while (barrier.blockedCount < 1) {
            Thread.sleep(100)
        }

        // Because lifecycle was RESUMED, we were receiving notifications
        assertTrue(lastSetPrevState is State.Running.AttemptSuccessful)
        assertTrue(lastSetNewState is State.Running.Attempting)

        // Reset to null so that we can verify later
        lastSetPrevState = null
        lastSetNewState = null

        currentState = Lifecycle.State.STARTED
        observers.forEach { (it as DefaultLifecycleObserver).onPause(mockOwner) }

        barrier.lift()

        while (job.state !is State.Terminal) {
            Thread.sleep(100)
        }

        // Stopped receiving notifications
        assertNull(lastSetPrevState)
        assertNull(lastSetNewState)

        assertEquals(0, job.result)
    }
}