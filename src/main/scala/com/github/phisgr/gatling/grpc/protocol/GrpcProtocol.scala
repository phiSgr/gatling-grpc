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
    override def onStart = identity

    override def onExit = { s =>
      s(ChannelAttributeName).asOption[ManagedChannel].foreach(_.shutdownNow())
    }
  }

  val GrpcProtocolKey = new ProtocolKey[GrpcProtocol, GrpcComponent] {
    override def protocolClass: Class[Protocol] = classOf[GrpcProtocol].asInstanceOf[Class[Protocol]]

    override def defaultProtocolValue(configuration: GatlingConfiguration) =
      GrpcProtocol(ManagedChannelBuilder.forAddress("localhost", 8080))

    override def newComponents(coreComponents: CoreComponents): GrpcProtocol => GrpcComponent = { protocol =>
      GrpcComponent(protocol.channelBuilder)
    }

  }
}

case class GrpcProtocol(channelBuilder: ManagedChannelBuilder[_]) extends Protocol
