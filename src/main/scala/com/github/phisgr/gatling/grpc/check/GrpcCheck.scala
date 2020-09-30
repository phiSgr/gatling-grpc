package com.github.phisgr.gatling.grpc.check

import java.util.{Map => JMap}

import com.github.phisgr.gatling.grpc.check.GrpcCheck.{Scope, Status}
import io.gatling.commons.validation.Validation
import io.gatling.core.check.{Check, CheckResult}
import io.gatling.core.session.Session

import scala.annotation.unchecked.uncheckedVariance

case class GrpcCheck[-T](wrapped: Check[GrpcResponse[T]@uncheckedVariance], scope: Scope) extends Check[GrpcResponse[T]@uncheckedVariance] {
  override def check(response: GrpcResponse[T], session: Session, cache: JMap[Any, Any]): Validation[CheckResult] =
    wrapped.check(response, session, cache)

  def checksStatus = scope == Status
}

object GrpcCheck {

  sealed trait Scope

  case object Status extends Scope

  case object Value extends Scope

  case object Trailers extends Scope

}
