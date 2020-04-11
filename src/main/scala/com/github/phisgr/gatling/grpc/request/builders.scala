package com.github.phisgr.gatling.grpc.request

import com.github.phisgr.gatling.grpc.action.GrpcCallActionBuilder
import io.gatling.core.session.Expression
import io.grpc.MethodDescriptor

case class Grpc private[gatling](requestName: Expression[String]) {
  def rpc[Req, Res](method: MethodDescriptor[Req, Res]): Unary[Req, Res] =
    new Unary(requestName, method)

  /**
   * Can handle a stream of responses
   */
  def rpcServerStreaming[Req, Res](method: MethodDescriptor[Req, Res]): ServerStreaming[Req, Res] =
    new ServerStreaming(requestName, method)
}

class Unary[Req, Res] private[gatling](requestName: Expression[String], method: MethodDescriptor[Req, Res]) {
  assert(method.getType == MethodDescriptor.MethodType.UNARY)
  def payload(req: Expression[Req]) = GrpcCallActionBuilder(
    requestName,
    method,
    req
  )
}

/**
 * Duplicated only to have a different name.
 *
 * How do we handle 0 response?
 */
class ServerStreaming[Req, Res] private[gatling](requestName: Expression[String], method: MethodDescriptor[Req, Res]) {
  assert(method.getType == MethodDescriptor.MethodType.SERVER_STREAMING)
  def payload(req: Expression[Req]) = GrpcCallActionBuilder(
    requestName,
    method,
    req
  )
}
