package com.github.phisgr.gatling.grpc.action

import java.io.{ByteArrayInputStream, InputStream}

import com.github.phisgr.gatling.forToMatch
import com.github.phisgr.gatling.grpc.ClientCalls
import com.github.phisgr.gatling.grpc.check.{GrpcCheck, GrpcResponse, StatusExtract}
import com.github.phisgr.gatling.grpc.protocol.GrpcProtocol
import com.github.phisgr.gatling.grpc.request.Call
import com.github.phisgr.gatling.grpc.util.GrpcStringBuilder
import io.gatling.commons.stats.{KO, OK}
import io.gatling.commons.util.Clock
import io.gatling.commons.util.StringHelper.Eol
import io.gatling.commons.validation.Validation
import io.gatling.core.action.{Action, RequestAction}
import io.gatling.core.check.Check
import io.gatling.core.session.{Expression, Session}
import io.gatling.core.stats.StatsEngine
import io.gatling.core.structure.ScenarioContext
import io.gatling.core.util.NameGen
import io.gatling.netty.util.StringBuilderPool
import io.grpc.MethodDescriptor.Marshaller
import io.grpc._

import scala.reflect.io.Streamable

class GrpcCallAction[Req, Res](
  builder: GrpcCallActionBuilder[Req, Res],
  ctx: ScenarioContext,
  override val next: Action
) extends Call(ctx, builder.callAttributes) with RequestAction with NameGen {

  private[this] val throttler = ctx.coreComponents.throttler match {
    // not calling .filter to not make ctx a field
    case Some(throttler) if ctx.throttled => throttler
    case _ => null
  }

  override val clock: Clock = ctx.coreComponents.clock
  override val statsEngine: StatsEngine = ctx.coreComponents.statsEngine

  override val name: String = genName("grpcCall")
  override def requestName: Expression[String] = builder.requestName

  private[this] val resolvedChecks = (if (builder.checks.exists(_.checksStatus)) builder.checks else {
    StatusExtract.DefaultCheck :: builder.checks
  }).asInstanceOf[List[GrpcCheck[Any]]]

  private[this] val notParsed = component.noParsing && builder.checks.forall(_.scope != GrpcCheck.Value)

  private[this] val method = (if (notParsed) {
    val mayLog = logger.underlying.isDebugEnabled || logger.underlying.isTraceEnabled
    val responseMarshaller = if (mayLog) {
      GrpcCallAction.ByteArrayMarshaller
    } else {
      GrpcProtocol.EmptyMarshaller
    }
    builder.method.toBuilder(
      builder.method.getRequestMarshaller,
      responseMarshaller
    ).build()
  } else {
    builder.method
  }).asInstanceOf[MethodDescriptor[Req, Any]]

  private def run(
    channel: Channel,
    payload: Req,
    session: Session,
    resolvedRequestName: String,
    callOptions: CallOptions,
    headers: Metadata
  ): Unit = {
    val call = channel.newCall(method, callOptions)
    ClientCalls.asyncUnaryRequestCall(
      call, headers, payload,
      new ContinuingListener(session, resolvedRequestName, clock.nowMillis, headers, payload),
      streamingResponse = false
    )
  }

  override def sendRequest(requestName: String, session: Session): Validation[Unit] = forToMatch {
    for {
      headers <- resolveHeaders(session)
      resolvedPayload <- builder.payload(session)
      callOptions <- callOptions(session)
    } yield {
      val channel = component.getChannel(session)
      if (throttler ne null) {
        throttler.throttle(session.scenario, () =>
          run(channel, resolvedPayload, session, resolvedRequestName = requestName, callOptions, headers)
        )
      } else {
        run(channel, resolvedPayload, session, resolvedRequestName = requestName, callOptions, headers)
      }
    }
  }

  /**
   * After the call ends, [[onClose]] will be called,
   * then the execution will continue at [[run]] in the session event loop
   * and finally forward to the next action.
   *
   * See [[io.grpc.stub.ClientCalls.UnaryStreamToFuture]]
   *
   * The headers object is read for logging after ClientCall.start. This is supposedly not safe.
   * We are accessing it after the call closed, so let's hope nothing bad happens.
   *
   * @param session         the virtual user
   * @param fullRequestName the resolved request name
   * @param startTimestamp  start of the call
   * @param headers         for logging
   * @param payload         for logging
   */
  class ContinuingListener(
    session: Session,
    fullRequestName: String,
    startTimestamp: Long,
    headers: Metadata,
    payload: Req
  ) extends ClientCall.Listener[Any] with Runnable {
    // null if failed;
    // Res if checks value; Array[Byte] if we may need logging; Unit if neither
    private[this] var body: Any = _

    private[this] var grpcStatus: Status = _
    private[this] var trailers: Metadata = _
    private[this] var endTimestamp = 0L

    override def onHeaders(headers: Metadata): Unit = {}

    override def onMessage(message: Any): Unit = {
      if (null != body) {
        throw Status.INTERNAL
          .withDescription("More than one value received for unary call")
          .asRuntimeException
      }
      this.body = message
    }

    override def onClose(status: Status, trailers: Metadata): Unit = {
      endTimestamp = clock.nowMillis
      this.trailers = trailers
      grpcStatus = if (status.isOk && null == body) {
        Status.INTERNAL.withDescription("No value received for unary call")
      } else {
        status
      }

      // run() in session event loop
      val eventLoop = session.eventLoop
      if (!eventLoop.isShutdown) {
        eventLoop.execute(this)
      }
    }

    override def run(): Unit = {
      val (checkSaveUpdated, checkError) = Check.check(
        new GrpcResponse(body, grpcStatus, trailers),
        session,
        resolvedChecks,
        // Not using preparedCache because the prepare step is cheap
        preparedCache = null
      )

      val status = if (checkError.isEmpty) OK else KO

      val errorMessage = checkError.map(_.message)

      val newSession = if (builder.isSilent) checkSaveUpdated else {
        val withStatus = if (status == KO) checkSaveUpdated.markAsFailed else checkSaveUpdated
        statsEngine.logResponse(
          withStatus.scenario,
          withStatus.groups,
          fullRequestName,
          startTimestamp = startTimestamp,
          endTimestamp = endTimestamp,
          status = status,
          responseCode = Some(grpcStatus.getCode.toString),
          message = errorMessage
        )
        withStatus.logGroupRequestTimings(startTimestamp = startTimestamp, endTimestamp = endTimestamp)
      }

      def dump = {
        val bodyParsed = if (null == body) null
        else if (notParsed) {
          // does not support runtime change of logger level
          val rawBytes = body.asInstanceOf[Array[Byte]]
          builder.method.parseResponse(new ByteArrayInputStream(rawBytes))
        } else {
          body.asInstanceOf[Res]
        }
        StringBuilderPool.DEFAULT
          .get()
          .append(Eol)
          .appendWithEol(">>>>>>>>>>>>>>>>>>>>>>>>>>")
          .appendWithEol("Request:")
          .appendWithEol(s"$fullRequestName: $status ${errorMessage.getOrElse("")}")
          .appendWithEol("=========================")
          .appendSession(session)
          .appendWithEol("=========================")
          .appendWithEol("gRPC request:")
          .appendRequest(payload, headers)
          .appendWithEol("=========================")
          .appendWithEol("gRPC response:")
          .appendResponse(bodyParsed, grpcStatus, trailers)
          .append("<<<<<<<<<<<<<<<<<<<<<<<<<")
          .toString
      }

      if (status == KO) {
        logger.info(s"Request '$fullRequestName' failed for user ${session.userId}: ${errorMessage.getOrElse("")}")
        if (!logger.underlying.isTraceEnabled) {
          logger.debug(dump)
        }
      }
      logger.trace(dump)

      next ! newSession
    }
  }

}

object GrpcCallAction {

  object ByteArrayMarshaller extends Marshaller[Array[Byte]] {
    override def stream(value: Array[Byte]): InputStream = new ByteArrayInputStream(value)
    override def parse(stream: InputStream): Array[Byte] = {
      val size = stream match {
        case knownLength: KnownLength => knownLength.available()
        case _ => -1
      }
      new Streamable.Bytes {
        def inputStream(): InputStream = stream
        override def length: Long = size.toLong
      }.toByteArray()
    }
  }

}
