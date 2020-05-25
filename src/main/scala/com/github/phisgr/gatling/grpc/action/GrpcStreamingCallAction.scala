package com.github.phisgr.gatling.grpc.action

import com.github.phisgr.gatling.grpc.ClientCalls
import com.github.phisgr.gatling.grpc.check.{GrpcResponse, StatusExtract}
import com.github.phisgr.gatling.grpc.protocol.{CallToStreamObserverAdapter, GrpcProtocol, GrpcResponseObserver}
import com.github.phisgr.gatling.grpc.util.GrpcStringBuilder
import io.gatling.commons.stats.{KO, OK}
import io.gatling.commons.util.Clock
import io.gatling.commons.util.StringHelper.Eol
import io.gatling.commons.validation.{SuccessWrapper, Validation, Success, Failure}
import io.gatling.core.action.{Action, RequestAction}
import io.gatling.core.check.Check
import io.gatling.core.session.{Expression, Session}
import io.gatling.core.stats.StatsEngine
import io.gatling.core.structure.ScenarioContext
import io.gatling.core.util.NameGen
import io.gatling.netty.util.StringBuilderPool
import io.grpc._
import io.grpc.stub.StreamObserver

case class GrpcStreamingCallAction[Req, Res](
                                          builder: GrpcStreamingCallActionBuilder[Req, Res],
                                          ctx: ScenarioContext,
                                          next: Action
) extends RequestAction with NameGen {
  override def clock: Clock = ctx.coreComponents.clock
  override def statsEngine: StatsEngine = ctx.coreComponents.statsEngine
  override val name = genName("grpcStreamingCall")
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
                   payload: Seq[Req],
                   session: Session,
                   resolvedRequestName: String,
                   callOptions: CallOptions,
                   headers: Metadata
                 ): Unit = {
    val call = channel.newCall(builder.method, callOptions)

    ClientCalls.asyncStreamingRequestCall(
      call, headers, builder.streamingResponse,
      new ContinuingListener(
        session,
        resolvedRequestName,
        clock.nowMillis,
        headers,
        payload,
        observer = new GrpcResponseObserver[Res],
        adapter = new CallToStreamObserverAdapter[Req, Res](call),
        builder.streamingResponse)
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

  private def resolve[T](pl: Seq[Validation[T]]): Validation[Seq[T]] =
    pl.foldLeft(Seq.empty[T].success) {
      case (s, Success(value)) => s.map(_ :+ value)
      case (_, failure: Failure) => failure
    }

  override def sendRequest(requestName: String, session: Session): Validation[Unit] = {
    for {
      headers <- resolveHeaders(session)
      resolvedPayload <- resolve(builder.payload.map(_ (session)))
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

  class ContinuingListener(
                            session: Session,
                            fullRequestName: String,
                            startTimestamp: Long,
                            headers: Metadata,
                            payload: Seq[Req],
                            observer: StreamObserver[Res],
                            adapter: CallToStreamObserverAdapter[Req, Res],
                            streamingResponse: Boolean
                          ) extends ClientCall.Listener[Res] with Runnable{
    private var body: Res = _
    private var grpcStatus: Status = _
    private var trailers: Metadata = _
    private var endTimestamp = 0L
    private var firstResponseReceived: Boolean = _

    override def onHeaders(headers: Metadata): Unit = {}

    override def onMessage(message: Res): Unit = {
      if (firstResponseReceived && !streamingResponse) {
        throw Status.INTERNAL
          .withDescription("More than one responses received for client-streaming call")
          .asRuntimeException
      }
      firstResponseReceived = true
      observer.onNext(message)
      if (streamingResponse && adapter.isAutoFlowControlEnabled){
        // Request delivery of the next inbound message.
        adapter.request(1)
      }
      this.body = message
    }

    override def onReady(): Unit = {
      while(adapter.isReady){
        payload.foreach{
          req => adapter.onNext(req)
        }
        adapter.onCompleted()
      }
    }

    override def onClose(status: Status, trailers: Metadata): Unit = {
      endTimestamp = clock.nowMillis
      this.trailers = trailers
      grpcStatus = if (status.isOk && null == body) {
        observer.onError(status.asRuntimeException(trailers))
        Status.INTERNAL
          .withDescription("No value received for server streaming call")
      } else {
        observer.onCompleted()
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

