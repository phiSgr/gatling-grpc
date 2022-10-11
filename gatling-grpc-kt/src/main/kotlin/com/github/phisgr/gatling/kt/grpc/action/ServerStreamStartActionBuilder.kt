package com.github.phisgr.gatling.kt.grpc.action

import com.github.phisgr.gatling.grpc.action.ServerStreamStartActionBuilder as ServerStreamStartActionBuilderS

class ServerStreamStartActionBuilder<Req, Res>(private val wrapped: ServerStreamStartActionBuilderS<Req, Res>) :
    StreamStartBuilder<
        ServerStreamStartActionBuilder<Req, Res>,
        Req,
        Res, ServerStreamStartActionBuilderS<Req, Res>>() {

    override fun asScala(): ServerStreamStartActionBuilderS<Req, Res> = wrapped

    @JvmSynthetic
    override fun wrap(wrapped: ServerStreamStartActionBuilderS<Req, Res>): ServerStreamStartActionBuilder<Req, Res> =
        ServerStreamStartActionBuilder(wrapped)
}
