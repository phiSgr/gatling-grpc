package com.github.phisgr.gatling.grpc.action

import io.gatling.commons.validation.Validation
import io.gatling.core.session.Session

import scala.reflect.ClassTag

trait StreamCallAccess {
  protected def direction: String
  private def callFetchErrorMessage: String = s"Couldn't fetch open $direction stream"

  final def fetchCall[Call: ClassTag](streamName: String, session: Session): Validation[Call] =
    session(streamName)
      .validate[Call]
      .mapFailure(m => s"$callFetchErrorMessage: $m")
}
