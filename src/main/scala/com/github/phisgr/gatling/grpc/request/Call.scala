package com.github.phisgr.gatling.grpc.request

import com.github.phisgr.gatling.grpc.protocol.{ByteArrayMarshaller, EmptyMarshaller, GrpcProtocol}
import io.gatling.commons.validation.{SuccessWrapper, Validation}
import io.gatling.core.action.RequestAction
import io.gatling.core.session.{Expression, Session}
import io.gatling.core.structure.ScenarioContext
import io.gatling.core.util.NameGen
import io.grpc.{CallOptions, ClientCall, Metadata, MethodDescriptor}

abstract class Call[Req, Res](
  ctx: ScenarioContext,
  callAttributes: CallAttributes,
  method: MethodDescriptor[Req, Res]
) extends RequestAction with NameGen {

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

  private[this] val component: GrpcProtocol.GrpcComponent = {
    val protocolKey = callAttributes.protocolOverride.fold[GrpcProtocol.Key](GrpcProtocol.GrpcProtocolKey)(_.overridingKey)
    ctx.protocolComponentsRegistry.components(protocolKey)
  }

  protected def needParsed: Boolean
  protected def mayNeedDelayedParsing: Boolean

  protected val lazyParseMethod: MethodDescriptor[Req, Any] = {
    val notParsed = component.lazyParsing && !needParsed
    val withResponseMarshaller = if (notParsed) {
      val responseMarshaller = if (mayNeedDelayedParsing) {
        ByteArrayMarshaller
      } else {
        EmptyMarshaller
      }
      method.toBuilder(
        method.getRequestMarshaller,
        responseMarshaller
      ).build()
    } else {
      method
    }

    withResponseMarshaller.asInstanceOf[MethodDescriptor[Req, Any]]
  }

  protected def newCall(session: Session, callOptions: CallOptions): ClientCall[Req, Any] = {
    component.newCall(session, lazyParseMethod, callOptions)
  }
}
