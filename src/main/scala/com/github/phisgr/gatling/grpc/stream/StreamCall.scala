package com.github.phisgr.gatling.grpc.stream

import com.github.phisgr.gatling.generic.SessionCombiner
import com.github.phisgr.gatling.grpc.check.GrpcResponse.GrpcStreamEnd
import com.github.phisgr.gatling.grpc.check.{GrpcResponse, StreamCheck}
import com.github.phisgr.gatling.grpc.stream.StreamCall._
import com.github.phisgr.gatling.grpc.util.{GrpcStringBuilder, toProtoString}
import com.typesafe.scalalogging.StrictLogging
import io.gatling.commons.stats.{KO, OK}
import io.gatling.commons.util.StringHelper.Eol
import io.gatling.commons.util.Throwables.PimpedException
import io.gatling.commons.validation.{Failure, Success, Validation}
import io.gatling.core.action.Action
import io.gatling.core.check.Check
import io.gatling.core.session.Session
import io.gatling.core.stats.StatsEngine
import io.gatling.netty.util.StringBuilderPool
import io.grpc.{ClientCall, Metadata, Status}

import scala.util.control.{NoStackTrace, NonFatal}

abstract class StreamCall[Req, Res, State >: ServerStreamState](
  requestName: String,
  streamName: String,
  initState: State,
  protected var streamSession: Session,
  val call: ClientCall[Req, Any],
  timestampExtractor: TimestampExtractor[Res],
  combine: SessionCombiner,
  checks: List[StreamCheck[Res]],
  endChecks: List[StreamCheck[GrpcStreamEnd]],
  statsEngine: StatsEngine
) extends StrictLogging with Cancellable {

  protected var state: State = initState
  protected var callStartTime: Long = _

  logger.info(s"Opening stream '$streamName': Scenario '${streamSession.scenario}', UserId #${streamSession.userId}")

  private[gatling] def onRes(res: Any, receiveTime: Long): Unit = {
    // res is Unit if
    // 1. checks are empty &&
    // 2. timestampExtractor does nothing &&
    // 3. trace logging is off &&
    // 4. user does not force parsing
    val response = res.asInstanceOf[Res]

    call.request(1)

    val extractedTime = try {
      timestampExtractor.extractTimestamp(streamSession, response, callStartTime)
    } catch {
      case NonFatal(e) =>
        val message = s"Timestamp extraction crashed ${e.detailedMessage}"

        logger.warn(message)
        statsEngine.logResponse(
          streamSession.scenario,
          streamSession.groups,
          requestName = requestName,
          startTimestamp = receiveTime,
          endTimestamp = receiveTime,
          status = KO,
          responseCode = None,
          message = Some(message)
        )
        streamSession = streamSession.markAsFailed
        return
    }

    val (newSession, checkError) = Check.check(response, streamSession, checks, preparedCache = null)
    streamSession = if (checkError.isEmpty) newSession else newSession.markAsFailed

    if (extractedTime == TimestampExtractor.IgnoreMessage) {
      logger.trace(s"Ignored message\n${toProtoString(response)}")
      return
    }

    val status = if (checkError.isEmpty) OK else KO
    val errorMessage = checkError.map(_.message)

    statsEngine.logResponse(
      streamSession.scenario,
      streamSession.groups,
      requestName = requestName,
      startTimestamp = extractedTime,
      endTimestamp = receiveTime,
      status = status,
      responseCode = None,
      message = errorMessage
    )

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
      logger.info(s"Stream response for '$streamName' failed for user ${streamSession.userId}: ${errorMessage.getOrElse("")}")
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
    state = Completed(grpcStatus, trailers)
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

    statsEngine.logResponse(
      streamSession.scenario,
      streamSession.groups,
      requestName = requestName,
      startTimestamp = callStartTime,
      endTimestamp = completeTimeMillis,
      status = status,
      responseCode = Some(grpcStatus.getCode.toString),
      message = errorMessage
    )

    if (status == KO) {
      logger.info(s"Stream '$streamName' failed for user ${streamSession.userId}: ${errorMessage.getOrElse("")}")
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
    val completed = state.isInstanceOf[Completed]
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

private[gatling] object StreamCall {
  object Cancelled extends NoStackTrace

  sealed trait BidiStreamState
  sealed trait ServerStreamState extends BidiStreamState

  case object BothOpen extends BidiStreamState

  case object Receiving extends ServerStreamState

  case class Completed(status: Status, header: Metadata) extends ServerStreamState

  def ensureNoStream(session: Session, streamName: String, direction: String): Validation[Unit] = {
    if (session.contains(streamName)) {
      Failure(s"Unable to create a new $direction stream with name $streamName: already exists")
    } else {
      Success(())
    }
  }

  sealed trait WaitType
  case object NoWait extends WaitType
  case object NextMessage extends WaitType
  case object StreamEnd extends WaitType
}
