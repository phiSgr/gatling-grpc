@file:JvmName("Check")

package com.github.phisgr.gatling.kt.grpc

import com.github.phisgr.gatling.generic.check.ResponseExtract
import com.github.phisgr.gatling.grpc.Predef
import com.github.phisgr.gatling.grpc.check.StatusExtract
import com.github.phisgr.gatling.grpc.check.TrailersExtract
import com.github.phisgr.gatling.kt.grpc.util.From
import com.github.phisgr.gatling.kt.grpc.util.dummyFrom
import com.github.phisgr.gatling.kt.grpc.util.toScalaOptionExpression
import com.github.phisgr.gatling.kt.grpc.util.toScalaSeqOptionExpression
import io.gatling.javaapi.core.CheckBuilder
import io.grpc.Metadata
import io.grpc.Status
import net.jodah.typetools.TypeResolver
import java.util.function.Function

@JvmName("_Ktextract")
inline fun <Res, reified T> extract(crossinline f: (Res) -> T?): CheckBuilder.Find<T> =
    (dummyFrom as From<Res>).extract { f(it) }

@JvmName("_KtextractMultiple")
inline fun <Res, reified T> extractMultiple(crossinline f: (Res) -> List<T>?): CheckBuilder.MultipleFind<T> =
    (dummyFrom as From<Res>).extractMultiple { f(it) }

fun <Res, X> extractMultiple(f: Function<Res, List<X>?>): CheckBuilder.MultipleFind<X> {
    val xClass = TypeResolver.resolveRawArguments(Function::class.java, f.javaClass)[1]
    return CheckBuilder.MultipleFind.Default(
        Predef.extractMultiple(toScalaSeqOptionExpression { res: Res -> f.apply(res) }),
        object : ResponseExtract, CheckBuilder.CheckType {},
        xClass,
        Function.identity()
    )
}

fun <Res, T> extract(f: Function<Res, T?>): CheckBuilder.Find<T> {
    val xClass = TypeResolver.resolveRawArguments(Function::class.java, f.javaClass)[1]
    return CheckBuilder.Find.Default(
        Predef.extract(toScalaOptionExpression { res: Res -> f.apply(res) }),
        object : ResponseExtract, CheckBuilder.CheckType {},
        xClass,
        Function.identity()
    )
}

@JvmField
val statusCode: CheckBuilder.Find<Status.Code> = CheckBuilder.Find.Default(
    Predef.statusCode(),
    object : StatusExtract, CheckBuilder.CheckType {},
    Status.Code::class.java,
    Function.identity()
)

@JvmField
val statusDescription: CheckBuilder.Find<String> = CheckBuilder.Find.Default(
    Predef.statusDescription(),
    object : StatusExtract, CheckBuilder.CheckType {},
    String::class.java,
    Function.identity()
)

@JvmName("_KotlinTraier")
inline fun <reified T> trailer(key: Metadata.Key<T>): CheckBuilder.MultipleFind<T> =
    trailer(key, T::class.java)

/**
 * [clazz] is for resolving equality and ordering in [io.gatling.javaapi.core.internal.Comparisons].
 */
fun <T> trailer(key: Metadata.Key<T>, clazz: Class<T>): CheckBuilder.MultipleFind<T> =
    CheckBuilder.MultipleFind.Default(
        Predef.trailer(key),
        object : TrailersExtract, CheckBuilder.CheckType {},
        clazz,
        Function.identity()
    )
