package com.github.phisgr.gatling.grpc.check

import io.gatling.commons.validation.{Failure, Success, Validation}
import io.grpc.{Metadata, Status}

import scala.annotation.unchecked.uncheckedVariance

case class GrpcResponse[+T](
  private val res: T, // can be null if status is not OK
  status: Status,
  trailers: Metadata
) {
  private var _validation: Validation[T@uncheckedVariance] = _

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

class SomeWrapper[T](val value: T) extends AnyVal {
  def some: Some[T] = Some(value)
}
