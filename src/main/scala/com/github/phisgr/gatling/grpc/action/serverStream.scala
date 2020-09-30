package com.github.phisgr.gatling.grpc.action

import com.github.phisgr.gatling.forToMatch
import com.github.phisgr.gatling.grpc.check.GrpcResponse.GrpcStreamEnd
import com.github.phisgr.gatling.grpc.check.{CheckMixin, GrpcCheck, StatusExtract, StreamCheck}
import com.github.phisgr.gatling.grpc.request.{Call, CallAttributes, CallAttributesMixin}
import com.github.phisgr.gatling.grpc.stream.StreamCall.ensureNoStream
import com.github.phisgr.gatling.grpc.stream.{ServerStreamCall, SessionCombiner, TimestampExtractor}
import io.gatling.commons.util.Clock
import io.gatling.commons.validation.Validation
import io.gatling.core.action.builder.ActionBuilder
import io.gatling.core.action.{Action, RequestAction}
import io.gatling.core.session.{Expression, Session}
import io.gatling.core.stats.StatsEngine
import io.gatling.core.structure.ScenarioContext
import io.gatling.core.util.NameGen
import io.grpc.MethodDescriptor

case class ServerStreamStartActionBuilder[Req, Res](
  private[gatling] val requestName: Expression[String],
  private[gatling] val streamName: String,
  private[gatling] val method: MethodDescriptor[Req, Res],
  private[gatling] val req: Expression[Req],
  private[gatling] val _timestampExtractor: TimestampExtractor[Res] = TimestampExtractor.Ignore,
  private[gatling] val _sessionCombiner: SessionCombiner = SessionCombiner.NoOp,
  private[gatling] val callAttributes: CallAttributes = CallAttributes(),
  private[gatling] val checks: List[StreamCheck[Res]] = Nil,
  private[gatling] val endChecks: List[StreamCheck[GrpcStreamEnd]] = Nil,
) extends ActionBuilder
  with CallAttributesMixin[ServerStreamStartActionBuilder[Req, Res]]
  with CheckMixin[ServerStreamStartActionBuilder[Req, Res], StreamCheck, Res] {

  override def build(ctx: ScenarioContext, next: Action): Action =
    new ServerStreamStartAction(this, ctx, next)

  def timestampExtractor(extractor: TimestampExtractor[Res]): ServerStreamStartActionBuilder[Req, Res] =
    copy(_timestampExtractor = extractor)
  def sessionCombiner(sessionCombiner: SessionCombiner): ServerStreamStartActionBuilder[Req, Res] =
    copy(_sessionCombiner = sessionCombiner)

  override def check(checks: StreamCheck[Res]*): ServerStreamStartActionBuilder[Req, Res] =
    copy(checks = this.checks ::: checks.toList)
  def endCheck(endChecks: StreamCheck[GrpcStreamEnd]*): ServerStreamStartActionBuilder[Req, Res] =
    copy(endChecks = this.endChecks ::: endChecks.toList)

  override private[gatling] def withCallAttributes(callAttributes: CallAttributes): ServerStreamStartActionBuilder[Req, Res] =
    copy(callAttributes = callAttributes)
}

class ServerStreamStartAction[Req, Res](
  builder: ServerStreamStartActionBuilder[Req, Res],
  ctx: ScenarioContext,
  override val next: Action
) extends Call(ctx, builder.callAttributes) with RequestAction with NameGen {

  private[this] val endChecks = if (builder.endChecks.exists(_.scope == GrpcCheck.Status)) builder.endChecks else {
    StatusExtract.DefaultStreamCheck :: builder.endChecks
  }

  override def requestName: Expression[String] = builder.requestName
  override def sendRequest(requestName: String, session: Session): Validation[Unit] = forToMatch {
    val streamName = builder.streamName

    for {
      _ <- ensureNoStream(session, streamName, isBidi = false)
      headers <- resolveHeaders(session)
      resolvedPayload <- builder.req(session)
      callOptions <- callOptions(session)
    } yield {
      val channel = component.getChannel(session)
      next ! session.set(streamName, new ServerStreamCall(
        requestName = requestName,
        streamName = streamName,
        channel.newCall(builder.method, callOptions),
        headers,
        resolvedPayload,
        ctx,
        builder._timestampExtractor,
        builder._sessionCombiner,
        session,
        builder.checks,
        endChecks
      ))
    }
  }
  override def statsEngine: StatsEngine = ctx.coreComponents.statsEngine
  override def clock: Clock = ctx.coreComponents.clock
  override val name: String = genName("serverStreamStart")
}
