package org.sbm4j.meercat

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.hasSize
import io.mockk.coVerify
import io.mockk.spyk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.sbm4j.meercat.channels.ChannelManager
import org.sbm4j.meercat.channels.SuperChannel
import org.sbm4j.meercat.data.*
import org.sbm4j.meercat.nodes.AbstractMiddleNode
import org.sbm4j.meercat.nodes.AbstractSinkNode
import org.sbm4j.meercat.nodes.dispatchers.*
import org.sbm4j.meercat.nodes.sendProcessors.AbstractInitiator
import org.sbm4j.meercat.nodes.sendProcessors.NodeStatus
import java.util.concurrent.ConcurrentHashMap
import java.util.UUID
import kotlin.reflect.KClass
import kotlin.test.Test

class StringSink(
    name: String,
    override var inChannel: SuperChannel
) : AbstractSinkNode(name) {

    val receivedSends: MutableList<StringSend> = mutableListOf()

    suspend fun processSend(send: StringSend): Any {
        receivedSends.add(send)
        return send.buildBack()
    }

    override suspend fun run() {
        val clazz = StringSend::class
        val flow = inChannel.getSendFlow(clazz)
        performSends(clazz, flow, ::processSend)

        super.run()
    }
}

class StringForwarder(
    name: String,
    override var inChannel: SuperChannel,
    override var outChannel: SuperChannel
) : AbstractMiddleNode(name) {

    suspend fun processSend(send: StringSend): Any {
        return send
    }

    suspend fun processBack(back: StringBack) {
    }

    override suspend fun run() {
        val clazz = StringSend::class
        val flow = inChannel.getSendFlow(clazz)
        performSends(clazz, flow, ::processSend)

        val backClazz = StringBack::class
        val backFlow = outChannel.getBackFlow(backClazz)
        this.receiveBacks(backClazz, backFlow, ::processBack)

        super.run()
    }
}


class StringInitiator(
    override val name: String,
    override var outChannel: SuperChannel,
    val sends: List<String>
) : AbstractInitiator() {

    val backs = mutableListOf<StringBack>()

    override suspend fun run() {
        sends.forEach { value ->
            val send = StringSend(value, this)
            val back = outChannel.sendSync<StringBack>(send)
            backs.add(back)
        }
    }
}

class StringRouter(
    override val name: String,
    override var channelIn: SuperChannel,
    channel1: SuperChannel,
    channel2: SuperChannel
) : AbstractRouter() {

    init {
        channelOuts.addAll(listOf(channel1, channel2))
    }

    fun selectFuncChannel(send: StringSend): SuperChannel {
        return if (send.value == "bonjour") channelOuts[0] else channelOuts[1]
    }

    override suspend fun run() {
        performSendBacks(
            StringSend::class,
            StringBack::class as KClass<Back<StringSend>>,
            selectFuncChannel = ::selectFuncChannel)

        super.run()
    }
}

class StringBroadcast(
    override val name: String,
    override var channelIn: SuperChannel,
    override val channelOuts: MutableList<SuperChannel>
) : AbstractBroadcast() {

    override suspend fun run() {
        val flow = channelIn.getSendFlow()
        broadcast("${name}-broadcast", flow)
        super.run()
    }
}

class DualPropagator(
    override val name: String,
    override var channelIn: SuperChannel,
    override val channelOuts: MutableList<SuperChannel>
) : AbstractPropagator(), Broadcast, Router{
    override suspend fun run() {
        performSendBacks(
            StringSend::class,
            StringBack::class as KClass<Back<StringSend>>,
            null,
        ){ send ->
            
            if(send.value == "bonjour") channelOuts[0] else channelOuts[1]
        }


        broadcast("${name}-broadcast",
            channelIn.getSendFlow(IntSend::class)
        )

        channelIn.awaitReady()
        channelOuts.forEach { it.awaitReady() }
    }
}

