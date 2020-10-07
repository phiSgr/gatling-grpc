package com.github.phisgr.gatling.grpc.stream

import com.github.phisgr.gatling.grpc.ClientCalls
import com.github.phisgr.gatling.grpc.check.GrpcResponse.GrpcStreamEnd
import com.github.phisgr.gatling.grpc.check.StreamCheck
import com.github.phisgr.gatling.grpc.stream.StreamCall.{Receiving, ServerStreamState}
import io.gatling.core.session.Session
import io.gatling.core.structure.ScenarioContext
import io.grpc.{ClientCall, Metadata, Status}

import scala.util.control.NonFatal

class ServerStreamCall[Req, Res](
  requestName: String,
  streamName: String,
  call: ClientCall[Req, Any],
  headers: Metadata,
  payload: Req,
  ctx: ScenarioContext,
  timestampExtractor: TimestampExtractor[Res],
  combine: SessionCombiner,
  startingSession: Session,
  checks: List[StreamCheck[Res]],
  endChecks: List[StreamCheck[GrpcStreamEnd]],
  ignoreMessage: Boolean
) extends StreamCall[Req, Res, ServerStreamState](
  requestName = requestName,
  streamName = streamName,
  initState = Receiving,
  startingSession,
  call,
  timestampExtractor,
  combine,
  checks,
  endChecks,
  ctx.coreComponents.statsEngine
) {

  {
    val clock = ctx.coreComponents.clock
    try {
      val listener = new StreamListener(this, clock, streamSession.eventLoop, ignoreMessage)
      callStartTime = clock.nowMillis
      ClientCalls.asyncUnaryRequestCall(
        call, headers, payload, listener,
        streamingResponse = true
      )
    } catch {
      case NonFatal(e) =>
        onServerCompleted(Status.ABORTED.withCause(e), new Metadata(), clock.nowMillis)
    }
  }
}
