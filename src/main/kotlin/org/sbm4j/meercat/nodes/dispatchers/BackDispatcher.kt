package org.sbm4j.meercat.nodes.dispatchers

import org.sbm4j.meercat.channels.SuperChannel
import org.sbm4j.meercat.data.Back
import org.sbm4j.meercat.data.Send
import org.sbm4j.meercat.nodes.logger
import java.util.*
import kotlin.reflect.KClass

/**
 * A [Combinator] that dispatches [Send] messages from multiple input branches to a single
 * output channel, and routes [Back] responses back to the correct originating branch.
 *
 * When a [Send] message arrives on one of the [channelsIns], [BackDispatcher] memorises
 * the association between the message's [Channelable.channelableId] and its originating
 * [SuperChannel] in [pendingAnswerable], then forwards the message to [channelOut].
 * When the corresponding [Back] response arrives on [channelOut], it is dispatched back
 * to the correct originating [SuperChannel] using the memorised association.
 *
 * Multiple dispatcher behaviours can be combined in the same node by calling
 * [performSendBacks] several times with different predicates.
 */
interface BackDispatcher: Combinator {

    /**
     * A map associating the [Channelable.channelableId] of each forwarded [Send] message
     * with the [SuperChannel] it originated from, allowing the corresponding [Back] response
     * to be routed back to the correct input branch.
     */
    val pendingAnswerable: MutableMap<UUID, SuperChannel>

    /**
     * Processes an incoming [Send] message by memorising its originating [SuperChannel]
     * and forwarding it to [channelOut].
     *
     * Called by the send collector registered in [performSendBacks].
     *
     * @param send the [Send] message to forward
     * @param sender the [SuperChannel] from which the message originated
     * @param index the index of [sender] in [channelsIns]
     */
    suspend fun <T: Send> performSendDispatcher(send: T, sender: SuperChannel, index: Int){
        logger.trace { "Received send ${send.name} from input #$index and forwards it" }
        pendingAnswerable[send.channelableId] = sender
        channelOut.send(send)
    }

    /**
     * Processes an incoming [Back] response by routing it back to the originating
     * [SuperChannel] identified by the [Channelable.channelableId] of the original [Send].
     *
     * The association is removed from [pendingAnswerable] once the response is dispatched.
     * If no originating channel is found for the given id, the response is silently dropped.
     *
     * Called by the back collector registered in [performSendBacks].
     *
     * @param back the [Back] response to dispatch
     */
    suspend fun <B: Back<*>> performBackDispatcher(back: B){
        logger.trace{ "${name}: Received back for the send ${back.send.name} and dispatch it"}
        val channel = pendingAnswerable.remove(back.send.channelableId)
        if (channel != null) {
            logger.trace{"${name}: dispatch $back to ${channel.name}"}
            channel.send(back)
        }
        else{
            logger.trace{"${name}: no channel to ${back.send.name} with id ${back.send.channelableId}"}
        }
    }


    /**
     * Registers both the send and back collectors for the dispatcher behaviour,
     * optionally filtered by [predicate].
     *
     * Incoming [Send] messages matching the predicate are forwarded to [channelOut]
     * via [performSendDispatcher], while [Back] responses matching the predicate are
     * routed back to their originating branch via [performBackDispathcher].
     *
     * @param T the type of [Send] message to process
     * @param B the type of [Back] response to process
     * @param clazz the [KClass] of [T], specifying the exact type of sends this collector handles
     * @param backClazz the [KClass] of [B], specifying the exact type of backs this collector handles
     * @param predicate an optional filter applied to [Send] messages and to the original [Send]
     * of [Back] responses, ensuring both collectors handle the same subset of messages
     */

    override suspend fun <T: Send, B: Back<T>>performSendBacks(
        clazz: KClass<T>,
        backClazz: KClass<B>,
        predicate: ((T) -> Boolean)?
    ) {
        val pred: ((B) -> Boolean)? = if(predicate != null){
            { predicate(it.send) }
        }
        else null
        performSends(clazz, predicate, ::performSendDispatcher)
        performBacks(backClazz,pred, ::performBackDispatcher)
    }

}

/**
 * Abstract base implementation of [BackDispatcher] that provides a concrete [pendingAnswerable]
 * store, delegating all other behavior to [AbstractCombinator].
 *
 * Subclasses only need to implement the routing and combining logic specific to their use case.
 */
abstract class AbstractBackDispatcher: AbstractCombinator(), BackDispatcher {
    /**
     * @see BackDispatcher.pendingAnswerable
     */
    override val pendingAnswerable: MutableMap<UUID, SuperChannel> = mutableMapOf()
}