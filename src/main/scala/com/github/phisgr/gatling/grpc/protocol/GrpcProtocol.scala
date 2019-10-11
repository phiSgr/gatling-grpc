package com.github.phisgr.gatling.grpc.protocol

import java.util.UUID

import com.typesafe.scalalogging.StrictLogging
import io.gatling.core.CoreComponents
import io.gatling.core.config.GatlingConfiguration
import io.gatling.core.protocol.{Protocol, ProtocolComponents, ProtocolKey}
import io.gatling.core.session.{Session, SessionPrivateAttributes}
import io.grpc.{ManagedChannel, ManagedChannelBuilder}

object GrpcProtocol {
  private val DefaultChannelAttributeName: String = SessionPrivateAttributes.PrivateAttributePrefix + "grpc.channel"

  class GrpcComponent(channelBuilder: ManagedChannelBuilder[_], shareChannel: Boolean, id: Option[String]) extends ProtocolComponents {
    private val channelAttributeName = id.fold(DefaultChannelAttributeName)(DefaultChannelAttributeName + "." + _)
    private val channel = if (shareChannel) channelBuilder.build() else null

    private[gatling] def getChannel(session: Session): ManagedChannel = {
      if (shareChannel) channel else session(channelAttributeName).as[ManagedChannel]
    }

    override val onStart: Session => Session = if (shareChannel) identity else { session =>
      session.set(channelAttributeName, channelBuilder.build())
    }

    override val onExit = { s =>
      s(channelAttributeName).asOption[ManagedChannel].foreach(_.shutdownNow())
    }
  }

  val GrpcProtocolKey = new ProtocolKey[GrpcProtocol, GrpcComponent] {
    override def protocolClass: Class[Protocol] = classOf[GrpcProtocol].asInstanceOf[Class[Protocol]]

    override def defaultProtocolValue(configuration: GatlingConfiguration) =
      GrpcProtocol(ManagedChannelBuilder.forAddress("localhost", 8080))

    override def newComponents(coreComponents: CoreComponents): GrpcProtocol => GrpcComponent = { protocol =>
      new GrpcComponent(protocol.channelBuilder, protocol._shareChannel, id = None)
    }

  }
}

case class GrpcProtocol(channelBuilder: ManagedChannelBuilder[_], private[gatling] val _shareChannel: Boolean = false) extends Protocol {
  def shareChannel: GrpcProtocol = copy(_shareChannel = true)

  import GrpcProtocol._

  private[gatling] lazy val overridingKey = new ProtocolKey[GrpcProtocol, GrpcComponent] with StrictLogging {
    override def protocolClass: Class[Protocol] = GrpcProtocolKey.protocolClass

    override def defaultProtocolValue(configuration: GatlingConfiguration): GrpcProtocol =
      GrpcProtocolKey.defaultProtocolValue(configuration)

    override def newComponents(coreComponents: CoreComponents): GrpcProtocol => GrpcComponent = { _ =>
      logger.info("Creating a new non-default GrpcComponent.")
      new GrpcComponent(channelBuilder, _shareChannel, Some(UUID.randomUUID().toString))
    }
  }

}
