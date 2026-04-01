package org.sbm4j.meercat.nodes.sendProcessors

import org.sbm4j.meercat.data.Back
import org.sbm4j.meercat.data.Send
import org.sbm4j.meercat.nodes.Node
import org.sbm4j.meercat.nodes.sendProcessors.SendSource
import org.sbm4j.meercat.nodes.logger

/**
 * Represents a forwarder node in the Meercat topology, capable of both receiving [Send] messages
 * and forwarding them to the next node.
 *
 * A [SendForwarder] sits between a [SendSource] and a [SendConsumer] in the topology. It receives
 * [Send] messages through [inChannel], applies its processing logic via [performSends], and then
 * either:
 * - forwards the original [Send] message, potentially modified, to the next node through
 *   [outChannel] if the processing result is not a [Back].
 * - sends a [Back] response back through [inChannel] if the processing logic produced one,
 *   short-circuiting the forwarding and returning the response directly to the [SendSource].
 */
interface SendForwarder : Node, SendSource, SendConsumer {

    /**
     * Automatically dispatches the result of processing a [Send] message.
     *
     * If [result] is a [Back], short-circuits the forwarding chain by sending it back
     * through [inChannel] to the originating [SendSource].
     * Otherwise, forwards the original [send] through [outChannel] to the next node.
     *
     * @see SendConsumer.sendPostProcess
     */
    override suspend fun sendPostProcess(send: Send, result: Any) {
        if(result is Back<*>){
            logger.trace { "${name}: returns a ${result.loggingLabel} for the ${send.loggingLabel} ${send.name}" }
            inChannel.send(result)
        }
        else {
            logger.trace { "${name}: forward ${send.loggingLabel} ${send.name}" }
            outChannel.send(send)
        }
    }

}