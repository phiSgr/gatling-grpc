package com.github.phisgr.gatling.grpc.request

import com.github.phisgr.gatling.grpc.action._
import com.github.phisgr.gatling.grpc.stream.StreamCall._
import com.github.phisgr.gatling.grpc.stream.{BidiStreamCall, ClientStreamCall, ServerStreamCall, StreamCall}
import io.gatling.core.Predef.Session
import io.gatling.core.session.Expression
import io.grpc.MethodDescriptor

import scala.reflect.ClassTag

case class Grpc private[gatling](requestName: Expression[String]) {
  def rpc[Req, Res](method: MethodDescriptor[Req, Res]): Unary[Req, Res] =
    new Unary(requestName, method)

  def serverStream(streamName: String): ServerStream =
    ServerStream(requestName, streamName)

  def bidiStream(streamName: String): BidiStream =
    BidiStream(requestName, streamName)

  def clientStream(streamName: String): ClientStream =
    ClientStream(requestName, streamName)
}

class Unary[Req, Res] private[gatling](requestName: Expression[String], method: MethodDescriptor[Req, Res]) {
  assert(method.getType == MethodDescriptor.MethodType.UNARY)

  def payload(req: Expression[Req]): GrpcCallActionBuilder[Req, Res] =
    GrpcCallActionBuilder(requestName, method, req)
}

sealed abstract class ListeningStream[Status >: Completed, C <: StreamCall[_, _, Status] : ClassTag] private[gatling] {
  val requestName: Expression[String]
  val streamName: String
  def direction: String

  def cancelStream = new StreamCancelBuilder(requestName, streamName, direction)
  def reconciliate(waitFor: WaitType = NoWait) =
    new StreamReconciliateBuilder(requestName, streamName, direction, waitFor)

  // Keep the nice looking no-parenthesis call syntax
  def reconciliate: StreamReconciliateBuilder = reconciliate()

  def status: Expression[Status] = { session: Session =>
    StreamMessageAction.fetchCall[C](streamName, session, direction).map(_.state)
  }
}

case class ServerStream private[gatling](
  requestName: Expression[String],
  streamName: String
) extends ListeningStream[ServerStreamState, ServerStreamCall[_, _]] {
  override def direction: String = "server"

  def start[Req, Res](method: MethodDescriptor[Req, Res])(req: Expression[Req]): ServerStreamStartActionBuilder[Req, Res] = {
    assert(method.getType == MethodDescriptor.MethodType.SERVER_STREAMING)
    ServerStreamStartActionBuilder(requestName, streamName, method, req)
  }
}

case class BidiStream private[gatling](
  requestName: Expression[String],
  streamName: String
) extends ListeningStream[BidiStreamState, BidiStreamCall[_, _]] {
  override def direction: String = "bidi"

  def connect[Req: ClassTag, Res](method: MethodDescriptor[Req, Res]): BidiStreamStartActionBuilder[Req, Res] = {
    assert(method.getType == MethodDescriptor.MethodType.BIDI_STREAMING)
    BidiStreamStartActionBuilder(requestName, streamName, method)
  }

  def send[Req](req: Expression[Req]) = new StreamSendBuilder(requestName, streamName, req, direction = direction)
  def complete(waitFor: WaitType = NoWait) = new StreamCompleteBuilder(requestName, streamName, waitFor)

  // Keep the nice looking no-parenthesis call syntax
  def complete: StreamCompleteBuilder = complete()
}

case class ClientStream private[gatling](
  requestName: Expression[String],
  streamName: String
) {
  private def clientStreamDirection: String = "client"

  def connect[Req: ClassTag, Res](method: MethodDescriptor[Req, Res]): ClientStreamStartActionBuilder[Req, Res] = {
    assert(method.getType == MethodDescriptor.MethodType.CLIENT_STREAMING)
    ClientStreamStartActionBuilder(requestName, streamName, method)
  }

  def send[Req](req: Expression[Req]) = new StreamSendBuilder(requestName, streamName, req, direction = clientStreamDirection)
  def cancelStream = new StreamCancelBuilder(requestName, streamName, direction = clientStreamDirection)
  /**
   * If server completes before client complete,
   * the measured time will be negative.
   * If this behaviour is not good enough for you,
   * please open an issue with your use case,
   * so that we can better design the API.
   */
  def completeAndWait = new ClientStreamCompletionBuilder(requestName, streamName)

  def status: Expression[ClientStreamState] = { session: Session =>
    StreamMessageAction.fetchCall[ClientStreamCall[_, _]](streamName, session, clientStreamDirection).map(_.state)
  }
}
