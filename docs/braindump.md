### Basic Requirements
- Each pipeline has states: `Scheduled`, `Running.Attempting`, `Running.AttemptFailed`, `Running.AttemptSuccessful`, `Terminal.Success`, and `Terminal.Failure`:

```
      (start)Scheduled ---> Attempting(x) ------------------> AttemptSuccessful(x) ------> RunningStep(y) ........ -> Success (end)
                |           ^         |                               |                                                 |
                |           | k times |                           Unexpected error                                     Unexpected error
                |           |         v                               v                                                 V
                |          AttemptFailed(x)----- k + 1th time ------->-----------------------------------------------Failure (end)
                |                                                     ^
                |------------Scheduling error-------------------------|
```
- Add Steps in the pipeline
    - Each step (x, y above) has the `Attempting`, `AttemptFailed`, and `AttemptSuccessful` states associated with it
    - The actual step for the instances of these states will be stored as metadata on the state objects

- Pipeline takes an input object, produces an output object, optionally has side effects.
- Pipeline is only a prototype of what's going on. The same pipeline object can be used multiple times.
- Pipeline doesn't hold any state by itself. You may optionally make them have state (because the steps are simply lambdas, see (1)).
- Multiple objects can be pushed into the same pipeline. Their results will emerge out whenever they're done.
	- FIFO is not guaranteed. The results will have a unique ID identifying their inputs. 

### Pipeline
```kt

// default fifo = false. Emit as soon as they are done
val pipeline: Pipeline<T> = buildPipeline(fifo = true) {
	 // (1) You can keep state here to track the number of objects that passed through the pipeline.
	 
    // Step 1
    addStep { input ->
        
        // Process input. Whether you want to keep the input/output immutable, or mutate them
        // in place, it's up to you.
        return@addStep output
    }

    // Step 2
    // Default attempt = 1
    addStep(attempts = 5) { input ->
			return@addStep output
    }
    
    addBarrier()

    // Step 3
    addStep { input ->
    		// This RV will be the result of the pipeline
			return@addStep output
    }
    
    addBarrier()
    
    // A special barrier, that'll keep holding items until the limit is reached.
    // Then, it'll put them in a list, and pass to the aggregator.
    
    // Any remaining steps will be continued on the return value of `inputs`.
    // If the pipeline has fifo = true, inputs will be sorted by the sequence number.
    // Else, it'll be sorted in the order of arrival to this step.
    
    // The RV will be executed (for any remaining steps) in a sorted order, or the return value's
    // order based on whether fifo = true or false, respectively. 
    addAggregator(limit = 10) { inputs ->
        return@addAggregator inputs
    }
} 

val orchestrator: Orchestrator<T> = pipeline.push(someInput)

// Each orchestrator has a UUID and a sequence number. The pipeline can be polled for orchestrator by the UUID.
// The sequence number can be used for sorting the outputs, since it's not a FIFO by default.

val anotherRefToOrchestrator = pipeline[uuid]
val orchestrators = pipeline.orchestrators

// Lifts the barrier for all currently blocked, and future arriving items
pipeline.barriers.forEach { it.lift() }
```


### Orchestrator
```kt

// Starts asynchronously on a background thread
orchestrator.run {
    // Also optionally attach a state listener.
    // These listeners can be used for reactively gathering the results vs. polling
    println("New State: $it")
}

// Calling run 2x will call IllegalStateException

// State listener API
orchestrator.unsubscribe(listener)
orchestrator.unsubscribeAll()
orchestrator.subscribe()

// If any of the state listeners above throw an exception, stop executing the steps, and move to the Failure state.
// That's basically what the "unexpected errors" in the above state diagram are.

// Polling API
orchestrator.state
orchestrator.result // the result if the state is Success, else null

// Execution API
orchestrator.run() // Same as above
orchestrator.interrupt() // Will try to quit the current step. Will definitely stop before the next step is run.
```