package org.sbm4j.meercat.nodes

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has
import com.natpryce.hamkrest.isA
import com.natpryce.hamkrest.sameInstance
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.sbm4j.meercat.channels.SuperChannel
import org.sbm4j.meercat.data.*
import org.sbm4j.meercat.nodes.sendProcessors.SendConsumer
import org.sbm4j.meercat.testingBack
import kotlin.test.Test


class TestingSendConcumer(
    override var inChannel: SuperChannel,
    name: String = "TestingConsumer2"
): AbstractSinkNode(name) {
    suspend fun consumeSend1(send: TestingSend): Any?{
        logger.debug{"${name}: inside consumeSend1 -> received a ${send.loggingLabel}: ${send.name}"}
        return send.buildBack()
    }

    suspend fun consumeSend2(send: TestingSend2): Any?{
        logger.debug{"${name}: inside consumeSend2 -> received a ${send.loggingLabel}: ${send.name}"}
        return send.buildBack()
    }

    override suspend fun run() {
        logger.debug { "Running ${name} ..." }
        val clazz1 = TestingSend::class
        val flow1 = inChannel.getSendFlow(clazz1)
        this.performSends(clazz1, flow1, ::consumeSend1)

        val clazz2 = TestingSend2::class
        val flow2 = inChannel.getSendFlow(clazz2)
        this.performSends(clazz2, flow2, ::consumeSend2)

        super.run()
    }
}


class SendConsumerTests : ConsumerNodeTester<TestingSendConcumer>() {

    override fun buildNode() = TestingSendConcumer(inChannel, "sendConsumer")

    @Test
    fun `consumer processes TestingSend and returns back`() = testScope.runTest {
        val send = TestingSend("coucou", sender)
        val back = inChannel.sendSync<TestingBack>(send)

        assertThat(back, testingBack(send))
    }

    @Test
    fun `consumer processes TestingSend2 and returns back`() = testScope.runTest {
        val send = TestingSend2("bonjour", sender)
        val back = inChannel.sendSync<TestingBack2>(send)

        assertThat(back, isA<TestingBack2>(has(TestingBack2::send, sameInstance(send))))
    }

    @Test
    fun `consumer processes multiple TestingSend concurrently`() = testScope.runTest {
        val backs = (0..2).map { index ->
            async {
                val send = TestingSend("coucou-$index", sender)
                inChannel.sendSync<TestingBack>(send)
            }
        }.awaitAll()

        backs.forEachIndexed { index, back ->
            assertThat(back, testingBack("coucou-$index"))
        }
    }

    @Test
    fun `consumer processes mixed Send types concurrently`() = testScope.runTest {
        val backs1 = (0..2).map { index ->
            async {
                val send = TestingSend("coucou-$index", sender)
                inChannel.sendSync<TestingBack>(send)
            }
        }
        val backs2 = (0..2).map { index ->
            async {
                val send = TestingSend2("bonjour-$index", sender)
                inChannel.sendSync<TestingBack2>(send)
            }
        }

        backs1.awaitAll().forEachIndexed { index, back ->
            assertThat(back, testingBack("coucou-$index"))
        }
        backs2.awaitAll().forEachIndexed { index, back ->
            assertThat(back, isA<TestingBack2>(
                has(TestingBack2::send,
                    has(TestingSend2::value,
                        equalTo("bonjour-$index")))
            ))
        }
    }
}