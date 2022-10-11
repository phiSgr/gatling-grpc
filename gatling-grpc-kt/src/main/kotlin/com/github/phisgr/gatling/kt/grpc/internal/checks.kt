@file:Suppress("UNCHECKED_CAST")

package com.github.phisgr.gatling.kt.grpc.internal

import com.github.phisgr.gatling.generic.check.ResponseExtract
import com.github.phisgr.gatling.grpc.Predef
import com.github.phisgr.gatling.grpc.check.*
import io.gatling.javaapi.core.CheckBuilder
import io.grpc.Metadata
import io.grpc.Status
import io.gatling.core.check.CheckBuilder as CheckBuilderS

enum class GrpcCheckType : CheckBuilder.CheckType {
    Response,
    Status,
    Trailers
}

/**
 * Kotlin does not know this class is contravariant
 */
private fun <Super, Sub : Super> GrpcCheck<Super>.upcast(): GrpcCheck<Sub> = this as GrpcCheck<Sub>

private fun <Res, Phantom> CheckBuilder.asScalaTyped(): CheckBuilderS<Phantom, Res> =
    asScala() as CheckBuilderS<Phantom, Res>

fun <Res> CheckBuilder.build(): GrpcCheck<Res> = when (val t = type()) {
    GrpcCheckType.Response -> asScalaTyped<Res, ResponseExtract>().build(Predef.resMat())
    GrpcCheckType.Status -> asScalaTyped<Status, StatusExtract>().build(Predef.statusMat()).upcast()
    GrpcCheckType.Trailers -> asScalaTyped<Metadata, TrailersExtract>().build(Predef.trailersMat()).upcast()
    else -> throw IllegalArgumentException("gRPC DSL doesn't support $t")
}

typealias GrpcStreamEnd = GrpcResponse<Nothing?>

fun CheckBuilder.buildStreamEnd(): StreamCheck<GrpcStreamEnd> = when (val t = type()) {
    GrpcCheckType.Status -> asScalaTyped<Status, StatusExtract>().build(Predef.streamStatusMat())
    GrpcCheckType.Trailers -> asScalaTyped<Metadata, TrailersExtract>().build(Predef.streamTrailersMat())
    else -> throw IllegalArgumentException("gRPC DSL doesn't support $t")
} as StreamCheck<GrpcStreamEnd>

fun <Res> CheckBuilder.buildStream(): StreamCheck<Res> = when (val t = type()) {
    GrpcCheckType.Response -> asScalaTyped<Res, ResponseExtract>().build(Predef.streamResMat())
    else -> throw IllegalArgumentException("gRPC DSL doesn't support $t")
}
