package com.github.phisgr.gatling.grpc

import com.github.phisgr.gatling.grpc.check.{GrpcCheckSupport, StreamingCheckSupport}
import com.github.phisgr.gatling.grpc.stream.StatusSupport

object Predef extends GrpcDsl with GrpcCheckSupport with StreamingCheckSupport with StatusSupport
