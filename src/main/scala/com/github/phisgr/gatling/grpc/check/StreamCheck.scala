package com.github.phisgr.gatling.grpc.check

import java.util.{Map => JMap}

import com.github.phisgr.gatling.grpc.check.GrpcCheck.Scope
import io.gatling.commons.validation.Validation
import io.gatling.core.check.{Check, CheckResult}
import io.gatling.core.session.Session

import scala.annotation.unchecked.uncheckedVariance

case class StreamCheck[-T](wrapped: Check[T@uncheckedVariance], scope: Scope) extends Check[T@uncheckedVariance] {
  override def check(response: T, session: Session, cache: JMap[Any, Any]): Validation[CheckResult] =
    wrapped.check(response, session, cache)
}
