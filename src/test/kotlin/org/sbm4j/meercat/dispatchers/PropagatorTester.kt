package org.sbm4j.meercat.dispatchers

import io.mockk.spyk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceUntilIdle
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
            stubs.add(spyk(Stub("stub-$index", channel)))
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
}