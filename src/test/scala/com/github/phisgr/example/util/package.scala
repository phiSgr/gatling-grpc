package com.github.phisgr.example

import io.grpc.{Context, Metadata}

package object util {
  val TokenHeaderKey = Metadata.Key.of("token", Metadata.ASCII_STRING_MARSHALLER)
  val TokenContextKey = Context.key[String]("token")
}
