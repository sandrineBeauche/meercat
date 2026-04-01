package org.sbm4j.meercat

import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.sbm4j.meercat.nodes.Node
import org.sbm4j.meercat.nodes.logger
import org.sbm4j.meercat.nodes.sendProcessors.SendSource

abstract class NodeTester<T: Node> {

    val sender = mockk<SendSource>()
    val testScope = TestScope()
    lateinit var rootScope: CoroutineScope
    lateinit var node: T

    abstract fun buildNode(): T

    @BeforeEach
    fun initTestScope(): Unit = runBlocking {
        rootScope = CoroutineScope(Dispatchers.Default)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun cleanupTestScope() {
        rootScope.cancel()
        testScope.advanceUntilIdle()
    }
}