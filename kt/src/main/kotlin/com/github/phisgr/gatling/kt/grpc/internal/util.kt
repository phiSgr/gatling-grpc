@file:Suppress("UNCHECKED_CAST")

package com.github.phisgr.gatling.kt.grpc.internal

import com.github.phisgr.gatling.grpc.Predef
import com.github.phisgr.gatling.grpc.action.GrpcCallActionBuilder
import io.gatling.commons.util.Throwables
import io.gatling.commons.validation.Failure
import io.gatling.commons.validation.Success
import io.gatling.commons.validation.Validation
import io.gatling.javaapi.core.CheckBuilder
import io.gatling.javaapi.core.internal.Converters
import scala.Function1
import scala.Option
import scala.Some
import scala.collection.immutable.Seq
import scala.util.control.NonFatal

typealias UnknownTypeParam = Any // weird Scala

fun <T> List<T>.toSeq(): Seq<T> = Converters
    // Seems rather hard to remove redundant copy, unless we write a new wrapper class.
    // At least with this method we copy to an array, not a linked list.
    .toScalaSeq<Any>((this as List<Any>).toTypedArray())
    as Seq<T>

/**
 * Helps the `Res` type from [GrpcCallActionBuilder] flow into the extraction function `f`.
 * Allows writing `.check({ extract { it.field }.shouldBe("stuff") })`, Kotlin only.
 */
class From<out Res> {
    inline fun <reified X> extract(crossinline f: (Res) -> X?): CheckBuilder.Find<X> =
        DefaultFind(
            Predef.extract(toScalaOptionExpression(f)),
            GrpcCheckType.Response,
            X::class.java
        )

    inline fun <reified X> extractMultiple(crossinline f: (Res) -> List<X>?): CheckBuilder.MultipleFind<X> =
        DefaultMultipleFind(
            Predef.extractMultiple(toScalaSeqOptionExpression(f)),
            GrpcCheckType.Response,
            X::class.java
        )
}


/**
 * Inline version of [io.gatling.javaapi.core.internal.Expressions.validation]
 */
inline fun <T> validation(f: () -> T): Validation<T> = safely { Success.apply(f()) }

/**
 * Like [validation], but no allocation of a new [Success] object.
 */
inline fun boolValidation(f: () -> Boolean): Validation<UnknownTypeParam> = safely {
    if (f()) Validation.TrueSuccess() else Validation.FalseSuccess()
}

inline fun <T> optionalValidation(f: () -> T?): Validation<Option<T>> = safely {
    val res = f()
    val wrapped = if (res == null) Validation.NoneSuccess() else Success(Some(res))
    wrapped as Validation<Option<T>>
}

inline fun <T> safely(f: () -> Validation<T>) = try {
    f()
} catch (e: Throwable) {
    handleThrowable(e) as Validation<T>
}

fun handleThrowable(e: Throwable): Failure {
    if (NonFatal.apply(e)) {
        val message = Throwables.`PimpedException$`.`MODULE$`.`detailedMessage$extension`(e)
        return Failure.apply(message)
    } else {
        throw e
    }
}

inline fun <Res, T> toScalaExpression(crossinline f: (Res) -> T): Function1<Res, Validation<T>> =
    Function1 { res: Res -> validation { f(res) } }

inline fun <Res, T> toScalaOptionExpression(crossinline f: (Res) -> T?): Function1<Res, Validation<Option<T>>> =
    Function1 { res: Res -> optionalValidation { f(res) } }

inline fun <Res, T> toScalaSeqOptionExpression(crossinline f: (Res) -> List<T>?): Function1<Res, Validation<Option<Seq<T>>>> =
    Function1 { res: Res -> optionalValidation { f(res)?.toSeq() } }

val dummyFrom = From<Nothing>()
