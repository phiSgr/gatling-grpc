package com.github.phisgr.gatling.grpc.check

import com.github.phisgr.gatling.generic.check.ResponseExtract
import io.gatling.core.check._

private[gatling] object ResponseMaterializers {

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
      override protected def preparer: Preparer[Res, Res] = identityPreparer
    }
}
