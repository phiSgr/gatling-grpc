package com.github.phisgr.gatling.grpc.action

import com.github.phisgr.gatling.grpc.check.GrpcCheck
import com.github.phisgr.gatling.grpc.request.CallAttributes
import io.gatling.core.action.Action
import io.gatling.core.session.Expression
import io.gatling.core.structure.ScenarioContext
import io.grpc.MethodDescriptor

case class GrpcCallActionBuilder[Req, Res](
  requestName: Expression[String],
  private[gatling] override val method: MethodDescriptor[Req, Res],
  payload: Expression[Req],
  private[gatling] override val callAttributes: CallAttributes = CallAttributes(),
  private[gatling] override val checks: List[GrpcCheck[Res]] = Nil,
  isSilent: Boolean = false
) extends UnaryResponseBuilder[GrpcCallActionBuilder[Req, Res], Req, Res] {

  override def build(ctx: ScenarioContext, next: Action): Action = new GrpcCallAction(this, ctx, next)

  override def check(checks: GrpcCheck[Res]*): GrpcCallActionBuilder[Req, Res] =
    copy(checks = this.checks ::: checks.toList)

  override private[gatling] def withCallAttributes(callAttributes: CallAttributes): GrpcCallActionBuilder[Req, Res] =
    copy(callAttributes = callAttributes)

  /**
   * If a request is silent, it will not be included in the final report.
   * On failure, it will not mark the session as failed.
   *
   * @return a new action builder with isSilent set to true
   */
  def silent: GrpcCallActionBuilder[Req, Res] =
    copy(isSilent = true)

}
