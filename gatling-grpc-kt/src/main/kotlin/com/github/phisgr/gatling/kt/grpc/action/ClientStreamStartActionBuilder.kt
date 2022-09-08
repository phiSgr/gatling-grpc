package com.github.phisgr.gatling.kt.grpc.action

import com.github.phisgr.gatling.grpc.check.GrpcCheck
import com.github.phisgr.gatling.grpc.check.GrpcResponse
import com.github.phisgr.gatling.kt.grpc.internal.build
import com.github.phisgr.gatling.kt.grpc.request.CallDefinition
import io.gatling.javaapi.core.CheckBuilder
import com.github.phisgr.gatling.grpc.action.ClientStreamStartActionBuilder as ClientStreamStartActionBuilderS

class ClientStreamStartActionBuilder<Req, Res>(private val wrapped: ClientStreamStartActionBuilderS<Req, Res>) :
    CallDefinition<
        ClientStreamStartActionBuilder<Req, Res>,
        Req,
        Res,
        GrpcResponse<Res>,
        ClientStreamStartActionBuilderS<Req, Res>,
        GrpcCheck<Res>>() {
    override fun asScala(): ClientStreamStartActionBuilderS<Req, Res> = wrapped
    override fun buildCheck(builder: CheckBuilder): GrpcCheck<Res> = builder.build()

    @JvmSynthetic
    override fun wrap(wrapped: ClientStreamStartActionBuilderS<Req, Res>): ClientStreamStartActionBuilder<Req, Res> =
        ClientStreamStartActionBuilder(wrapped)
}
