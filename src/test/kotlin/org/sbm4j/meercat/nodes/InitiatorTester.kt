package org.sbm4j.meercat.nodes

import io.mockk.spyk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.sbm4j.meercat.NodeTester
import org.sbm4j.meercat.Stub
import org.sbm4j.meercat.channels.SuperChannel
import org.sbm4j.meercat.nodes.sendProcessors.Initiator

abstract class InitiatorTester<T : Initiator> : NodeTester<T>() {

    lateinit var outChannel: SuperChannel
    lateinit var stub: Stub

    @BeforeEach
    fun setup(): Unit = runBlocking {
        outChannel = SuperChannel.build(rootScope)
        node = buildNode()
        node.initialize()
        stub = spyk(Stub("stub", outChannel))

        stub.start(rootScope)?.join()
    }

    @AfterEach
    fun tearDown(): Unit = runBlocking {
        node.stop()
        stub.stop()
        outChannel.close()
        cleanupTestScope()
    }

    suspend fun startAndWait() {
        node.start(rootScope)?.join()
        node.waitCompleted()
    }
}