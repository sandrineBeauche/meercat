package org.sbm4j.meercat.data

import org.sbm4j.meercat.nodes.sendProcessors.SendSource
import java.util.UUID

data class TestingSend(
    val value: String,
    override var sender: SendSource,
    override val name: String = "$value-send"
) : Send {
    companion object {
        fun predicateOnValue(value: String): (Send) -> Boolean {
            return { send: Send -> send is TestingSend && send.value == value }
        }
    }

    override var channelableId: UUID = UUID.randomUUID()

    override fun buildErrorBack(
        infos: ErrorInfo,
        status: Status
    ): Back<*> {
        return TestingBack(this, status, mutableListOf(infos))
    }

    override fun buildBack(): Back<*> {
        return TestingBack(this)
    }


    override fun clone(): Send {
        return this.copy()
    }

    override fun getKeyBarrier(): String {
        return value
    }
}

data class TestingBack(
    override val send: TestingSend,
    override var status: Status = Status.OK,
    override val errorInfos: MutableList<ErrorInfo> = mutableListOf(),
    override val name: String = "${send.value}-back"
): Back<TestingSend> {

    override var channelableId: UUID = UUID.randomUUID()

    override fun clone(): Back<TestingSend> {
        return this.copy()
    }

}

data class TestingSend2(
    val value: String,
    override var sender: SendSource,
    override val name: String = "$value-send2"
) : Send {

    override var channelableId: UUID = UUID.randomUUID()

    override fun buildErrorBack(
        infos: ErrorInfo,
        status: Status
    ): Back<*> {
        return TestingBack2(this, status, mutableListOf(infos))
    }

    override fun buildBack(): Back<*> {
        return TestingBack2(this)
    }

    override fun clone(): Send {
        return this.copy()
    }

    override fun getKeyBarrier(): String {
        return value
    }
}

data class TestingBack2(
    override val send: TestingSend2,
    override var status: Status = Status.OK,
    override val errorInfos: MutableList<ErrorInfo> = mutableListOf(),
    override val name: String = "${send.value}-back2"
): Back<TestingSend2> {

    override var channelableId: UUID = UUID.randomUUID()

    override fun clone(): Back<TestingSend2> {
        return this.copy()
    }

}