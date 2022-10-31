package com.github.phisgr.gatling.grpc.action

import com.github.phisgr.gatling.generic.SessionCombiner
import com.github.phisgr.gatling.grpc.check.GrpcResponse.GrpcStreamEnd
import com.github.phisgr.gatling.grpc.check.{StatusExtract, StreamCheck}
import com.github.phisgr.gatling.grpc.request.{Call, CallDefinition}
import com.github.phisgr.gatling.grpc.stream.StreamCall.StreamEndLog
import com.github.phisgr.gatling.grpc.stream.{EventExtractor, TimestampExtractor}
import io.gatling.commons.validation.Validation
import io.gatling.core.session.{Expression, Session}
import io.gatling.core.structure.ScenarioContext
import org.slf4j.LoggerFactory

/**
 * See also [[UnaryResponseBuilder]]
 */
trait StreamStartBuilder[Self, Req, Res] extends CallDefinition[Self, Req, Res] {
  override type Wrap[T] = T
  override type Check[T] = StreamCheck[T]

  private[gatling] val endChecks: List[StreamCheck[GrpcStreamEnd]]
  private[gatling] val extractor: EventExtractor[Res]

  def endCheck(value: StreamCheck[GrpcStreamEnd]*): Self

  def endCheckIf(condition: Expression[Boolean])(endChecks: StreamCheck[GrpcStreamEnd]*): Self =
    endCheck(endChecks.map(_.checkIf(condition)): _*)

  def endCheckIf(condition: (GrpcStreamEnd, Session) => Validation[Boolean])(endChecks: StreamCheck[GrpcStreamEnd]*): Self =
    endCheck(endChecks.map(_.checkIf(condition)): _*)

  def streamEndLog(logWhen: StreamEndLog): Self

  def eventExtractor(extractor: EventExtractor[Res]): Self
  def timestampExtractor(extractor: TimestampExtractor[Res]): Self =
    eventExtractor(extractor)
  def sessionCombiner(sessionCombiner: SessionCombiner): Self
}

abstract class StreamStartAction[Req, Res](
  ctx: ScenarioContext,
  builder: StreamStartBuilder[_, Req, Res],
) extends Call[Req, Res](ctx, builder.callAttributes, builder.method) {
  // We call logger.debug when status is KO
  // that implies builder.checks.nonEmpty and we eagerly parse
  // So either the received message is eagerly parsed or discarded
  override protected def mayNeedDelayedParsing: Boolean = false

  override protected def needParsed: Boolean =
    builder.checks.nonEmpty ||
      builder.extractor.ne(TimestampExtractor.Ignore) ||
      // If trace is enabled, we always log the response. No need to delay parsing
      LoggerFactory.getLogger(callClass.getName).isTraceEnabled

  protected def callClass: Class[_]

  protected val ignoreMessage: Boolean = !needParsed

  protected val endChecks: List[StreamCheck[GrpcStreamEnd]] =
    if (builder.endChecks.exists(_.scope.checksStatus)) builder.endChecks
    else StatusExtract.DefaultStreamCheck :: builder.endChecks

}
