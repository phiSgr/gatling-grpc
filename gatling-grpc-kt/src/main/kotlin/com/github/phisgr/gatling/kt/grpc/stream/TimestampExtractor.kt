package com.github.phisgr.gatling.kt.grpc.stream

import io.gatling.javaapi.core.Session
import com.github.phisgr.gatling.grpc.stream.TimestampExtractor as TimestampExtractorS
import io.gatling.core.session.Session as SessionS

@FunctionalInterface
fun interface TimestampExtractor<in Res> : TimestampExtractorS<@UnsafeVariance Res> {
    companion object {
        @JvmField
        val IGNORE: TimestampExtractor<Any?> = TimestampExtractor { _, _, _ -> IGNORE_MESSAGE }

        /**
         * See [TimestampExtractorS.IgnoreMessage]
         */
        @JvmField
        val IGNORE_MESSAGE = TimestampExtractorS.IgnoreMessage()
    }

    /**
     * See [TimestampExtractorS.extractTimestamp]
     */
    fun extractTimestamp(session: Session, message: Res, streamStartTime: Long): Long

    override fun extractTimestamp(session: SessionS, message: Res, streamStartTime: Long): Long =
        extractTimestamp(Session(session), message, streamStartTime)
}
