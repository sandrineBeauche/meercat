package org.sbm4j.meercat.nodes.sendProcessors

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import org.sbm4j.meercat.channels.SuperChannel
import org.sbm4j.meercat.data.Send
import org.sbm4j.meercat.nodes.Node
import org.sbm4j.meercat.nodes.logger
import kotlin.reflect.KClass

/**
 * Represents a node in the Meercat topology that is capable of receiving [Send] messages.
 *
 * A [SendConsumer] listens on its [inChannel] for incoming [Send] messages and processes each
 * one concurrently in a dedicated coroutine. After processing, [sendPostProcess] is called
 * with the original [Send] and the processing result, and its behaviour depends on the
 * concrete type of node:
 * - a terminal consumer node will build a [Back] response and send it back through [inChannel].
 * - a forwarder node will forward the original [Send] message, potentially modified,
 *   to the next node in the topology.
 */
interface SendConsumer: Node {

    /**
     * The [SuperChannel] on which this node listens for incoming [Send] messages.
     * This channel is strictly an input — responses and forwarded messages are dispatched
     * through dedicated output channels defined by the concrete node type.
     */
    var inChannel: SuperChannel

    /**
     * Registers a collector on the given [flow] that processes each incoming [Send] message
     * of type [T] by calling [func] concurrently in a dedicated coroutine.
     *
     * [func] is the core processing function of the node — it contains the actual business logic,
     * which may modify the original [Send] message or produce a [Back] response as its result.
     * Once [func] returns a non-null result, or a [Boolean] `true`, [sendPostProcess] is called
     * to automatically dispatch the result: either forwarding the message to the next node,
     * or sending a [Back] response back through [inChannel], depending on the concrete node type.
     *
     * If an exception is thrown during processing, an error [Back] is automatically built
     * using [generateErrorInfos] and sent back through [inChannel].
     *
     * By default, [flow] is obtained via [SuperChannel.getSendFlow], which registers the collector
     * in [SuperChannel.collectReadyLatch] and guarantees via [onSubscription] that the subscriber
     * is active on the [SharedFlow] before any message is dispatched.
     *
     * This function is typically called from [run] to set up the node's message processing
     * pipeline during startup.
     *
     * @param T the type of [Send] message to process
     * @param sendClazz the [KClass] of [T], which specifies the exact type of messages
     * this collector will handle, constraining both the type of [flow] and [func]
     * @param flow the [Flow] of [Send] messages of type [T] to collect from, defaults to
     * [SuperChannel.getSendFlow] on [inChannel]
     * @param func the core processing function applied to each received [Send] message of type [T].
     * It may modify the message or produce a [Back] response, and its return value is forwarded
     * to [sendPostProcess] for automatic dispatching
     */
    suspend fun <T : Send> performSends(
        sendClazz: KClass<T>,
        flow: Flow<T> = inChannel.getSendFlow(sendClazz),
        func: suspend (T) -> Any?
    ) {
        val coroutineName = "${name}-perform${sendClazz.simpleName}"
        scope.launch(CoroutineName(coroutineName)) {
            logger.debug { "${name}: Waits for ${sendClazz.simpleName} to process" }
            flow.collect { send ->
                this.launch() {
                    try {
                        logger.trace { "${name}: received ${send.loggingLabel} ${send.channelableId}: $send" }
                        val result: Any? = func(send)

                        if ((result is Boolean && result) || result != null) {
                            sendPostProcess(send, result)
                        }
                    } catch (ex: Exception) {
                        logger.error { "${this@SendConsumer.name}: error when processing ${sendClazz.simpleName} ${send.channelableId} - ${ex.message}" }
                        val infos = generateErrorInfos(ex)
                        val back = send.buildErrorBack(infos)
                        inChannel.send(back)
                    }
                }
                logger.trace { "${name}: ready to receive another ${sendClazz.simpleName}" }
            }
            logger.debug { "${name}: Finished to receive ${sendClazz.simpleName}" }
        }
    }

    /**
     * Handles the post-processing of a [Send] message after it has been successfully processed.
     *
     * Implementations should build the appropriate [Back] response from [send] and [result],
     * and dispatch it back through [inChannel].
     *
     * @param send the [Send] message that was processed
     * @param result the non-null result returned by the processing function
     */
    suspend fun sendPostProcess(send: Send, result: Any)

}