class DualSink(
    override val name: String,
    override var inChannel: SuperChannel
) : AbstractSinkNode(name) {

    val receivedStringSends: MutableList<StringSend> = mutableListOf()
    val receivedIntSends: MutableList<IntSend> = mutableListOf()

    suspend fun processStringSend(send: StringSend): Any {
        receivedStringSends.add(send)
        return send.buildBack()
    }

    suspend fun processIntSend(send: IntSend): Any {
        receivedIntSends.add(send)
        return send.buildBack()
    }

    override suspend fun run() {
        performSends(StringSend::class, func =::processStringSend)
        performSends(IntSend::class, func =::processIntSend)
        super.run()
    }
}

class DualInitiator(
    override val name: String,
    override var outChannel: SuperChannel,
    val stringSends: List<String>,
    val intSends: List<Int>
) : AbstractInitiator() {

    val backs = mutableListOf<Back<*>>()

    override suspend fun run() {
        stringSends.forEach { value ->
            val b = outChannel.sendSync<StringBack>(StringSend(value, this))
            backs.add(b)
        }
        intSends.forEach { value ->
            val b = outChannel.sendSync<IntBack>(IntSend(value, this))
            backs.add(b)
        }
        initiatorStatus.value = NodeStatus.COMPLETED
    }
}

class DualCombinator(
    override val name: String,
    override val channelsIns: MutableList<SuperChannel>,
    override var channelOut: SuperChannel
) : AbstractCombinator(), Barrier, BackDispatcher {

    override val pendingBarrier: ConcurrentHashMap<String, MutableList<Pair<Send, Int>>> = ConcurrentHashMap()
    override val pendingAnswerable: MutableMap<UUID, SuperChannel> = mutableMapOf()

    override suspend fun <T : Send, B : Back<T>> performSendBacks(
        clazz: KClass<T>,
        backClazz: KClass<B>,
        predicate: ((T) -> Boolean)?
    ) {
        when (clazz) {
            StringSend::class -> super<Barrier>.performSendBacks(clazz, backClazz, predicate)
            IntSend::class -> super<BackDispatcher>.performSendBacks(clazz, backClazz, predicate)
            else -> throw IllegalArgumentException("Unsupported type: $clazz")
        }
    }


    override suspend fun run() {
        performSendBacks(
            StringSend::class,
            StringBack::class as KClass<Back<StringSend>>)
        performSendBacks(
            IntSend::class,
            IntBack::class as KClass<Back<IntSend>>)
        super<AbstractCombinator>.run()
    }
}


class StringBarrier(
    override val channelsIns: MutableList<SuperChannel>,
    override var channelOut: SuperChannel,
    override val name: String = "stringBarrier"
) : AbstractBarrier() {

    override suspend fun run() {
        performSendBacks(StringSend::class, StringBack::class as KClass<Back<StringSend>>)
        super.run()
    }

}

class StringBackDispatcher(
    override val channelsIns: MutableList<SuperChannel>,
    override var channelOut: SuperChannel,
    override val name: String = "stringBackDispatcher"
) : AbstractBackDispatcher() {

    override suspend fun run() {
        performSendBacks(StringSend::class, StringBack::class as KClass<Back<StringSend>>)
        super.run()
    }
}

class SimpleTopologyTests {

    private lateinit var parentScope: CoroutineScope

    @BeforeEach
    fun setup() {
        parentScope = CoroutineScope(Dispatchers.Default)
    }

    @AfterEach
    fun tearDown() {
        parentScope.cancel()
    }

    @Test
    fun `initiator forwarder sink`() = runBlocking {
        val channel1 = SuperChannel("channel1")
        val channel2 = SuperChannel("channel2")

        val initiator = StringInitiator("initiator", channel1, listOf("item1", "item2", "item3"))
        val forwarder = spyk(StringForwarder("forwarder", channel1, channel2))
        val sink = spyk(StringSink("sink", channel2))

        val channelManager = ChannelManager()
        channelManager.channels.addAll(listOf(channel1, channel2))
        val topologyManager = TopologyManager(channelManager).apply {
            nodes.addAll(listOf(initiator, forwarder, sink))
        }

        topologyManager.start(parentScope)?.join()
        topologyManager.waitCompleted()

        coVerify(exactly = 3) { forwarder.processSend(any()) }
        assertThat(sink.receivedSends, hasSize(equalTo(3)))
        assertThat(initiator.backs, hasSize(equalTo(3)))

        topologyManager.stop()
    }


