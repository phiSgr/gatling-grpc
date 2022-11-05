package com.github.phisgr.gatling.grpc.request

import com.github.phisgr.gatling.grpc.action.UnaryResponseBuilder
import com.github.phisgr.gatling.grpc.check.{GrpcCheck, StatusExtract}
import com.github.phisgr.gatling.grpc.protocol.ByteArrayMarshaller
import io.gatling.core.structure.ScenarioContext
import io.grpc.MethodDescriptor
import io.grpc.MethodDescriptor.Marshaller
import org.slf4j.LoggerFactory

abstract class UnaryResponse[Req, Res](
  protected val resolvedChecks: List[GrpcCheck[Any]],
  callAttributes: CallAttributes,
  method: MethodDescriptor[Req, Res],
  ctx: ScenarioContext
) extends Call[Req, Res](ctx, callAttributes, method) {

  def this(
    builder: UnaryResponseBuilder[_, Req, Res],
    ctx: ScenarioContext
  ) = this(
    (if (builder.checks.exists(_.scope.checksStatus)) builder.checks else {
      StatusExtract.DefaultCheck :: builder.checks
    }).asInstanceOf[List[GrpcCheck[Any]]],
    builder.callAttributes,
    builder.method,
    ctx
  )

  protected def loggingClass: Class[_]

  override protected def needParsed: Boolean =
    resolvedChecks.exists(_.scope.checksValue) ||
      // If trace is enabled, we always log the response. No need to delay parsing
      LoggerFactory.getLogger(loggingClass.getName).isTraceEnabled
  override protected def mayNeedDelayedParsing: Boolean =
    LoggerFactory.getLogger(loggingClass.getName).isDebugEnabled

  // For delayed parsing
  protected val responseMarshaller: Marshaller[Res] =
    if (lazyParseMethod.getResponseMarshaller eq ByteArrayMarshaller) method.getResponseMarshaller else null

}
