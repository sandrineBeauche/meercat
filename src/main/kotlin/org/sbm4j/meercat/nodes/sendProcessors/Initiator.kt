package org.sbm4j.meercat.nodes.sendProcessors

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.sbm4j.meercat.channels.SuperChannel
import org.sbm4j.meercat.childScope
import org.sbm4j.meercat.nodes.AbstractProcessingNode
import org.sbm4j.meercat.nodes.logger

/**
 * Represents the lifecycle status of an [Initiator] node.
 *
 * - [READY]: the initiator is ready to start emitting messages.
 * - [RUNNING]: the initiator is currently emitting messages and waiting for responses.
 * - [COMPLETED]: the initiator has emitted all its messages and received all corresponding
 *  *   [Back] responses, meaning it has no more work to do.
 */
enum class NodeStatus{
    READY,
    RUNNING,
    COMPLETED
}

/**
 * Represents a node that can autonomously initiate message exchanges in the Meercat topology.
 *
 * While most nodes react to incoming [Send] messages, an [Initiator] is the only type of node
 * that can start an exchange by itself, without being triggered by another node.
 *
 * An [Initiator] exposes its lifecycle state through [initiatorStatus]. When all [Initiator]
 * nodes in the topology have reached [NodeStatus.COMPLETED] — meaning they have emitted all
 * their messages and received all corresponding [Back] responses — the topology scenario is
 * considered fully finished and the topology can be stopped.
 */
interface Initiator: SendSource{

    /**
     * A [MutableStateFlow] tracking the current [NodeStatus] of this initiator.
     * Transitions from [NodeStatus.READY] to [NodeStatus.RUNNING] when the initiator
     * starts emitting messages, and finally to [NodeStatus.COMPLETED] once it has
     * no more messages to emit and all responses have been received.
     */
    var initiatorStatus: MutableStateFlow<NodeStatus>

    /**
     * Initializes this initiator by setting up the [initiatorStatus] flow
     * with an initial value of [NodeStatus.READY].
     * Should be called before [start].
     */
    fun initialize(){
        initiatorStatus = MutableStateFlow(NodeStatus.READY)
    }

    /**
     * Starts this initiator by launching a setup coroutine within [parentScope] that
     * initializes the node's [scope], registers the flow collectors via [run], and then
     * launches a separate coroutine to execute the initiator's message emission logic.
     *
     * Sets [initiatorStatus] to [NodeStatus.READY] before launching, and to
     * [NodeStatus.COMPLETED] once the emission logic finishes, signalling that this
     * initiator has no more messages to emit and all responses have been received.
     *
     * @param parentScope the coroutine scope to use as parent for this initiator's internal scope
     * @return the setup [Job] that completes once collectors are in place
     */
    override suspend fun start(parentScope: CoroutineScope): Job? {
        initiatorStatus.value = NodeStatus.READY
        scope = childScope(parentScope, "${name}-root")
        val job = scope.launch {
            run()
            logger.trace{"${name}: finished run"}
            initiatorStatus.value = NodeStatus.COMPLETED
        }
        return job
    }

    /**
     * Suspends the caller until this initiator reaches [NodeStatus.COMPLETED],
     * indicating that it has emitted all its messages and received all corresponding
     * [Back] responses.
     * Returns immediately if the initiator has already completed.
     */
    suspend fun waitCompleted(){
        if(initiatorStatus.value != NodeStatus.COMPLETED){
            initiatorStatus.first{it == NodeStatus.COMPLETED}
        }
    }
}

/**
 * Abstract base implementation of [Initiator] that provides concrete [initiatorStatus] and
 * [outChannel] properties, delegating all other behavior to [AbstractProcessingNode].
 *
 * Subclasses only need to implement the initiation logic specific to their use case.
 */
abstract class AbstractInitiator: Initiator, AbstractProcessingNode(){

    /**
     * @see Initiator.initiatorStatus
     */
    override lateinit var initiatorStatus: MutableStateFlow<NodeStatus>

    /**
     * @see Initiator.outChannel
     */
    override lateinit var outChannel: SuperChannel
}