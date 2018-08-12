package com.github.phisgr.gatling.grpc.check

import io.grpc.Status

trait GrpcCheckSupport {

  import StatusExtract._

  def statusCode[T] = StatusCode.asInstanceOf[StatusV[T, Status.Code]]

  def statusDescription[T] = StatusDescription.asInstanceOf[StatusV[T, String]]

  def extract[T, X](f: T => Option[X]) = ResponseExtract.extract(f)

  def extractMultiple[T, X](f: T => Option[Seq[X]]) = ResponseExtract.extractMultiple(f)
}
