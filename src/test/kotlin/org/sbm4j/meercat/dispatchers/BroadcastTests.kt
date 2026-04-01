package org.sbm4j.meercat.dispatchers

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.hasSize
import com.natpryce.hamkrest.sameInstance
import io.mockk.coEvery
import io.mockk.coVerify
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.sbm4j.meercat.channels.SuperChannel
import org.sbm4j.meercat.data.Send
import org.sbm4j.meercat.data.Status
import org.sbm4j.meercat.data.TestingBack
import org.sbm4j.meercat.data.TestingSend
import org.sbm4j.meercat.nodes.AbstractProcessingNode
import org.sbm4j.meercat.nodes.dispatchers.AbstractBroadcast
import org.sbm4j.meercat.nodes.dispatchers.Broadcast
import org.sbm4j.meercat.nodes.logger
import org.sbm4j.meercat.testingBack
import org.sbm4j.meercat.testingBackWithErrors
import kotlin.test.Test

class TestingBroadcastNode(
    override var channelIn: SuperChannel,
    override val channelOuts: MutableList<SuperChannel>,
    override val name: String = "testingBroadcast"
) : AbstractBroadcast() {

    override suspend fun run() {
        val flow = channelIn.getSendFlow()
        broadcast("$name-broadcast", flow)
        super.run()
    }
}

class BroadcastTests : PropagatorTester<TestingBroadcastNode>() {

    override val nbChannelsOuts = 2

    override fun buildNode() = TestingBroadcastNode(
        channelIn = channelIn,
        channelOuts = channelOuts
    )

    @Test
    fun `broadcast sends to all branches and aggregates backs`() = testScope.runTest {
        val back = channelIn.sendSync<TestingBack>(TestingSend("item1", sender))
        logger.debug { "received back $back" }

        coVerify(exactly = 1) { stubs[0].processSend(match { it is TestingSend && it.value == "item1" }) }
        coVerify(exactly = 1) { stubs[1].processSend(match { it is TestingSend && it.value == "item1" }) }
        assertThat(back, testingBack("item1"))
    }

    @Test


    fun `broadcast sends independent clones to each branch`() = testScope.runTest {
        val sentSends = mutableListOf<Send>()
        coEvery { stubs[0].processSend(any()) } coAnswers {
            sentSends.add(firstArg())
            firstArg<Send>().buildBack()
        }
        coEvery { stubs[1].processSend(any()) } coAnswers {
            sentSends.add(firstArg())
            firstArg<Send>().buildBack()
        }

        val back = channelIn.sendSync<TestingBack>(TestingSend("item1", sender))
        logger.debug { "received back $back" }

        assertThat(sentSends, hasSize(equalTo(2)))
        assertThat(sentSends[0], !sameInstance(sentSends[1]))
    }

    @Test
    fun `broadcast aggregates error from one branch`() = testScope.runTest {
        stubs[0].respondWithError()

        val back = channelIn.sendSync<TestingBack>(TestingSend("item1", sender))
        logger.debug { "received back $back" }

        assertThat(back, testingBackWithErrors("item1", Status.ERROR, 1))
    }

    @Test
    fun `broadcast aggregates errors from all branches`() = testScope.runTest {
        stubs[0].respondWithError()
        stubs[1].respondWithError()

        val back = channelIn.sendSync<TestingBack>(TestingSend("item1", sender))

        assertThat(back, testingBackWithErrors("item1", Status.ERROR, 2))
    }



    @Test
    fun `broadcast handles multiple parallels sends`() = testScope.runTest {
        val job1 = async {channelIn.sendSync<TestingBack>(TestingSend("item1", sender))}
        val job2 = async{channelIn.sendSync<TestingBack>(TestingSend("item2", sender))}

        val back1 = job1.await()
        val back2 = job2.await()

        logger.debug { "received back $back1" }
        logger.debug { "received back $back2" }

        coVerify(exactly = 1) { stubs[0].processSend(match { it is TestingSend && it.value == "item1" }) }
        coVerify(exactly = 1) { stubs[1].processSend(match { it is TestingSend && it.value == "item1" }) }
        coVerify(exactly = 1) { stubs[0].processSend(match { it is TestingSend && it.value == "item2" }) }
        coVerify(exactly = 1) { stubs[1].processSend(match { it is TestingSend && it.value == "item2" }) }
        assertThat(back1, testingBack("item1"))
        assertThat(back2, testingBack("item2"))
    }

}