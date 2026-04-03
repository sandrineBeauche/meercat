package org.sbm4j.meercat.nodes

import io.mockk.spyk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.sbm4j.meercat.NodeTester
import org.sbm4j.meercat.Stub
import org.sbm4j.meercat.channels.SuperChannel
import org.sbm4j.meercat.data.ErrorInfo
import org.sbm4j.meercat.data.ErrorLevel
import org.sbm4j.meercat.data.Send
import org.sbm4j.meercat.nodes.sendProcessors.SendForwarder

abstract class MiddleNodeTester<T>: NodeTester<T>()
        where T: SendForwarder, T: BackForwarder
{

    lateinit var inChannel: SuperChannel
    lateinit var outChannel: SuperChannel

    lateinit var stub: Stub

    open fun buildStub(channel: SuperChannel): Stub {
        return spyk(Stub("stub", channel))
    }

    @BeforeEach
    fun setup(): Unit = runBlocking {
        inChannel = SuperChannel.build(rootScope)
        outChannel = SuperChannel.build(rootScope)
        node = buildNode()
        stub = buildStub(outChannel)

        node.start(rootScope)?.join()
        stub.start(rootScope)?.join()
    }

    @AfterEach
    fun tearDown() = runBlocking {
        node.stop()
        stub.stop()
        inChannel.close()
        outChannel.close()
        cleanupTestScope()
    }

    fun addMinorErrorToNode(send: Send, ex: Exception){
        val error = ErrorInfo(Exception(), sender, ErrorLevel.MINOR)
        node.pendingMinorError[send.channelableId] = mutableListOf(error)
    }
}