package com.github.phisgr.gatling.grpc.action

import com.github.phisgr.gatling.grpc.HeaderPair
import com.github.phisgr.gatling.grpc.check.{GrpcCheck, ResponseExtract}
import com.github.phisgr.gatling.grpc.protocol.GrpcProtocol
import io.gatling.commons.validation.Success
import io.gatling.core.action.Action
import io.gatling.core.action.builder.ActionBuilder
import io.gatling.core.check.{MultipleFindCheckBuilder, ValidatorCheckBuilder}
import io.gatling.core.session.{Expression, ExpressionSuccessWrapper, Session}
import io.gatling.core.structure.ScenarioContext
import io.grpc.{CallOptions, Metadata, MethodDescriptor}

import scala.collection.breakOut

case class GrpcCallActionBuilder[Req, Res](
  requestName: Expression[String],
  method: MethodDescriptor[Req, Res],
  payload: Expression[Req],
  callOptions: Expression[CallOptions] = CallOptions.DEFAULT.expressionSuccess,
  reversedHeaders: List[HeaderPair[_]] = Nil,
  checks: List[GrpcCheck[Res]] = Nil,
  protocolOverride: Option[GrpcProtocol] = None,
  isSilent: Boolean = false
) extends ActionBuilder {
  override def build(ctx: ScenarioContext, next: Action): Action = GrpcCallAction(this, ctx, next)

  def callOptions(callOptions: Expression[CallOptions]) = copy(
    callOptions = callOptions
  )

  /**
   * Sets call options of the call.
   *
   * @param callOptions is a call-by-name param, it will be run every time the call is executed.
   * @return a new GrpcCallActionBuilder
   */
  def callOptions(callOptions: => CallOptions) = copy(
    callOptions = { _: Session => Success(callOptions) }
  )

  def header[T](key: Metadata.Key[T])(value: Expression[T]) = copy(
    reversedHeaders = HeaderPair(key, value) :: reversedHeaders
  )

  private def mapToList[T, U](s: Seq[T])(f: T => U) = s.map[U, List[U]](f)(breakOut)

  def check(checks: GrpcCheck[Res]*) = copy(
    checks = this.checks ::: checks.toList
  )

  // In fact they can be added to checks using .check
  // but the type Res cannot be inferred there
  def extract[X](
    f: Res => Option[X])(
    ts: (ValidatorCheckBuilder[ResponseExtract, Res, X] => GrpcCheck[Res])*
  ) = {
    val e = ResponseExtract.extract(f)
    copy(
      checks = checks ::: mapToList(ts)(_.apply(e))
    )
  }

  def exists[X](f: Res => Option[X]) = extract(f)(_.exists.build(ResponseExtract.materializer))

  def extractMultiple[X](
    f: Res => Option[Seq[X]])(
    ts: (MultipleFindCheckBuilder[ResponseExtract, Res, X] => GrpcCheck[Res])*
  ) = {
    val e = ResponseExtract.extractMultiple[Res, X](f)
    copy(
      checks = checks ::: mapToList(ts)(_.apply(e))
    )
  }

  def target(protocol: GrpcProtocol) = copy(protocolOverride = Some(protocol))

  def silent = copy(isSilent = true)

}
