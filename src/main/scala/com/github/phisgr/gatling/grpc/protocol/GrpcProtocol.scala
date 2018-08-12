package com.github.phisgr.gatling.grpc.protocol

import akka.actor.ActorSystem
import io.gatling.core.CoreComponents
import io.gatling.core.config.GatlingConfiguration
import io.gatling.core.protocol.{Protocol, ProtocolComponents, ProtocolKey}
import io.gatling.core.session.SessionPrivateAttributes
import io.grpc.{ManagedChannel, ManagedChannelBuilder}

object GrpcProtocol {
  val ChannelAttributeName = SessionPrivateAttributes.PrivateAttributePrefix + "grpc.channel"

  case class GrpcComponent(channelBuilder: ManagedChannelBuilder[_]) extends ProtocolComponents {
    override def onStart = None

    override def onExit = Some { s =>
      s(ChannelAttributeName).asOption[ManagedChannel].foreach(_.shutdownNow())
    }
  }

  val GrpcProtocolKey = new ProtocolKey {
    override type Protocol = GrpcProtocol
    override type Components = GrpcComponent

    override def protocolClass = classOf[GrpcProtocol].asInstanceOf[Class[io.gatling.core.protocol.Protocol]]

    override def defaultProtocolValue(configuration: GatlingConfiguration) =
      GrpcProtocol(ManagedChannelBuilder.forAddress("localhost", 8080))

    override def newComponents(system: ActorSystem, coreComponents: CoreComponents) = { protocol: GrpcProtocol =>
      GrpcComponent(protocol.channelBuilder)
    }

  }
}

case class GrpcProtocol(channelBuilder: ManagedChannelBuilder[_]) extends Protocol
