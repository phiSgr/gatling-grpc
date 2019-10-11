package com.github.phisgr.gatling.grpc.action

import com.github.phisgr.gatling.grpc.HeaderPair
import com.github.phisgr.gatling.grpc.check.StatusExtract
import com.github.phisgr.gatling.grpc.protocol.GrpcProtocol
import io.gatling.commons.stats.{KO, OK}
import io.gatling.commons.util.Clock
import io.gatling.commons.validation.{Failure, Success, SuccessWrapper, Validation}
import io.gatling.core.action.{Action, RequestAction}
import io.gatling.core.check.Check
import io.gatling.core.session.{Expression, Session}
import io.gatling.core.stats.StatsEngine
import io.gatling.core.structure.ScenarioContext
import io.gatling.core.util.NameGen
import io.grpc._
import io.grpc.stub.MetadataUtils

import scala.concurrent.ExecutionContext

case class GrpcCallAction[Req, Res](
  builder: GrpcCallActionBuilder[Req, Res],
  ctx: ScenarioContext,
  next: Action
) extends RequestAction with NameGen {
  override def clock: Clock = ctx.coreComponents.clock

  override def statsEngine: StatsEngine = ctx.coreComponents.statsEngine

  override val name = genName("grpcCall")

  override def requestName: Expression[String] = builder.requestName

  private val component: GrpcProtocol.GrpcComponent = {
    val protocolKey = builder.protocolOverride.fold(GrpcProtocol.GrpcProtocolKey)(_.overridingKey)
    ctx.protocolComponentsRegistry.components(protocolKey)
  }

  private def run(channel: Channel, payload: Req, session: Session, resolvedRequestName: String): Unit = {
    implicit val ec: ExecutionContext = ctx.coreComponents.actorSystem.dispatcher

    val start = clock.nowMillis
    builder.method(channel)(payload).onComplete { t =>
      val endTimestamp = clock.nowMillis

      val resolvedChecks = if (builder.checks.exists(_.checksStatus)) builder.checks else {
        StatusExtract.DefaultCheck :: builder.checks
      }
      // Not using preparedCache because the prepare step is cheap
      val (checkSaveUpdated, checkError) = Check.check(t, session, resolvedChecks, preparedCache = null)

      val (status, newSession) = if (checkError.isEmpty) {
        (OK, checkSaveUpdated)
      } else {
        (KO, checkSaveUpdated.markAsFailed)
      }

      statsEngine.logResponse(
        newSession,
        resolvedRequestName,
        startTimestamp = start,
        endTimestamp = endTimestamp,
        status = status,
        responseCode = StatusExtract.extractStatus(t) match {
          case Success(value) => Some(value.getCode.toString)
          case Failure(_) => None
        },
        message = checkError.map(_.message)
      )
      next ! newSession
    }
  }

  override def sendRequest(requestName: String, session: Session): Validation[Unit] = {
    type ResolvedH = (Metadata.Key[T], T) forSome {type T}
    for {
      resolvedHeaders <- builder.headers.foldLeft[Validation[List[ResolvedH]]](Nil.success) { case (lV, HeaderPair(key, value)) =>
        for {
          l <- lV
          resolvedValue <- value(session)
        } yield (key, resolvedValue) :: l
      }
      resolvedPayload <- builder.payload(session)
    } yield {
      val rawChannel = component.getChannel(session)
      val channel = if (resolvedHeaders.isEmpty) rawChannel else {
        val headers = new Metadata()
        resolvedHeaders.foreach { case (key, value) => headers.put(key, value) }
        ClientInterceptors.intercept(rawChannel, MetadataUtils.newAttachHeadersInterceptor(headers))
      }
      if (ctx.throttled) {
        ctx.coreComponents.throttler.throttle(session.scenario, () =>
          run(channel, resolvedPayload, session, resolvedRequestName = requestName)
        )
      } else {
        run(channel, resolvedPayload, session, resolvedRequestName = requestName)
      }
    }
  }
}
