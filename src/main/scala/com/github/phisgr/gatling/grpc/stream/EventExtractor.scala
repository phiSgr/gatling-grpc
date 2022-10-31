package com.github.phisgr.gatling.grpc.stream

import com.typesafe.scalalogging.Logger
import io.gatling.commons.stats.Status
import io.gatling.core.session.Session
import io.gatling.core.stats.StatsEngine

/**
 * Advanced API allowing control over events written for stream messages.
 */
trait EventExtractor[-Res] {
  def writeEvents(
    session: Session,
    streamStartTime: Long,
    requestName: String,
    message: Res,
    receiveTime: Long,
    statsEngine: StatsEngine,
    logger: Logger,
    status: Status, // is OK iff errorMessage is None
    errorMessage: Option[String]
  ): Unit
}
