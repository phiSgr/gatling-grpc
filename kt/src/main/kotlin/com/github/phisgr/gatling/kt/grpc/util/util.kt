@file:Suppress("UNCHECKED_CAST", "NOTHING_TO_INLINE")

package com.github.phisgr.gatling.kt.grpc.util

import com.github.phisgr.gatling.generic.check.ResponseExtract
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
import scala.collection.immutable.Seq
import scala.util.control.NonFatal
import java.util.function.Function

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
    inline fun <reified T> extract(crossinline f: (Res) -> T?): CheckBuilder.Find<T> =
        CheckBuilder.Find.Default<ResponseExtract?, Res, T, T>(
            Predef.extract(toScalaOptionExpression(f)),
            object : ResponseExtract, CheckBuilder.CheckType {},
            T::class.java,
            Function.identity()
        )

    inline fun <reified T> extractMultiple(crossinline f: (Res) -> List<T>?): CheckBuilder.MultipleFind<T> =
        CheckBuilder.MultipleFind.Default(
            Predef.extractMultiple(toScalaSeqOptionExpression(f)),
            object : ResponseExtract, CheckBuilder.CheckType {},
            T::class.java,
            Function.identity()
        )
}


/**
 * Inline version of [io.gatling.javaapi.core.internal.Expressions.validation]
 */
inline fun <T> validation(f: () -> T): Validation<T> = safely { Success.apply(f()) }

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

inline fun <Res, T> toScalaOptionExpression(crossinline f: (Res) -> T?): Function1<Res, Validation<Option<T>>> =
    toScalaExpression { Option.apply(f(it)) }

inline fun <Res, T> toScalaExpression(crossinline f: (Res) -> T): Function1<Res, Validation<T>> =
    Function1 { res: Res -> validation { f(res) } }

inline fun <Res, T> toScalaSeqOptionExpression(crossinline f: (Res) -> List<T>?): Function1<Res, Validation<Option<Seq<T>?>>> =
    Function1 { res: Res -> validation { Option.apply(f(res)?.toSeq()) } }

val dummyFrom = From<Nothing>()
