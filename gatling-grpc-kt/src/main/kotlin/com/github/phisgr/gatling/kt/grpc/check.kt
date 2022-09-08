@file:JvmMultifileClass
@file:JvmName("GrpcDsl")

package com.github.phisgr.gatling.kt.grpc

import com.github.phisgr.gatling.grpc.Predef
import com.github.phisgr.gatling.kt.grpc.internal.GrpcCheckType
import com.github.phisgr.gatling.kt.grpc.internal.dummyFrom
import com.github.phisgr.gatling.kt.internal.*
import io.gatling.javaapi.core.CheckBuilder
import io.grpc.Metadata
import io.grpc.Status
import net.jodah.typetools.TypeResolver
import java.util.function.Function

/**
 * Allows defining extraction [CheckBuilder]s outside of
 * the [From] context in [ActionCheckBuilder.check].
 * See also [From.extract].
 */
inline fun <Res, reified T> extract(crossinline f: (Res) -> T?): CheckBuilder.Find<T> =
    (dummyFrom as From<Res>).extract { f(it) }

/**
 * Java API to define extraction [CheckBuilder]s.
 * This resolves the [Class] of the extracted type [X] from the [Function] object.
 *
 * See the other overload if it does not work as expected.
 */
fun <Res, X> extract(f: Function<Res, X?>): CheckBuilder.Find<X> {
    val xClass = TypeResolver.resolveRawArguments(Function::class.java, f.javaClass)[1]
    @Suppress("UNCHECKED_CAST")
    return extract(f, xClass as Class<X>)
}

/**
 * Java API to define extraction [CheckBuilder]s.
 */
fun <Res, X> extract(f: Function<Res, X?>, xClass: Class<X>): CheckBuilder.Find<X> {
    return extract(
        GrpcCheckType.Response,
        "grpcResponse",
        xClass,
        toScalaOptionF { res: Res -> f.apply(res) }
    )
}

/**
 * Allows defining extraction [CheckBuilder]s outside of
 * the [From] context in [ActionCheckBuilder.check].
 * See also [From.extractMultiple].
 */
inline fun <Res, reified T> extractMultiple(crossinline f: (Res) -> List<T>?): CheckBuilder.MultipleFind<T> =
    (dummyFrom as From<Res>).extractMultiple { f(it) }

/**
 * Java API to define extraction [CheckBuilder]s.
 */
fun <Res, X> extractMultiple(f: Function<Res, List<X>?>, clazz: Class<X>): CheckBuilder.MultipleFind<X> =
    extractMultiple(
        GrpcCheckType.Response,
        "grpcResponse",
        clazz,
        toScalaSeqOptionF { res: Res -> f.apply(res) }
    )


@get:JvmName("statusCode")
val statusCode: CheckBuilder.Find<Status.Code> = CheckBuilder.Find.Default(
    Predef.statusCode(),
    GrpcCheckType.Status,
    Status.Code::class.java,
    null,
)

@get:JvmName("statusDescription")
val statusDescription: CheckBuilder.Find<String> = CheckBuilder.Find.Default(
    Predef.statusDescription(),
    GrpcCheckType.Status,
    String::class.java,
    null,
)

inline fun <reified T> trailer(key: Metadata.Key<T>): CheckBuilder.MultipleFind<T> =
    trailer(key, T::class.java)

/**
 * Java API to check trailer values.
 * [clazz] is for resolving equality and ordering in [io.gatling.javaapi.core.internal.Comparisons].
 */
fun <T> trailer(key: Metadata.Key<T>, clazz: Class<T>): CheckBuilder.MultipleFind<T> =
    CheckBuilder.MultipleFind.Default(
        Predef.trailer(key),
        GrpcCheckType.Trailers,
        clazz,
        null,
    )
