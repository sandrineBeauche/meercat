package org.sbm4j.meercat.nodes.dispatchers

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import org.sbm4j.meercat.channels.SuperChannel
import org.sbm4j.meercat.data.Back
import org.sbm4j.meercat.data.Send
import org.sbm4j.meercat.nodes.AbstractNode
import org.sbm4j.meercat.nodes.Node
import org.sbm4j.meercat.nodes.logger
import kotlin.reflect.KClass

/**
 * Represents a node in the Meercat topology that can receive [Send] messages from multiple
 * input channels and forward them to a single output channel, then dispatch the [Back] responses
 * back to the appropriate input channel.
 *
 * A [Combinator] sits at a branching point in the topology, managing multiple [channelsIns]
 * as entry points and a single [channelOut] as exit point. It can handle different types of
 * messages with different behaviours, by registering multiple collectors with distinct predicates
 * on the same node.
 *
 * Concrete behaviours are defined by sub-interfaces, the two main ones being:
 * - **Barrier**: accumulates messages from different branches that share a common key
 *   (see [Send.getKeyBarrier]), then forwards a combined message on [channelOut] once all
 *   expected messages have arrived. The [Back] response is then dispatched back to all
 *   originating branches.
 * - **Dispatcher**: forwards a message received on one of the [channelsIns] to [channelOut],
 *   memorises the association between the message and its originating branch, then dispatches
 *   the [Back] response back to the correct [SuperChannel] in [channelsIns].
 *
 * A single [Combinator] node can cumulate multiple behaviours by registering several
 * [performSendBacks] calls with different predicates, each handled by a dedicated pair
 * of send and back collectors. Sub-interfaces define their own specifically named processing
 * methods to avoid naming conflicts when multiple behaviours are combined in the same node.
 *
 * For consistency, every [performSends] registration with a given predicate must have a
 * matching [performBacks] registration with the exact same predicate. [performSendBacks]
 * enforces this by registering both collectors at once.
 */
interface Combinator: Node {

    /**
     * The list of input [SuperChannel] instances from which this node receives [Send] messages.
     * Each channel typically corresponds to a distinct branch of the topology.
     */
    val channelsIns : MutableList<SuperChannel>

    /**
     * The output [SuperChannel] through which this node forwards [Send] messages
     * and receives [Back] responses from downstream nodes.
     */
    var channelOut: SuperChannel

    /**
     * Registers a collector on each channel in [channelsIns] that processes incoming [Send]
     * messages of type [T] concurrently, optionally filtered by [predicate].
     *
     * Each collector is launched in a dedicated coroutine. For each received [Send] message,
     * [perform] is called with the message, the originating [SuperChannel], and its index
     * in [channelsIns], allowing the implementation to identify which branch the message
     * came from.
     *
     * The flow for each channel is obtained via [SuperChannel.getSendFlow], which registers
     * the collector in [SuperChannel.collectReadyLatch] and guarantees via [onSubscription]
     * that the subscriber is active on the [SharedFlow] before any message is dispatched.
     *
     * This method is typically not called directly — use [performSendBacks] instead to ensure
     * a matching [performBacks] is always registered with the same predicate.
     *
     * @param T the type of [Send] message to process
     * @param clazz the [KClass] of [T], defaults to [Send] to receive all sends
     * @param predicate an optional filter applied to incoming [Send] messages.
     * Only messages for which the predicate returns `true` are processed
     * @param perform the processing function applied to each received [Send] message,
     * receiving the message, its originating [SuperChannel], and its index in [channelsIns]
     */
    @Suppress("UNCHECKED_CAST")
    suspend fun <T:Send> performSends(
        clazz: KClass<T> = Send::class as KClass<T>,
        predicate: ((T) -> Boolean)? = null,
        perform: suspend (T, SuperChannel, Int) -> Unit
    ) {
        for ((index, channel) in channelsIns.withIndex()) {
            val coroutineName = "${name}-performSends-${index}"
            scope.launch(CoroutineName(coroutineName)) {
                val flow = channel.getSendFlow(clazz)
                val filtered = if(predicate != null) flow.filter(predicate) else flow
                filtered.collect { send ->
                    perform(send, channel, index)
                }
            }
        }
    }

    /**
     * Registers a collector on [channelOut] that processes incoming [Back] responses of type [T],
     * optionally filtered by [predicate].
     *
     * The flow is obtained via [SuperChannel.getBackFlow], which registers the collector
     * in [SuperChannel.collectReadyLatch] and guarantees via [onSubscription] that the subscriber
     * is active on the [SharedFlow] before any message is dispatched.
     *
     * This method is typically not called directly — use [performSendBacks] instead to ensure
     * a matching [performSends] is always registered with the same predicate.
     *
     * @param T the type of [Back] response to process
     * @param clazz the [KClass] of [T], specifying the exact type of responses this collector handles
     * @param predicate an optional filter applied to incoming [Back] responses.
     * Only responses for which the predicate returns `true` are processed
     * @param perform the processing function applied to each received [Back] response
     */
    suspend fun <T: Back<*>> performBacks(
        clazz: KClass<T>,
        predicate: ((T) -> Boolean)? = null,
        perform: suspend (Back<*>) -> Unit
    ) {
        val coroutineName = "${name}-performBacks"
        scope.launch(CoroutineName(coroutineName)) {
            val flow = channelOut.getBackFlow(clazz)
            val filtered = if(predicate != null) flow.filter(predicate) else flow
            filtered.collect { back ->
                perform(back)
            }
        }
    }

    /**
     * Registers both a send collector and a back collector with the same [predicate],
     * ensuring consistency between the forward and return paths for a given type of message.
     *
     * Implementations are provided by sub-interfaces, each defining their own specifically
     * named processing methods to avoid naming conflicts when multiple behaviours are
     * combined in the same node.
     *
     * @param predicate an optional filter applied to [Send] messages and to the original [Send]
     * of [Back] responses, ensuring both collectors handle the same subset of messages
     */
    @Suppress("UNCHECKED_CAST")
    suspend fun <T: Send, B: Back<T>> performSendBacks(
        clazz: KClass<T> = Send::class as KClass<T>,
        backClazz: KClass<B> = Back::class as KClass<B>,
        predicate: ((T) -> Boolean)? = null
    )
}

/**
 * Abstract base implementation of [Combinator] that provides concrete [channelsIns] and
 * [channelOut] properties, and waits for all input channels to be ready before proceeding,
 * delegating all other behavior to [AbstractNode].
 *
 * Subclasses only need to implement the combining logic specific to their use case,
 * as the [channelsIns] readiness is handled here.
 */
abstract class AbstractCombinator : AbstractNode(), Combinator {

    /**
     * @see Combinator.channelsIns
     */
    override val channelsIns : MutableList<SuperChannel> = mutableListOf()

    /**
     * @see Combinator.channelOut
     */
    override lateinit var channelOut: SuperChannel

    /**
     * Waits until all [channelsIns] are ready to receive messages before the node
     * is considered started.
     *
     * @see Combinator.run
     */
    override suspend fun run() {
        channelsIns.forEach { it.awaitReady() }
    }
}