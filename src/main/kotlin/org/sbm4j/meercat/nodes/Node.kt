package org.sbm4j.meercat.nodes

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import org.sbm4j.meercat.data.ErrorInfo
import org.sbm4j.meercat.data.ErrorLevel
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

val logger = KotlinLogging.logger {}




/**
 * Represents a node in the Meercat topology.
 *
 * A [Node] is a [Controllable] element that participates in the message flow. It owns a [name]
 * used for logging and coroutine naming, and defines a [run] function that is called during
 * startup to set up the flow collectors that will handle incoming messages.
 *
 * The [start] function launches a short-lived setup coroutine that calls [run] and waits
 * for the collectors to be properly registered before completing. The collectors themselves
 * keep running within [scope] independently, for as long as the node is active.
 *
 * Errors encountered during message processing can be packaged into [ErrorInfo] instances
 * using [generateErrorInfos].
 */
interface Node: Controllable {

    /**
     * The name of this node, used for logging and coroutine naming.
     */
    val name: String


    /**
     * Starts this node by launching a setup coroutine within [parentScope],
     * using a default child scope name derived from [name].
     *
     * @param parentScope the coroutine scope to use as parent for this node's internal scope
     * @return the setup [Job] that completes once [run] has finished registering collectors
     */
    suspend fun start(parentScope: CoroutineScope): Job?{
        return start(parentScope, "${name}-root")
    }

    /**
     * Starts this node by launching a short-lived setup coroutine within [parentScope].
     *
     * The setup coroutine initializes the node's [scope], calls [run] to register the flow
     * collectors, then waits briefly to ensure the collectors are in place before completing.
     * The collectors themselves keep running within [scope] independently after the setup
     * coroutine finishes.
     *
     * @param parentScope the coroutine scope to use as parent for this node's internal scope
     * @param rootName the name to assign to the child scope
     * @return the setup [Job] that completes once [run] has finished registering collectors
     */
    override suspend fun start(parentScope: CoroutineScope, rootName: String): Job?{
        return parentScope.launch(CoroutineName("${name}-starting")) {
            super.start(parentScope, rootName)
            run()
            logger.trace{"${name}: finished run"}
        }
    }

    /**
     * Sets up the flow collectors of this node, called during [start].
     *
     * Implementations should override this function to register their collectors
     * on the appropriate [SuperChannel] flows. The default implementation does nothing.
     */
    suspend fun run(){
    }

    /**
     * Stops this node by cancelling its coroutine [scope], which also cancels
     * all collectors running within it.
     */
    override suspend fun stop(){
        super.stop()
    }

    /**
     * Creates an [ErrorInfo] describing an error that occurred in this node.
     *
     * @param ex the exception that was raised
     * @param level the severity level of the error, defaults to [ErrorLevel.MAJOR]
     * @param message an optional human-readable description providing additional context
     * @return an [ErrorInfo] pinpointing this node as the source of the error
     */
    fun generateErrorInfos(
        ex: Exception,
        level: ErrorLevel = ErrorLevel.MAJOR,
        message: String = ""
    ): ErrorInfo {
        return ErrorInfo(ex, this, level, message)
    }
}


abstract class AbstractNode(): Node{
    /**
     * The coroutine scope owned by this node, initialized by [start].
     */
    override lateinit var scope: CoroutineScope
}

/**
 * A base abstract implementation of [Node] that provides the [scope] and [pendingMinorError]
 * properties common to all concrete nodes.
 *
 * Concrete node implementations should extend [AbstractProcessingNode] rather than implementing
 * [Node] directly.
 *
 * Minor errors encountered during message processing are stored in [pendingMinorError],
 * keyed by the [UUID] of the [Send] message being processed, allowing them to be
 * accumulated and attached to the [Back] response later.
 */
abstract class AbstractProcessingNode(): AbstractNode(){

    /**
     * Stores pending minor errors indexed by their associated [Send] identifier,
     * accumulated during processing and attached to the final [Back] response.
     *
     * @see BackForwarder.pendingMinorError
     */
    val pendingMinorError: ConcurrentHashMap<UUID, MutableList<ErrorInfo>> =
        ConcurrentHashMap()
}