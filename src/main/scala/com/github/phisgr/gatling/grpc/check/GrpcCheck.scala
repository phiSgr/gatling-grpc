package com.github.phisgr.gatling.grpc.check

import com.github.phisgr.gatling.grpc.check.GrpcCheck.{Scope, Status}
import io.gatling.core.check.Check
import io.gatling.core.session.Session

import scala.collection.mutable
import scala.util.Try

case class GrpcCheck[T](wrapped: Check[Try[T]], scope: Scope) extends Check[Try[T]] {
  override def check(response: Try[T], session: Session)(implicit cache: mutable.Map[Any, Any]) =
    wrapped.check(response, session)(cache)

  def checksStatus = scope == Status
}

object GrpcCheck {

  sealed trait Scope

  case object Status extends Scope

  case object Value extends Scope

}