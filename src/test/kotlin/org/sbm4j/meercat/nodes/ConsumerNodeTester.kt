package org.sbm4j.meercat.nodes

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.sbm4j.meercat.NodeTester
import org.sbm4j.meercat.channels.SuperChannel
import org.sbm4j.meercat.nodes.sendProcessors.SendConsumer

abstract class ConsumerNodeTester<T> : NodeTester<T>()
        where T : SendConsumer {

    lateinit var inChannel: SuperChannel

    @BeforeEach
    fun setup(): Unit = runBlocking {
        inChannel = SuperChannel.build(rootScope)
        node = buildNode()

        node.start(rootScope)?.join()
    }


    @AfterEach
    fun tearDown(): Unit = runBlocking {
        inChannel.close()
        node.stop()
        rootScope.cancel()
        cleanupTestScope()
    }
}