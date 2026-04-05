package org.sbm4j.meercat.nodes

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.sbm4j.meercat.channels.SuperChannel
import org.sbm4j.meercat.data.ErrorInfo
import org.sbm4j.meercat.data.ErrorLevel
import org.sbm4j.meercat.data.Send
import org.sbm4j.meercat.nodes.sendProcessors.SendForwarder

abstract class MiddleNodeTester<T>: AbstractSourceNodeTester<T>()
        where T: SendForwarder, T: BackForwarder
{
    lateinit var inChannel: SuperChannel

    @BeforeEach
    fun setup(): Unit = runBlocking {
        inChannel = SuperChannel.build(rootScope)
        node = buildNode()
        node.start(rootScope)?.join()
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