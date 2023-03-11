package com.github.phisgr.gatling.grpc.stream

import com.github.phisgr.gatling.generic.util.EventLoopHelper
import com.github.phisgr.gatling.grpc.check.{GrpcCheck, GrpcResponse}
import com.github.phisgr.gatling.grpc.protocol.Statuses.{MultipleResponses, NoResponses}
import com.github.phisgr.gatling.grpc.stream.StreamCall.{BothOpen, Cancelled, ClientStreamState, Completed}
import com.github.phisgr.gatling.grpc.util.{GrpcStringBuilder, delayedParsing, statusCodeOption, toProtoString, wrongTypeMessage}
import com.typesafe.scalalogging.StrictLogging
import io.gatling.commons.stats.{KO, OK}
import io.gatling.commons.util.Clock
import io.gatling.commons.util.StringHelper.Eol
import io.gatling.commons.validation.Validation
import io.gatling.core.action.Action
import io.gatling.core.check.Check
import io.gatling.core.session.Session
import io.gatling.core.structure.ScenarioContext
import io.gatling.jdk.util.StringBuilderPool
import io.grpc.MethodDescriptor.Marshaller
import io.grpc.{ClientCall, Metadata, Status}
import io.netty.channel.EventLoop

import scala.reflect.ClassTag
import scala.util.control.NonFatal

class ClientStreamCall[Req, Res](
  requestName: String,
  streamName: String,
  call: ClientCall[Req, Any],
  responseMarshaller: Marshaller[Res],
  headers: Metadata,
  ctx: ScenarioContext,
  checks: List[GrpcCheck[Any]],
  reqClass: Class[Req],
  eventLoop: EventLoop,
  scenario: String,
  userId: Long,
  clock: Clock
) extends ClientStreamer[Req] with Cancellable with StrictLogging {
  def state: ClientStreamState = if (callCompleted) {
    Completed(grpcStatus, trailers)
  } else {
    BothOpen
  }

  private implicit def reqTag: ClassTag[Req] = ClassTag(reqClass)

  try {
    val listener = new Listener
    call.start(listener, headers)
    call.request(2)
  } catch {
    case NonFatal(e) =>
      onCallCompleted(null, Status.ABORTED.withCause(e), new Metadata(), clock.nowMillis)
  }


  class Listener extends ClientCall.Listener[Any] {
    private[this] var body: Any = _
    override def onHeaders(headers: Metadata): Unit = {}

    override def onMessage(message: Any): Unit = {
      if (null != body) {
        throw MultipleResponses
      }
      this.body = message
    }

    override def onClose(status: Status, trailers: Metadata): Unit = {
      val receiveTime = clock.nowMillis
      val grpcStatus = if (status.isOk && null == body) NoResponses else status

      eventLoop.checkAndExecute { () =>
        onCallCompleted(body, grpcStatus, trailers, receiveTime)
      }
    }
  }

  private[this] var res: Any = _

  private[this] var startTimestamp = 0L
  private[this] var endTimestamp = 0L
  private[this] var grpcStatus: Status = _
  private[this] var trailers: Metadata = _

  private def callCompleted = grpcStatus ne null

  private[this] var session: Session = _
  private[this] var next: Action = _

  def onReq(req: Req): Validation[Unit] = {
    if (!reqClass.isInstance(req)) {
      wrongTypeMessage[Req](req)
    } else {
      if (callCompleted) {
        logger.debug(s"Client issued message but stream $streamName already completed")
      } else {
        logger.debug(s"Sending message ${toProtoString(req)} with stream '$streamName': Scenario '$scenario', UserId #$userId")
        call.sendMessage(req)
      }
      Validation.unit
    }
  }

  def onCallCompleted(res: Any, grpcStatus: Status, trailers: Metadata, endTimestamp: Long): Unit = {
    this.res = res
    this.grpcStatus = grpcStatus
    this.trailers = trailers
    this.endTimestamp = endTimestamp

    if (session ne null) {
      finishCall()
    }
  }

  def completeAndWait(session: Session, next: Action): Unit = {
    call.halfClose()
    startTimestamp = clock.nowMillis
    this.session = session
    this.next = next
    if (callCompleted) {
      finishCall()
    }
  }

  def finishCall(): Unit = {
    val (checkSaveUpdated, checkError) = Check.check(
      new GrpcResponse(res, grpcStatus, trailers),
      session,
      checks,
      preparedCache = null
    )

    val status = if (checkError.isEmpty) OK else KO
    val errorMessage = checkError.map(_.message)

    val withStatus = if (status == KO) checkSaveUpdated.markAsFailed else checkSaveUpdated
    ctx.coreComponents.statsEngine.logResponse(
      withStatus.scenario,
      withStatus.groups,
      requestName,
      startTimestamp = startTimestamp,
      endTimestamp = endTimestamp,
      status = status,
      responseCode = statusCodeOption(grpcStatus.getCode.value()),
      message = errorMessage
    )
    val newSession = withStatus.logGroupRequestTimings(startTimestamp = startTimestamp, endTimestamp = endTimestamp)


    def dump = {
      val bodyParsed = delayedParsing(res, responseMarshaller)
      StringBuilderPool.DEFAULT
        .get()
        .append(Eol)
        .appendWithEol(">>>>>>>>>>>>>>>>>>>>>>>>>>")
        .appendWithEol("Client Stream:")
        .appendWithEol(s"$requestName - $streamName: $status ${errorMessage.getOrElse("")}")
        .appendWithEol("=========================")
        .appendSession(session)
        .appendWithEol("=========================")
        .appendWithEol("gRPC response:")
        .appendResponse(bodyParsed, grpcStatus, trailers)
        .append("<<<<<<<<<<<<<<<<<<<<<<<<<")
        .toString
    }

    if (status == KO) {
      logger.debug(s"Request '$requestName' failed for user ${session.userId}: ${errorMessage.getOrElse("")}")
      if (!logger.underlying.isTraceEnabled) {
        logger.debug(dump)
      }
    }
    logger.trace(dump)

    next ! newSession.remove(streamName)
  }

  override def cancel(mainSession: Session, next: Action): Unit = {
    next ! mainSession.remove(streamName)
    call.cancel(null, Cancelled)
  }
}
