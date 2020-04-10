package com.github.phisgr.gatling.grpc.action

import com.github.phisgr.gatling.grpc.ClientCalls
import com.github.phisgr.gatling.grpc.check.{GrpcResponse, StatusExtract}
import com.github.phisgr.gatling.grpc.protocol.GrpcProtocol
import com.github.phisgr.gatling.grpc.util.GrpcStringBuilder
import io.gatling.commons.stats.{KO, OK}
import io.gatling.commons.util.Clock
import io.gatling.commons.util.StringHelper.Eol
import io.gatling.commons.validation.{SuccessWrapper, Validation}
import io.gatling.core.action.{Action, RequestAction}
import io.gatling.core.check.Check
import io.gatling.core.session.{Expression, Session}
import io.gatling.core.stats.StatsEngine
import io.gatling.core.structure.ScenarioContext
import io.gatling.core.util.NameGen
import io.gatling.netty.util.StringBuilderPool
import io.grpc._

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

  private val resolvedChecks = if (builder.checks.exists(_.checksStatus)) builder.checks else {
    StatusExtract.DefaultCheck :: builder.checks
  }

  private val dispatcher = ctx.coreComponents.actorSystem.dispatcher

  private def run(
    channel: Channel,
    payload: Req,
    session: Session,
    resolvedRequestName: String,
    callOptions: CallOptions,
    headers: Metadata
  ): Unit = {
    val call = channel.newCall(builder.method, callOptions)
    ClientCalls.unaryCall(
      call, headers, payload,
      new ContinuingListener(session, resolvedRequestName, clock.nowMillis, headers, payload),
      builder.method.getType == MethodDescriptor.MethodType.SERVER_STREAMING
    )
  }

  private val headerPairs = builder.reversedHeaders.reverse.toArray
  private def resolveHeaders(session: Session): Validation[Metadata] = {
    val md = new Metadata()
    val size = headerPairs.length
    var i = 0
    while (i < size) {
      val failure = headerPairs(i).mutateMetadata(session, md)
      if (failure ne null) return failure
      i += 1
    }
    md.success
  }

  override def sendRequest(requestName: String, session: Session): Validation[Unit] = {
    for {
      headers <- resolveHeaders(session)
      resolvedPayload <- builder.payload(session)
      callOptions <- builder.callOptions(session)
    } yield {
      val channel = component.getChannel(session)
      if (ctx.throttled) {
        ctx.coreComponents.throttler.throttle(session.scenario, () =>
          run(channel, resolvedPayload, session, resolvedRequestName = requestName, callOptions, headers)
        )
      } else {
        run(channel, resolvedPayload, session, resolvedRequestName = requestName, callOptions, headers)
      }
    }
  }

  /**
   * After the call ends, [[onClose]] will be called,
   * then the execution will continue at [[run]] in the Akka dispatcher
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
  ) extends ClientCall.Listener[Res] with Runnable {
    private var body: Res = _
    private var grpcStatus: Status = _
    private var trailers: Metadata = _
    private var endTimestamp = 0L

    override def onHeaders(headers: Metadata): Unit = {}

    override def onMessage(message: Res): Unit = {
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
      // run() in Akka threads
      dispatcher.execute(this)
    }

    override def run(): Unit = {
      val (checkSaveUpdated, checkError) = Check.check(
        GrpcResponse(body, grpcStatus, trailers),
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
          withStatus,
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
        StringBuilderPool.DEFAULT
          .get()
          .append(Eol)
          .appendWithEol(">>>>>>>>>>>>>>>>>>>>>>>>>>")
          .appendWithEol("Request:")
          .appendWithEol(s"$fullRequestName: $status ${errorMessage.getOrElse("")}")
          .appendWithEol("=========================")
          .appendWithEol("Session:")
          .appendWithEol(session)
          .appendWithEol("=========================")
          .appendWithEol("gRPC request:")
          .appendRequest(payload, headers)
          .appendWithEol("=========================")
          .appendWithEol("gRPC response:")
          .appendResponse(body, grpcStatus, trailers)
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
