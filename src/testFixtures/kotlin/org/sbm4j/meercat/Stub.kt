package org.sbm4j.meercat

import kotlinx.coroutines.delay
import org.sbm4j.meercat.channels.SuperChannel
import org.sbm4j.meercat.data.Back
import org.sbm4j.meercat.data.Send
import org.sbm4j.meercat.nodes.AbstractSinkNode
import org.sbm4j.meercat.nodes.logger

open class Stub(
    name: String,
    override var inChannel: SuperChannel
) : AbstractSinkNode(name) {

    val matches: MutableList<Pair<(Send) -> Boolean, Any>> = mutableListOf()

    val responses: MutableMap<Send, Any> = mutableMapOf()

    var processingDelay: Long = 0L

    override suspend fun sendPostProcess(send: Send, result: Any) {
        if(result is Back<*>){
            inChannel.send(result)
        }
    }

    fun findResponse(send: Send): Any? {
        return responses[send]
            ?: matches.firstOrNull { (predicate, _) -> predicate(send) }?.second
    }

    suspend fun processSend(send: Send): Any {
        if(processingDelay > 0) delay(processingDelay)
        logger.debug{"${name}: received ${send.loggingLabel} ${send}"}
        when(val resp = findResponse(send)) {
            null -> {
                logger.debug{"${name}: return default back for ${send.loggingLabel} ${send}"}
                return send.buildBack()
            }
            is Back<*> -> {
                logger.debug{"${name}: return given back for ${send.loggingLabel} ${send}"}
                return resp
            }
            is Exception -> {
                logger.debug{"${name}: throws exception ${resp} for ${send.loggingLabel} ${send}"}
                throw resp
            }
            else -> throw Exception("Unexpected type ${resp.javaClass}")
        }
    }


    override suspend fun run() {
        val sendClazz = Send::class
        val flow = inChannel.getSendFlow()
        performSends(sendClazz, flow, ::processSend)
        super.run()
    }

    override suspend fun stop() {
        responses.clear()
        super.stop()
    }


}