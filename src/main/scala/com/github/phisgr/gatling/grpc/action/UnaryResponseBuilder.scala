package com.github.phisgr.gatling.grpc.action

import com.github.phisgr.gatling.grpc.check.{GrpcCheck, GrpcResponse}
import com.github.phisgr.gatling.grpc.request.CallDefinition

/**
 * See also [[StreamStartBuilder]]
 */
trait UnaryResponseBuilder[Self, Req, Res] extends CallDefinition[Self, Req, Res] {
  override type Wrap[T] = GrpcResponse[T]
  override type Check[T] = GrpcCheck[T]
}
