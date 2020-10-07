package com.github.phisgr.gatling.grpc.request

import com.github.phisgr.gatling.grpc.HeaderPair
import com.github.phisgr.gatling.grpc.protocol.GrpcProtocol
import io.gatling.core.session.{Expression, ExpressionSuccessWrapper}
import io.grpc.CallOptions

case class CallAttributes(
  callOptions: Expression[CallOptions] = CallOptions.DEFAULT.expressionSuccess,
  reversedHeaders: List[HeaderPair[_]] = Nil,
  protocolOverride: Option[GrpcProtocol] = None
)
