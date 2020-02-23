package com.github.phisgr.example

import com.github.phisgr.example.greet.CustomError
import io.grpc.{Context, Metadata}
import scalapb.grpc.ProtoUtils

package object util {
  val TokenHeaderKey = Metadata.Key.of("token", Metadata.ASCII_STRING_MARSHALLER)
  val TokenContextKey = Context.key[String]("token")
  val ErrorResponseKey = ProtoUtils.keyForProto[CustomError]
}
