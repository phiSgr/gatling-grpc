package com.github.phisgr.gatling.grpc.stream

import io.gatling.commons.validation.Validation
import io.gatling.core.action.Action
import io.gatling.core.session.Session

trait Cancellable {
  def cancel(mainSession: Session, next: Action): Unit
}

trait ClientStreamer[Req] {
  def onReq(req: Req): Validation[Unit]
}
