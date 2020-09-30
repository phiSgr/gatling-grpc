package com.github.phisgr.gatling.grpc

import com.github.phisgr.gatling.grpc.check.{GrpcCheckSupport, StreamingCheckSupport}

object Predef extends GrpcDsl with GrpcCheckSupport with StreamingCheckSupport
