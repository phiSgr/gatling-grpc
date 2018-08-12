package com.github.phisgr.gatling.grpc.check

import io.gatling.commons.validation.{FailureWrapper, SuccessWrapper, Validation}
import io.gatling.core.Predef.value2Expression
import io.gatling.core.check.ValidatorCheckBuilder
import io.gatling.core.check.extractor.{Extractor, SingleArity}
import io.gatling.core.session.ExpressionSuccessWrapper
import io.grpc.{Status, StatusException, StatusRuntimeException}

import scala.util.{Failure, Success, Try}

private[gatling] object StatusExtract {

  def extractStatus(t: Try[_]): Validation[Status] = t match {
    case Success(_) => Status.OK.success
    case Failure(e: StatusException) => e.getStatus.success
    case Failure(e: StatusRuntimeException) => e.getStatus.success
    case Failure(_) => "Response wasn't received".failure
  }

  type StatusV[T, X] = ValidatorCheckBuilder[GrpcCheck[T], Try[T], Try[T], X]

  private def statusExtract[X](extractor: Extractor[Try[_], X]): StatusV[Any, X] = ValidatorCheckBuilder(
    extender = GrpcCheck(_, GrpcCheck.Status),
    preparer = _.success,
    extractor = extractor.expressionSuccess
  )

  trait StatusExtractor[X] extends Extractor[Try[_], X] with SingleArity

  val StatusDescription = statusExtract(new StatusExtractor[String] {
    val name = "grpcStatusDescription"

    override def apply(prepared: Try[_]) = extractStatus(prepared).map(s => Option(s.getDescription))
  })

  val StatusCode: StatusV[Any, Status.Code] = statusExtract(new StatusExtractor[Status.Code] {
    val name = "grpcStatusCode"

    override def apply(prepared: Try[_]) = extractStatus(prepared).map(s => Some(s.getCode))
  })

  val DefaultCheck = StatusCode.is(value2Expression(Status.Code.OK)).build

}
