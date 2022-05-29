@file:Suppress("UNCHECKED_CAST", "NOTHING_TO_INLINE")

package com.github.phisgr.gatling.kt.grpc.internal

import com.github.phisgr.gatling.generic.check.ResponseExtract
import com.github.phisgr.gatling.grpc.Predef
import com.github.phisgr.gatling.grpc.check.*
import io.gatling.javaapi.core.CheckBuilder
import io.gatling.javaapi.core.internal.Converters
import io.grpc.Metadata
import io.grpc.Status
import scala.runtime.`Null$`
import java.util.function.Function

enum class GrpcCheckType : CheckBuilder.CheckType {
    Response,
    Status,
    Trailers
}

class DefaultFind<Phantom, T, X>(
    wrapped: io.gatling.core.check.CheckBuilder.Find<Phantom, T, X>,
    checkType: GrpcCheckType,
    clazz: Class<X>,
) : CheckBuilder.Find.Default<Phantom, T, X, X>(
    wrapped,
    checkType,
    clazz,
    Function.identity()
) {
    override fun find(): CheckBuilder.Validate<X> = CheckBuilder.Validate.Default(wrapped.find(), type, javaXClass)
}


class DefaultMultipleFind<Phantom, T, X>(
    wrapped: io.gatling.core.check.CheckBuilder.MultipleFind<Phantom, T, X>,
    checkType: GrpcCheckType,
    clazz: Class<X>,
) : CheckBuilder.MultipleFind.Default<Phantom, T, X, X>(
    wrapped,
    checkType,
    clazz,
    Function.identity()
) {
    override fun find(): CheckBuilder.Validate<X> = CheckBuilder.Validate.Default(wrapped.find(), type, javaXClass)
    override fun find(occurrence: Int): CheckBuilder.Validate<X> =
        CheckBuilder.Validate.Default(wrapped.find(occurrence), type, javaXClass)

    override fun findRandom(): CheckBuilder.Validate<X> =
        CheckBuilder.Validate.Default(wrapped.findRandom(), type, javaXClass)

    // change to .transform0
    // https://github.com/gatling/gatling/commit/df79a4f43acc89c26307027dea14fa6f0a14c989
    override fun findRandom(count: Int, failIfLess: Boolean): CheckBuilder.Validate<List<X>> =
        CheckBuilder.Validate.Default(
            wrapped.findRandom(count, failIfLess).transform(Converters::toJavaList),
            type,
            List::class.java
        )

    override fun findAll(): CheckBuilder.Validate<List<X>> =
        CheckBuilder.Validate.Default(
            wrapped.findAll().transform(Converters::toJavaList),
            type,
            List::class.java
        )
}

/**
 * Kotlin does not know this class is contravariant
 */
private fun <Super, Sub : Super> GrpcCheck<Super>.upcast(): GrpcCheck<Sub> = this as GrpcCheck<Sub>

private inline fun <Res, Phantom> CheckBuilder.asScalaTyped(): io.gatling.core.check.CheckBuilder<Phantom, Res> =
    asScala() as io.gatling.core.check.CheckBuilder<Phantom, Res>

fun <Res> CheckBuilder.build(): GrpcCheck<Res> = when (val t = type()) {
    GrpcCheckType.Response -> asScalaTyped<Res, ResponseExtract>().build(Predef.resMat())
    GrpcCheckType.Status -> asScalaTyped<Status, StatusExtract>().build(Predef.statusMat()).upcast()
    GrpcCheckType.Trailers -> asScalaTyped<Metadata, TrailersExtract>().build(Predef.trailersMat()).upcast()
    else -> throw IllegalArgumentException("gRPC DSL doesn't support $t")
}

typealias GrpcStreamEnd = GrpcResponse<`Null$`>

fun CheckBuilder.buildStreamEnd(): StreamCheck<GrpcStreamEnd> = when (val t = type()) {
    GrpcCheckType.Status -> asScalaTyped<Status, StatusExtract>().build(Predef.streamStatusMat())
    GrpcCheckType.Trailers -> asScalaTyped<Metadata, TrailersExtract>().build(Predef.streamTrailersMat())
    else -> throw IllegalArgumentException("gRPC DSL doesn't support $t")
} as StreamCheck<GrpcStreamEnd>

fun <Res> CheckBuilder.buildStream(): StreamCheck<Res> = when (val t = type()) {
    GrpcCheckType.Response -> asScalaTyped<Res, ResponseExtract>().build(Predef.streamResMat())
    else -> throw IllegalArgumentException("gRPC DSL doesn't support $t")
}
