package org.sbm4j.meercat.dispatchers

import com.natpryce.hamkrest.assertion.assertThat
import io.mockk.coVerify
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.sbm4j.meercat.channels.SuperChannel
import org.sbm4j.meercat.data.Send
import org.sbm4j.meercat.data.Status
import org.sbm4j.meercat.data.TestingBack
import org.sbm4j.meercat.data.TestingSend
import org.sbm4j.meercat.nodes.dispatchers.AbstractBarrier
import org.sbm4j.meercat.nodes.logger
import org.sbm4j.meercat.testingBack
import org.sbm4j.meercat.testingBackWithErrors

class TestingBarrierNode(
    override val channelsIns: MutableList<SuperChannel>,
    override var channelOut: SuperChannel,
    override val name: String = "testingBarrier"
) : AbstractBarrier() {

    override suspend fun run() {
        performSendBacks(TestingSend::class, TestingBack::class)
        super.run()
    }

}


class BarrierTests : CombinatorTester<TestingBarrierNode>() {

    override val nbChannelsIns = 2

    override fun buildNode() = TestingBarrierNode(
        channelsIns = channelsIns,
        channelOut = channelOut
    )

    @Test
    fun `barrier forwards send when all branches have sent`() = testScope.runTest {
        val backs = sendToAllBranches{ TestingSend("item1", sender) }

        logger.debug { "received all backs: $backs" }

        coVerify(exactly = 1) { stub.processSend(match { it is TestingSend && it.value == "item1" }) }
        assertThat(backs[0], testingBack("item1"))
        assertThat(backs[1], testingBack("item1"))
    }

    @Test
    fun `barrier does not forward when only one branch has sent`() = testScope.runTest {
        channelsIns[0].send(TestingSend("item1", sender))

        delay(300)

        coVerify(exactly = 0) { stub.processSend(any()) }
    }

    @Test
    fun `barrier resolves two independent keys independently`() = testScope.runTest {
        val backs1 = sendToAllBranches{ TestingSend("item1", sender) }
        val backs2 = sendToAllBranches{ TestingSend("item2", sender) }

        coVerify(exactly = 1) { stub.processSend(match { it is TestingSend && it.value == "item1" }) }
        coVerify(exactly = 1) { stub.processSend(match { it is TestingSend && it.value == "item2" }) }
        assertThat(backs1[0], testingBack("item1"))
        assertThat(backs1[1], testingBack("item1"))
        assertThat(backs2[0], testingBack("item2"))
        assertThat(backs2[1], testingBack("item2"))
    }

    @Test
    fun `barrier broadcasts error back to all branches`() = testScope.runTest {
        val ex = Exception("an exception")
        val pred = TestingSend.predicateOnValue("item1")
        stub.matches.add(pred to ex)

        val backs = sendToAllBranches{ TestingSend("item1", sender) }

        coVerify(exactly = 1) { stub.processSend(any()) }
        assertThat(backs[0], testingBackWithErrors("item1", Status.ERROR, 1))
        assertThat(backs[1], testingBackWithErrors("item1", Status.ERROR, 1))
    }

}