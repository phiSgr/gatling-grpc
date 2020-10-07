package com.github.phisgr.gatling.grpc.action

import com.github.phisgr.gatling.grpc.check.GrpcResponse.GrpcStreamEnd
import com.github.phisgr.gatling.grpc.check.{GrpcCheck, StatusExtract, StreamCheck}
import com.github.phisgr.gatling.grpc.request.{Call, CallDefinition}
import com.github.phisgr.gatling.grpc.stream.TimestampExtractor
import io.gatling.core.structure.ScenarioContext
import org.slf4j.LoggerFactory

trait StreamStartBuilder[Self, Check[_], Req, Res] extends CallDefinition[Self, Check, Req, Res] {
  private[gatling] val endChecks: List[StreamCheck[GrpcStreamEnd]]
  private[gatling] val _timestampExtractor: TimestampExtractor[Res]
}

abstract class StreamStartAction[Req, Res](
  ctx: ScenarioContext,
  builder: StreamStartBuilder[_, StreamCheck, Req, Res],
) extends Call[Req, Res](ctx, builder.callAttributes, builder.method) {
  // We call logger.debug when status is KO
  // that implies builder.checks.nonEmpty and we eagerly parse
  // So either the received message is eagerly parsed or discarded
  override protected def mayNeedDelayedParsing: Boolean = false

  override protected def needParsed: Boolean =
    builder.checks.nonEmpty ||
      builder._timestampExtractor.ne(TimestampExtractor.Ignore) ||
      // If trace is enabled, we always log the response. No need to delay parsing
      LoggerFactory.getLogger(callClass.getName).isTraceEnabled

  protected def callClass: Class[_]

  protected val ignoreMessage: Boolean = !needParsed

  protected val endChecks: List[StreamCheck[GrpcStreamEnd]] =
    if (builder.endChecks.exists(_.scope == GrpcCheck.Status)) builder.endChecks
    else StatusExtract.DefaultStreamCheck :: builder.endChecks
}
