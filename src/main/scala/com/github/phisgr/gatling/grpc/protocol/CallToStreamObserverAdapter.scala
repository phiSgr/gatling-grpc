package com.github.phisgr.gatling.grpc.protocol

import javax.annotation.Nullable

import io.grpc.ClientCall
import io.grpc.stub.ClientCallStreamObserver

class CallToStreamObserverAdapter[Req, Res](
                                             private val call: ClientCall[Req, _]
                                           ) extends ClientCallStreamObserver[Req]{

  private var frozen: Boolean = _
  private var onReadyHandler: Runnable = _
  private var autoFlowControlEnabled: Boolean = true

  def getOnReadyHandler: Runnable = onReadyHandler

  def isAutoFlowControlEnabled: Boolean = autoFlowControlEnabled

  def freeze(): Unit = {
    this.frozen = true
  }

  override def onNext(value: Req): Unit = {
    call.sendMessage(value)
  }

  override def onError(t: Throwable): Unit = {
    call.cancel("Cancelled by client with StreamObserver.onError()", t)
  }

  override def onCompleted(): Unit = {
    call.halfClose()
  }

  override def isReady: Boolean = call.isReady

  override def setOnReadyHandler(onReadyHandler: Runnable): Unit = {
    if (frozen) throw new IllegalStateException("Cannot alter onReadyHandler after call started")
    this.onReadyHandler = onReadyHandler
  }

  override def disableAutoInboundFlowControl(): Unit = {
    if (frozen) throw new IllegalStateException("Cannot disable auto flow control call started")
    autoFlowControlEnabled = false
  }

  override def request(count: Int): Unit = {
    call.request(count)
  }

  override def setMessageCompression(enable: Boolean): Unit = {
    call.setMessageCompression(enable)
  }

  override def cancel(@Nullable message: String, @Nullable cause: Throwable): Unit = {
    call.cancel(message, cause)
  }
}
