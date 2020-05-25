package com.github.phisgr.gatling.grpc.request

import com.github.phisgr.gatling.grpc.action._
import io.gatling.core.session._
import io.grpc.MethodDescriptor

case class Grpc private[gatling](requestName: Expression[String]) {
  def unaryCall[Req, Res](method: MethodDescriptor[Req, Res]): AsyncUnaryRequestCall[Req, Res] =
    new AsyncUnaryRequestCall(requestName, method, streamingResponse = false)

  def serverStreamingCall[Req, Res](method: MethodDescriptor[Req, Res]): AsyncUnaryRequestCall[Req, Res] =
    new AsyncUnaryRequestCall(requestName, method, streamingResponse = true)

  def ClientStreamingCall[Req, Res](method: MethodDescriptor[Req, Res]): AsyncStreamingRequestCall[Req, Res] =
    new AsyncStreamingRequestCall(requestName, method, streamingResponse = false)

  def BidiStreamingCall[Req, Res](method: MethodDescriptor[Req, Res]): AsyncStreamingRequestCall[Req, Res] =
    new AsyncStreamingRequestCall(requestName, method, streamingResponse = true)
}

class AsyncUnaryRequestCall[Req, Res] private[gatling](requestName: Expression[String], method: MethodDescriptor[Req, Res], streamingResponse: Boolean) {
  assert(method.getType == MethodDescriptor.MethodType.UNARY || method.getType == MethodDescriptor.MethodType.SERVER_STREAMING)
  def payload(req: Expression[Req]): GrpcUnaryCallActionBuilder[Req, Res] = GrpcUnaryCallActionBuilder(
    requestName,
    method,
    req,
    streamingResponse
  )
}

class AsyncStreamingRequestCall[Req, Res] private[gatling](requestName: Expression[String], method: MethodDescriptor[Req, Res], streamingResponse: Boolean) {
  assert(method.getType == MethodDescriptor.MethodType.CLIENT_STREAMING || method.getType == MethodDescriptor.MethodType.BIDI_STREAMING)
  def payload(reqs: Req*): GrpcStreamingCallActionBuilder[Req, Res] = payloadStreamE(reqs.map(_.expressionSuccess): _*)

  def payloadStreamE(reqs: Expression[Req]*): GrpcStreamingCallActionBuilder[Req, Res] = GrpcStreamingCallActionBuilder(
    requestName,
    method,
    reqs,
    streamingResponse
  )
}
