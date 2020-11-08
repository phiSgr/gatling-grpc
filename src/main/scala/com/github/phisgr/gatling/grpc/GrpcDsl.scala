package com.github.phisgr.gatling.grpc

import com.github.phisgr.gatling.grpc.protocol.{DynamicGrpcProtocol, StaticGrpcProtocol}
import com.github.phisgr.gatling.grpc.request.Grpc
import com.github.phisgr.gatling.grpc.util.wrongTypeMessage
import io.gatling.commons.NotNothing
import io.gatling.commons.validation.Success
import io.gatling.core.session.Expression
import io.gatling.core.session.el.ElMessages
import io.grpc.{ManagedChannelBuilder => MCB}

import scala.reflect.ClassTag

trait GrpcDsl {
  // Better type inference for IntelliJ
  type ManagedChannelBuilder = MCB[T] forSome {type T <: MCB[T]}

  def managedChannelBuilder(target: String): ManagedChannelBuilder =
    MCB.forTarget(target).asInstanceOf[ManagedChannelBuilder].directExecutor()

  def managedChannelBuilder(name: String, port: Int): ManagedChannelBuilder =
    MCB.forAddress(name, port).asInstanceOf[ManagedChannelBuilder].directExecutor()

  /**
   * Creates a GrpcProtocol that looks up a channel created at runtime.
   * The virtual user has to go through
   * `exec(`[[com.github.phisgr.gatling.grpc.protocol.DynamicGrpcProtocol.setChannel]]`)`
   * before running a gRPC action with `.target(dynamicChannel)`
   */
  def dynamicChannel(channelAttributeName: String): DynamicGrpcProtocol = DynamicGrpcProtocol(channelAttributeName)

  def grpc(channelBuilder: MCB[_]): StaticGrpcProtocol = StaticGrpcProtocol(channelBuilder)

  def grpc(requestName: Expression[String]): Grpc = Grpc(requestName)

  def $[T: ClassTag : NotNothing](name: String): Expression[T] = s => s.attributes.get(name) match {
    case Some(t: T) => Success(t)
    case None => ElMessages.undefinedSessionAttribute(name)
    case Some(value) => wrongTypeMessage[T](value)
  }
}
