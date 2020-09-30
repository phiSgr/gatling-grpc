package com.github.phisgr.gatling.grpc.check

import io.gatling.commons.validation.{SuccessWrapper, Validation, safely}
import io.gatling.core.check._
import io.gatling.core.session.{Expression, ExpressionSuccessWrapper}

private[gatling] object ResponseExtract {

  trait ResponseExtractor[T, X] extends Extractor[T, X] {
    val name = "grpcResponse"

    def extract(prepared: T): Option[X]

    override final def apply(prepared: T): Validation[Option[X]] = safely() {
      extract(prepared).success
    }
  }

  case class SingleExtractor[T, X](f: T => Option[X]) extends ResponseExtractor[T, X] {
    override def extract(prepared: T) = f(prepared)
    override val arity = "find"
  }

  def extract[T, X](f: T => Option[X]): FindCheckBuilder[ResponseExtract, T, X] = new DefaultFindCheckBuilder(
    displayActualValue = true,
    extractor = SingleExtractor(f).expressionSuccess
  )

  def extractMultiple[T, X](f: T => Option[Seq[X]]): DefaultMultipleFindCheckBuilder[ResponseExtract, T, X] =
    new DefaultMultipleFindCheckBuilder[ResponseExtract, T, X](
      displayActualValue = true
    ) {
      override def findExtractor(occurrence: Int): Expression[ResponseExtractor[T, X]] =
        new ResponseExtractor[T, X] {
          override def extract(prepared: T): Option[X] = f(prepared).flatMap(s =>
            if (s.isDefinedAt(occurrence)) Some(s(occurrence)) else None
          )

          // Since the arity traits got fused into the CriterionExtractors
          // and our criteria are functions that do not look good in string
          // I have no choice but to write them manually
          override val arity: String = if (occurrence == 0) "find" else s"find($occurrence)"
        }.expressionSuccess

      override def findAllExtractor: Expression[ResponseExtractor[T, Seq[X]]] =
        new ResponseExtractor[T, Seq[X]] {
          override def extract(prepared: T): Option[Seq[X]] = f(prepared)
          override val arity = "findAll"
        }.expressionSuccess

      override def countExtractor: Expression[ResponseExtractor[T, Int]] =
        new ResponseExtractor[T, Int] {
          override def extract(prepared: T): Option[Int] = f(prepared).map(_.size)
          override val arity = "count"
        }.expressionSuccess
    }

  def materializer[Res]: CheckMaterializer[ResponseExtract, GrpcCheck[Res], GrpcResponse[Res], Res] =
    new CheckMaterializer[ResponseExtract, GrpcCheck[Res], GrpcResponse[Res], Res](
      specializer = GrpcCheck(_, GrpcCheck.Value)
    ) {
      override protected def preparer: Preparer[GrpcResponse[Res], Res] = _.validation
    }

  def streamMaterializer[Res]: CheckMaterializer[ResponseExtract, StreamCheck[Res], Res, Res] =
    new CheckMaterializer[ResponseExtract, StreamCheck[Res], Res, Res](
      specializer = StreamCheck(_, GrpcCheck.Value)
    ) {
      override protected def preparer: Preparer[Res, Res] = _.success
    }
}

// phantom type for implicit materializer resolution
trait ResponseExtract
