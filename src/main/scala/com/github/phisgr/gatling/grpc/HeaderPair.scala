package com.github.phisgr.gatling.grpc

import io.gatling.commons.validation.{Failure, Success}
import io.gatling.core.Predef.Session
import io.gatling.core.session.Expression
import io.grpc.Metadata

case class HeaderPair[T](key: Metadata.Key[T], value: Expression[T]) {
  def mutateMetadata(session: Session, headers: Metadata): Failure = {
    value(session) match {
      case Success(value) =>
        headers.put(key, value)
        null
      case f@Failure(_) =>
        f
    }
  }
}
