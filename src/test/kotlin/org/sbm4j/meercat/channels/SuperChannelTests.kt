package org.sbm4j.meercat.channels

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.greaterThanOrEqualTo
import com.natpryce.hamkrest.hasSize
import com.natpryce.hamkrest.sameInstance
import io.mockk.mockk
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.sbm4j.meercat.data.Back
import org.sbm4j.meercat.data.Channelable.Companion.lastId
import org.sbm4j.meercat.data.ErrorInfo
import org.sbm4j.meercat.data.ErrorLevel
import org.sbm4j.meercat.data.Send
import org.sbm4j.meercat.data.Status
import org.sbm4j.meercat.nodes.Node
import org.sbm4j.meercat.nodes.logger
import org.sbm4j.meercat.nodes.sendProcessors.SendSource
import org.sbm4j.meercat.testingBack
import java.util.*
import kotlin.test.Test


data class SendA(
    val message: String,
    override var sender: SendSource,
    override val name: String = "A${lastId.getAndIncrement()}"
) : Send {

    override var channelableId: UUID = UUID.randomUUID()
    override fun buildErrorBack(infos: ErrorInfo, status: Status): Back<*> {
        return BackB(this,
            Status.ERROR, mutableListOf(infos))
    }

    override fun buildBack(): BackB {
        return BackB(send = this)
    }

    override fun clone(): Send {
        return this.copy()
    }

    override fun getKeyBarrier(): String {
        return message
    }
}

data class SendC(
    val message: String,
    override var sender: SendSource,
    override val name: String = "A${lastId.getAndIncrement()}"
) : Send {

    override var channelableId: UUID = UUID.randomUUID()
    override fun buildErrorBack(infos: ErrorInfo, status: Status): Back<*> {
        return BackD(this,
            Status.ERROR, mutableListOf(infos))
    }

    override fun buildBack(): BackD {
        return BackD(send = this)
    }

    override fun clone(): Send {
        return this.copy()
    }
}

data class BackB(
    override val send: SendA,
    override var status: Status = Status.OK,
    override val errorInfos: MutableList<ErrorInfo> = mutableListOf(),
    override val name: String = "B${lastId.getAndIncrement()}",
    ) : Back<SendA> {

    override var channelableId: UUID = UUID.randomUUID()
    override fun clone(): Back<SendA> {
        return this.copy()
    }
}

data class BackD(
    override val send: SendC,
    override var status: Status = Status.OK,
    override val errorInfos: MutableList<ErrorInfo> = mutableListOf(),
    override val name: String = "D${lastId.getAndIncrement()}",
) : Back<SendC> {

    override var channelableId: UUID = UUID.randomUUID()
    override fun clone(): Back<SendC> {
        return this.copy()
    }
}




class SuperChannelTests {

    @Test
    fun `send on SuperChannel and receive`() = TestScope().runTest{
        val contA = mockk<SendSource>()

        coroutineScope {
            val channel = SuperChannel.build(this)
            val flow = channel.getSendFlow(SendA::class)

            launch(CoroutineName("launchA")){
                channel.awaitReady()
                repeat(5){
                    val chanA = SendA("messsage #$it from A", sender = contA)
                    val chanB = channel.sendSync<BackB>(chanA)
                    logger.debug{"received ${chanB.loggingLabel} ${chanB.name} for the ${chanA.loggingLabel} ${chanA.name}: ${chanB}"}
                }
                logger.debug{"finished to send sends"}
                channel.close()
            }
            launch(CoroutineName("launchB")){
                flow.take(5).collect { chanA ->
                    logger.debug{"received ${chanA.name} from ${chanA.sender.name} and answers with a back"}
                    val chanB = chanA.buildBack()
                    channel.send(chanB)
                }
                logger.debug{"finished to answers to sends"}
            }
        }
    }

    @Test
    fun `send and receive multiple type of messages`() = TestScope().runTest {
        val sender = mockk<SendSource>()

        coroutineScope {
            val channel = SuperChannel.build(this)
            val flowA = channel.getSendFlow(SendA::class)
            val flowC= channel.getSendFlow(SendC::class)

            coroutineScope {
                launch {
                    channel.awaitReady()
                    repeat(3){
                        val s1 = SendA("coucou$it", sender)
                        channel.send(s1)

                        val s2 = SendC("salut$it", sender)
                        channel.send(s2)
                    }
                }
                launch {
                    flowA.take(3).collect {
                        logger.debug { "received the sendA: ${it}" }
                    }
                }
                launch {
                    flowC.take(3).collect {
                        logger.debug { "received the sendC: ${it}" }
                    }
                }
            }

            channel.close()
        }
    }


