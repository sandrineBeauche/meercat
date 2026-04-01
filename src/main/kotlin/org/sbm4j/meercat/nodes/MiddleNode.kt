package org.sbm4j.meercat.nodes

import org.sbm4j.meercat.channels.SuperChannel
import org.sbm4j.meercat.nodes.sendProcessors.SendForwarder

/**
 * Abstract base implementation of both [SendForwarder] and [BackForwarder], sitting between
 * a [SendSource] and a [SendConsumer] in the topology.
 *
 * Provides concrete [inChannel] and [outChannel] properties and waits for both to be ready
 * before proceeding, delegating all other behavior to [AbstractProcessingNode].
 *
 * Subclasses only need to implement the processing logic specific to their use case.
 *
 * @param name the name of this node, used for logging and coroutine naming
 */
abstract class AbstractMiddleNode(
    override val name: String
): AbstractProcessingNode(), SendForwarder, BackForwarder {

    /**
     * @see SendForwarder.outChannel
     */
    override lateinit var outChannel: SuperChannel

    /**
     * @see SendConsumer.inChannel
     */
    override lateinit var inChannel: SuperChannel

    /**
     * Waits until both [inChannel] and [outChannel] are ready to receive messages
     * before the node is considered started.
     *
     * @see Node.run
     */
    override suspend fun run() {
        inChannel.awaitReady()
        outChannel.awaitReady()
    }

}