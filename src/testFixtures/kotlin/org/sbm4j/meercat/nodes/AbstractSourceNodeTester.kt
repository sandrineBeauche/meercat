package org.sbm4j.meercat.nodes

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.spyk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.sbm4j.meercat.NodeTester
import org.sbm4j.meercat.Stub
import org.sbm4j.meercat.channels.SuperChannel
import org.sbm4j.meercat.data.ErrorInfo
import org.sbm4j.meercat.data.ErrorLevel
import org.sbm4j.meercat.data.Send
import org.sbm4j.meercat.nodes.sendProcessors.SendSource

interface SourceNodeTester<T: SendSource>{
    var stub: Stub

    open
    fun buildStub(channel: SuperChannel): Stub {
        return Stub("stub", channel)
    }

    fun getReceivedSend(stub: Stub = this.stub): List<Send>{
        val result = mutableListOf<Send>()
        coVerify { stub.processSend(capture(result)) }
        return result
    }

    fun respondWithError(
        stub: Stub = this.stub,
        predicate: ((Send) -> Boolean)? = null,
        message: String = "",
        level: ErrorLevel = ErrorLevel.MAJOR,
        ex: Exception = Exception(message)
    ) {
        if(predicate == null) {
            coEvery { stub.processSend(any()) } answers {
                val send = firstArg<Send>()
                send.buildErrorBack(
                    ErrorInfo(ex, stub, level, message)
                )
            }
        }
        else{
            coEvery { stub.processSend(match { predicate(it) }) } answers {
                val send = firstArg<Send>()
                send.buildErrorBack(
                    ErrorInfo(ex, stub, level, message)
                )
            }
        }
    }
}

abstract class AbstractSourceNodeTester<T: SendSource>: NodeTester<T>(), SourceNodeTester<T> {

    override lateinit var stub: Stub

    lateinit var outChannel: SuperChannel

    @BeforeEach
    fun setupStub(): Unit = runBlocking{
        outChannel = SuperChannel.build(rootScope)
        stub = spyk(buildStub(outChannel))
        stub.start(rootScope)?.join()
    }
}