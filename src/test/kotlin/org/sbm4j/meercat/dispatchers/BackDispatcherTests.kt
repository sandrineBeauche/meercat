package org.sbm4j.meercat.dispatchers

import com.natpryce.hamkrest.assertion.assertThat
import io.mockk.coVerify
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import java.util.UUID
import org.sbm4j.meercat.channels.SuperChannel
import org.sbm4j.meercat.data.Back
import org.sbm4j.meercat.data.Send
import org.sbm4j.meercat.data.TestingBack
import org.sbm4j.meercat.data.TestingSend
import org.sbm4j.meercat.nodes.AbstractProcessingNode
import org.sbm4j.meercat.nodes.dispatchers.AbstractBackDispatcher
import org.sbm4j.meercat.nodes.dispatchers.BackDispatcher
import org.sbm4j.meercat.testingBack
import kotlin.reflect.KClass
import kotlin.test.Test

class TestingBackDispatcherNode(
    override val channelsIns: MutableList<SuperChannel>,
    override var channelOut: SuperChannel,
    override val name: String = "testingBackDispatcher"
) : AbstractBackDispatcher() {

    override suspend fun run() {
        performSendBacks(TestingSend::class, TestingBack::class)
        super.run()
    }
}

class BackDispatcherTests : CombinatorTester<TestingBackDispatcherNode>() {

    override val nbChannelsIns = 2

    override fun buildNode() = TestingBackDispatcherNode(
        channelsIns = channelsIns,
        channelOut = channelOut
    )

    @Test
    fun `dispatcher forwards send from branch 0 and dispatches back`() = testScope.runTest {
        val send = TestingSend("item1", sender)
        val back = channelsIns[0].sendSync<TestingBack>(send)

        coVerify(exactly = 1) { stub.processSend(match { it is TestingSend && it.value == "item1" }) }
        assertThat(back, testingBack("item1"))
    }

    @Test
    fun `dispatcher forwards send from branch 1 and dispatches back`() = testScope.runTest {
        val send = TestingSend("item1", sender)
        val back = channelsIns[1].sendSync<TestingBack>(send)

        coVerify(exactly = 1) { stub.processSend(match { it is TestingSend && it.value == "item1" }) }
        assertThat(back, testingBack("item1"))
    }

    @Test
    fun `dispatcher dispatches backs to correct branches`() = testScope.runTest {
        val backs = channelsIns.mapIndexed { index, channel ->
            val send = TestingSend("item${index + 1}", sender)
            async {channel.sendSync<TestingBack>(send)}
        }.awaitAll()

        coVerify(exactly = 1) { stub.processSend(match { it is TestingSend && it.value == "item1" }) }
        coVerify(exactly = 1) { stub.processSend(match { it is TestingSend && it.value == "item2" }) }

        assertThat(backs[0], testingBack("item1"))
        assertThat(backs[1], testingBack("item2"))
    }


    @Test
    fun `dispatcher handles concurrent sends from same branch`() = testScope.runTest {
        val backs = (0..1).toList().map{ index ->
            async{
                val send = TestingSend("item${index + 1}", sender)
                channelsIns[0].sendSync<TestingBack>(send)
            }
        }.awaitAll()

        coVerify(exactly = 2) { stub.processSend(any()) }
        assertThat(backs[0], testingBack("item1"))
        assertThat(backs[1], testingBack("item2"))
    }

}