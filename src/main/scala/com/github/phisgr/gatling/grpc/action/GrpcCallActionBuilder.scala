package com.github.phisgr.gatling.grpc.action

import com.github.phisgr.gatling.grpc.check.{CheckMixin, GrpcCheck}
import com.github.phisgr.gatling.grpc.request.{CallAttributes, CallAttributesMixin}
import io.gatling.core.action.Action
import io.gatling.core.action.builder.ActionBuilder
import io.gatling.core.session.Expression
import io.gatling.core.structure.ScenarioContext
import io.grpc.MethodDescriptor

case class GrpcCallActionBuilder[Req, Res](
  requestName: Expression[String],
  method: MethodDescriptor[Req, Res],
  payload: Expression[Req],
  private[gatling] val callAttributes: CallAttributes = CallAttributes(),
  checks: List[GrpcCheck[Res]] = Nil,
  isSilent: Boolean = false
) extends ActionBuilder
  with CallAttributesMixin[GrpcCallActionBuilder[Req, Res]]
  with CheckMixin[GrpcCallActionBuilder[Req, Res], GrpcCheck, Res] {

  override def build(ctx: ScenarioContext, next: Action): Action = new GrpcCallAction(this, ctx, next)

  override def check(checks: GrpcCheck[Res]*): GrpcCallActionBuilder[Req, Res] =
    copy(checks = this.checks ::: checks.toList)

  override private[gatling] def withCallAttributes(callAttributes: CallAttributes): GrpcCallActionBuilder[Req, Res] =
    copy(callAttributes = callAttributes)

  def silent: GrpcCallActionBuilder[Req, Res] =
    copy(isSilent = true)

}
