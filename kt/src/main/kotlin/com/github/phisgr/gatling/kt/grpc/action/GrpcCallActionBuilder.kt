package com.github.phisgr.gatling.kt.grpc.action

import com.github.phisgr.gatling.grpc.check.GrpcCheck
import com.github.phisgr.gatling.grpc.check.GrpcResponse
import com.github.phisgr.gatling.kt.grpc.internal.build
import com.github.phisgr.gatling.kt.grpc.request.CallDefinition
import io.gatling.javaapi.core.CheckBuilder
import com.github.phisgr.gatling.grpc.action.GrpcCallActionBuilder as GrpcCallActionBuilderS

class GrpcCallActionBuilder<Req, Res>(private val wrapped: GrpcCallActionBuilderS<Req, Res>) : CallDefinition<
    GrpcCallActionBuilder<Req, Res>,
    Req,
    Res,
    GrpcResponse<Res>,
    GrpcCallActionBuilderS<Req, Res>,
    GrpcCheck<Res>>() {
    override fun asScala(): GrpcCallActionBuilderS<Req, Res> = wrapped

    override fun buildCheck(check: CheckBuilder): GrpcCheck<Res> =
        check.build()

    override fun wrap(wrapped: GrpcCallActionBuilderS<Req, Res>): GrpcCallActionBuilder<Req, Res> =
        GrpcCallActionBuilder(wrapped)

}
