package com.github.phisgr.gatling.grpc.action

import com.github.phisgr.gatling.forToMatch
import com.github.phisgr.gatling.grpc.check.GrpcCheck
import com.github.phisgr.gatling.grpc.request.{CallAttributes, UnaryResponse}
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

import scala.reflect.ClassTag

case class ClientStreamStartActionBuilder[Req: ClassTag, Res](
  private[gatling] val requestName: Expression[String],
  private[gatling] val streamName: Expression[String],
  private[gatling] override val method: MethodDescriptor[Req, Res],
  private[gatling] override val callAttributes: CallAttributes = CallAttributes(),
  private[gatling] override val checks: List[GrpcCheck[Res]] = Nil,
  isSilent: Boolean = false
) extends UnaryResponseBuilder[ClientStreamStartActionBuilder[Req, Res], Req, Res] {

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
) extends UnaryResponse[Req, Res](builder, ctx) {
  override def loggingClass: Class[_] = classOf[ClientStreamCall[_, _]]

  private[this] val reqClass = implicitly[ClassTag[Req]].runtimeClass.asInstanceOf[Class[Req]]

  override val requestName: Expression[String] = builder.requestName
  private[this] val streamName = builder.streamName

  override def sendRequest(session: Session): Validation[Unit] = forToMatch {
    for {
      name <- requestName(session)
      streamName <- streamName(session)
      _ <- ensureNoStream(session, streamName, direction = "client")
      headers <- resolveHeaders(session)
      callOptions <- callOptions(session)
    } yield {
      next ! session.set(streamName, new ClientStreamCall(
        requestName = name,
        streamName = streamName,
        call = newCall(session, callOptions),
        responseMarshaller = responseMarshaller,
        headers = headers,
        statsEngine = statsEngine,
        checks = resolvedChecks,
        reqClass = reqClass,
        eventLoop = session.eventLoop,
        scenario = session.scenario,
        userId = session.userId,
        clock = clock
      ))
    }
  }

  override val statsEngine: StatsEngine = ctx.coreComponents.statsEngine
  override val clock: Clock = ctx.coreComponents.clock
  override val name: String = genName("serverStreamStart")
}

class ClientStreamCompletionBuilder(requestName: Expression[String], streamName: Expression[String]) extends ActionBuilder {
  override def build(ctx: ScenarioContext, next: Action): Action =
    new StreamMessageAction(requestName, streamName, ctx, next, baseName = "StreamEnd", direction = "client") {
      override def sendRequest(session: Session): Validation[Unit] = forToMatch {
        for {
          call <- fetchCall(classOf[ClientStreamCall[_, _]], session)
        } yield {
          call.completeAndWait(session, this.next)
        }
      }
    }
}
