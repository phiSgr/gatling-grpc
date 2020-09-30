package com.github.phisgr.gatling.grpc.request

import com.github.phisgr.gatling.grpc.HeaderPair
import com.github.phisgr.gatling.grpc.protocol.GrpcProtocol
import io.gatling.commons.validation.Success
import io.gatling.core.session.{Expression, ExpressionSuccessWrapper, Session}
import io.grpc.{CallOptions, Metadata}

case class CallAttributes(
  callOptions: Expression[CallOptions] = CallOptions.DEFAULT.expressionSuccess,
  reversedHeaders: List[HeaderPair[_]] = Nil,
  protocolOverride: Option[GrpcProtocol] = None
)

trait CallAttributesMixin[Self] {

  private[gatling] def callAttributes: CallAttributes

  private[gatling] def withCallAttributes(callAttributes: CallAttributes): Self

  def callOptions(callOptions: Expression[CallOptions]): Self =
    withCallAttributes(callAttributes.copy(callOptions = callOptions))

  /**
   * Sets call options of the call.
   *
   * @param callOptions is a call-by-name param, it will be run every time the call is executed.
   * @return a new GrpcCallActionBuilder
   */
  def callOptions(callOptions: => CallOptions): Self =
    this.callOptions { _: Session => Success(callOptions) }

  def header[T](key: Metadata.Key[T])(value: Expression[T]): Self =
    withCallAttributes(callAttributes.copy(
      reversedHeaders = HeaderPair(key, value) :: callAttributes.reversedHeaders
    ))

  def target(protocol: GrpcProtocol): Self =
    this.withCallAttributes(callAttributes.copy(protocolOverride = Some(protocol)))

}
