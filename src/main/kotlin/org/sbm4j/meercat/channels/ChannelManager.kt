package org.sbm4j.meercat.channels

import kotlinx.coroutines.CoroutineScope

/**
 * Manages all [SuperChannel] instances across the entire Meercat node topology.
 *
 * A [ChannelManager] is responsible for creating, initializing, and closing all [SuperChannel]
 * instances used by the topology. It acts as a central registry of channels, ensuring
 * that they are all initialized within a given coroutine scope before the topology starts
 * processing messages, and properly closed when the topology is shut down.
 *
 * Initialization is guarded by the [ready] flag to prevent double initialization.
 */
open class ChannelManager {

    /**
     * The list of all [SuperChannel] instances in the topology.
     */
    val channels: MutableList<SuperChannel> = mutableListOf()

    /**
     * A counter used to generate unique names for [SuperChannel] instances
     * created by [buildChannel].
     */
    var channelIndex = 0

    /**
     * Indicates whether this [ChannelManager] has already been initialized.
     * Set to `true` after the first call to [initChannels].
     */
    var ready = false

    /**
     * Initializes all [SuperChannel] instances in the topology within the given coroutine scope.
     * If this [ChannelManager] has already been initialized, this call is a no-op.
     *
     * @param parentScope the coroutine scope to use as parent for all channels
     */
    fun initChannels(parentScope: CoroutineScope) {
        if(!ready) {
            for (channel in channels) {
                channel.init(parentScope)
            }
            ready = true
        }
    }

    /**
     * Creates a new [SuperChannel], registers it in the topology, and returns it.
     * The channel is not yet initialized — call [initChannels] to initialize
     * all topology channels at once.
     *
     * @return the newly created [SuperChannel]
     */
    fun buildChannel(): SuperChannel {
        val newChannel = SuperChannel("SuperChannel-$channelIndex")
        channelIndex++
        channels.add(newChannel)
        return newChannel
    }

    /**
     * Closes all [SuperChannel] instances in the topology.
     */
    fun closeChannels(){
        for(channel in channels){
            channel.close()
        }
    }
}