    @Test
    fun `send and receive asynchrone`() = TestScope().runTest {
        val sender = mockk<SendSource>()

        coroutineScope {
            val channel = SuperChannel.build(this)
            val flow = channel.getSendFlow()
            val flowBack = channel.getBackFlow()
            val s1 = SendA("coucou", sender)

            launch{
                channel.awaitReady()
                logger.debug{ "send s1"}
                channel.send(s1)
            }
            launch {
                logger.debug{"wait s1"}
                val s = flow.first()
                logger.debug{"received s1: $s"}
                val b = s.buildBack()
                channel.send(b)
                logger.debug{"sent b"}
            }
            launch {
                logger.debug{"wait back"}
                val back = flowBack.first()
                logger.debug{"received back: $back"}
                assertThat(back.send, sameInstance(s1))
                channel.close()
            }
        }
    }

    @Test
    fun `broadcast a message with sendSyncAll`() = TestScope().runTest {
        val sender = mockk<SendSource>()
        val s1 = SendA("coucou", sender)

        coroutineScope {
            val channels = (1..3).map{ SuperChannel.build(this@coroutineScope) }
            val flows = channels.map {
                Pair(it, it.getSendFlow(SendA::class))
            }

            launch{
                channels.forEach{ it.awaitReady() }
                val back = sendSyncAll(channels, s1)
                logger.debug{"received back: $back"}
                channels.forEach { it.close() }
            }
            flows.forEach {
                launch{
                    val message = it.second.first()
                    logger.debug{"received $message on ${it.first.name}" }
                    val response = message.buildBack()
                    it.first.send(response)
                    logger.debug{"sent $response on ${it.first.name}"}
                }
            }
        }
    }

    @Test
    fun `multiple send on channel and receive responses`() = TestScope().runTest{
        val contA = mockk<SendSource>()
        val messages = (1..3).map { SendA("coucou$it", contA) }

        coroutineScope {
            val channel = SuperChannel.build(this)
            val flow = channel.getSendFlow(SendA::class)

            launch(CoroutineName("launchA")){
                channel.awaitReady()
                val backs = channel.sendSync(messages)

                logger.debug{"finished to send sends"}
                channel.close()

                assertThat(backs.size, equalTo(3))
            }
            launch(CoroutineName("launchB")){
                flow.take(3).collect { chanA ->
                    logger.debug{"received ${chanA.name} from ${chanA.sender.name} and answers with a back"}
                    val chanB = chanA.buildBack()
                    channel.send(chanB)
                }
                logger.debug{"finished to answers to sends"}
            }
        }
    }

    @Test
    fun `multiple send on channel and receive aggregated`() = TestScope().runTest{
        val contA = mockk<SendSource>()
        val messages = (1..3).map { SendA("coucou$it", contA) }

        coroutineScope {
            val channel = SuperChannel.build(this)
            val flow = channel.getSendFlow(SendA::class)

            launch(CoroutineName("launchA")){
                channel.awaitReady()
                val back = channel.sendSyncAggregate(messages)

                logger.debug{"finished to send sends"}
                channel.close()

                assertThat(back.status, equalTo(Status.OK))
            }
            launch(CoroutineName("launchB")){
                flow.take(3).collect { chanA ->
                    logger.debug{"received ${chanA.name} from ${chanA.sender.name} and answers with a back"}
                    val chanB = chanA.buildBack()
                    channel.send(chanB)
                }
                logger.debug{"finished to answers to sends"}
            }
        }
    }

    @Test
    fun `multiple send on channel and receive aggregated with error`() = TestScope().runTest{
        val contA = mockk<SendSource>()
        val messages = (1..3).map { SendA("coucou$it", contA) }

        coroutineScope {
            val channel = SuperChannel.build(this)
            val flow = channel.getSendFlow(SendA::class)

            launch(CoroutineName("launchA")){
                channel.awaitReady()
                val back = channel.sendSyncAggregate(messages)

                logger.debug{"finished to send sends"}
                channel.close()

                assertThat(back.status, equalTo(Status.ERROR))
                assertThat(back.errorInfos, hasSize(equalTo(1)))
            }
            launch(CoroutineName("launchB")){
                flow.take(3).collect { chanA ->
                    logger.debug{"received ${chanA.name} from ${chanA.sender.name} and answers with a back"}
                    val chanB = if(chanA.message ==  "coucou2"){
                        val error = ErrorInfo(Exception("an error"), mockk<Node>(), ErrorLevel.MAJOR)
                        chanA.buildErrorBack(error, Status.ERROR)
                    }
                    else {
                        chanA.buildBack()
                    }
                    channel.send(chanB)
                }
                logger.debug{"finished to answers to sends"}
            }
        }
    }
}