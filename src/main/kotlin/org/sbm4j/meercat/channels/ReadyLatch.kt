package org.sbm4j.meercat.channels

import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.atomic.AtomicInteger

/**
 * A coroutine-friendly countdown latch that suspends until all expected signals have been received.
 *
 * Callers first declare how many signals to expect via [increment], then each signal source
 * calls [signal] when ready. Once all expected signals have been received, any coroutine
 * suspended on [await] is resumed.
 *
 * Typical usage: ensuring all flow collectors are active before proceeding.
 */
class ReadyLatch {

    /**
     * The number of signals expected before [await] completes.
     */
    private val expected = AtomicInteger(0)

    /**
     * The number of signals received so far.
     */
    private val received = AtomicInteger(0)

    /**
     * Completed when [received] reaches [expected].
     */
    private val deferred = CompletableDeferred<Unit>()

    /**
     * Increments the number of expected signals by one.
     * Must be called before the corresponding [signal] call.
     */
    fun increment() {
        expected.incrementAndGet()
    }

    /**
     * Registers one signal as received.
     * When all expected signals have been received, resumes any coroutine suspended on [await].
     */
    fun signal() {
        if (received.incrementAndGet() == expected.get())
            deferred.complete(Unit)
    }

    /**
     * Suspends until all expected signals have been received.
     * Returns immediately if no signals were expected.
     */
    suspend fun await() {
        if (expected.get() > 0)
            deferred.await()
    }
}