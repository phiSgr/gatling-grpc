package com.github.phisgr.gatling.grpc.check

import io.gatling.commons.validation.{FailureWrapper, SuccessWrapper, Validation, safely}
import io.gatling.core.check.extractor.{CountArity, Extractor, FindAllArity, FindArity, SingleArity}
import io.gatling.core.check.{DefaultMultipleFindCheckBuilder, ValidatorCheckBuilder}
import io.gatling.core.session.ExpressionSuccessWrapper

import scala.util.{Failure, Success, Try}

private[gatling] object ResponseExtract {
  def tryToValidation[V](t: Try[V]): Validation[V] = t match {
    case Success(a) => a.success
    case Failure(e) => e.getMessage.failure
  }

  trait ResponseExtractor[T, X] extends Extractor[T, X] {
    val name = "grpcResponse"

    def extract(prepared: T): Option[X]

    override final def apply(prepared: T) = safely("Extraction crashed: " + _) {
      extract(prepared).success
    }
  }

  case class SingleExtractor[T, X](f: T => Option[X]) extends ResponseExtractor[T, X] with SingleArity {
    override def extract(prepared: T) = f(prepared)
  }

  def extract[T, X](f: T => Option[X]) = ValidatorCheckBuilder[GrpcCheck[T], Try[T], T, X](
    extender = GrpcCheck(_, GrpcCheck.Value),
    preparer = tryToValidation,
    extractor = SingleExtractor(f).expressionSuccess
  )

  def extractMultiple[T, X](f: T => Option[Seq[X]]) = new DefaultMultipleFindCheckBuilder[GrpcCheck[T], Try[T], T, X](
    extender = GrpcCheck(_, GrpcCheck.Value),
    preparer = tryToValidation
  ) {
    override def findExtractor(_occurrence: Int) = new ResponseExtractor[T, X] with FindArity {
      override def extract(prepared: T) = f(prepared).flatMap(s =>
        if (s.isDefinedAt(occurrence)) Some(s(occurrence)) else None
      )

      override def occurrence = _occurrence
    }.expressionSuccess

    override def findAllExtractor = new ResponseExtractor[T, Seq[X]] with FindAllArity {
      override def extract(prepared: T) = f(prepared)
    }.expressionSuccess

    override def countExtractor = new ResponseExtractor[T, Int] with CountArity {
      override def extract(prepared: T) = f(prepared).map(_.size)
    }.expressionSuccess
  }
}
