package com.mewname.app

import androidx.annotation.MainThread
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** One read at a time; cancellation invalidates callbacks before releasing the job.
 * Start/cancel and UI effects run on Main. Workers may retain a Request for posted callbacks.
 */
internal class CurrentReadTask {
    @Volatile private var current: Request? = null
    private var job: Job? = null

    inner class Request internal constructor() {
        val isCurrent: Boolean get() = current === this
        fun ensureCurrent() {
            if (!isCurrent) throw CancellationException("Read superseded")
        }
    }

    @MainThread
    fun launch(scope: CoroutineScope, block: suspend (Request) -> Unit) {
        if (job?.isActive == true) return
        val request = Request()
        current = request
        // Assign before starting: Main.immediate can complete the block synchronously.
        val next = scope.launch(start = CoroutineStart.LAZY) {
            try {
                block(request)
            } finally {
                if (request.isCurrent) {
                    current = null
                    job = null
                }
            }
        }
        job = next
        next.start()
    }

    @MainThread
    fun cancel() {
        current = null
        val previous = job
        job = null
        previous?.cancel()
    }
}
