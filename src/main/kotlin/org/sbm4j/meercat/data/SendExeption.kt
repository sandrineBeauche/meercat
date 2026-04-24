package org.sbm4j.meercat.data

/**
 * Exception thrown by a [SendSource] when sending a [Send] message results in an error.
 *
 * [SendException] is a convenience exception that allows a [SendSource] to handle
 * error cases through standard Kotlin exception flow control, rather than inspecting
 * the [Back] response manually. The associated [Back] response is carried along
 * for reference.
 *
 * @param message a human-readable description of the error
 * @param resp the [Back] response associated with the failed [Send]
 * @param cause the underlying exception that triggered this one, if any
 */
class SendException(message: String, val resp: Back<*>, cause: Throwable? = null) :
    Exception(message, cause)


/**
 * Thrown when multiple [SendException] errors occur during a batch send operation,
 * such as [SuperChannel.sendSync] with a list of [Send] messages.
 *
 * Aggregates all individual [SendException] instances so that the caller can inspect
 * each failure independently.
 *
 * @param message a human-readable description of the batch failure
 * @param exs the list of individual [SendException] instances that were raised
 */
class MultipleSendException(message: String, val exs: List<SendException>) :
        Exception(message, null) {}