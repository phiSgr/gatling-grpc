package com.github.phisgr.gatling.grpc.stream

import io.gatling.core.session.Session

trait TimestampExtractor[-Res] {
  /**
   * @param session         The session of the streaming branch.
   * @param message         The message received from the streaming call
   * @param streamStartTime The start time of this stream
   * @return The "start time" of this message,
   *         or [[TimestampExtractor.IgnoreMessage]] if this message should not be logged.
   */
  def extractTimestamp(session: Session, message: Res, streamStartTime: Long): Long
}

object TimestampExtractor {
  /** Return this sentinel value and the message will not be logged */
  final val IgnoreMessage = Long.MinValue

  final val Ignore: TimestampExtractor[Any] = (_, _, _) => IgnoreMessage

  /**
   * Java/Kotlin API.
   * Java and Kotlin compilers don't know that
   * [[TimestampExtractor]] is contravariant.
   */
  def ignore[T]: TimestampExtractor[T] = Ignore
}
