package org.sbm4j.meercat.nodes

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import org.sbm4j.meercat.channels.SuperChannel
import org.sbm4j.meercat.data.Back
import org.sbm4j.meercat.data.ErrorInfo
import org.sbm4j.meercat.data.Status
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

/**
 * Represents a node in the Meercat topology that is responsible for receiving [Back] responses
 * and forwarding them back towards the originating [SendSource].
 *
 * A [BackForwarder] sits between a [SendConsumer] and a [SendSource] in the topology, handling
 * the return path of the bidirectional communication. It listens for incoming [Back] responses
 * on [outChannel], applies optional processing logic, accumulates any pending [ErrorInfo] entries
 * collected during the forward pass, and forwards the [Back] back through [inChannel] towards
 * the originating [SendSource].
 *
 * Minor errors accumulated during the processing of the original [Send] message are stored in
 * [pendingMinorError] and automatically merged into the [Back] response before it is forwarded.
 */
interface BackForwarder: Node {

    /**
     * The [SuperChannel] through which processed [Back] responses are forwarded back
     * towards the originating [SendSource].
     */
    var inChannel: SuperChannel

    /**
     * The [SuperChannel] on which this node listens for incoming [Back] responses
     * coming from downstream nodes.
     */
    var outChannel: SuperChannel

    /**
     * A thread-safe map accumulating [ErrorInfo] entries of [ErrorLevel.MINOR] severity
     * that were collected during the forward pass of a [Send] message, keyed by the
     * [Channelable.channelableId] of the original [Send].
     * These errors are automatically merged into the corresponding [Back] response
     * before it is forwarded back to the [SendSource].
     */
    val pendingMinorError: ConcurrentHashMap<UUID, MutableList<ErrorInfo>>

    /**
     * Registers a collector on the given [flow] that processes each incoming [Back] response
     * of type [B] concurrently in a dedicated coroutine.
     *
     * Before calling [func], any pending minor errors associated with the original [Send] message
     * are retrieved from [pendingMinorError] and merged into the [Back] response, updating its
     * [Status] to [Status.ERROR] if any are found.
     *
     * [func] is the core processing logic applied to the [Back] response. It may further modify
     * the response before it is forwarded. Any exception thrown by [func] is caught, wrapped into
     * an [ErrorInfo], and added to the [Back] response.
     *
     * Once [func] completes — whether successfully or not — the [Back] response is always
     * forwarded back through [inChannel] towards the originating [SendSource].
     *
     * By default, [flow] is obtained via [SuperChannel.getBackFlow], which registers the collector
     * in [SuperChannel.collectReadyLatch] and guarantees via [onSubscription] that the subscriber
     * is active on the [SharedFlow] before any message is dispatched.
     *
     * @param B the type of [Back] response to process
     * @param backClazz the [KClass] of [B], which specifies the exact type of responses
     * this collector will handle, constraining both the type of [flow] and [func]
     * @param flow the [Flow] of [Back] responses of type [B] to collect from, defaults to
     * [SuperChannel.getBackFlow] on [outChannel]
     * @param func the core processing function applied to each received [Back] response of type [B],
     * which may further modify the response before it is forwarded
     */
    suspend fun <B : Back<*>> receiveBacks(
        backClazz: KClass<B>,
        flow: Flow<B> = outChannel.getBackFlow(backClazz),
        func: suspend (B) -> Unit
    ) {
        val coroutineName = "${name}-perform${backClazz.simpleName}"
        scope.launch(CoroutineName(coroutineName)) {
            logger.debug { "${name}: Waits for ${backClazz.simpleName} to process" }
            flow.collect { back ->
                logger.trace { "${name}: received a ${backClazz.simpleName} for the ${back.send::class.simpleName} ${back.send.name}" }
                scope.launch(CoroutineName("${name}-perform${backClazz.simpleName}-${back.send.name}")) {
                    val errors = pendingMinorError.remove(back.send.channelableId)
                    if (errors != null && errors.isNotEmpty()) {
                        back.status = Status.ERROR
                        back.errorInfos.addAll(errors)
                    }

                    logger.trace { "${name}: Process ${back.loggingLabel} for the ${back.send.loggingLabel} ${back.send.name}" }
                    try {
                        func(back)
                    } catch (ex: Exception) {
                        logger.error(ex) { "${name}: Error while processing ${back.loggingLabel} - ${ex.message}" }
                        val infos = generateErrorInfos(ex)
                        back.status = Status.ERROR
                        back.errorInfos.add(infos)
                    } finally {
                        this@BackForwarder.inChannel.send(back)
                    }
                }
                logger.trace { "$name: ready to receive another ${backClazz.simpleName}" }
            }
            logger.debug { "${name}: Finished receiving ${backClazz.simpleName}" }
        }
    }
}