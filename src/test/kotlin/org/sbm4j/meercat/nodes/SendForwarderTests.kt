package org.sbm4j.meercat.nodes

import com.natpryce.hamkrest.assertion.assertThat
import io.mockk.coVerify
import kotlinx.coroutines.test.runTest
import org.sbm4j.meercat.channels.SuperChannel
import org.sbm4j.meercat.data.Status
import org.sbm4j.meercat.data.TestingBack
import org.sbm4j.meercat.data.TestingSend
import org.sbm4j.meercat.nodes.sendProcessors.SendForwarder
import org.sbm4j.meercat.testingBackWithErrors
import kotlin.test.Test

class TestingSendForwarder(
    override var inChannel: SuperChannel,
    override var outChannel: SuperChannel,
    override val name: String = "TestingSendForwarder"
): SendForwarder, BackForwarder, AbstractProcessingNode(){

    fun consumeSend(send: TestingSend): Any?{
        logger.debug { "${name}: received a ${send.loggingLabel}: ${send.name}" }
        return if(send.value == "coucou"){
            send
        }
        else{
            throw Exception("bad value in Send")
        }
    }

    override suspend fun run() {
        logger.debug { "Running ${name} ..." }
        val clazz = TestingSend::class
        val flow = inChannel.getSendFlow(clazz)
        this.performSends(clazz, flow, ::consumeSend)

        super<SendForwarder>.run()
    }

}

class SendForwarderTests: MiddleNodeTester<TestingSendForwarder>() {

    override fun buildNode(): TestingSendForwarder{
        return TestingSendForwarder(inChannel, outChannel)
    }

    @Test
    fun `simple forward`() = testScope.runTest {
        val send = TestingSend("coucou", sender)
        inChannel.send(send)

        coVerify { stub.processSend(send) }
    }

    @Test
    fun `forward 2 sends`() = testScope.runTest {
        val send = TestingSend("coucou", sender)
        val send2 = TestingSend("coucou", sender)
        inChannel.send(send)
        inChannel.send(send2)

        coVerify { stub.processSend(send) }
        coVerify { stub.processSend(send2) }
    }


    @Test
    fun `node generate error`() = testScope.runTest {
        val send = TestingSend("bonjour", sender)
        val back = inChannel.sendSync<TestingBack>(send)


        coVerify(exactly = 0) { stub.processSend(send) }

        assertThat(back,
            testingBackWithErrors("bonjour", Status.ERROR, 1))

    }
}