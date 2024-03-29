package com.github.phisgr.gatling.grpc.request

import com.github.phisgr.gatling.generic.check.ResponseExtract
import com.github.phisgr.gatling.grpc
import com.github.phisgr.gatling.grpc.HeaderPair
import com.github.phisgr.gatling.grpc.check.CheckWithSelfType
import com.github.phisgr.gatling.grpc.protocol.GrpcProtocol
import io.gatling.commons.validation.{Success, Validation}
import io.gatling.core.action.builder.ActionBuilder
import io.gatling.core.check.CheckBuilder.{Find, MultipleFind}
import io.gatling.core.session.{Expression, Session}
import io.grpc.{CallOptions, Metadata, MethodDescriptor}

/**
 * Trait to reuse code for specifying checks and call options.
 *
 * @tparam Self Curiously Recurring Template Pattern
 * @tparam Req  request
 * @tparam Res  response
 */
trait CallDefinition[Self, Req, Res] extends ActionBuilder {
  // putting Self as a type member makes type inference dumber

  /**
   * Wraps around `Res` for checking.
   * `GrpcResponse[Res]` (which includes status and trailers) for client streams and unary calls;
   * simply `Res` for server and bidi-streams.
   */
  type Wrap[_]
  type Check[R] <: CheckWithSelfType[Wrap[R], Check[R]]

  private[gatling] def checks: List[Check[Res]]

  def check(checks: Check[Res]*): Self

  def checkIf(condition: Expression[Boolean])(thenChecks: Check[Res]*): Self =
    check(thenChecks.map(_.checkIf(condition)): _*)

  def checkIf(condition: (Wrap[Res], Session) => Validation[Boolean])(thenChecks: Check[Res]*): Self =
    check(thenChecks.map(_.checkIf(condition)): _*)

  // In fact they can be added to checks using .check
  // but the type Res cannot be inferred there
  def extract[X](
    f: Res => Validation[Option[X]])(
    ts: (Find[ResponseExtract, Res, X] => Check[Res])*
  ): Self = {
    val e = grpc.Predef.extract(f)
    check(mapToList(ts)(_.apply(e)): _*)
  }

  def extractIf[X](
    condition: Expression[Boolean])(
    f: Res => Validation[Option[X]])(
    ts: (Find[ResponseExtract, Res, X] => Check[Res])*
  ): Self =
    extract(f)(ts.map(_.andThen(_.checkIf(condition))): _*)

  def extractIf[X](
    condition: (Wrap[Res], Session) => Validation[Boolean])(
    f: Res => Validation[Option[X]])(
    ts: (Find[ResponseExtract, Res, X] => Check[Res])*
  ): Self =
    extract(f)(ts.map(_.andThen(_.checkIf(condition))): _*)

  def extractMultiple[X](
    f: Res => Validation[Option[Seq[X]]])(
    ts: (MultipleFind[ResponseExtract, Res, X] => Check[Res])*
  ): Self = {
    val e = grpc.Predef.extractMultiple[Res, X](f)
    check(mapToList(ts)(_.apply(e)): _*)
  }

  def extractMultipleIf[X](
    condition: Expression[Boolean])(
    f: Res => Validation[Option[Seq[X]]])(
    ts: (MultipleFind[ResponseExtract, Res, X] => Check[Res])*
  ): Self =
    extractMultiple(f)(ts.map(_.andThen(_.checkIf(condition))): _*)

  def extractMultipleIf[X](
    condition: (Wrap[Res], Session) => Validation[Boolean])(
    f: Res => Validation[Option[Seq[X]]])(
    ts: (MultipleFind[ResponseExtract, Res, X] => Check[Res])*
  ): Self =
    extractMultiple(f)(ts.map(_.andThen(_.checkIf(condition))): _*)

  private def mapToList[T, U](s: Seq[T])(f: T => U): List[U] = s.map(f).toList


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
