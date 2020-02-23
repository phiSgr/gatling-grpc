package com.github.phisgr.gatling.grpc.check

import io.gatling.commons.validation.SuccessWrapper
import io.gatling.core.Predef.value2Expression
import io.gatling.core.check._
import io.grpc.Status

private[gatling] object StatusExtract {

  val StatusDescription: ValidatorCheckBuilder[StatusExtract, Status, String] = ValidatorCheckBuilder(
    extractor = new FindExtractor[Status, String](
      name = "grpcStatusDescription",
      extractor = status => Option(status.getDescription).success
    ),
    displayActualValue = true
  )

  val StatusCode: ValidatorCheckBuilder[StatusExtract, Status, Status.Code] = ValidatorCheckBuilder(
    extractor = new FindExtractor[Status, Status.Code](
      name = "grpcStatusCode",
      extractor = status => Some(status.getCode).success
    ),
    displayActualValue = true
  )

  object Materializer extends CheckMaterializer[StatusExtract, GrpcCheck[Any], GrpcResponse[Any], Status](
    specializer = GrpcCheck(_, GrpcCheck.Status)
  ) {
    override protected def preparer: Preparer[GrpcResponse[Any], Status] = _.status.success
  }

  val DefaultCheck: GrpcCheck[Any] = StatusCode.is(value2Expression(Status.Code.OK)).build(Materializer)

}

// phantom type for implicit materializer resolution
trait StatusExtract
