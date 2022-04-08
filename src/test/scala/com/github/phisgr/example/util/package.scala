package com.github.phisgr.example

import ch.qos.logback.classic.{Level, Logger}
import com.github.phisgr.example.chat.CustomError
import io.grpc.{Context, Metadata}
import org.slf4j.LoggerFactory
import scalapb.grpc.ProtoUtils

package object util {
  val ports = Seq(8081, 8082, 8083)

  val TokenHeaderKey: Metadata.Key[String] = Metadata.Key.of("token", Metadata.ASCII_STRING_MARSHALLER)
  val TokenContextKey: Context.Key[String] = Context.key[String]("token")
  val ErrorResponseKey: Metadata.Key[CustomError] = ProtoUtils.keyForProto[CustomError]

  // Although the actions do not support runtime change of logging level
  // this is done before the actions are built.
  def tuneLogging(clazz: Class[_], level: Level): Unit = {
    LoggerFactory.getLogger(clazz.getName)
      .asInstanceOf[Logger]
      .setLevel(level)
  }
}
