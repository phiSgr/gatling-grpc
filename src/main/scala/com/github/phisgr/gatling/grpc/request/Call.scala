package com.github.phisgr.gatling.grpc.request

import com.github.phisgr.gatling.grpc.protocol.GrpcProtocol
import io.gatling.commons.validation.{SuccessWrapper, Validation}
import io.gatling.core.session.{Expression, Session}
import io.gatling.core.structure.ScenarioContext
import io.grpc.{CallOptions, Metadata}

abstract class Call(ctx: ScenarioContext, callAttributes: CallAttributes) {

  private[this] val headerPairs = callAttributes.reversedHeaders.reverse.toArray
  protected def resolveHeaders(session: Session): Validation[Metadata] = {
    val md = new Metadata()
    val size = headerPairs.length
    var i = 0
    while (i < size) {
      val failure = headerPairs(i).mutateMetadata(session, md)
      if (failure ne null) return failure
      i += 1
    }
    md.success
  }

  protected val callOptions: Expression[CallOptions] = callAttributes.callOptions

  protected val component: GrpcProtocol.GrpcComponent = {
    val protocolKey = callAttributes.protocolOverride.fold(GrpcProtocol.GrpcProtocolKey)(_.overridingKey)
    ctx.protocolComponentsRegistry.components(protocolKey)
  }
}
