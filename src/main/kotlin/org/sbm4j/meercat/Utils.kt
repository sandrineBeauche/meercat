package org.sbm4j.meercat

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job

/**
 * Creates a new [CoroutineScope] as a child of [parentScope], inheriting its coroutine context
 * while introducing a new [Job] and an optional [CoroutineName].
 *
 * The child scope's [Job] is linked to the parent's [Job], meaning:
 * - if the parent scope is cancelled, the child scope is cancelled as well.
 * - if the child scope is cancelled, the parent scope is not affected.
 *
 * This function is used throughout the Meercat topology to create dedicated scopes for
 * each node, ensuring that nodes can be stopped independently without affecting the rest
 * of the topology.
 *
 * @param parentScope the [CoroutineScope] to use as parent for the new scope
 * @param name the name to assign to the child scope's coroutine context, defaults to `"child"`
 * @return a new [CoroutineScope] that is a child of [parentScope]
 */
fun childScope(parentScope: CoroutineScope, name: String = "child"): CoroutineScope{
    val job = Job(parentScope.coroutineContext[Job])
    val scope =
        CoroutineScope(parentScope.coroutineContext
                + job
                + CoroutineName(name)
        )
    return scope
}

