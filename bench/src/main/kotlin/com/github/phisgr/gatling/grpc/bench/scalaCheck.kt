package com.github.phisgr.gatling.grpc.bench

import com.github.phisgr.gatling.grpc.check.GrpcCheck
import com.github.phisgr.gatling.pb.Test
import io.gatling.commons.`Exclude$`
import io.gatling.commons.util.Equality
import io.gatling.commons.util.TypeCaster
import io.gatling.commons.validation.Success
import io.gatling.core.Predef
import scala.Function1
import scala.Some
import scala.reflect.ClassTag

private val helloExpression =
    Predef.stringToExpression(
        CheckJ.MESSAGE,
        TypeCaster.StringCaster(),
        `Exclude$`.`MODULE$`.NOT_FOR_USER_CODE(),
        ClassTag.apply(String::class.java)
    )

/**
 * Can't refer to Scala code in Kotlin files.
 *
 * Yes this is what it looks like when implicits are expanded.
 *
 * Equivalent to
 * ```scala
 * val check: GrpcCheck[Test.SimpleMessage] = extract { m: Test.SimpleMessage => m.getS.some }.is("Hello")
 * ```
 */
val scalaImplicitCheck: GrpcCheck<Test.SimpleMessage> = com.github.phisgr.gatling.grpc.Predef.extract(
    Function1 { m: Test.SimpleMessage ->
        Predef.value2Success(
            Some.apply(m.s),
            // This evidence param may increase the bytecode size and inhibit inlining
            `Exclude$`.`MODULE$`.NOT_FOR_USER_CODE()
        )
    }
)
    .find()
    .`is`(
        helloExpression,
        Equality.StringEquality()
    )
    .build(
        com.github.phisgr.gatling.grpc.Predef.resMat()
    )

val scalaBaselineCheck: GrpcCheck<Test.SimpleMessage> = com.github.phisgr.gatling.grpc.Predef.extract(
    Function1 { m: Test.SimpleMessage ->
        Success.apply(Some.apply(m.s))
    }
)
    .find()
    .`is`(helloExpression, Equality.StringEquality())
    .build(
        com.github.phisgr.gatling.grpc.Predef.resMat()
    )
