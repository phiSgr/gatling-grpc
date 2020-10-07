package com.github.phisgr.gatling.grpc.request

import com.github.phisgr.gatling.grpc.HeaderPair
import com.github.phisgr.gatling.grpc.check.ResponseExtract
import com.github.phisgr.gatling.grpc.protocol.GrpcProtocol
import io.gatling.commons.validation.Success
import io.gatling.core.check.{FindCheckBuilder, MultipleFindCheckBuilder}
import io.gatling.core.session.{Expression, Session}
import io.grpc.{CallOptions, Metadata, MethodDescriptor}

import scala.collection.breakOut

trait CallDefinition[Self, Check[_], Req, Res] {

  private[gatling] def checks: List[Check[Res]]

  def check(checks: Check[Res]*): Self

  // In fact they can be added to checks using .check
  // but the type Res cannot be inferred there
  def extract[X](
    f: Res => Option[X])(
    ts: (FindCheckBuilder[ResponseExtract, Res, X] => Check[Res])*
  ): Self = {
    val e = ResponseExtract.extract(f)
    check(mapToList(ts)(_.apply(e)): _*)
  }

  def extractMultiple[X](
    f: Res => Option[Seq[X]])(
    ts: (MultipleFindCheckBuilder[ResponseExtract, Res, X] => Check[Res])*
  ): Self = {
    val e = ResponseExtract.extractMultiple[Res, X](f)
    check(mapToList(ts)(_.apply(e)): _*)
  }

  private def mapToList[T, U](s: Seq[T])(f: T => U): List[U] = s.map[U, List[U]](f)(breakOut)


  private[gatling] def callAttributes: CallAttributes

  private[gatling] def withCallAttributes(callAttributes: CallAttributes): Self

  def callOptions(callOptions: Expression[CallOptions]): Self =
    withCallAttributes(callAttributes.copy(callOptions = callOptions))

  /**
   * Sets call options of the call.
   *
   * @param callOptions is a call-by-name param, it will be run every time the call is executed.
   * @return a new GrpcCallActionBuilder
   */
  def callOptions(callOptions: => CallOptions): Self =
    this.callOptions { _: Session => Success(callOptions) }

  def header[T](key: Metadata.Key[T])(value: Expression[T]): Self =
    withCallAttributes(callAttributes.copy(
      reversedHeaders = HeaderPair(key, value) :: callAttributes.reversedHeaders
    ))

  def target(protocol: GrpcProtocol): Self =
    this.withCallAttributes(callAttributes.copy(protocolOverride = Some(protocol)))

  private[gatling] val method: MethodDescriptor[Req, Res]
}
