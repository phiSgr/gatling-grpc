package com.github.phisgr.gatling.grpc.stream

import com.github.phisgr.gatling.generic.SessionCombiner
import com.github.phisgr.gatling.grpc.check.GrpcResponse.GrpcStreamEnd
import com.github.phisgr.gatling.grpc.check.StreamCheck
import com.github.phisgr.gatling.grpc.stream.StreamCall._
import com.github.phisgr.gatling.grpc.util.{toProtoString, wrongTypeMessage}
import io.gatling.commons.validation.{Failure, Validation}
import io.gatling.core.action.Action
import io.gatling.core.session.Session
import io.gatling.core.structure.ScenarioContext
import io.grpc.{ClientCall, Metadata, Status}

import scala.util.control.NonFatal

class BidiStreamCall[Req, Res](
  requestName: String,
  streamName: String,
  call: ClientCall[Req, Any],
  headers: Metadata,
  ctx: ScenarioContext,
  eventExtractor: EventExtractor[Res],
  combine: SessionCombiner,
  startingSession: Session,
  checks: List[StreamCheck[Res]],
  endChecks: List[StreamCheck[GrpcStreamEnd]],
  reqClass: Class[Req],
  ignoreMessage: Boolean,
  logWhen: StreamEndLog
) extends StreamCall[Req, Res, BidiStreamState](
  requestName = requestName,
  streamName = streamName,
  initState = BothOpen,
  startingSession,
  call,
  eventExtractor,
  combine,
  checks,
  endChecks,
  ctx.coreComponents.statsEngine,
  logWhen
) with ClientStreamer[Req] {

  {
    val clock = ctx.coreComponents.clock
    try {
      val listener = new StreamListener(this, clock, streamSession.eventLoop, ignoreMessage)
      callStartTime = clock.nowMillis
      call.start(listener, headers)
      call.request(1)
    } catch {
      case NonFatal(e) =>
        logger.warn("Call failed", e)
        onServerCompleted(Status.ABORTED.withCause(e), new Metadata(), clock.nowMillis)
    }
  }

  override def onReq(req: Req): Validation[Unit] = {
    if (!reqClass.isInstance(req)) {
      wrongTypeMessage(req, reqClass)
    } else {
      _state match {
        case BothOpen =>
          logger.debug(
            s"Sending message ${toProtoString(req)} with stream '$streamName': Scenario '${streamSession.scenario}', UserId #${streamSession.userId}"
          )
          call.sendMessage(req)
        case Receiving =>
          logger.error(s"Client issued message after client completion in stream $streamName")
          return alreadyHalfClosed
        case _: Completed =>
          logger.debug(s"Client issued message but stream $streamName already completed")
      }
      Validation.unit
    }
  }

  def onClientCompleted(session: Session, next: Action, waitType: WaitType): Validation[Unit] = {
    _state match {
      case Receiving =>
        logger.error(s"Client completed bidi stream $streamName twice")
        return alreadyHalfClosed
      case BothOpen =>
        _state = Receiving
        logger.debug(s"Completing bidi stream '$streamName': Scenario '${session.scenario}', UserId #${session.userId}")
        call.halfClose()
      case _: Completed =>
        logger.debug(s"Client issued complete order but stream $streamName already completed")
    }
    combineState(mainSession = session, next, waitType, sync = false)
    Validation.unit
  }

  private def alreadyHalfClosed = Failure(s"Stream $streamName already completed by client")
}
