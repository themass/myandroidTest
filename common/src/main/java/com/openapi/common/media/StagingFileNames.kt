package com.openapi.common.media

import java.util.concurrent.atomic.AtomicLong

/** Process-wide monotonic stamps for names created in concurrently written staging directories. */
object StagingFileNames {
    private val lastStamp = AtomicLong(0L)

    fun nextStamp(): Long {
        while (true) {
            val previous = lastStamp.get()
            val candidate = maxOf(System.currentTimeMillis(), previous + 1L)
            if (lastStamp.compareAndSet(previous, candidate)) return candidate
        }
    }
}
