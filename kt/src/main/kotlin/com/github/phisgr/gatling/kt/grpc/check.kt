@file:JvmName("Check")

package com.github.phisgr.gatling.kt.grpc

import com.github.phisgr.gatling.grpc.Predef
import com.github.phisgr.gatling.kt.grpc.internal.*
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

@Suppress("UNCHECKED_CAST")
fun <Res, X> extractMultiple(f: Function<Res, List<X>?>): CheckBuilder.MultipleFind<X> {
    return DefaultMultipleFind(
        Predef.extractMultiple(toScalaSeqOptionExpression { res: Res -> f.apply(res) }),
        GrpcCheckType.Response,
        // TODO: resolve class X from f
        Object::class.java as Class<X>
    )
}

@Suppress("UNCHECKED_CAST")
fun <Res, X> extract(f: Function<Res, X?>): CheckBuilder.Find<X> {
    val xClass = TypeResolver.resolveRawArguments(Function::class.java, f.javaClass)[1]
    return DefaultFind(
        Predef.extract(toScalaOptionExpression { res: Res -> f.apply(res) }),
        GrpcCheckType.Response,
        xClass as Class<X>
    )
}

@JvmField
val statusCode: CheckBuilder.Find<Status.Code> = DefaultFind(
    Predef.statusCode(),
    GrpcCheckType.Status,
    Status.Code::class.java,
)

@JvmField
val statusDescription: CheckBuilder.Find<String> = DefaultFind(
    Predef.statusDescription(),
    GrpcCheckType.Status,
    String::class.java
)

@JvmName("_KotlinTraier")
inline fun <reified T> trailer(key: Metadata.Key<T>): CheckBuilder.MultipleFind<T> =
    trailer(key, T::class.java)

/**
 * [clazz] is for resolving equality and ordering in [io.gatling.javaapi.core.internal.Comparisons].
 */
fun <T> trailer(key: Metadata.Key<T>, clazz: Class<T>): CheckBuilder.MultipleFind<T> =
    DefaultMultipleFind(
        Predef.trailer(key),
        GrpcCheckType.Trailers,
        clazz,
    )
