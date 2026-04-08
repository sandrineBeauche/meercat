package org.sbm4j.meercat.dispatchers

import io.mockk.spyk
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.sbm4j.meercat.NodeTester
import org.sbm4j.meercat.Stub
import org.sbm4j.meercat.channels.SuperChannel
import org.sbm4j.meercat.data.TestingBack
import org.sbm4j.meercat.data.TestingSend
import org.sbm4j.meercat.nodes.dispatchers.Combinator
import org.sbm4j.meercat.nodes.logger

abstract class CombinatorTester<T: Combinator> : NodeTester<T>() {

    lateinit var channelOut: SuperChannel
    val channelsIns: MutableList<SuperChannel> = mutableListOf()

    lateinit var stub: Stub

    abstract val nbChannelsIns: Int

    @BeforeEach
    fun setup(): Unit = runBlocking {
        channelOut = SuperChannel.build(rootScope, name = "channelOut")
        repeat(nbChannelsIns) { index ->
            channelsIns.add(SuperChannel.build(rootScope, name = "channelIn-${index}"))
        }
        node = buildNode()
        stub = spyk(buildStub(channelOut))

        node.start(rootScope)?.join()
        stub.start(rootScope)?.join()
    }

    @AfterEach
    fun tearDown():Unit = runBlocking {
        node.stop()
        stub.stop()
        channelOut.close()
        channelsIns.forEach { it.close() }
        channelsIns.clear()
        cleanupTestScope()
    }

    suspend fun sendToAllBranches(make: () -> TestingSend): List<TestingBack> = coroutineScope {
        channelsIns.map { channel ->
            async {
                val send = make()
                logger.debug { "sending ${send} on ${channel.name}" }
                val back = channel.sendSync<TestingBack>(send)
                logger.debug { "received back on ${channel.name}: $back" }
                back
            }
        }.awaitAll()
    }

    open fun buildStub(channel: SuperChannel): Stub {
        return Stub("stub", channel)
    }
}