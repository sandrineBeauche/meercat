package org.sbm4j.meercat.nodes

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import org.sbm4j.meercat.childScope

/**
 * Represents an element of the Meercat topology that has a managed coroutine lifecycle.
 *
 * A [Controllable] owns a coroutine [scope] that is created as a child of a given parent scope
 * when [start] is called, and cancelled when [stop] is called. This allows the topology
 * to start and stop all its elements in a coordinated fashion.
 */
interface Controllable {

    /**
     * The coroutine scope owned by this element, used to launch and manage its internal coroutines.
     * Initialized by [start].
     */
    var scope: CoroutineScope

    /**
     * Starts this element by creating a child coroutine scope from [parentScope] and setting up
     * the flow collectors that will handle incoming messages.
     *
     * The returned [Job] is a short-lived setup job that completes once all collectors are
     * properly registered. The collectors themselves keep running within [scope] for as long
     * as the element is active, independently of the returned job.
     *
     * Implementations should override this method to register their collectors within [scope].
     *
     * @param parentScope the coroutine scope to use as parent for this element's internal scope
     * @param rootName the name to assign to the child scope, defaults to an empty string
     * @return a setup [Job] that completes once all collectors are in place, or `null` if
     * no setup is needed
     */
    suspend fun start(parentScope: CoroutineScope, rootName: String = ""): Job? {
        scope = childScope(parentScope, rootName)
        return null
    }

    /**
     * Stops this element by cancelling its coroutine [scope], which also cancels
     * all collectors running within it.
     */
    suspend fun stop() {
        this.scope.cancel()
    }
}