package com.github.phisgr.gatling.grpc.protocol

import com.typesafe.scalalogging.LazyLogging
import io.grpc.stub.StreamObserver

class GrpcResponseObserver[Resp] extends StreamObserver[Resp] with LazyLogging {

  override def onNext(value: Resp): Unit = {
    logger.debug(s"Response message: ${value.toString}")
  }

  override def onError(t: Throwable): Unit = {
    logger.debug(s"Error on client ${t.getMessage}", t)
  }

  override def onCompleted(): Unit = {
    logger.debug("Complete response stream")
  }
}
