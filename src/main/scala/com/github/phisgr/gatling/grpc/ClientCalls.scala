package com.github.phisgr.gatling.grpc

import io.grpc.{ClientCall, Metadata}

object ClientCalls {
  /**
   * Adapted from [[io.grpc.stub.ClientCalls.asyncUnaryRequestCall]]
   */
  def unaryCall[ReqT, RespT](
    call: ClientCall[ReqT, RespT],
    headers: Metadata,
    req: ReqT,
    responseListener: ClientCall.Listener[RespT],
    streamingResponse: Boolean
  ): Unit = {
    call.start(responseListener, headers)
    // Initially ask for two responses from flow-control so that if a misbehaving server sends
    // more than one responses, we can catch it and fail it in the listener.
    if(!streamingResponse)
      call.request(2)
    else
      call.request(1)

    try {
      call.sendMessage(req)
      call.halfClose()
    } catch {
      case e: RuntimeException =>
        throw Reflections.cancelThrow(call, e)
      case e: Error =>
        throw Reflections.cancelThrow(call, e)
    }
  }
}
