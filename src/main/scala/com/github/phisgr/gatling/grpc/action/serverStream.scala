package com.github.phisgr.gatling.grpc.action

import com.github.phisgr.gatling.forToMatch
import com.github.phisgr.gatling.generic.SessionCombiner
import com.github.phisgr.gatling.grpc.check.GrpcResponse.GrpcStreamEnd
import com.github.phisgr.gatling.grpc.check.StreamCheck
import com.github.phisgr.gatling.grpc.request.CallAttributes
import com.github.phisgr.gatling.grpc.stream.StreamCall.{AlwaysLog, StreamEndLog, ensureNoStream}
import com.github.phisgr.gatling.grpc.stream.{EventExtractor, ServerStreamCall, TimestampExtractor}
import io.gatling.commons.util.Clock
import io.gatling.commons.validation.Validation
import io.gatling.core.action.Action
import io.gatling.core.session.{Expression, Session}
import io.gatling.core.stats.StatsEngine
import io.gatling.core.structure.ScenarioContext
import io.grpc.MethodDescriptor

case class ServerStreamStartActionBuilder[Req, Res](
  private[gatling] val requestName: Expression[String],
  private[gatling] val streamName: Expression[String],
  private[gatling] override val method: MethodDescriptor[Req, Res],
  private[gatling] val req: Expression[Req],
  private[gatling] override val extractor: EventExtractor[Res] = TimestampExtractor.Ignore,
  private[gatling] val _sessionCombiner: SessionCombiner = SessionCombiner.NoOp,
  private[gatling] override val callAttributes: CallAttributes = CallAttributes(),
  private[gatling] override val checks: List[StreamCheck[Res]] = Nil,
  private[gatling] override val endChecks: List[StreamCheck[GrpcStreamEnd]] = Nil,
  private[gatling] val logWhen: StreamEndLog = AlwaysLog
) extends StreamStartBuilder[ServerStreamStartActionBuilder[Req, Res], Req, Res] {

  override def build(ctx: ScenarioContext, next: Action): Action =
    new ServerStreamStartAction(this, ctx, next)

  override def eventExtractor(extractor: EventExtractor[Res]): ServerStreamStartActionBuilder[Req, Res] =
    copy(extractor = extractor)
  override def sessionCombiner(sessionCombiner: SessionCombiner): ServerStreamStartActionBuilder[Req, Res] =
    copy(_sessionCombiner = sessionCombiner)

  override private[gatling] def withCallAttributes(callAttributes: CallAttributes): ServerStreamStartActionBuilder[Req, Res] =
    copy(callAttributes = callAttributes)

  override def check(checks: StreamCheck[Res]*): ServerStreamStartActionBuilder[Req, Res] =
    copy(checks = this.checks ::: checks.toList)
  override def endCheck(endChecks: StreamCheck[GrpcStreamEnd]*): ServerStreamStartActionBuilder[Req, Res] =
    copy(endChecks = this.endChecks ::: endChecks.toList)

  override def streamEndLog(logWhen: StreamEndLog): ServerStreamStartActionBuilder[Req, Res] =
    copy(logWhen = logWhen)
}

class ServerStreamStartAction[Req, Res](
  builder: ServerStreamStartActionBuilder[Req, Res],
  ctx: ScenarioContext,
  override val next: Action
) extends StreamStartAction[Req, Res](ctx, builder) {

  override protected def callClass: Class[_] = classOf[ServerStreamCall[_, _]]

  override def requestName: Expression[String] = builder.requestName
  override def sendRequest(session: Session): Validation[Unit] = forToMatch {
    for {
      name <- requestName(session)
      streamName <- builder.streamName(session)
      _ <- ensureNoStream(session, streamName, direction = "server")
      headers <- resolveHeaders(session)
      resolvedPayload <- builder.req(session)
      callOptions <- callOptions(session)
    } yield {
      val call = newCall(session, callOptions)
      next ! session.set(streamName, new ServerStreamCall(
        requestName = name,
        streamName = streamName,
        call,
        headers,
        resolvedPayload,
        ctx,
        builder.extractor,
        builder._sessionCombiner,
        session,
        builder.checks,
        endChecks,
        ignoreMessage = ignoreMessage,
        builder.logWhen
      ))
    }
  }
  override def statsEngine: StatsEngine = ctx.coreComponents.statsEngine
  override def clock: Clock = ctx.coreComponents.clock
  override val name: String = genName("serverStreamStart")
}
