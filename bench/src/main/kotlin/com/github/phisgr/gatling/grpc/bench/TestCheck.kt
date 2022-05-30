package com.github.phisgr.gatling.grpc.bench

import io.gatling.commons.validation.Failure
import io.gatling.core.check.`Check$`
import io.gatling.core.session.Session
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.Fork
import org.openjdk.jmh.annotations.OutputTimeUnit
import scala.Option
import scala.Tuple2
import java.util.concurrent.TimeUnit

// JVM args from io.gatling.sbt.utils.PropertyUtils.DefaultJvmArgs
@Fork(jvmArgsAppend = ["-XX:MaxInlineLevel=20", "-XX:MaxTrivialSize=12"])
@OutputTimeUnit(TimeUnit.MILLISECONDS)
open class TestCheck {

    @Benchmark
    fun javaCheck(): Tuple2<Session, Option<Failure>> = `Check$`.`MODULE$`.check(
        res(),
        session,
        javaCheckList,
        null
    )

    @Benchmark
    fun kotlinElCheck(): Tuple2<Session, Option<Failure>> = `Check$`.`MODULE$`.check(
        res(),
        session,
        kotlinElCheckList,
        null
    )

    @Benchmark
    fun kotlinPlainCheck(): Tuple2<Session, Option<Failure>> = `Check$`.`MODULE$`.check(
        res(),
        session,
        kotlinPlainCheckList,
        null
    )

    @Benchmark
    fun kotlinWrappedCheck(): Tuple2<Session, Option<Failure>> = `Check$`.`MODULE$`.check(
        res(),
        session,
        kotlinWrappedCheckList,
        null
    )

    @Benchmark
    fun scalaCheckWithImplicit(): Tuple2<Session, Option<Failure>> = `Check$`.`MODULE$`.check(
        res(),
        session,
        scalaWithImplicitCheckList,
        null
    )

    @Benchmark
    fun scalaBaselineCheck(): Tuple2<Session, Option<Failure>> = `Check$`.`MODULE$`.check(
        res(),
        session,
        scalaBaselineCheckList,
        null
    )

    @Benchmark
    fun javaCheckIf(): Tuple2<Session, Option<Failure>> = `Check$`.`MODULE$`.check(
        res(),
        session,
        javaCheckIf,
        null
    )

    @Benchmark
    fun kotlinCheckIf(): Tuple2<Session, Option<Failure>> = `Check$`.`MODULE$`.check(
        res(),
        session,
        kotlinCheckIf,
        null
    )

    @Benchmark
    fun justParse(): Tuple2<Session, Option<Failure>> = `Check$`.`MODULE$`.check(
        res(),
        session,
        noCheck,
        null
    )
}
