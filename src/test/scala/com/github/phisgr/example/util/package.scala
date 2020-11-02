package com.github.phisgr.example

import ch.qos.logback.classic.{Level, Logger}
import com.github.phisgr.example.chat.CustomError
import io.grpc.{Context, Metadata}
import org.slf4j.LoggerFactory
import scalapb.grpc.ProtoUtils

package object util {
  val ports = Seq(8081, 8082, 8083)

  val TokenHeaderKey = Metadata.Key.of("token", Metadata.ASCII_STRING_MARSHALLER)
  val TokenContextKey = Context.key[String]("token")
  val ErrorResponseKey = ProtoUtils.keyForProto[CustomError]

  def tuneLogging(clazz: String, level: Level): Unit = {
    LoggerFactory.getLogger(clazz)
      .asInstanceOf[Logger]
      .setLevel(level)
  }
}
