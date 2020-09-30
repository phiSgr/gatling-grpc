package com.github.phisgr.gatling.grpc.stream

import io.gatling.core.Predef.Session

trait SessionCombiner {
  /**
   * Combines the main session and the streaming session
   *
   * @return a new session for both the stream and main flow
   */
  def reconcile(main: Session, stream: Session): Session
}


object SessionCombiner {
  val NoOp: SessionCombiner = (main: Session, _: Session) => main

  def pick(attributes: String*): SessionCombiner = (main: Session, stream: Session) => {
    attributes.foldLeft(main) { case (acc, key) =>
      stream.attributes.get(key) match {
        case Some(value) => acc.set(key, value)
        case None => acc
      }
    }
  }
}