    @Test
    fun `initiator router sink1 sink2`() = runBlocking {
        val channelIn = SuperChannel("channelIn")
        val channel1 = SuperChannel("channel1")
        val channel2 = SuperChannel("channel2")

        val initiator = StringInitiator("initiator", channelIn, listOf("bonjour", "hello", "bonjour"))
        val router = spyk(StringRouter("router", channelIn, channel1, channel2))
        val sink1 = spyk(StringSink("sink1", channel1))
        val sink2 = spyk(StringSink("sink2", channel2))

        val channelManager = ChannelManager()
        channelManager.channels.addAll(listOf(channelIn, channel1, channel2))
        val topologyManager = TopologyManager(channelManager).apply {
            nodes.addAll(listOf(initiator, router, sink1, sink2))
        }

        topologyManager.start(parentScope)?.join()
        topologyManager.waitCompleted()

        assertThat(sink1.receivedSends, hasSize(equalTo(2)))
        assertThat(sink2.receivedSends, hasSize(equalTo(1)))
        assertThat(initiator.backs, hasSize(equalTo(3)))

        topologyManager.stop()
    }

    @Test
    fun `initiator broadcast sink1 sink2`() = runBlocking {
        val channelIn = SuperChannel("channelIn")
        val channel1 = SuperChannel("channel1")
        val channel2 = SuperChannel("channel2")

        val initiator = StringInitiator("initiator", channelIn, listOf("item1", "item2", "item3"))
        val broadcast = spyk(StringBroadcast("broadcast", channelIn, mutableListOf(channel1, channel2)))
        val sink1 = spyk(StringSink("sink1", channel1))
        val sink2 = spyk(StringSink("sink2", channel2))

        val channelManager = ChannelManager()
        channelManager.channels.addAll(listOf(channelIn, channel1, channel2))
        val topologyManager = TopologyManager(channelManager).apply {
            nodes.addAll(listOf(initiator, broadcast, sink1, sink2))
        }

        topologyManager.start(parentScope)?.join()
        topologyManager.waitCompleted()

        assertThat(sink1.receivedSends, hasSize(equalTo(3)))
        assertThat(sink2.receivedSends, hasSize(equalTo(3)))
        assertThat(initiator.backs, hasSize(equalTo(3)))

        topologyManager.stop()
    }

    @Test
    fun `initiator1 initiator2 barrier sink`() = runBlocking {
        val channel1 = SuperChannel("channel1")
        val channel2 = SuperChannel("channel2")
        val channelOut = SuperChannel("channelOut")

        val initiator1 = StringInitiator("initiator1", channel1, listOf("item1", "item2", "item3"))
        val initiator2 = StringInitiator("initiator2", channel2, listOf("item1", "item2", "item3"))
        val barrier = spyk(StringBarrier(mutableListOf(channel1, channel2), channelOut))
        val sink = spyk(StringSink("sink", channelOut))

        val channelManager = ChannelManager()
        channelManager.channels.addAll(listOf(channel1, channel2, channelOut))
        val topologyManager = TopologyManager(channelManager).apply {
            nodes.addAll(listOf(initiator1, initiator2, barrier, sink))
        }

        topologyManager.start(parentScope)?.join()
        topologyManager.waitCompleted()

        assertThat(sink.receivedSends, hasSize(equalTo(3)))
        assertThat(initiator1.backs, hasSize(equalTo(3)))
        assertThat(initiator2.backs, hasSize(equalTo(3)))

        topologyManager.stop()
    }

