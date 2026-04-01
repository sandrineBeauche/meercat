package org.sbm4j.meercat.nodes

import org.sbm4j.meercat.channels.SuperChannel
import org.sbm4j.meercat.data.Back
import org.sbm4j.meercat.data.Send
import org.sbm4j.meercat.nodes.sendProcessors.SendConsumer

/**
 * Abstract base implementation of [SendConsumer] acting as a terminal node in the topology,
 * receiving [Send] messages without forwarding them further.
 *
 * Provides a concrete [inChannel] property and waits for it to be ready before proceeding,
 * delegating all other behavior to [AbstractProcessingNode].
 *
 * Subclasses only need to implement the processing logic specific to their use case.
 *
 * @param name the name of this node, used for logging and coroutine naming
 */
abstract class AbstractSinkNode(
    override val name: String
): AbstractProcessingNode(), SendConsumer {

    /**
     * @see SendConsumer.inChannel
     */
    override lateinit var inChannel: SuperChannel

    /**
     * Sends the [Back] response back through [inChannel] if [result] is a [Back],
     * otherwise does nothing as this is a terminal node.
     *
     * @see SendConsumer.sendPostProcess
     */
    override suspend fun sendPostProcess(send: Send, result: Any) {
        if(result is Back<*>){
            inChannel.send(result)
        }
    }

    /**
     * Waits until [inChannel] is ready to receive messages before the node is considered started.
     *
     * @see Node.run
     */
    override suspend fun run() {
        inChannel.awaitReady()
    }
}