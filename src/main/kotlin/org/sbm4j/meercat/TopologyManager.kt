package org.sbm4j.meercat

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import org.sbm4j.meercat.channels.ChannelManager
import org.sbm4j.meercat.nodes.Controllable
import org.sbm4j.meercat.nodes.Node
import org.sbm4j.meercat.nodes.sendProcessors.Initiator

/**
 * Orchestrates the startup, execution, and shutdown of the entire Meercat node topology.
 *
 * A [TopologyManager] holds references to all [Node] instances and the [ChannelManager]
 * of the topology. It is responsible for initializing the channels, starting the nodes
 * in the correct order, waiting for the topology to complete, and shutting everything down
 * cleanly.
 *
 * The startup sequence is carefully ordered:
 * 1. All [SuperChannel] instances are initialized via [ChannelManager.initChannels].
 * 2. All non-[Initiator] nodes are started concurrently and awaited, ensuring their
 *    flow collectors are in place before any message is emitted.
 * 3. [Initiator] nodes are then initialized and started, triggering the message flow.
 *
 * This ordering guarantees that no message emitted by an [Initiator] is missed by the
 * nodes that are supposed to receive it.
 *
 * The topology is considered complete when all [Initiator] nodes have reached
 * [NodeStatus.COMPLETED], which can be awaited via [waitCompleted].
 *
 * @property channelManager the [ChannelManager] managing all [SuperChannel] instances
 * in the topology
 */
class TopologyManager(val channelManager: ChannelManager): Controllable {

    /**
     * The list of all [Node] instances in the topology, including both [Initiator] nodes
     * and all other node types.
     */
    val nodes: MutableList<Node> = mutableListOf()

    /**
     * The root coroutine scope of the entire topology, initialized by [start].
     *
     * Every [Node] and [SuperChannel] in the topology owns a child scope derived from this
     * root scope, forming a tree of coroutine scopes that mirrors the topology structure.
     * Cancelling this scope cascades through the entire hierarchy, stopping all nodes
     * and channels at once.
     */
    override lateinit var scope: CoroutineScope

    /**
     * Starts the topology by initializing channels and starting all nodes in the correct order.
     *
     * First, all [SuperChannel] instances are initialized. Then, all non-[Initiator] nodes
     * are started concurrently and awaited, ensuring their flow collectors are fully in place.
     * Finally, [Initiator] nodes are initialized and started, triggering the message flow
     * through the topology.
     *
     * @param parentScope the coroutine scope to use as parent for this manager's internal scope
     * @param rootName the name to assign to the child scope
     * @return the [Job] corresponding to the startup coroutine
     */
    override suspend fun start(parentScope: CoroutineScope, rootName: String): Job? {
        return parentScope.launch {
            super.start(parentScope, rootName)
            channelManager.initChannels(scope)
            nodes.filter { it !is Initiator }.map {
                launch {
                    it.start(scope)?.join()
                }
            }.joinAll()
            nodes.filterIsInstance<Initiator>().forEach {
                it.initialize()
                it.start(scope)
            }
        }
    }

    /**
     * Suspends the caller until all [Initiator] nodes in the topology have reached
     * [NodeStatus.COMPLETED], indicating that the topology scenario is fully finished.
     *
     * This function should be called after [start] to wait for the topology to complete
     * before proceeding with shutdown.
     */
    suspend fun waitCompleted(){
        nodes.filterIsInstance<Initiator>().forEach {
            it.waitCompleted()
        }
    }

    /**
     * Stops the topology by stopping all nodes, closing all channels, and cancelling
     * this manager's coroutine scope.
     *
     * Nodes are stopped first to allow them to finish any in-progress processing,
     * then channels are closed via [ChannelManager.closeChannels], and finally
     * the manager's own scope is cancelled.
     */
    override suspend fun stop() {
        nodes.forEach {
            it.stop()
        }
        channelManager.closeChannels()
        super.stop()
    }

}