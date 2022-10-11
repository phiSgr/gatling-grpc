@file:Suppress("UNCHECKED_CAST")

package com.github.phisgr.gatling.kt.grpc.internal

import com.github.phisgr.gatling.kt.internal.From
import io.gatling.commons.validation.Failure
import io.gatling.commons.validation.Success
import io.gatling.commons.validation.Validation
import io.grpc.Metadata

val dummyFrom = From<Nothing>("grpcResponse", GrpcCheckType.Response)

private val EMPTY_MESSAGE = Failure.apply("")

/**
 * For a non-existing attribute, a Scala `Expression` evaluates to a [Failure],
 * which may then be ignored in [com.github.phisgr.gatling.grpc.HeaderPair.mutateMetadata].
 *
 * Translating to the Java API, a `Failure` corresponds to `throw`ing an exception.
 * But for a simple `null` value we can skip the throw and catch.
 */
fun <T> nullToFailure(value: T?, key: Metadata.Key<T>, optional: Boolean): Validation<T> = when (value) {
    null -> (if (optional) { // Failure#message is not read anyway, save an allocation
        EMPTY_MESSAGE
    } else {
        Failure.apply("Header for ${key.name()} not found.")
    }) as Validation<T>
    else -> Success.apply(value)
}
