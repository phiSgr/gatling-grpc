package com.github.phisgr.gatling.grpc.stream

import com.github.phisgr.gatling.generic.SessionCombiner
import com.github.phisgr.gatling.grpc.check.GrpcResponse.GrpcStreamEnd
import com.github.phisgr.gatling.grpc.check.{GrpcResponse, StreamCheck}
import com.github.phisgr.gatling.grpc.stream.StreamCall._
import com.github.phisgr.gatling.grpc.util.{GrpcStringBuilder, statusCodeOption}
import com.typesafe.scalalogging.StrictLogging
import io.gatling.commons.stats.{KO, OK}
import io.gatling.commons.util.StringHelper.Eol
import io.gatling.commons.util.Throwables.PimpedException
import io.gatling.commons.validation.{Failure, Validation}
import io.gatling.core.action.Action
import io.gatling.core.check.Check
import io.gatling.core.session.Session
import io.gatling.core.stats.StatsEngine
import io.gatling.jdk.util.StringBuilderPool
import io.grpc.{ClientCall, Metadata, Status}

import scala.util.control.{NoStackTrace, NonFatal}

abstract class StreamCall[Req, Res, State >: Completed](
  requestName: String,
  streamName: String,
  initState: State,
  protected var streamSession: Session,
  val call: ClientCall[Req, Any],
  eventExtractor: EventExtractor[Res],
  combine: SessionCombiner,
  checks: List[StreamCheck[Res]],
  endChecks: List[StreamCheck[GrpcStreamEnd]],
  statsEngine: StatsEngine,
  logWhen: StreamEndLog
) extends StrictLogging with Cancellable {
  def state: State = _state

  protected var _state: State = initState
  protected var callStartTime: Long = _

  logger.debug(s"Opening stream '$streamName': Scenario '${streamSession.scenario}', UserId #${streamSession.userId}")

  private[gatling] def onRes(res: Any, receiveTime: Long): Unit = {
    // res is Unit if
    // 1. checks are empty &&
    // 2. eventExtractor does nothing &&
    // 3. trace logging is off &&
    // 4. user does not force parsing
    val response = res.asInstanceOf[Res]

    call.request(1)

    val (newSession, checkError) = Check.check(response, streamSession, checks, preparedCache = null)
    streamSession = if (checkError.isEmpty) newSession else newSession.markAsFailed

    val status = if (checkError.isEmpty) OK else KO
    val errorMessage = checkError.map(_.message)

    try {
      eventExtractor.writeEvents(
        session = streamSession,
        streamStartTime = callStartTime,
        requestName = requestName,
        message = response,
        receiveTime = receiveTime,
        statsEngine = statsEngine,
        logger = logger,
        status = status,
        errorMessage = errorMessage
      )
    } catch {
      case NonFatal(e) =>
        val message = if (eventExtractor.isInstanceOf[TimestampExtractor[_]]) {
          "Timestamp extraction crashed"
        } else {
          "Event extraction crashed"
        }

        logger.warn(message, e)
        statsEngine.logResponse(
          streamSession.scenario,
          streamSession.groups,
          requestName = requestName,
          startTimestamp = receiveTime,
          endTimestamp = receiveTime,
          status = KO,
          responseCode = None,
          message = Some(s"$message ${e.detailedMessage}")
        )
        streamSession = streamSession.markAsFailed
        return
    }

    def dump = {
      StringBuilderPool.DEFAULT
        .get()
        .append(Eol)
        .appendWithEol(">>>>>>>>>>>>>>>>>>>>>>>>>>")
        .appendWithEol("Stream Message Check:")
        .appendWithEol(s"$requestName - $streamName: $status ${errorMessage.getOrElse("")}")
        .appendWithEol("=========================")
        .appendSession(streamSession)
        .appendWithEol("=========================")
        .appendWithEol("gRPC stream message:")
        .appendMessage(response)
        .append("<<<<<<<<<<<<<<<<<<<<<<<<<")
        .toString
    }

    if (status == KO) {
      logger.debug(s"Stream response for '$streamName' failed for user ${streamSession.userId}: ${errorMessage.getOrElse("")}")
      if (!logger.underlying.isTraceEnabled) {
        logger.debug(dump)
      }
    }
    logger.trace(dump)

    if (this.waitType eq NextMessage) {
      val session = this.mainSession
      val next = this.mainCont

      this.mainSession = null
      this.mainCont = null
      this.waitType = NoWait

      combineAndNext(mainSession = session, next = next, close = false)
    }
  }

  def onServerCompleted(grpcStatus: Status, trailers: Metadata, completeTimeMillis: Long): Unit = {
    _state = Completed(grpcStatus, trailers)
    val (newSession, checkError) = Check.check(
      new GrpcResponse(null, grpcStatus, trailers),
      streamSession,
      endChecks,
      preparedCache = null
    )
    streamSession = newSession

    val status = if (checkError.isEmpty) OK else KO
    val errorMessage = checkError.map(_.message)

    def dump = {
      StringBuilderPool.DEFAULT
        .get()
        .append(Eol)
        .appendWithEol(">>>>>>>>>>>>>>>>>>>>>>>>>>")
        .appendWithEol("Stream Close:")
        .appendWithEol(s"$requestName - $streamName: $status ${errorMessage.getOrElse("")}")
        .appendWithEol("=========================")
        .appendSession(streamSession)
        .appendWithEol("=========================")
        .appendWithEol("gRPC stream completion:")
        .appendStatus(grpcStatus)
        .appendTrailers(trailers)
        .append("<<<<<<<<<<<<<<<<<<<<<<<<<")
        .toString
    }

    if (logWhen.eq(StreamCall.AlwaysLog) || (status == KO && logWhen.eq(ErrorOnly))) {
      statsEngine.logResponse(
        streamSession.scenario,
        streamSession.groups,
        requestName = requestName,
        startTimestamp = callStartTime,
        endTimestamp = completeTimeMillis,
        status = status,
        responseCode = statusCodeOption(grpcStatus.getCode.value()),
        message = errorMessage
      )
    }

    if (status == KO) {
      logger.debug(s"Stream '$streamName' failed for user ${streamSession.userId}: ${errorMessage.getOrElse("")}")
      if (!logger.underlying.isTraceEnabled) {
        logger.debug(dump)
      }
    }
    logger.trace(dump)

    if (this.mainSession ne null) {
      combineAndNext(mainSession = this.mainSession, next = this.mainCont, close = true)
    }
  }

  private[this] var mainSession: Session = _
  private[this] var mainCont: Action = _
  private[this] var waitType: WaitType = NoWait

  def combineState(mainSession: Session, next: Action, waitType: WaitType): Unit = {
    val completed = _state.isInstanceOf[Completed]
    if (!completed && (waitType ne NoWait)) {
      this.mainSession = mainSession
      this.mainCont = next
      this.waitType = waitType
    } else {
      combineAndNext(mainSession, next, close = completed)
    }
  }

  def cancel(mainSession: Session, next: Action): Unit = {
    combineAndNext(mainSession, next, close = true)
    call.cancel(null, Cancelled)
  }

  private def combineAndNext(mainSession: Session, next: Action, close: Boolean): Unit = {
    val combined = combine.combineSafely(main = mainSession, branched = streamSession, logger = logger)

    val newSession = if (!close) combined else combined.remove(streamName)
    next ! newSession
  }
}

object StreamCall {
  object Cancelled extends NoStackTrace

  sealed trait BidiStreamState
  sealed trait ServerStreamState
  sealed trait ClientStreamState

  case object BothOpen extends BidiStreamState with ClientStreamState

  // technically the ClientStreamState can be Receiving,
  // but with the current API, the virtual user "blocks" after client completes
  // and unblocks when the server responds
  // so this state is not accessible
  case object Receiving extends ServerStreamState with BidiStreamState

  case class Completed(status: Status, header: Metadata) extends ServerStreamState with BidiStreamState with ClientStreamState

  def ensureNoStream(session: Session, streamName: String, direction: String): Validation[Unit] = {
    if (session.contains(streamName)) {
      Failure(s"Unable to create a new $direction stream with name $streamName: already exists")
    } else {
      Validation.unit
    }
  }

  sealed trait WaitType
  case object NoWait extends WaitType
  case object NextMessage extends WaitType
  case object StreamEnd extends WaitType

  sealed trait StreamEndLog
  case object Never extends StreamEndLog
  case object ErrorOnly extends StreamEndLog
  case object AlwaysLog extends StreamEndLog
}
