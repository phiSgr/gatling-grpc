package com.github.phisgr.gatling.grpc.action

import com.github.phisgr.gatling.forToMatch
import com.github.phisgr.gatling.generic.SessionCombiner
import com.github.phisgr.gatling.grpc.check.GrpcResponse.GrpcStreamEnd
import com.github.phisgr.gatling.grpc.check.StreamCheck
import com.github.phisgr.gatling.grpc.request.CallAttributes
import com.github.phisgr.gatling.grpc.stream.StreamCall.{AlwaysLog, StreamEndLog, WaitType, ensureNoStream}
import com.github.phisgr.gatling.grpc.stream.{BidiStreamCall, ClientStreamer, TimestampExtractor}
import io.gatling.commons.util.Clock
import io.gatling.commons.validation.Validation
import io.gatling.core.action.Action
import io.gatling.core.action.builder.ActionBuilder
import io.gatling.core.session.{Expression, Session}
import io.gatling.core.stats.StatsEngine
import io.gatling.core.structure.ScenarioContext
import io.grpc.MethodDescriptor

import scala.reflect.ClassTag

case class BidiStreamStartActionBuilder[Req: ClassTag, Res](
  private[gatling] val requestName: Expression[String],
  private[gatling] val streamName: String,
  private[gatling] override val method: MethodDescriptor[Req, Res],
  private[gatling] override val _timestampExtractor: TimestampExtractor[Res] = TimestampExtractor.Ignore,
  private[gatling] val _sessionCombiner: SessionCombiner = SessionCombiner.NoOp,
  private[gatling] override val callAttributes: CallAttributes = CallAttributes(),
  private[gatling] override val checks: List[StreamCheck[Res]] = Nil,
  private[gatling] override val endChecks: List[StreamCheck[GrpcStreamEnd]] = Nil,
  private[gatling] val logWhen: StreamEndLog = AlwaysLog
) extends StreamStartBuilder[BidiStreamStartActionBuilder[Req, Res], Req, Res] {

  override def build(ctx: ScenarioContext, next: Action): Action =
    new BidiStreamStartAction(this, ctx, next)

  def timestampExtractor(extractor: TimestampExtractor[Res]): BidiStreamStartActionBuilder[Req, Res] =
    copy(_timestampExtractor = extractor)
  def sessionCombiner(sessionCombiner: SessionCombiner): BidiStreamStartActionBuilder[Req, Res] =
    copy(_sessionCombiner = sessionCombiner)

  override private[gatling] def withCallAttributes(callAttributes: CallAttributes): BidiStreamStartActionBuilder[Req, Res] =
    copy(callAttributes = callAttributes)

  override def check(checks: StreamCheck[Res]*): BidiStreamStartActionBuilder[Req, Res] =
    copy(checks = this.checks ::: checks.toList)
  override def endCheck(endChecks: StreamCheck[GrpcStreamEnd]*): BidiStreamStartActionBuilder[Req, Res] =
    copy(endChecks = this.endChecks ::: endChecks.toList)

  override def streamEndLog(logWhen: StreamEndLog): BidiStreamStartActionBuilder[Req, Res] =
    copy(logWhen = logWhen)
}

class BidiStreamStartAction[Req: ClassTag, Res](
  builder: BidiStreamStartActionBuilder[Req, Res],
  ctx: ScenarioContext,
  override val next: Action
) extends StreamStartAction[Req, Res](ctx, builder) {
  override protected def callClass: Class[_] = classOf[BidiStreamCall[_, _]]

  private[this] val reqClass = implicitly[ClassTag[Req]].runtimeClass.asInstanceOf[Class[Req]]

  override def requestName: Expression[String] = builder.requestName
  override def sendRequest(session: Session): Validation[Unit] = forToMatch {
    val streamName = builder.streamName

    for {
      name <- requestName(session)
      _ <- ensureNoStream(session, streamName, direction = "bidi")
      headers <- resolveHeaders(session)
      callOptions <- callOptions(session)
    } yield {
      next ! session.set(streamName, new BidiStreamCall(
        requestName = name,
        streamName = streamName,
        newCall(session, callOptions),
        headers,
        ctx,
        builder._timestampExtractor,
        builder._sessionCombiner,
        session,
        builder.checks,
        endChecks,
        reqClass,
        ignoreMessage = ignoreMessage,
        builder.logWhen
      ))
    }
  }

  override def statsEngine: StatsEngine = ctx.coreComponents.statsEngine
  override def clock: Clock = ctx.coreComponents.clock
  override val name: String = genName("serverStreamStart")
}

class StreamCompleteBuilder(requestName: Expression[String], streamName: String, waitType: WaitType) extends ActionBuilder {
  override def build(ctx: ScenarioContext, next: Action): Action =
    new StreamMessageAction(requestName, ctx, next, baseName = "StreamComplete", direction = "bidi") {
      override def sendRequest(session: Session): Validation[Unit] = forToMatch {
        for {
          call <- fetchCall[BidiStreamCall[_, _]](streamName, session)
          _ <- call.onClientCompleted(session, next, waitType)
        } yield ()
      }
    }
}

class StreamSendBuilder[Req](
  requestName: Expression[String],
  streamName: String,
  req: Expression[Req],
  direction: String
) extends ActionBuilder {
  override def build(ctx: ScenarioContext, next: Action): Action =
    new StreamMessageAction(requestName, ctx, next, baseName = "StreamSend", direction = direction) {
      override def sendRequest(session: Session): Validation[Unit] = forToMatch {
        for {
          call <- fetchCall[ClientStreamer[Req]](streamName, session)
          payload <- req(session)
          _ <- call.onReq(payload)
        } yield {
          next ! session
        }
      }
    }
}
