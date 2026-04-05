package org.sbm4j.meercat.nodes

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.sbm4j.meercat.nodes.sendProcessors.Initiator

abstract class InitiatorTester<T : Initiator> : AbstractSourceNodeTester<T>() {


    @BeforeEach
    fun setup(): Unit = runBlocking {
        node = buildNode()
        node.initialize()
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