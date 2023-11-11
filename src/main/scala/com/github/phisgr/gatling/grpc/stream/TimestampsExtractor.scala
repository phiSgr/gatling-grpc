package com.github.phisgr.gatling.grpc.stream

import com.github.phisgr.gatling.grpc.util.toProtoString
import com.typesafe.scalalogging.Logger
import io.gatling.commons.stats.Status
import io.gatling.core.session.Session
import io.gatling.core.stats.StatsEngine

trait TimestampsExtractor[-Res] extends EventExtractor[Res] {
   /**
   * Extracts timestamps from the given response message in the context of the streaming session.
   *
   * @param session         The streaming session.
   * @param message         The response message.
   * @param streamStartTime The start time of the entire streaming session.
   * @return A list of timestamps extracted from the response, or [[TimestampsExtractor.IgnoreMessage]]
   *         if the message should be ignored.
   */
  def extractTimestamps(session: Session, message: Res, streamStartTime: Long): List[Long]

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
    val extractedTimes = extractTimestamps(session, message, streamStartTime)

    if (extractedTimes == TimestampsExtractor.IgnoreMessage) {
      logger.trace(s"Ignored message\n${toProtoString(message)}")
    } else {
      extractedTimes.foreach { extractedTime => 
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
}

object TimestampsExtractor {
  /** Return this sentinel value and the message will not be logged */
  final val IgnoreMessage = Nil

  final val Ignore: TimestampsExtractor[Any] = (_, _, _) => IgnoreMessage
}