    @Test
    fun `initiator1 initiator2 backDispatcher sink`() = runBlocking {
        val channel1 = SuperChannel("channel1")
        val channel2 = SuperChannel("channel2")
        val channelOut = SuperChannel("channelOut")

        val initiator1 = StringInitiator("initiator1", channel1, listOf("item1", "item2", "item3"))
        val initiator2 = StringInitiator("initiator2", channel2, listOf("item4", "item5", "item6"))
        val backDispatcher = spyk(StringBackDispatcher(mutableListOf(channel1, channel2), channelOut))
        val sink = spyk(StringSink("sink", channelOut))

        val channelManager = ChannelManager()
        channelManager.channels.addAll(listOf(channel1, channel2, channelOut))
        val topologyManager = TopologyManager(channelManager).apply {
            nodes.addAll(listOf(initiator1, initiator2, backDispatcher, sink))
        }

        topologyManager.start(parentScope)?.join()
        topologyManager.waitCompleted()

        assertThat(sink.receivedSends, hasSize(equalTo(6)))
        assertThat(initiator1.backs, hasSize(equalTo(3)))

        val stringBacks1 = initiator1.backs.map { (it.send).value }
        val stringBacks2 = initiator2.backs.map { (it.send).value }

        assertThat(stringBacks1, hasElements("item1", "item2", "item3"))
        assertThat(initiator2.backs, hasSize(equalTo(3)))
        assertThat(stringBacks2, hasElements("item4", "item5", "item6"))

        topologyManager.stop()
    }

    @Test
    fun `dualInitiator dualPropagator dualSink1 dualSink2`() = runBlocking {
        val channelIn = SuperChannel("channelIn")
        val channel1 = SuperChannel("channel1")
        val channel2 = SuperChannel("channel2")

        val initiator = DualInitiator("dualInitiator", channelIn,
            listOf("bonjour", "hello", "bonjour"),
            listOf(1, 2, 3)
        )
        val propagator = DualPropagator("dualPropagator", channelIn, mutableListOf(channel1, channel2))
        val sink1 = spyk(DualSink("sink1", channel1))
        val sink2 = spyk(DualSink("sink2", channel2))

        val channelManager = ChannelManager()
        channelManager.channels.addAll(listOf(channelIn, channel1, channel2))
        val topologyManager = TopologyManager(channelManager).apply {
            nodes.addAll(listOf(initiator, propagator, sink1, sink2))
        }

        topologyManager.start(parentScope)?.join()
        topologyManager.waitCompleted()

        // routing: "bonjour" → sink1, autres → sink2
        assertThat(sink1.receivedStringSends, hasSize(equalTo(2)))
        assertThat(sink2.receivedStringSends, hasSize(equalTo(1)))

        // broadcast: tous les IntSend sur les deux sinks
        assertThat(sink1.receivedIntSends, hasSize(equalTo(3)))
        assertThat(sink2.receivedIntSends, hasSize(equalTo(3)))

        assertThat(initiator.backs, hasSize(equalTo(6)))

        topologyManager.stop()
    }

    @Test
    fun `dualInitiator1 dualInitiator2 dualCombinator sink`() = runBlocking {
        val channel1 = SuperChannel("channel1")
        val channel2 = SuperChannel("channel2")
        val channelOut = SuperChannel("channelOut")

        val initiator1 = DualInitiator("initiator1", channel1,
            listOf("bonjour", "hello"), listOf(1, 2, 3))
        val initiator2 = DualInitiator("initiator2", channel2,
            listOf("bonjour", "hello"), listOf(4, 5, 6))
        val combinator = DualCombinator("dualCombinator", mutableListOf(channel1, channel2), channelOut)
        val sink = spyk(DualSink("sink", channelOut))

        val channelManager = ChannelManager()
        channelManager.channels.addAll(listOf(channel1, channel2, channelOut))
        val topologyManager = TopologyManager(channelManager).apply {
            nodes.addAll(listOf(initiator1, initiator2, combinator, sink))
        }

        topologyManager.start(parentScope)?.join()
        topologyManager.waitCompleted()

        assertThat(sink.receivedStringSends, hasSize(equalTo(2)))
        assertThat(sink.receivedIntSends, hasSize(equalTo(6)))
        assertThat(initiator1.backs, hasSize(equalTo(5)))
        assertThat(initiator2.backs, hasSize(equalTo(5)))

        topologyManager.stop()
    }
}