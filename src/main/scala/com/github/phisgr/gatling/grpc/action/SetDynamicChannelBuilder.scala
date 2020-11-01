package com.github.phisgr.gatling.grpc.action

import com.github.phisgr.gatling.grpc.protocol.GrpcProtocol
import io.gatling.core.CoreComponents
import io.gatling.core.action.builder.ActionBuilder
import io.gatling.core.action.{Action, ChainableAction}
import io.gatling.core.session.Session
import io.gatling.core.structure.ScenarioContext
import io.gatling.core.util.NameGen
import io.grpc.ManagedChannelBuilder

class SetDynamicChannelBuilder(
  channelAttributeName: String,
  createBuilder: Session => ManagedChannelBuilder[_]
) extends ActionBuilder {
  override def build(ctx: ScenarioContext, next: Action): Action =
    new SetDynamicChannel(channelAttributeName, createBuilder, ctx.coreComponents, next)
}

class SetDynamicChannel(
  channelAttributeName: String,
  createBuilder: Session => ManagedChannelBuilder[_],
  coreComponents: CoreComponents,
  override val next: Action
) extends ChainableAction with NameGen {
  override val name: String = genName("setDynamicChannel")
  override protected def execute(session: Session): Unit = {
    val builder = createBuilder(session)
    GrpcProtocol.setEventLoopGroup(builder, coreComponents)
    next ! session.set(channelAttributeName, builder.build())
  }
}
