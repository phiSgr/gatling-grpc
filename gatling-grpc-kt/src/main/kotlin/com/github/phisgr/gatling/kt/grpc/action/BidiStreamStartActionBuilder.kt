package com.github.phisgr.gatling.kt.grpc.action

import com.github.phisgr.gatling.grpc.action.BidiStreamStartActionBuilder as BidiStreamStartActionBuilderS

class BidiStreamStartActionBuilder<Req, Res>(private val wrapped: BidiStreamStartActionBuilderS<Req, Res>) :
    StreamStartBuilder<
        BidiStreamStartActionBuilder<Req, Res>,
        Req,
        Res,
        BidiStreamStartActionBuilderS<Req, Res>>() {

    override fun asScala(): BidiStreamStartActionBuilderS<Req, Res> = wrapped

    @JvmSynthetic
    override fun wrap(wrapped: BidiStreamStartActionBuilderS<Req, Res>): BidiStreamStartActionBuilder<Req, Res> =
        BidiStreamStartActionBuilder(wrapped)
}
