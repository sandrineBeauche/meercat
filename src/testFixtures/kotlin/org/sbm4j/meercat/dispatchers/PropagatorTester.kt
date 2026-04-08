package org.sbm4j.meercat.dispatchers

import io.mockk.spyk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.sbm4j.meercat.NodeTester
import org.sbm4j.meercat.Stub
import org.sbm4j.meercat.channels.SuperChannel
import org.sbm4j.meercat.nodes.dispatchers.Propagator

abstract class PropagatorTester<T> : NodeTester<T>()
        where T : Propagator {

    lateinit var channelIn: SuperChannel
    val channelOuts: MutableList<SuperChannel> = mutableListOf()
    val stubs: MutableList<Stub> = mutableListOf()

    abstract val nbChannelsOuts: Int

    @BeforeEach
    fun setup(): Unit = runBlocking {
        channelIn = SuperChannel.build(rootScope)
        repeat(nbChannelsOuts) { index ->
            val channel = SuperChannel.build(rootScope)
            channelOuts.add(channel)
            stubs.add(spyk(buildStub(channel, "stub-$index")))
        }
        node = buildNode()

        node.start(rootScope)?.join()
        stubs.forEach { it.start(rootScope)?.join() }
    }

    @AfterEach
    fun tearDown():Unit = runBlocking {
        node.stop()
        stubs.forEach { it.stop() }
        channelIn.close()
        channelOuts.forEach { it.close() }
        channelOuts.clear()
        stubs.clear()
        cleanupTestScope()
    }

    open fun buildStub(channel: SuperChannel, name: String): Stub {
        return Stub(name, channel)
    }
}