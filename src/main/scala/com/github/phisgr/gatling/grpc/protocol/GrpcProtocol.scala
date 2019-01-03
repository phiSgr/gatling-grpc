package com.github.phisgr.gatling.grpc.protocol

import io.gatling.core.CoreComponents
import io.gatling.core.config.GatlingConfiguration
import io.gatling.core.protocol.{Protocol, ProtocolComponents, ProtocolKey}
import io.gatling.core.session.{Session, SessionPrivateAttributes}
import io.grpc.{ManagedChannel, ManagedChannelBuilder}

object GrpcProtocol {
  val ChannelAttributeName: String = SessionPrivateAttributes.PrivateAttributePrefix + "grpc.channel"

  class GrpcComponent(channelBuilder: ManagedChannelBuilder[_], shareChannel: Boolean) extends ProtocolComponents {
    private val channel = if (shareChannel) channelBuilder.build() else null

    private[gatling] def getChannel(session: Session): ManagedChannel = {
      if (shareChannel) channel else session(GrpcProtocol.ChannelAttributeName).as[ManagedChannel]
    }

    override val onStart: Session => Session = if (shareChannel) identity else { session =>
      session.set(GrpcProtocol.ChannelAttributeName, channelBuilder.build())
    }

    override val onExit = { s =>
      s(ChannelAttributeName).asOption[ManagedChannel].foreach(_.shutdownNow())
    }
  }

  val GrpcProtocolKey = new ProtocolKey[GrpcProtocol, GrpcComponent] {
    override def protocolClass: Class[Protocol] = classOf[GrpcProtocol].asInstanceOf[Class[Protocol]]

    override def defaultProtocolValue(configuration: GatlingConfiguration) =
      GrpcProtocol(ManagedChannelBuilder.forAddress("localhost", 8080))

    override def newComponents(coreComponents: CoreComponents): GrpcProtocol => GrpcComponent = { protocol =>
      new GrpcComponent(protocol.channelBuilder, protocol._shareChannel)
    }

  }
}

case class GrpcProtocol(channelBuilder: ManagedChannelBuilder[_], private[gatling] val _shareChannel: Boolean = false) extends Protocol {
  def shareChannel: GrpcProtocol = copy(_shareChannel = true)
}
