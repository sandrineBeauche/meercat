package org.sbm4j.meercat.data

import org.sbm4j.meercat.nodes.sendProcessors.SendSource

/**
 * Represents a message travelling from a [SendSource] node towards a [SendConsumer] node.
 *
 * A [Send] carries the payload emitted by a [SendSource] and provides the ability
 * to build the corresponding [Back] response. The [Back] response will travel back
 * to the originating [SendSource], carrying the processing [Status] and any [ErrorInfo]
 * entries that were collected along the way.
 */
interface Send: Channelable {

    /**
     * The [SendSource] node that emitted this message.
     */
    var sender: SendSource

    /**
     * Builds an error [Back] response for this message.
     * The resulting [Back] will carry the given [ErrorInfo] and [Status],
     * allowing the [SendSource] to inspect what went wrong during processing.
     *
     * @param infos details about the error that occurred, including the [Node] where it happened
     * and its [ErrorLevel] severity
     * @param status the status to attach to the back, defaults to [Status.ERROR]
     * @return a [Back] carrying the error information and the given [Status]
     */
    fun buildErrorBack(infos: ErrorInfo, status: Status = Status.ERROR): Back<*>

    /**
     * Builds a successful [Back] response for this message,
     * with a [Status.OK] status and an empty [Back.errorInfos] list.
     *
     * @param back if the resulting back should be built from another back. In this case, the resulting
     * back take all the values from the given back except the [Send].
     * @return a [Back] carrying the result of the processing
     */
    fun buildBack(): Back<*>



    /**
     * Builds a new [Back] response based on an existing [Back], preserving its [Status]
     * and [ErrorInfo] entries but replacing the original [Send] with this instance.
     *
     * This function is typically used in a [Barrier] node when broadcasting a [Back] response
     * to all originating branches. Each branch needs a [Back] that references its own
     * original [Send], not the one that was actually forwarded downstream by the barrier.
     * By restoring the correct [Send] on each [Back], the corresponding [sendSync] on each
     * branch can match the response and unblock.
     *
     * @param back the [Back] response to build from, whose [Status] and [ErrorInfo] entries
     * are preserved in the returned [Back]
     * @return a new [Back] carrying this [Send] as its origin, with the same [Status]
     * and [ErrorInfo] entries as [back]
     */
    fun buildFromBack(back: Back<*>): Back<*> {
        val result = this.buildBack()
        result.status = back.status
        result.errorInfos.addAll(back.errorInfos)
        return result
    }


    /**
     * Creates and returns a copy of this [Send] instance.
     * The depth of the copy is left to the discretion of the implementing class.
     *
     * @return a cloned [Send] instance
     */
    override fun clone(): Send

    /**
     * Returns the key used to group messages at a barrier node.
     * Messages coming from different branches that produce the same key
     * will be held until all expected messages have arrived,
     * acting as a synchronization point in the topology.
     * Defaults to [name], but can be overridden by implementing classes.
     *
     * @return the barrier key string
     */
    fun getKeyBarrier(): String{
        return name
    }
}