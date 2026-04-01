package org.sbm4j.meercat.data

import org.sbm4j.meercat.nodes.Node

/**
 * Holds information about an error that occurred during message processing in the Meercat topology.
 *
 * An [ErrorInfo] is collected into the [Back.errorInfos] list when a node encounters
 * an error while processing a [Send] message, allowing the [SendSource] to inspect
 * what went wrong and where.
 *
 * @property ex the exception that was raised during processing
 * @property node the node in the topology where the error occurred
 * @property level the severity level of the error
 * @property message an optional human-readable description providing additional context,
 * defaults to an empty string
 */
data class ErrorInfo(
    val ex: Exception,
    val node: Node,
    val level: ErrorLevel,
    val message: String = ""
)

/**
 * Represents the severity level of an error that occurred during message processing.
 *
 * - [MINOR]: a non-critical error that does not compromise the overall processing.
 * - [MAJOR]: a significant error that may partially affect the processing result.
 * - [FATAL]: a critical error that prevents any further processing.
 */
enum class ErrorLevel{
    MINOR,
    MAJOR,
    FATAL
}