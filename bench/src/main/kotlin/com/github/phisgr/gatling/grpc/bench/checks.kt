package com.github.phisgr.gatling.grpc.bench

import com.github.phisgr.gatling.grpc.Predef
import com.github.phisgr.gatling.grpc.check.GrpcCheck
import com.github.phisgr.gatling.grpc.check.GrpcResponse
import com.github.phisgr.gatling.grpc.check.StatusExtract
import com.github.phisgr.gatling.kt.grpc.extract
import com.github.phisgr.gatling.kt.grpc.internal.GrpcCheckType
import com.github.phisgr.gatling.kt.grpc.internal.build
import com.github.phisgr.gatling.kt.grpc.internal.toScalaOptionExpression
import com.github.phisgr.gatling.pb.Test
import io.gatling.core.check.Check
import io.gatling.core.session.Session
import io.gatling.core.session.`Session$`
import io.gatling.javaapi.core.CheckBuilder
import io.grpc.Metadata
import io.grpc.Status
import scala.collection.immutable.`$colon$colon`
import scala.collection.immutable.`Nil$`
import java.util.function.Function

private val checkWithIdentityTransform: GrpcCheck<Test.SimpleMessage> =
    CheckBuilder.Find.Default(
        Predef.extract(toScalaOptionExpression { m: Test.SimpleMessage -> m.s }),
        GrpcCheckType.Response,
        String::class.java,
        Function.identity()
    )
        .shouldBe(CheckJ.MESSAGE)
        .build()

private val checkPlain: GrpcCheck<Test.SimpleMessage> = extract { m: Test.SimpleMessage -> m.s }
    .shouldBe(CheckJ.MESSAGE)
    .build()

// compare with Scala baseline.
private val checkEl: GrpcCheck<Test.SimpleMessage> = extract { m: Test.SimpleMessage -> m.s }
    .isEL(CheckJ.MESSAGE)
    .build()

private val simpleMessageBytes = Test.SimpleMessage.newBuilder().setS(CheckJ.MESSAGE).build().toByteString()

fun res(): GrpcResponse<Test.SimpleMessage> = GrpcResponse(
    Test.SimpleMessage.parseFrom(simpleMessageBytes), Status.OK, Metadata()
)

val session: Session = `Session$`.`MODULE$`.apply(
    "Scenario",
    1L,
    null // irrelevant in this test
)

typealias Checks = scala.collection.immutable.List<Check<GrpcResponse<Test.SimpleMessage>>>

@Suppress("UNCHECKED_CAST")
private fun resolvedChecks(check: Check<GrpcResponse<Test.SimpleMessage>>) =
    `$colon$colon`(
        StatusExtract.DefaultCheck() as Check<GrpcResponse<Test.SimpleMessage>>,
        `$colon$colon`(
            check,
            `Nil$`.`MODULE$` as Checks
        ) as Checks
    ) as Checks

val kotlinPlainCheckList: Checks = resolvedChecks(checkPlain)
val kotlinElCheckList: Checks = resolvedChecks(checkEl)
val kotlinWrappedCheckList: Checks = resolvedChecks(checkWithIdentityTransform)
val javaCheckList: Checks = resolvedChecks(CheckJ.check)
val scalaWithImplicitCheckList: Checks = resolvedChecks(scalaImplicitCheck)
val scalaBaselineCheckList: Checks = resolvedChecks(scalaBaselineCheck)
