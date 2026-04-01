
package org.sbm4j.meercat.nodes.dispatchers

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import org.sbm4j.meercat.channels.SuperChannel
import org.sbm4j.meercat.data.Back
import org.sbm4j.meercat.data.Send
import org.sbm4j.meercat.nodes.AbstractNode
import org.sbm4j.meercat.nodes.logger
import kotlin.reflect.KClass

/**
 * A [Propagator] that routes each incoming [Send] message to a single output channel
 * selected dynamically, and forwards all [Back] responses back through [channelIn].
 *
 * Unlike a broadcasting propagator that would send each message to all [channelOuts],
 * a [Router] applies a selection function to each incoming [Send] message to determine
 * which single [SuperChannel] in [channelOuts] it should be routed to. This allows
 * the topology to branch conditionally based on the content of the message.
 *
 * On the return path, [Back] responses arriving on any of the [channelOuts] are
 * simply forwarded back through [channelIn] towards the originating [SendSource],
 * without any aggregation.
 */
interface Router: Propagator {

    /**
     * Registers a collector on [channelIn] that routes each incoming [Send] message
     * of type [T] to a single output channel determined by [selectChannelFunc].
     *
     * Each message is processed in a dedicated coroutine. [selectChannelFunc] is called
     * for each received message to dynamically select the target [SuperChannel] from
     * [channelOuts], allowing conditional routing based on the message content.
     *
     * The flow is obtained via [SuperChannel.getSendFlow], which registers the collector
     * in [SuperChannel.collectReadyLatch] and guarantees via [onSubscription] that the
     * subscriber is active on the [SharedFlow] before any message is dispatched.
     *
     * @param T the type of [Send] message to route
     * @param clazz the [KClass] of [T], specifying the exact type of sends this collector handles
     * @param predicate an optional filter applied to incoming [Send] messages
     * @param selectChannelFunc a function that receives each [Send] message and returns
     * the [SuperChannel] from [channelOuts] to which it should be routed
     */
    suspend fun <T : Send> route(
        clazz: KClass<T>,
        predicate: ((T) -> Boolean)? = null,
        selectChannelFunc: (T) -> SuperChannel
    ) {
        val coroutineName = "${name}-route"
        scope.launch(CoroutineName(coroutineName)) {
            val flow = channelIn.getSendFlow(clazz)
            val filtered = if(predicate != null) { flow.filter(predicate) } else flow
            filtered.collect { send ->
                launch(CoroutineName("${coroutineName}-${send.name}")) {
                    val channel = selectChannelFunc(send)
                    channel.send(send)
                }
            }
        }
    }

    /**
     * Registers a collector on each channel in [channelOuts] that processes incoming [Back]
     * responses of type [B], optionally filtered by a predicate applied to the original [Send].
     *
     * Each [SuperChannel] in [channelOuts] gets its own dedicated collector coroutine.
     * The flow for each channel is obtained via [SuperChannel.getBackFlow], which registers
     * the collector in [SuperChannel.collectReadyLatch] and guarantees via [onSubscription]
     * that the subscriber is active on the [SharedFlow] before any message is dispatched.
     *
     * @param T the type of [Send] of the original message
     * @param B the type of [Back] response to process
     * @param clazz the [KClass] of [B], specifying the exact type of backs this collector handles
     * @param predicate an optional filter applied to the original [Send] of each incoming [Back].
     * Must match the filter applied to [route] to ensure consistency between forward and return paths
     * @param perform the function to apply to each received [Back] response
     */
    suspend fun <T: Send, B:Back<T>> forwardBacks(
        clazz: KClass<B>,
        predicate: ((T) -> Boolean)? = null,
        perform: suspend (B) -> Unit
    ) {
        channelOuts.forEach { channel ->
            val coroutineName = "${name}-${channel.name}"
            scope.launch(CoroutineName(coroutineName)) {
                val flow = channel.getBackFlow(clazz)
                val filtered = if(predicate != null){
                    val pred: (B) -> Boolean = { predicate(it.send) }
                    flow.filter(pred)
                }
                else flow
                filtered.collect {
                    perform(it)
                }
            }
        }
    }

    /**
     * Forwards the given [Back] response back through [channelIn] towards the originating
     * [SendSource]. This is the default [perform] function used by [forwardBacks] in
     * [performSendBacks].
     *
     * @param back the [Back] response to forward
     */
    suspend fun forwardBackRouter(back: Back<*>){
        logger.debug {"${name}: received $back and forward it"}
        channelIn.send(back)
    }

    /**
     * Registers both the send collector and the back collectors for the router behaviour,
     * optionally filtered by [predicate].
     *
     * Incoming [Send] messages matching the predicate are routed to a single [SuperChannel]
     * selected by [selectFuncChannel] via [route], while [Back] responses matching the
     * predicate are forwarded back through [channelIn] via [forwardBackRouter].
     *
     * @param T the type of [Send] message to process
     * @param B the type of [Back] response to process
     * @param clazz the [KClass] of [T], specifying the exact type of sends this collector handles
     * @param backClazz the [KClass] of [B], specifying the exact type of backs this collector handles
     * @param predicate an optional filter ensuring both collectors handle the same subset of messages
     * @param selectFuncChannel a function that receives each [Send] message and returns
     * the single [SuperChannel] from [channelOuts] to which it should be routed
     */
    suspend fun <T: Send, B: Back<T>> performSendBacks(
        clazz: KClass<T>,
        backClazz: KClass<B>,
        predicate: ((T) -> Boolean)? = null,
        selectFuncChannel: (T) -> SuperChannel
    ) {
        route(clazz, predicate, selectFuncChannel)
        forwardBacks(backClazz, predicate) { forwardBackRouter(it) }
    }
}

/**
 * Abstract base implementation of [Router] that waits for [channelIn] and all [channelOuts]
 * to be ready before proceeding, delegating all other behavior to [AbstractPropagator].
 *
 * Subclasses only need to implement the routing logic specific to their use case.
 */
abstract class AbstractRouter : AbstractPropagator(), Router {

    /**
     * Waits until [channelIn] and all [channelOuts] are ready to receive messages
     * before the node is considered started.
     *
     * @see Router.run
     */
    override suspend fun run() {
        channelIn.awaitReady()
        channelOuts.forEach { it.awaitReady() }
    }
}