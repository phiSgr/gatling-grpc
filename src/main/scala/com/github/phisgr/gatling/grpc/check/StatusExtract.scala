package com.github.phisgr.gatling.grpc.check

import io.gatling.commons.validation.{FailureWrapper, SuccessWrapper, Validation}
import io.gatling.core.Predef.value2Expression
import io.gatling.core.check._
import io.gatling.core.check.extractor.{Extractor, SingleArity}
import io.grpc.{Status, StatusException, StatusRuntimeException}

import scala.util.{Failure, Success, Try}

private[gatling] object StatusExtract {

  def extractStatus(t: Try[_]): Validation[Status] = t match {
    case Success(_) => Status.OK.success
    case Failure(e: StatusException) => e.getStatus.success
    case Failure(e: StatusRuntimeException) => e.getStatus.success
    case Failure(_) => "Response wasn't received".failure
  }

  trait StatusExtractor[X] extends Extractor[Try[Any], X] with SingleArity

  val StatusDescription: ValidatorCheckBuilder[StatusExtract, Try[Any], String] = ValidatorCheckBuilder(
    extractor = new StatusExtractor[String] {
      val name = "grpcStatusDescription"

      override def apply(prepared: Try[Any]): Validation[Option[String]] =
        extractStatus(prepared).map(s => Option(s.getDescription))
    },
    displayActualValue = true
  )

  val StatusCode: ValidatorCheckBuilder[StatusExtract, Try[Any], Status.Code] = ValidatorCheckBuilder(
    extractor = new StatusExtractor[Status.Code] {
      val name = "grpcStatusCode"

      override def apply(prepared: Try[Any]): Validation[Option[Status.Code]] =
        extractStatus(prepared).map(s => Some(s.getCode))
    },
    displayActualValue = true
  )

  object Materializer extends CheckMaterializer[StatusExtract, GrpcCheck[Any], Try[Any], Try[Any]] {
    override protected def preparer: Preparer[Try[Any], Try[Any]] = _.success

    override protected def specializer: Specializer[GrpcCheck[Any], Try[Any]] = GrpcCheck(_, GrpcCheck.Status)
  }

  val DefaultCheck = StatusCode.is(value2Expression(Status.Code.OK)).build(Materializer)

}

// phantom type for implicit materializer resolution
trait StatusExtract
