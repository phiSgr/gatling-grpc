package com.github.phisgr.gatling.grpc.action

import com.github.phisgr.gatling.forToMatch
import com.github.phisgr.gatling.grpc.check.GrpcResponse.GrpcStreamEnd
import com.github.phisgr.gatling.grpc.check.StreamCheck
import com.github.phisgr.gatling.grpc.request.CallAttributes
import com.github.phisgr.gatling.grpc.stream.StreamCall.ensureNoStream
import com.github.phisgr.gatling.grpc.stream.{BidiStreamCall, SessionCombiner, TimestampExtractor}
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
) extends ActionBuilder
  with StreamStartBuilder[BidiStreamStartActionBuilder[Req, Res], StreamCheck, Req, Res] {

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
  def endCheck(endChecks: StreamCheck[GrpcStreamEnd]*): BidiStreamStartActionBuilder[Req, Res] =
    copy(endChecks = this.endChecks ::: endChecks.toList)

}

class BidiStreamStartAction[Req: ClassTag, Res](
  builder: BidiStreamStartActionBuilder[Req, Res],
  ctx: ScenarioContext,
  override val next: Action
) extends StreamStartAction[Req, Res](ctx, builder) {
  override protected def callClass: Class[_] = classOf[BidiStreamCall[_, _]]

  override def requestName: Expression[String] = builder.requestName
  override def sendRequest(requestName: String, session: Session): Validation[Unit] = forToMatch {
    val streamName = builder.streamName

    for {
      _ <- ensureNoStream(session, streamName, isBidi = true)
      headers <- resolveHeaders(session)
      callOptions <- callOptions(session)
    } yield {
      next ! session.set(streamName, new BidiStreamCall(
        requestName = requestName,
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
        ignoreMessage = ignoreMessage
      ))
    }
  }

  private[this] val reqClass: Class[Req] = implicitly[ClassTag[Req]].runtimeClass.asInstanceOf[Class[Req]]

  override def statsEngine: StatsEngine = ctx.coreComponents.statsEngine
  override def clock: Clock = ctx.coreComponents.clock
  override val name: String = genName("serverStreamStart")
}

class StreamCompleteBuilder(requestName: Expression[String], streamName: String) extends ActionBuilder {
  override def build(ctx: ScenarioContext, next: Action): Action =
    new StreamMessageAction(requestName, ctx, next, baseName = "StreamComplete", isBidi = true) {
      override def sendRequest(requestName: String, session: Session): Validation[Unit] = forToMatch {
        for {
          call <- fetchCall[BidiStreamCall[_, _]](streamName, session)
          _ <- call.onClientCompleted(session, next)
        } yield ()
      }
    }
}

class StreamSendBuilder[Req](requestName: Expression[String], streamName: String, req: Expression[Req]) extends ActionBuilder {
  override def build(ctx: ScenarioContext, next: Action): Action =
    new StreamMessageAction(requestName, ctx, next, baseName = "StreamSend", isBidi = true) {
      override def sendRequest(requestName: String, session: Session): Validation[Unit] = forToMatch {
        for {
          call <- fetchCall[BidiStreamCall[Req, _]](streamName, session)
          payload <- req(session)
          _ <- call.onReq(payload)
        } yield {
          next ! session
        }
      }
    }
}
