package com.github.phisgr.gatling.grpc.check

import io.gatling.core.check.{CheckBuilder, CheckMaterializer, FindCheckBuilder, ValidatorCheckBuilder}
import io.grpc.Status

import scala.util.Try

trait GrpcCheckSupport {

  val statusCode = StatusExtract.StatusCode

  val statusDescription = StatusExtract.StatusDescription

  def extract[T, X](f: T => Option[X]) = ResponseExtract.extract(f)

  def extractMultiple[T, X](f: T => Option[Seq[X]]) = ResponseExtract.extractMultiple(f)

  implicit def resMat[Res]: CheckMaterializer[ResponseExtract, GrpcCheck[Res], Try[Res], Res] =
    ResponseExtract.materializer[Res]

  implicit val statusMat: CheckMaterializer[StatusExtract, GrpcCheck[Any], Try[Any], Try[Any]] =
    StatusExtract.Materializer

  // The contravarianceHelper is needed because without it, the implicit conversion does not turn
  // CheckBuilder[StatusExtract, Try[Any], X] into a GrpcCheck[Res]
  // Despite GrpcCheck[Any] is a subtype of GrpcCheck[Res].
  implicit def checkBuilder2GrpcCheck[A, P, X, ResOrAny, Res](checkBuilder: CheckBuilder[A, P, X])(
    implicit materializer: CheckMaterializer[A, GrpcCheck[ResOrAny], Try[ResOrAny], P],
    contravarianceHelper: GrpcCheck[ResOrAny] => GrpcCheck[Res]
  ): GrpcCheck[Res] =
    contravarianceHelper(checkBuilder.build(materializer))

  implicit def validatorCheckBuilder2GrpcCheck[A, P, X, ResOrAny, Res](vCheckBuilder: ValidatorCheckBuilder[A, P, X])(
    implicit materializer: CheckMaterializer[A, GrpcCheck[ResOrAny], Try[ResOrAny], P],
    contravarianceHelper: GrpcCheck[ResOrAny] => GrpcCheck[Res]
  ): GrpcCheck[Res] =
    vCheckBuilder.exists

  implicit def findCheckBuilder2GrpcCheck[A, P, X, ResOrAny, Res](findCheckBuilder: FindCheckBuilder[A, P, X])(
    implicit materializer: CheckMaterializer[A, GrpcCheck[ResOrAny], Try[ResOrAny], P],
    contravarianceHelper: GrpcCheck[ResOrAny] => GrpcCheck[Res]
  ): GrpcCheck[Res] =
    findCheckBuilder.find.exists

}
