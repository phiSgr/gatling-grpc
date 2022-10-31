package com.github.phisgr.gatling.grpc.stream

import com.github.phisgr.gatling.grpc.util.toProtoString
import com.typesafe.scalalogging.Logger
import io.gatling.commons.stats.Status
import io.gatling.core.session.Session
import io.gatling.core.stats.StatsEngine

trait TimestampExtractor[-Res] extends EventExtractor[Res] {
  /**
   * @param session         The session of the streaming branch.
   * @param message         The message received from the streaming call
   * @param streamStartTime The start time of this stream
   * @return The "start time" of this message,
   *         or [[TimestampExtractor.IgnoreMessage]] if this message should not be logged.
   */
  def extractTimestamp(session: Session, message: Res, streamStartTime: Long): Long

  final override def writeEvents(
    session: Session,
    streamStartTime: Long,
    requestName: String,
    message: Res,
    receiveTime: Long,
    statsEngine: StatsEngine,
    logger: Logger,
    status: Status,
    errorMessage: Option[String]
  ): Unit = {
    val extractedTime = extractTimestamp(session, message, streamStartTime)

    if (extractedTime == TimestampExtractor.IgnoreMessage) {
      logger.trace(s"Ignored message\n${toProtoString(message)}")
    } else {
      statsEngine.logResponse(
        session.scenario,
        session.groups,
        requestName = requestName,
        startTimestamp = extractedTime,
        endTimestamp = receiveTime,
        status = status,
        responseCode = None,
        message = errorMessage
      )
    }

  }
}

object TimestampExtractor {
  /** Return this sentinel value and the message will not be logged */
  final val IgnoreMessage = Long.MinValue

  final val Ignore: TimestampExtractor[Any] = (_, _, _) => IgnoreMessage

  /**
   * Java/Kotlin API.
   * Java and Kotlin compilers don't know that
   * [[TimestampExtractor]] is contravariant.
   */
  def ignore[T]: TimestampExtractor[T] = Ignore
}
