package com.github.phisgr.gatling.grpc.request

import com.github.phisgr.gatling.grpc.action._
import io.gatling.core.session.Expression
import io.grpc.MethodDescriptor

import scala.reflect.ClassTag

case class Grpc private[gatling](requestName: Expression[String]) {
  def rpc[Req, Res](method: MethodDescriptor[Req, Res]): Unary[Req, Res] =
    new Unary(requestName, method)

  def serverStream[Req, Res](streamName: String): ServerStream =
    ServerStream(requestName, streamName)

  def bidiStream[Req, Res](streamName: String): BidiStream =
    BidiStream(requestName, streamName)
}

class Unary[Req, Res] private[gatling](requestName: Expression[String], method: MethodDescriptor[Req, Res]) {
  assert(method.getType == MethodDescriptor.MethodType.UNARY)

  def payload(req: Expression[Req]): GrpcCallActionBuilder[Req, Res] =
    GrpcCallActionBuilder(requestName, method, req)
}

sealed abstract class Stream private[gatling] {
  val requestName: Expression[String]
  val streamName: String
  val isBidi: Boolean

  def cancelStream = new StreamCancelBuilder(requestName, streamName, isBidi = isBidi)
  def reconciliate = new StreamReconciliateBuilder(requestName, streamName, isBidi = isBidi)
}

case class ServerStream private[gatling](
  requestName: Expression[String],
  streamName: String
) extends Stream {
  val isBidi = false

  def start[Req, Res](method: MethodDescriptor[Req, Res])(req: Expression[Req]): ServerStreamStartActionBuilder[Req, Res] = {
    assert(method.getType == MethodDescriptor.MethodType.SERVER_STREAMING)
    ServerStreamStartActionBuilder(requestName, streamName, method, req)
  }
}

case class BidiStream private[gatling](
  requestName: Expression[String],
  streamName: String
) extends Stream {
  val isBidi = true

  def connect[Req: ClassTag, Res](method: MethodDescriptor[Req, Res]): BidiStreamStartActionBuilder[Req, Res] = {
    assert(method.getType == MethodDescriptor.MethodType.BIDI_STREAMING)
    BidiStreamStartActionBuilder(requestName, streamName, method)
  }

  def send[Req](req: Expression[Req]) = new StreamSendBuilder(requestName, streamName, req)
  def complete = new StreamCompleteBuilder(requestName, streamName)
}
