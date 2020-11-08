package com.github.phisgr.gatling.grpc.check

import com.github.phisgr.gatling.generic.check.ResponseExtract
import io.gatling.commons.validation.Validation
import io.gatling.core.check.{CheckBuilder, CheckMaterializer, DefaultMultipleFindCheckBuilder, FindCheckBuilder, ValidatorCheckBuilder}
import io.grpc.{Metadata, Status}

trait GrpcCheckSupport {

  val statusCode: FindCheckBuilder[StatusExtract, Status, Status.Code] =
    StatusExtract.StatusCode

  val statusDescription: FindCheckBuilder[StatusExtract, Status, String] =
    StatusExtract.StatusDescription

  def extract[T, X](f: T => Validation[Option[X]]): FindCheckBuilder[ResponseExtract, T, X] =
    ResponseExtract.extract(f, "grpcResponse")

  def extractMultiple[T, X](f: T => Validation[Option[Seq[X]]]): DefaultMultipleFindCheckBuilder[ResponseExtract, T, X] =
    ResponseExtract.extractMultiple(f, "grpcResponse")

  def trailer[T](key: Metadata.Key[T]): DefaultMultipleFindCheckBuilder[TrailersExtract, Metadata, T] =
    TrailersExtract.trailer(key)

  implicit def resMat[Res]: CheckMaterializer[ResponseExtract, GrpcCheck[Res], GrpcResponse[Res], Res] =
    ResponseMaterializers.materializer[Res]

  implicit val statusMat: CheckMaterializer[StatusExtract, GrpcCheck[Any], GrpcResponse[Any], Status] =
    StatusExtract.Materializer

  implicit val trailersMat: CheckMaterializer[TrailersExtract, GrpcCheck[Any], GrpcResponse[Any], Metadata] =
    TrailersExtract.Materializer

  // The contravarianceHelper is needed because without it, the implicit conversion does not turn
  // CheckBuilder[StatusExtract, GrpcResponse[Any], X] into a GrpcCheck[Res]
  // Despite GrpcCheck[Any] is a subtype of GrpcCheck[Res].
  implicit def checkBuilder2GrpcCheck[A, P, X, ResOrAny, Res](checkBuilder: CheckBuilder[A, P, X])(
    implicit materializer: CheckMaterializer[A, GrpcCheck[ResOrAny], GrpcResponse[ResOrAny], P],
    contravarianceHelper: GrpcCheck[ResOrAny] => GrpcCheck[Res]
  ): GrpcCheck[Res] =
    contravarianceHelper(checkBuilder.build(materializer))

  implicit def validatorCheckBuilder2GrpcCheck[A, P, X, ResOrAny, Res](vCheckBuilder: ValidatorCheckBuilder[A, P, X])(
    implicit materializer: CheckMaterializer[A, GrpcCheck[ResOrAny], GrpcResponse[ResOrAny], P],
    contravarianceHelper: GrpcCheck[ResOrAny] => GrpcCheck[Res]
  ): GrpcCheck[Res] =
    vCheckBuilder.exists

  implicit def findCheckBuilder2GrpcCheck[A, P, X, ResOrAny, Res](findCheckBuilder: FindCheckBuilder[A, P, X])(
    implicit materializer: CheckMaterializer[A, GrpcCheck[ResOrAny], GrpcResponse[ResOrAny], P],
    contravarianceHelper: GrpcCheck[ResOrAny] => GrpcCheck[Res]
  ): GrpcCheck[Res] =
    findCheckBuilder.find.exists

  implicit def someWrapper[T](value: T): SomeWrapper[T] = new SomeWrapper(value)
}
