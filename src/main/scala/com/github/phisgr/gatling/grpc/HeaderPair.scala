package com.github.phisgr.gatling.grpc

import io.gatling.core.session.Expression
import io.grpc.Metadata

case class HeaderPair[T](key: Metadata.Key[T], value: Expression[T])
