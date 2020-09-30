package com.github.phisgr.gatling.grpc.check

import com.github.phisgr.gatling.grpc.check.GrpcResponse.GrpcStreamEnd
import io.gatling.commons.validation.SuccessWrapper
import io.gatling.core.Predef.value2Expression
import io.gatling.core.check._
import io.grpc.Status

private[gatling] object StatusExtract {

  val StatusDescription: FindCheckBuilder[StatusExtract, Status, String] = new DefaultFindCheckBuilder(
    extractor = new FindExtractor[Status, String](
      name = "grpcStatusDescription",
      extractor = status => Option(status.getDescription).success
    ),
    displayActualValue = true
  )

  val StatusCode: FindCheckBuilder[StatusExtract, Status, Status.Code] = new DefaultFindCheckBuilder(
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

  object StreamMaterializer extends CheckMaterializer[StatusExtract, StreamCheck[GrpcStreamEnd], GrpcStreamEnd, Status](
    specializer = StreamCheck(_, GrpcCheck.Status)
  ) {
    override protected def preparer: Preparer[GrpcStreamEnd, Status] = _.status.success
  }

  private val isOk = StatusCode.find.is(Status.Code.OK)
  val DefaultCheck: GrpcCheck[Any] = isOk.build(Materializer)
  val DefaultStreamCheck: StreamCheck[GrpcStreamEnd] = isOk.build(StreamMaterializer)

}

// phantom type for implicit materializer resolution
trait StatusExtract
