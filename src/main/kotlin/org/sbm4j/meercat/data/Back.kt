package org.sbm4j.meercat.data

/**
 * Represents the processing status of a message as it travels through the Meercat topology.
 *
 * Statuses can be combined using the [plus] operator to aggregate the result
 * of multiple processing steps into a single overall status.
 *
 * - [OK]: the message was processed successfully.
 * - [ERROR]: a recoverable error occurred during processing.
 * - [FAIL]: a non-recoverable failure occurred during processing.
 * - [IGNORED]: the message was deliberately ignored by the node.
 */
enum class Status{
    OK,
    ERROR,
    FAIL,
    IGNORED;

    /**
     * Combines this status with another, returning an aggregated status.
     *
     * Combination rules:
     * - [OK] + [OK] → [OK]
     * - [OK] + [ERROR] or [ERROR] + [OK] → [ERROR]
     * - any other combination → the left-hand status is preserved
     *
     * @param other the status to combine with
     * @return the resulting aggregated [Status]
     */
    operator fun plus(other: Status): Status {
        return when (this to other) {
            OK to OK -> OK
            OK to ERROR, ERROR to OK -> ERROR
            else -> this // Default behavior
        }
    }
}


/**
 * Represents a response message travelling back from a [SendConsumer] node towards a [SendSource] node,
 * in response to a [Send] message.
 *
 * A [Back] carries the original [Send] message that triggered it, the processing [Status],
 * and a list of [ErrorInfo] that may have been collected along the way.
 *
 * [Back] instances can be aggregated using the [plus] operator, which merges statuses
 * and error information from multiple branches into a single response,
 * typically at a barrier node.
 *
 * @param T the type of [Send] message this back is responding to
 */
interface Back<T: Send>: Channelable{

    /**
     * The original [Send] message that triggered this back response.
     */
    val send: T

    /**
     * The processing status of this back response.
     */
    var status: Status

    /**
     * The list of [ErrorInfo] collected during the processing of the original [Send] message.
     * Each entry identifies the [Node] where the error occurred, the [ErrorLevel] severity,
     * and the exception that was raised. May be empty if no errors occurred.
     */
    val errorInfos: MutableList<ErrorInfo>

    /**
     * Creates and returns a copy of this [Back] instance.
     * The depth of the copy is left to the discretion of the implementing class.
     *
     * @return a cloned [Back] instance of the same type
     */
    override fun clone(): Back<T>


    /**
     * Combines this [Back] with another, aggregating their statuses and error information.
     * This is typically used at a barrier node to merge responses coming from different branches.
     *
     * The resulting [Status] is computed using the [Status.plus] operator,
     * and all [ErrorInfo] entries from [increment] are appended to those of this instance.
     *
     * @param increment the [Back] to merge into this one
     * @return a new [Back] instance carrying the aggregated status and error information
     */
    operator fun plus(increment: Back<*>): Back<*>{
        val result = this.clone()
        result.status += result.status + increment.status
        result.errorInfos.addAll(increment.errorInfos)
        return result
    }
}
