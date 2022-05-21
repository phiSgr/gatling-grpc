package com.github.phisgr.gatling.kt.grpc

import com.github.phisgr.gatling.kt.javapb.MessageUpdater.Companion.updateWith
import com.google.protobuf.DescriptorProtos.FileOptions
import io.gatling.javaapi.core.*
import io.gatling.javaapi.core.CoreDsl.*
import io.grpc.CallOptions
import io.grpc.ManagedChannelBuilder
import io.grpc.MethodDescriptor
import java.util.concurrent.TimeUnit

private class Drafts : Simulation() { // 3

    val asdf = grpc(ManagedChannelBuilder.forTarget(""))

    fun zxcv() = grpc("")
        .rpc(MethodDescriptor.newBuilder<FileOptions, String>(null, null).build())
        .payload("#asdf")
        .check(
            { extract { it.length }.shouldBe(1) },
            { extractMultiple { it.split(' ') }.find(2).shouldBe("asdf") },
            { statusDescription.isNull },
        )
        .checkIf { wrapped, session ->
            wrapped.status().isOk && session.isFailed
        }.then(
            { extract { it.length }.shouldBe(1) },
            { extractMultiple { it.split(' ') }.find(2).shouldBe("asdf") },
            { statusDescription.isNull },
        )
        .checkIf { session -> session.userId() == 0L }.then(
            { extract { it.length }.shouldBe(1) },
            { extractMultiple { it.split(' ') }.find(2).shouldBe("asdf") },
            { statusDescription.isNull },
        )

    fun qwer() = grpc("")
        .rpc(MethodDescriptor.newBuilder<String, String>(null, null).build())
        .payload { session: Session -> "${session.userId()}" }
        .check(
            { extract { it.length }.shouldBe(1) },
            { extractMultiple { it.split(' ') }.find(2).shouldBe("asdf") },
            { statusDescription.isNull },
        )
        .callOptions { CallOptions.DEFAULT.withDeadlineAfter(1, TimeUnit.MINUTES) }

    val scn = scenario("BasicSimulation") // 7
        .pause(5) // 10

    init {
        FileOptions.getDefaultInstance().updateWith { it.toBuilder() }
            .update({ it::setSwiftPrefix }, "#{asdf}")
    }


}
