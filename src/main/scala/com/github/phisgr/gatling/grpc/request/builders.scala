package com.github.phisgr.gatling.grpc.request

import com.github.phisgr.gatling.grpc.action.GrpcCallActionBuilder
import io.gatling.core.session.Expression
import io.grpc.MethodDescriptor

case class Grpc private[gatling](requestName: Expression[String]) {
  def rpc[Req, Res](method: MethodDescriptor[Req, Res]): Unary[Req, Res] =
    new Unary(requestName, method)
}

class Unary[Req, Res] private[gatling](requestName: Expression[String], method: MethodDescriptor[Req, Res]) {
  assert(method.getType == MethodDescriptor.MethodType.UNARY || method.getType == MethodDescriptor.MethodType.SERVER_STREAMING)
  def payload(req: Expression[Req]) = GrpcCallActionBuilder(
    requestName,
    method,
    req
  )
}
