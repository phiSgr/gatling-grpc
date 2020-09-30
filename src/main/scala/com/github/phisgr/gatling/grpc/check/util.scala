package com.github.phisgr.gatling.grpc.check

import io.gatling.commons.validation.{Failure, Success, Validation}
import io.grpc.{Metadata, Status}

import scala.annotation.unchecked.uncheckedVariance

class GrpcResponse[+T](
  // can be null if status is not OK, or in GrpcStreamEnd
  res: T,
  val status: Status,
  val trailers: Metadata
) {
  private[this] var _validation: Validation[T@uncheckedVariance] = _

  // Hand-rolling lazy val because lazy is thread-safe
  def validation: Validation[T] = {
    if (_validation eq null) {
      _validation = if (status.isOk) {
        Success(res)
      } else {
        val description = status.getDescription
        Failure(if (description eq null) status.getCode.toString else s"${status.getCode}: $description")
      }
    }
    _validation
  }
}

object GrpcResponse {
  type GrpcStreamEnd = GrpcResponse[Null]
}

class SomeWrapper[T](val value: T) extends AnyVal {
  def some: Some[T] = Some(value)
}
