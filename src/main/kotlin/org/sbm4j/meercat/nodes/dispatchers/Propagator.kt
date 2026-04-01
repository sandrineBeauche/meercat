package org.sbm4j.meercat.nodes.dispatchers

import org.sbm4j.meercat.channels.SuperChannel
import org.sbm4j.meercat.data.Back
import org.sbm4j.meercat.data.Send
import org.sbm4j.meercat.nodes.AbstractNode
import org.sbm4j.meercat.nodes.Node

/**
 * Represents a node in the Meercat topology that receives [Send] messages from a single
 * input channel and propagates them to multiple output channels, then collects the [Back]
 * responses from all output branches and routes them back through [channelIn].
 *
 * A [Propagator] sits at a divergence point in the topology, acting as the mirror of
 * a [Combinator]. Where a [Combinator] merges multiple input branches into a single output,
 * a [Propagator] fans out a single input into multiple output branches.
 *
 * The behaviour for collecting and routing [Back] responses back through [channelIn]
 * depends entirely on the concrete sub-interface implementation. For instance, a sub-interface
 * may aggregate all [Back] responses into a single one before forwarding, or dispatch each
 * response individually.
 *
 * As with [Combinator], a single [Propagator] node can cumulate multiple behaviours
 * by registering several collectors with different predicates, each handled by a dedicated
 * pair of send and back collectors defined in the sub-interfaces.
 */
interface Propagator: Node {

    /**
     * The [SuperChannel] on which this node listens for incoming [Send] messages,
     * and through which [Back] responses are routed back to the originating [SendSource].
     */
    var channelIn: SuperChannel

    /**
     * The list of output [SuperChannel] instances to which incoming [Send] messages
     * are propagated. Each channel typically corresponds to a distinct branch of the topology.
     */
    val channelOuts: MutableList<SuperChannel>

}

/**
 * Abstract base implementation of [Propagator] that provides concrete [channelIn] and
 * [channelOuts] properties, delegating all other behavior to [AbstractNode].
 *
 * Subclasses only need to implement the propagation logic specific to their use case.
 */
abstract class AbstractPropagator : AbstractNode(), Propagator {

    /**
     * @see Propagator.channelIn
     */
    override lateinit var channelIn: SuperChannel

    /**
     * @see Propagator.channelOuts
     */
    override val channelOuts: MutableList<SuperChannel> = mutableListOf()
}