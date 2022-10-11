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

    override fun buildCheck(builder: CheckBuilder): GrpcCheck<Res> =
        builder.build()

    @JvmSynthetic
    override fun wrap(wrapped: GrpcCallActionBuilderS<Req, Res>): GrpcCallActionBuilder<Req, Res> =
        GrpcCallActionBuilder(wrapped)

    /**
     * See [GrpcCallActionBuilderS.silent]
     */
    fun silent() = wrap(asScala().silent())
}
