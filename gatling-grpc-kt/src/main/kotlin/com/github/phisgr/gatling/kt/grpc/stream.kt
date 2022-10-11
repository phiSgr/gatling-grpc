@file:JvmMultifileClass
@file:JvmName("GrpcDsl")

package com.github.phisgr.gatling.kt.grpc

import com.github.phisgr.gatling.grpc.Predef
import com.github.phisgr.gatling.grpc.stream.StreamCall
import com.github.phisgr.gatling.grpc.stream.StreamCall.*

typealias StreamEndLog = StreamCall.StreamEndLog

/**
 * See [Predef.Never]
 */
val NEVER: StreamEndLog get() = Predef.Never()

/**
 * See [Predef.ErrorOnly]
 */
val ERROR_ONLY: StreamEndLog get() = Predef.ErrorOnly()

val ALWAYS_LOG: StreamEndLog get() = `AlwaysLog$`.`MODULE$`

typealias WaitType = StreamCall.WaitType

/**
 * See [Predef.StreamEnd]
 */
val STREAM_END: WaitType get() = Predef.StreamEnd()

/**
 * See [Predef.NextMessage]
 */
val NEXT_MESSAGE: WaitType get() = Predef.NextMessage()

val NO_WAIT: WaitType get() = `NoWait$`.`MODULE$`

fun ServerStreamState.isCompleted() = this is Completed
fun ServerStreamState.isOpen() = this === `Receiving$`.`MODULE$`

fun BidiStreamState.isCompleted() = this is Completed
fun BidiStreamState.isHalfClosed() = this === `Receiving$`.`MODULE$`
fun BidiStreamState.isBothOpen() = this === `BothOpen$`.`MODULE$`

fun ClientStreamState.isCompleted() = this is Completed
fun ClientStreamState.isOpen() = this === `BothOpen$`.`MODULE$`
