package com.github.phisgr.gatling.grpc

import com.github.phisgr.gatling.grpc.protocol.{DynamicGrpcProtocol, StaticGrpcProtocol}
import com.github.phisgr.gatling.grpc.request.Grpc
import com.github.phisgr.gatling.grpc.stream.StreamCall
import com.github.phisgr.gatling.grpc.util.getFromSession
import io.gatling.commons.NotNothing
import io.gatling.core.session._
import io.grpc.{ManagedChannelBuilder => MCB}

import java.lang.invoke.MethodType
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
   *
   * @param channelAttributeName The key in the session for storing the channel.
   *                             Start it with `"gatling."` to stop it from being removed by [[Session.reset]].
   * @return a [[DynamicGrpcProtocol]]
   */
  def dynamicChannel(channelAttributeName: String): DynamicGrpcProtocol = DynamicGrpcProtocol(channelAttributeName)

  def grpc(channelBuilder: MCB[_]): StaticGrpcProtocol = StaticGrpcProtocol(channelBuilder)

  def grpc(requestName: Expression[String]): Grpc = Grpc(requestName)

  /**
   * `$("name")` is a simpler alternative to the
   * [[https://gatling.io/docs/gatling/reference/current/core/session/el/ EL string]] `"#{name}"`.
   * Unlike the EL string, this [[Expression]] does not do type casting (e.g. parsing a String to an Int).
   *
   * @param name name of the session attribute
   * @tparam T expected type of the session attribute. Usually inferred by the compiler
   * @return an Expression that retrieves the session attribute and checks its type
   */
  def $[T: ClassTag : NotNothing](name: String): Expression[T] = {
    // trick to get `java.lang.Integer` from `int`
    val clazz = MethodType.methodType(implicitly[ClassTag[T]].runtimeClass).wrap().returnType()

    { session => getFromSession[T](clazz, session, name) }
  }

  /**
   * Passed to [[com.github.phisgr.gatling.grpc.request.ListeningStream.reconciliate]]
   * or [[com.github.phisgr.gatling.grpc.request.BidiStream.complete]].
   * Suspends the execution of the virtual user until
   * the stream ends.
   */
  def StreamEnd: StreamCall.WaitType = StreamCall.StreamEnd

  /**
   * Passed to [[com.github.phisgr.gatling.grpc.request.ListeningStream.reconciliate]]
   * or [[com.github.phisgr.gatling.grpc.request.BidiStream.complete]].
   * Suspends the execution of the virtual user until
   * the next message arrives or
   * the stream ends.
   */
  def NextMessage: StreamCall.WaitType = StreamCall.NextMessage

  /**
   * Passed to [[com.github.phisgr.gatling.grpc.action.StreamStartBuilder.streamEndLog]]
   * Never add the stream end event to the report.
   */
  def Never: StreamCall.StreamEndLog = StreamCall.Never

  /**
   * Passed to [[com.github.phisgr.gatling.grpc.action.StreamStartBuilder.streamEndLog]]
   * Add the stream end event to the report only when an error occurred.
   */
  def ErrorOnly: StreamCall.StreamEndLog = StreamCall.ErrorOnly
}
