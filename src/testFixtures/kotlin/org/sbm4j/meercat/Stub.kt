package org.sbm4j.meercat

import io.mockk.coEvery
import kotlinx.coroutines.delay
import org.sbm4j.meercat.channels.SuperChannel
import org.sbm4j.meercat.data.Back
import org.sbm4j.meercat.data.ErrorInfo
import org.sbm4j.meercat.data.ErrorLevel
import org.sbm4j.meercat.data.Send
import org.sbm4j.meercat.nodes.AbstractSinkNode
import org.sbm4j.meercat.nodes.logger

open class Stub(
    name: String,
    override var inChannel: SuperChannel
) : AbstractSinkNode(name) {

    val responses: MutableMap<Send, Any> = mutableMapOf()

    var processingDelay: Long = 0L

    override suspend fun sendPostProcess(send: Send, result: Any) {
        if(result is Back<*>){
            inChannel.send(result)
        }
    }

    suspend fun processSend(send: Send): Any {
        if(processingDelay > 0) delay(processingDelay)
        logger.debug{"${name}: received ${send.loggingLabel} ${send}"}
        when(val resp = responses.get(send)){
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

    fun respondWithError(
        predicate: ((Send) -> Boolean)? = null,
        message: String = "",
        level: ErrorLevel = ErrorLevel.MAJOR,
        ex: Exception = Exception(message)
    ) {
        if(predicate == null) {
            coEvery { processSend(any()) } answers {
                val send = firstArg<Send>()
                send.buildErrorBack(
                    ErrorInfo(ex, this@Stub, level, message)
                )
            }
        }
        else{
            coEvery { processSend(match { predicate(it) }) } answers {
                val send = firstArg<Send>()
                send.buildErrorBack(
                    ErrorInfo(ex, this@Stub, level, message)
                )
            }
        }
    }
}