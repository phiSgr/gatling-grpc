package com.github.phisgr.gatling.grpc.check

import com.github.phisgr.gatling.generic.check.ResponseExtract
import com.github.phisgr.gatling.grpc.check.GrpcResponse.GrpcStreamEnd
import io.gatling.core.check.{CheckBuilder, CheckMaterializer, FindCheckBuilder, ValidatorCheckBuilder}
import io.grpc.{Metadata, Status}

trait StreamingCheckSupport {

  implicit def streamResMat[Res]: CheckMaterializer[ResponseExtract, StreamCheck[Res], Res, Res] =
    ResponseMaterializers.streamMaterializer[Res]

  implicit val streamStatusMat: CheckMaterializer[StatusExtract, StreamCheck[GrpcStreamEnd], GrpcStreamEnd, Status] =
    StatusExtract.StreamMaterializer

  implicit val streamTrailersMat: CheckMaterializer[TrailersExtract, StreamCheck[GrpcStreamEnd], GrpcStreamEnd, Metadata] =
    TrailersExtract.StreamMaterializer

  implicit def checkBuilder2GrpcCheck[A, P, X, Res](checkBuilder: CheckBuilder[A, P, X])(
    implicit materializer: CheckMaterializer[A, StreamCheck[Res], Res, P]
  ): StreamCheck[Res] =
    checkBuilder.build(materializer)

  implicit def validatorCheckBuilder2GrpcCheck[A, P, X, Res](vCheckBuilder: ValidatorCheckBuilder[A, P, X])(
    implicit materializer: CheckMaterializer[A, StreamCheck[Res], Res, P]
  ): StreamCheck[Res] =
    vCheckBuilder.exists

  implicit def findCheckBuilder2GrpcCheck[A, P, X, Res](findCheckBuilder: FindCheckBuilder[A, P, X])(
    implicit materializer: CheckMaterializer[A, StreamCheck[Res], Res, P]
  ): StreamCheck[Res] =
    findCheckBuilder.find.exists
}
