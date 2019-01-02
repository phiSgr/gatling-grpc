package com.github.phisgr.gatling.grpc.action

import com.github.phisgr.gatling.grpc.protocol.GrpcProtocol
import io.gatling.core.action.builder.ActionBuilder
import io.gatling.core.action.{Action, ChainableAction}
import io.gatling.core.session.Session
import io.gatling.core.structure.ScenarioContext
import io.gatling.core.util.NameGen
import io.grpc.{ManagedChannel, ManagedChannelBuilder}

object GrpcSetUpActionBuilder extends ActionBuilder {
  override def build(ctx: ScenarioContext, next: Action): Action = {
    val protocol = ctx.protocolComponentsRegistry.components(GrpcProtocol.GrpcProtocolKey)
    new GrpcSetUpAction(protocol.channelBuilder, next = next)
  }

  class GrpcSetUpAction(
    builder: ManagedChannelBuilder[_],
    val next: Action
  ) extends ChainableAction with NameGen {

    override val name = genName("grpcChannelSetUp")

    override def execute(session: Session): Unit = {
      session(GrpcProtocol.ChannelAttributeName).asOption[ManagedChannel].foreach(_.shutdownNow())
      next ! session.set(GrpcProtocol.ChannelAttributeName, builder.build())
    }
  }

}
