package org.sbm4j.meercat.data

import java.util.*
import java.util.concurrent.atomic.AtomicInteger

/**
 * Represents a piece of data that can transit through the Meercat node topology.
 *
 * All messages exchanged between nodes — whether emitted by a source, processed by a consumer,
 * or forwarded by a forwarder — must implement this interface.
 *
 * Each [Channelable] instance is uniquely identified by a [channelableId] and is cloneable,
 * allowing nodes to safely duplicate messages as they pass through the pipeline.
 */
interface Channelable: Cloneable {

    companion object{
        /**
         * A shared atomic counter used to track or generate sequential identifiers
         * across all [Channelable] instances.
         */
        val lastId = AtomicInteger(0)
    }

    /**
     * The unique identifier of this channelable instance.
     * Each message in the topology should carry a distinct UUID to allow tracing and correlation.
     */
    var channelableId: UUID

    /**
     * A human-readable label used in log output to identify this instance.
     * Defaults to the simple class name of the implementing type.
     */
    val loggingLabel: String
        get() = "${this::class.simpleName}"

    /**
     * A descriptive name for this channelable instance.
     * Can be used to identify the nature or origin of the message.
     */
    val name: String

    /**
     * Creates and returns a copy of this [Channelable] instance.
     * The depth of the copy (shallow or deep) is left to the discretion
     * of the implementing class.
     *
     * @return a cloned [Channelable] instance
     */
    public override fun clone(): Channelable
}



