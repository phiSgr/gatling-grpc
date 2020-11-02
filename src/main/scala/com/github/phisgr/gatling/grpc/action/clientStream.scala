package com.github.phisgr.gatling.grpc.action

import com.github.phisgr.gatling.forToMatch
import com.github.phisgr.gatling.grpc.check.{GrpcCheck, StatusExtract}
import com.github.phisgr.gatling.grpc.request.{Call, CallAttributes, CallDefinition}
import com.github.phisgr.gatling.grpc.stream.ClientStreamCall
import com.github.phisgr.gatling.grpc.stream.StreamCall.ensureNoStream
import io.gatling.commons.util.Clock
import io.gatling.commons.validation.Validation
import io.gatling.core.action.Action
import io.gatling.core.action.builder.ActionBuilder
import io.gatling.core.session.{Expression, Session}
import io.gatling.core.stats.StatsEngine
import io.gatling.core.structure.ScenarioContext
import io.grpc.MethodDescriptor
import io.grpc.MethodDescriptor.Marshaller
import org.slf4j.LoggerFactory

import scala.reflect.ClassTag

case class ClientStreamStartActionBuilder[Req: ClassTag, Res](
  private[gatling] val requestName: Expression[String],
  private[gatling] val streamName: String,
  private[gatling] override val method: MethodDescriptor[Req, Res],
  private[gatling] override val callAttributes: CallAttributes = CallAttributes(),
  private[gatling] override val checks: List[GrpcCheck[Res]] = Nil,
) extends ActionBuilder
  with CallDefinition[ClientStreamStartActionBuilder[Req, Res], GrpcCheck, Req, Res] {

  override def build(ctx: ScenarioContext, next: Action): Action =
    new ClientStreamStartAction(this, ctx, next)

  override private[gatling] def withCallAttributes(callAttributes: CallAttributes): ClientStreamStartActionBuilder[Req, Res] =
    copy(callAttributes = callAttributes)

  override def check(checks: GrpcCheck[Res]*): ClientStreamStartActionBuilder[Req, Res] =
    copy(checks = this.checks ::: checks.toList)
}

class ClientStreamStartAction[Req: ClassTag, Res](
  builder: ClientStreamStartActionBuilder[Req, Res],
  ctx: ScenarioContext,
  override val next: Action
) extends Call[Req, Res](ctx, builder.callAttributes, builder.method) {
  override protected def needParsed: Boolean =
    builder.checks.exists(_.scope == GrpcCheck.Value) ||
      // If trace is enabled, we always log the response. No need to delay parsing
      LoggerFactory.getLogger(classOf[ClientStreamCall[_, _]]).isTraceEnabled
  override protected def mayNeedDelayedParsing: Boolean =
    LoggerFactory.getLogger(classOf[ClientStreamCall[_, _]]).isDebugEnabled


  private[this] val resolvedChecks = (if (builder.checks.exists(_.checksStatus)) builder.checks else {
    StatusExtract.DefaultCheck :: builder.checks
  }).asInstanceOf[List[GrpcCheck[Any]]]

  private[this] val reqClass = implicitly[ClassTag[Req]].runtimeClass.asInstanceOf[Class[Req]]

  // For delayed parsing
  private[this] val responseMarshaller: Marshaller[Res] =
    if (lazyParseMethod eq builder.method) null else builder.method.getResponseMarshaller

  override def requestName: Expression[String] = builder.requestName
  override def sendRequest(requestName: String, session: Session): Validation[Unit] = forToMatch {
    val streamName = builder.streamName

    for {
      _ <- ensureNoStream(session, streamName, direction = "client")
      headers <- resolveHeaders(session)
      callOptions <- callOptions(session)
    } yield {
      next ! session.set(streamName, new ClientStreamCall(
        requestName,
        streamName,
        newCall(session, callOptions),
        responseMarshaller,
        headers,
        ctx,
        resolvedChecks,
        reqClass,
        session.eventLoop,
        session.scenario,
        session.userId,
        clock
      ))
    }
  }

  override def statsEngine: StatsEngine = ctx.coreComponents.statsEngine
  override val clock: Clock = ctx.coreComponents.clock
  override val name: String = genName("serverStreamStart")
}

class ClientStreamCompletionBuilder(requestName: Expression[String], streamName: String) extends ActionBuilder {
  override def build(ctx: ScenarioContext, next: Action): Action =
    new StreamMessageAction(requestName, ctx, next, baseName = "StreamEnd", direction = "client") {
      override def sendRequest(requestName: String, session: Session): Validation[Unit] = forToMatch {
        for {
          call <- fetchCall[ClientStreamCall[_, _]](streamName, session)
        } yield {
          call.completeAndWait(session, next)
        }
      }
    }
}
