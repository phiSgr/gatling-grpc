package com.github.phisgr.gatling.grpc.stream

import com.github.phisgr.gatling.grpc.check.GrpcResponse.GrpcStreamEnd
import com.github.phisgr.gatling.grpc.check.StreamCheck
import com.github.phisgr.gatling.grpc.stream.StreamCall._
import com.github.phisgr.gatling.grpc.util.wrongTypeMessage
import io.gatling.commons.validation.{Success, Validation}
import io.gatling.core.action.Action
import io.gatling.core.session.Session
import io.gatling.core.structure.ScenarioContext
import io.grpc._

import scala.reflect.ClassTag
import scala.util.control.NonFatal

class BidiStreamCall[Req, Res](
  requestName: String,
  streamName: String,
  call: ClientCall[Req, Res],
  headers: Metadata,
  ctx: ScenarioContext,
  timestampExtractor: TimestampExtractor[Res],
  combine: SessionCombiner,
  startingSession: Session,
  checks: List[StreamCheck[Res]],
  endChecks: List[StreamCheck[GrpcStreamEnd]],
  reqClass: Class[Req]
) extends StreamCall[Req, Res, BidiStreamState](
  requestName = requestName,
  streamName = streamName,
  initState = BothOpen,
  startingSession,
  call,
  timestampExtractor,
  combine,
  checks,
  endChecks,
  ctx.coreComponents.statsEngine
) {
  private implicit def reqTag: ClassTag[Req] = ClassTag(reqClass)

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

  def onReq(req: Req): Validation[Unit] = {
    if (!reqClass.isInstance(req)) {
      wrongTypeMessage[Req](req)
    } else {
      state match {
        case BothOpen =>
          logger.info(s"Sending message $req with stream '$streamName': Scenario '${streamSession.scenario}', UserId #${streamSession.userId}")
          call.sendMessage(req)
        case Receiving =>
          logger.error(s"Got message after client completion in stream $streamName.")
        case _: Completed =>
          logger.info(s"Got message after stream $streamName completion, ignoring.")
      }
      Success(())
    }
  }

  def onClientCompleted(session: Session, next: Action): Unit = {
    state match {
      case BothOpen =>
        state = Receiving
      case Receiving =>
        logger.error(s"Completing stream $streamName twice.")
        return
      case _: Completed =>
        logger.debug(s"Stream $streamName already completed. Ignoring.")
        return
    }
    call.halfClose()
    combineState(mainSession = session, next)
  }
}
