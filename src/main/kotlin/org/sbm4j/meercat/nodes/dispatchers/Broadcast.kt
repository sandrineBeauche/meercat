package org.sbm4j.meercat.nodes.dispatchers

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import org.sbm4j.meercat.channels.sendSyncAll
import org.sbm4j.meercat.data.Send
import org.sbm4j.meercat.nodes.AbstractNode
import org.sbm4j.meercat.nodes.logger
import kotlin.reflect.KClass

/**
 * A [Propagator] that broadcasts each incoming [Send] message to all [channelOuts]
 * simultaneously, aggregates the [Back] responses, and forwards the combined result
 * back through [channelIn].
 *
 * Unlike a [Router] that routes each message to a single selected output channel,
 * a [Broadcast] sends a clone of each incoming [Send] message to every channel in
 * [channelOuts] concurrently, using [sendSyncAll]. The individual [Back] responses
 * from all branches are then aggregated into a single [Back] — merging their statuses
 * and [ErrorInfo] entries — before being forwarded back through [channelIn] to the
 * originating [SendSource].
 *
 * As with [Router], consistency between the filter applied to the [flow] passed to
 * [broadcast] and the handling of [Back] responses is ensured internally, since
 * [sendSyncAll] handles both the dispatching and the aggregation in a single call.
 */
interface Broadcast: Propagator {

    /**
     * Registers a collector on the given [flow] that broadcasts each incoming [Send] message
     * to all [channelOuts] concurrently, then aggregates the [Back] responses and forwards
     * the combined result back through [channelIn].
     *
     * Each message is processed in a dedicated coroutine. A clone of the message is sent
     * to each channel in [channelOuts] simultaneously via [sendSyncAll], which suspends
     * until all [Back] responses have been received. The individual responses are then
     * aggregated into a single [Back] — merging their [Status] and [ErrorInfo] entries —
     * before being forwarded back through [channelIn].
     *
     * For consistency, the filter applied to [flow] before passing it to this function
     * must match any predicate used on the return path.
     *
     * @param coroutineName the name to assign to the collector coroutine
     * @param flow the [Flow] of [Send] messages to broadcast to all [channelOuts].
     * Any filter applied to this flow upstream determines which messages are broadcast
     * @param message an optional human-readable label used for logging purposes
     */
    suspend fun <T: Send> broadcast(
        coroutineName: String,
        flow: Flow<T>,
        message: String = ""
    ) {
        scope.launch(CoroutineName(coroutineName)) {
            flow.collect { send ->
                launch(CoroutineName("${coroutineName}-${send.name}")) {
                    logger.trace { "${name}: Received ${send.name} and dispatch it to all" }
                    val result = sendSyncAll(channelOuts, send)
                    logger.trace { "${name}: Received back for ${send.name} and forward it" }
                    channelIn.send(result)
                }
            }
        }
    }


}

/**
 * Abstract base implementation of [Broadcast] that waits for [channelIn] to be ready
 * before proceeding, delegating all other behavior to [AbstractNode].
 *
 * Subclasses only need to implement the broadcasting logic specific to their use case,
 * as the [channelIn] readiness is handled here.
 */
abstract class AbstractBroadcast: AbstractNode(), Broadcast {

    /**
     * Waits until [channelIn] is ready to receive messages before the node is considered started.
     *
     * @see Broadcast.run
     */
    override suspend fun run() {
        channelIn.awaitReady()
    }
}