package org.sbm4j.meercat.dispatchers

import com.natpryce.hamkrest.assertion.assertThat
import io.mockk.coVerify
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.sbm4j.meercat.channels.SuperChannel
import org.sbm4j.meercat.data.TestingBack
import org.sbm4j.meercat.data.TestingSend
import org.sbm4j.meercat.nodes.dispatchers.AbstractRouter
import org.sbm4j.meercat.testingBack
import kotlin.test.Test

class TestingRouterNode(
    override var channelIn: SuperChannel,
    override val channelOuts: MutableList<SuperChannel>,
    override val name: String = "testingRouter"
) : AbstractRouter() {

    override suspend fun run() {

        performSendBacks(
            TestingSend::class,
            TestingBack::class)
        { send ->
            when {
                send.value.startsWith("branch1") -> channelOuts[0]
                else -> channelOuts[1]
            }
        }
        super.run()
    }
}

class RouterTests : PropagatorTester<TestingRouterNode>() {

    override val nbChannelsOuts = 2

    override fun buildNode() = TestingRouterNode(
        channelIn = channelIn,
        channelOuts = channelOuts
    )

    @Test
    fun `router routes send to branch 0`() = testScope.runTest {
        val send = TestingSend("branch1-item", sender)
        val back = channelIn.sendSync<TestingBack>(send)

        coVerify(exactly = 1) { stubs[0].processSend(match { it is TestingSend && it.value == "branch1-item" }) }
        coVerify(exactly = 0) { stubs[1].processSend(any()) }
        assertThat(back, testingBack("branch1-item"))
    }

    @Test
    fun `router routes send to branch 1`() = testScope.runTest {
        val send = TestingSend("branch2-item", sender)
        val back = channelIn.sendSync<TestingBack>(send)

        coVerify(exactly = 0) { stubs[0].processSend(any()) }
        coVerify(exactly = 1) { stubs[1].processSend(match { it is TestingSend && it.value == "branch2-item" }) }
        assertThat(back, testingBack("branch2-item"))
    }

    @Test
    fun `router routes multiple sends to correct branches`() = testScope.runTest {
        val back1 = channelIn.sendSync<TestingBack>(TestingSend("branch1-item1", sender))
        val back2 = channelIn.sendSync<TestingBack>(TestingSend("branch2-item1", sender))
        val back3 = channelIn.sendSync<TestingBack>(TestingSend("branch1-item2", sender))

        coVerify(exactly = 2) { stubs[0].processSend(any()) }
        coVerify(exactly = 1) { stubs[1].processSend(any()) }
        assertThat(back1, testingBack("branch1-item1"))
        assertThat(back2, testingBack("branch2-item1"))
        assertThat(back3, testingBack("branch1-item2"))
    }



    @Test
    fun `router handles concurrent sends to different branches`() = testScope.runTest {
        val back1Job = async { channelIn.sendSync<TestingBack>(TestingSend("branch1-item", sender)) }
        val back2Job = async { channelIn.sendSync<TestingBack>(TestingSend("branch2-item", sender)) }

        val back1 = back1Job.await()
        val back2 = back2Job.await()

        coVerify(exactly = 1) { stubs[0].processSend(any()) }
        coVerify(exactly = 1) { stubs[1].processSend(any()) }
        assertThat(back1, testingBack("branch1-item"))
        assertThat(back2, testingBack("branch2-item"))
    }
}