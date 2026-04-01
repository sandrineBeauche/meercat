package org.sbm4j.meercat.nodes.dispatchers

import org.sbm4j.meercat.channels.SuperChannel
import org.sbm4j.meercat.data.Back
import org.sbm4j.meercat.data.Send
import org.sbm4j.meercat.nodes.logger
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

/**
 * A [Combinator] that synchronises [Send] messages arriving from multiple input branches
 * before forwarding them downstream, and broadcasts the [Back] response back to all branches.
 *
 * When a [Send] message arrives on one of the [channelsIns], [Barrier] groups it with other
 * messages sharing the same barrier key (see [Send.getKeyBarrier]) in [pendingBarrier].
 * Once all expected branches have contributed a message for a given key — i.e. the number
 * of accumulated messages reaches the size of [channelsIns] — the last received message
 * is forwarded to [channelOut].
 *
 * When the corresponding [Back] response arrives, it is cloned and broadcast to all
 * [channelsIns], so that every originating branch receives its own copy of the response.
 * The barrier entry is then removed from [pendingBarrier].
 *
 * Multiple barrier behaviours can be combined in the same node by calling
 * [performSendBacks] several times with different predicates.
 */
interface Barrier: Combinator {

    /**
     * A thread-safe map accumulating incoming [Send] messages grouped by their barrier key
     * (see [Send.getKeyBarrier]), pending synchronisation across all input branches.
     * Once all branches have contributed a message for a given key, the group is forwarded
     * and the entry is removed when the [Back] response is received.
     */
    val pendingBarrier: ConcurrentHashMap<String, MutableList<Pair<Send, Int>>>

    /**
     * Processes an incoming [Send] message by adding it to the pending barrier group
     * identified by its barrier key. Once messages from all [channelsIns] branches have
     * arrived for that key, the last received message is forwarded to [channelOut].
     *
     * Called by the send collector registered in [performSendBacks].
     *
     * @param send the [Send] message to accumulate
     * @param sender the [SuperChannel] from which the message originated
     * @param index the index of [sender] in [channelsIns]
     */
    suspend fun performSendBarrier(send: Send, sender: SuperChannel, index: Int){
        val key = send.getKeyBarrier()
        logger.debug { "${name}: received ${send.loggingLabel} ${send} on input #$index with key $key" }
        val sends = pendingBarrier.getOrPut(key) { mutableListOf() }
        sends.add(Pair(send, index))
        if(sends.size >= channelsIns.size){
            logger.debug { "${name}: all branches have received with key $key, forward ${send}" }
            channelOut.send(send)
        }
    }

    /**
     * Processes an incoming [Back] response by cloning it and broadcasting a copy to each
     * [SuperChannel] in [channelsIns], so that every originating branch receives its own
     * independent response.
     *
     * The barrier group associated with the response's barrier key is removed from
     * [pendingBarrier] once the broadcast is complete.
     * If no barrier group is found for the given key, the response is silently dropped.
     *
     * Called by the back collector registered in [performSendBacks].
     *
     * @param back the [Back] response to broadcast
     */
    suspend fun performBackBarrier(back: Back<*>){
        val key = back.send.getKeyBarrier()
        logger.debug { "${name}: received back for key $key, forward ${back} to all branches" }
        val sends = pendingBarrier.remove(key)
        sends?.forEach { (send, index) ->
            val resp = send.buildFromBack(back)
            logger.debug { "${name}: forward ${back} on branch #$index" }
            channelsIns[index].send(resp)
        }
    }


    /**
     * Registers both the send and back collectors for the barrier behaviour,
     * optionally filtered by [predicate].
     *
     * Incoming [Send] messages matching the predicate are accumulated via [performSendBarrier]
     * until all branches have contributed, then forwarded to [channelOut].
     * The [Back] response is then broadcast to all originating branches via [performBackBarrier].
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
        performSends(clazz, predicate, ::performSendBarrier)
        performBacks(backClazz,pred, ::performBackBarrier)
    }
}

/**
 * Abstract base implementation of [Barrier] that provides a concrete [pendingBarrier]
 * store, delegating all other behavior to [AbstractCombinator].
 *
 * Subclasses only need to implement the key extraction and back-building logic
 * specific to their use case.
 */
abstract class AbstractBarrier: AbstractCombinator(), Barrier {

    /**
     * @see Barrier.pendingBarrier
     */
    override val pendingBarrier: ConcurrentHashMap<String, MutableList<Pair<Send, Int>>> = ConcurrentHashMap()
}