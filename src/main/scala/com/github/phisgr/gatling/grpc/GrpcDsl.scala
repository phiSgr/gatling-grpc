package com.github.phisgr.gatling.grpc

import com.github.phisgr.gatling.grpc.action.{GrpcCallActionBuilder, GrpcSetUpActionBuilder}
import com.github.phisgr.gatling.grpc.protocol.GrpcProtocol
import io.gatling.commons.NotNothing
import io.gatling.commons.validation.{Failure, Success}
import io.gatling.core.session.Expression
import io.gatling.core.session.el.ElMessages
import io.grpc.stub.{AbstractStub, ClientCalls}
import io.grpc.{CallOptions, Channel, ManagedChannelBuilder, MethodDescriptor}
import scalapb.grpc.Grpc.guavaFuture2ScalaFuture

import scala.concurrent.Future
import scala.reflect.ClassTag


trait GrpcDsl {
  def grpc(channelBuilder: ManagedChannelBuilder[_]) = GrpcProtocol(channelBuilder)

  def grpc(requestName: String) = new Call(requestName)

  class Call private[gatling](requestName: String) {
    def service[Service <: AbstractStub[Service]](stub: Channel => Service) = new CallWithService(requestName, stub)

    def rpc[Req, Res](method: MethodDescriptor[Req, Res]) = {
      assert(method.getType == MethodDescriptor.MethodType.UNARY)
      new CallWithMethod(requestName, method)
    }
  }

  class CallWithMethod[Req, Res] private[gatling](requestName: String, method: MethodDescriptor[Req, Res]) {
    val f = { channel: Channel =>
      request: Req =>
        guavaFuture2ScalaFuture(ClientCalls.futureUnaryCall(channel.newCall(method, CallOptions.DEFAULT), request))
    }

    def payload(req: Expression[Req]) = GrpcCallActionBuilder(requestName, f, req, headers = Nil)
  }

  class CallWithService[Service <: AbstractStub[Service]] private[gatling](requestName: String, stub: Channel => Service) {
    def rpc[Req, Res](fun: Service => Req => Future[Res])(request: Expression[Req]) =
      GrpcCallActionBuilder(requestName, stub andThen fun, request, headers = Nil)
  }

  val setUpGrpc = GrpcSetUpActionBuilder

  def $[T: ClassTag : NotNothing](name: String): Expression[T] = s => s.attributes.get(name) match {
    case Some(t: T) => Success(t)
    case None => ElMessages.undefinedSessionAttribute(name)
    case Some(t) => Failure(s"Value $t is of type ${t.getClass}, expected ${implicitly[ClassTag[T]].runtimeClass}")
  }
}
