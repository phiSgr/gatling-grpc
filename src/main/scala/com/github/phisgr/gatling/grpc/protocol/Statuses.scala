package com.github.phisgr.gatling.grpc.protocol

import io.grpc.Status

object Statuses {
  final val MultipleResponses =
    Status.INTERNAL
      .withDescription("More than one value received for unary call")
      .asRuntimeException

  final val NoResponses =
    Status.INTERNAL.withDescription("No value received for unary call")
}
