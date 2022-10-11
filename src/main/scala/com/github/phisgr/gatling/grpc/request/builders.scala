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

  def serverStream[Req, Res](method: MethodDescriptor[Req, Res], streamName: String): ServerStream[Req, Res] =
    ServerStream(requestName, method, streamName)

  def bidiStream[Req: ClassTag, Res](method: MethodDescriptor[Req, Res], streamName: String): BidiStream[Req, Res] =
    BidiStream(requestName, method, streamName)

  def clientStream[Req: ClassTag, Res](method: MethodDescriptor[Req, Res], streamName: String): ClientStream[Req, Res] =
    ClientStream(requestName, method, streamName)
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

case class ServerStream[Req, Res] private[gatling](
  requestName: Expression[String],
  method: MethodDescriptor[Req, Res],
  streamName: String
) extends ListeningStream[ServerStreamState, ServerStreamCall[_, _]] {
  require(method.getType == MethodDescriptor.MethodType.SERVER_STREAMING)

  override def direction: String = "server"

  def start(req: Expression[Req]): ServerStreamStartActionBuilder[Req, Res] = {
    ServerStreamStartActionBuilder(requestName, streamName, method, req)
  }
}

case class BidiStream[Req: ClassTag, Res] private[gatling](
  requestName: Expression[String],
  method: MethodDescriptor[Req, Res],
  streamName: String
) extends ListeningStream[BidiStreamState, BidiStreamCall[_, _]] {
  require(method.getType == MethodDescriptor.MethodType.BIDI_STREAMING)

  override def direction: String = "bidi"

  def connect: BidiStreamStartActionBuilder[Req, Res] = {
    BidiStreamStartActionBuilder(requestName, streamName, method)
  }

  def send(req: Expression[Req]) = new StreamSendBuilder(requestName, streamName, req, direction = direction)
  def complete(waitFor: WaitType = NoWait) = new StreamCompleteBuilder(requestName, streamName, waitFor)

  // Keep the nice looking no-parenthesis call syntax
  def complete: StreamCompleteBuilder = complete()
}

case class ClientStream[Req: ClassTag, Res] private[gatling](
  requestName: Expression[String],
  method: MethodDescriptor[Req, Res],
  streamName: String
) {
  require(method.getType == MethodDescriptor.MethodType.CLIENT_STREAMING)
  private def clientStreamDirection: String = "client"

  def connect: ClientStreamStartActionBuilder[Req, Res] = {
    ClientStreamStartActionBuilder(requestName, streamName, method)
  }

  def send(req: Expression[Req]) = new StreamSendBuilder(requestName, streamName, req, direction = clientStreamDirection)
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
