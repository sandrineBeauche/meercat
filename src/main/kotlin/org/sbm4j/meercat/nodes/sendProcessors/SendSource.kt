package org.sbm4j.meercat.nodes.sendProcessors

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.sbm4j.meercat.channels.SuperChannel
import org.sbm4j.meercat.data.Back
import org.sbm4j.meercat.data.MultipleSendException
import org.sbm4j.meercat.data.Send
import org.sbm4j.meercat.data.SendException
import org.sbm4j.meercat.data.Status
import org.sbm4j.meercat.nodes.Node
import org.sbm4j.meercat.nodes.logger

typealias CallbackError = suspend (Throwable) -> Unit

/**
 * Represents a source node in the Meercat topology, responsible for emitting [Send] messages
 * and receiving the corresponding [Back] responses.
 *
 * A [SendSource] dispatches messages through its [outChannel] and provides two messaging
 * strategies:
 * - [sendSync]: suspends the current coroutine until the [Back] response is received,
 *   throwing a [SendException] if the response status is not [Status.OK].
 * - [send]: dispatches the message in a new coroutine and handles the response asynchronously
 *   through callbacks.
 */
interface SendSource: Node {

    /**
     * The [SuperChannel] through which this source emits [Send] messages
     * and receives [Back] responses.
     */
    var outChannel: SuperChannel

    /**
     * Sends a [Send] message through [outChannel] and suspends until the matching [Back]
     * response is received, then dispatches it to the appropriate callback.
     *
     * If the response status is not [Status.OK] and a [callbackError] is provided,
     * the error callback is invoked with a [SendException]. Otherwise, the main [callback]
     * is called regardless of the status.
     * Any exception thrown inside [callback] is caught and forwarded to [callbackError]
     * if provided.
     *
     * @param S the type of [Send] message
     * @param send the [Send] message to dispatch
     * @param callback the callback invoked with the [Back] response on success
     * @param callbackError an optional callback invoked with a [SendException] on error
     */
    private suspend fun <S: Send> peformSendSync(
        send: S,
        callback: (Back<S>) -> Unit,
        callbackError: CallbackError? = null
    ){
        val back = outChannel.sendSync<Back<S>>(send)

        logger.trace { "${name}: received the ${back.loggingLabel} for the ${send.loggingLabel} ${send.name} and call callback" }

        try {
            when (back.status) {
                Status.OK -> callback(back)
                else -> {
                    if (callbackError != null) {
                        val ex = SendException("Error when fetching the ${send.loggingLabel} ${send.sender}", back)
                        callbackError(ex)
                    } else callback(back)
                }
            }
        } catch (ex: Exception) {
            val message = "Error while executing callback from the ${send.loggingLabel} ${send.name}"
            logger.error(ex) { message }
            if (callbackError != null) {
                callbackError(SendException(message, back, ex))
            }
        }
    }


    /**
     * Sends a [Send] message through [outChannel] and suspends until the matching [Back]
     * response is received.
     *
     * @param S the type of [Send] message
     * @param send the [Send] message to dispatch
     * @return the [Back] response matching the sent message
     * @throws SendException if the response status is not [Status.OK]
     */
    suspend fun <S: Send> sendSync(
        send: S
    ): Back<S> {
        val back = outChannel.sendSync<Back<S>>(send)

        logger.trace { "${name}: received the ${back.loggingLabel} for the ${send.loggingLabel} ${send.name} and call callback" }
        when (back.status) {
            Status.OK -> return back
            else -> {
                val ex = SendException("Error when fetching the ${send.loggingLabel} ${send.sender}", back)
                throw ex
            }
        }
    }

    /**
     * Sends a list of [Send] messages through [outChannel] concurrently, suspends until
     * all matching [Back] responses have been received, and aggregates them into a single [Back].
     *
     * If the aggregated response status is not [Status.OK], a [SendException] is thrown.
     *
     * @param sends the list of [Send] messages to dispatch concurrently
     * @return the aggregated [Back] combining all individual responses
     * @throws SendException if the aggregated response status is not [Status.OK]
     */
    suspend fun sendSyncAggregate(
        sends: List<Send>
    ): Back<*> {
        val back = outChannel.sendSyncAggregate(sends)

        logger.trace { "${name}: received the ${back.loggingLabel} for the sends" }
        when (back.status) {
            Status.OK -> return back
            else -> {
                val ex = SendException("Error when fetching the sends", back)
                throw ex
            }
        }
    }

    /**
     * Sends a list of [Send] messages through [outChannel] concurrently, suspends until
     * all matching [Back] responses have been received, and returns them individually.
     *
     * Unlike [sendSyncAggregate], the responses are not aggregated — they are returned
     * as a list, one per message. If any response has a status of [Status.ERROR] or
     * [Status.FAIL], a [MultipleSendException] is thrown containing all the individual
     * [SendException] instances for the failed responses.
     *
     * @param sends the list of [Send] messages to dispatch concurrently
     * @return the list of [Back] responses, one per message, in the same order as [sends]
     * @throws MultipleSendException if any of the responses has a non-OK status
     */
    suspend fun sendSync(sends: List<Send>): List<Back<*>> {
        val backs = outChannel.sendSync(sends)

        logger.trace { "${name}: received the backs for the sends" }
        val globalStatus = backs.map{it.status}.reduce { acc, status -> acc + status }
        when(globalStatus){
            Status.OK -> return backs
            else -> {
                val exs = backs
                    .filter { it.status == Status.ERROR || it.status == Status.FAIL }
                    .map { back -> SendException("Error when fetching the send", back) }
                throw MultipleSendException("Error when fetching the sends", exs)
            }
        }
    }

    /**
     * Dispatches a [Send] message in a new coroutine and handles the [Back] response
     * asynchronously through callbacks, without suspending the caller.
     *
     * If the response status is not [Status.OK] and a [callbackError] is provided,
     * the error callback is invoked with a [SendException]. Otherwise, [callback] is called
     * regardless of the status.
     *
     * @param S the type of [Send] message
     * @param request the [Send] message to dispatch
     * @param callback the callback invoked with the [Back] response on success
     * @param callbackError an optional callback invoked with a [SendException] on error
     * @param subScope the coroutine scope in which the new coroutine is launched,
     * defaults to the node's own [scope]
     */
    suspend fun <S: Send> send(
        request: S,
        callback: (Back<S>) -> Unit,
        callbackError: CallbackError? = null,
        subScope: CoroutineScope = scope
    ) {
        subScope.launch(CoroutineName("${name}-${request.name}")){
            this@SendSource.peformSendSync(request, callback, callbackError)
        }
    }
}