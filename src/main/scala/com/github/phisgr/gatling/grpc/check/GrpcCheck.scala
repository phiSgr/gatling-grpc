package com.github.phisgr.gatling.grpc.check

import com.github.phisgr.gatling.grpc.check.GrpcCheck.{All, Scope}
import com.softwaremill.quicklens._
import io.gatling.commons.validation.Validation
import io.gatling.core.check.{Check, CheckResult}
import io.gatling.core.session.{Expression, Session}

import java.util.{Map => JMap}
import scala.annotation.unchecked.uncheckedVariance

case class GrpcCheck[-T](
  wrapped: Check[GrpcResponse[T]@uncheckedVariance], scope: Scope
) extends CheckWithSelfType[GrpcResponse[T], GrpcCheck[T]] {

  override def check(response: GrpcResponse[T], session: Session, cache: JMap[Any, Any]): Validation[CheckResult] =
    wrapped.check(response, session, cache)

  override def checkIf(condition: Expression[Boolean]): GrpcCheck[T] =
    this.modify(_.wrapped).using(_.checkIf(condition))
  override def checkIf(condition: (GrpcResponse[T]@uncheckedVariance, Session) => Validation[Boolean]): GrpcCheck[T] =
    this
      .modify(_.wrapped).using(_.checkIf(condition))
      .modify(_.scope).setTo(All)
}

object GrpcCheck {

  private[gatling] class Scope(val flags: Int) extends AnyVal {
    def checksStatus: Boolean = (flags & Status.flags) != 0
    def checksValue: Boolean = (flags & Value.flags) != 0

    def |(that: Scope) = new Scope(this.flags | that.flags)
  }

  val Status: Scope = new Scope(1)
  val Value: Scope = new Scope(2)
  val Trailers: Scope = new Scope(4)
  val Close: Scope = Status | Trailers
  val All: Scope = Close | Value

}
