package com.github.phisgr.gatling.kt.javapb

import com.github.phisgr.gatling.kt.getOrThrow
import com.github.phisgr.gatling.kt.internal.validation
import com.google.protobuf.Message
import io.gatling.commons.validation.Validation
import io.gatling.javaapi.core.Session
import io.gatling.javaapi.core.internal.Expressions
import scala.Function1
import java.util.function.Function
import io.gatling.core.session.Session as SessionS


/**
 * Wraps around a Scala `Expression`, and gives a "JavaExpression"
 * See also [io.gatling.javaapi.core.internal.Expressions.expressionToJavaFunction]
 */
@PublishedApi
internal class ExpressionFunction<M : Any>(
    val asScala: Function1<SessionS, Validation<M>>,
) : Function<Session, M> {
    // ideally unused, because our code is aware of this type
    // and get the unwrapped asScala value instead.
    override fun apply(session: Session): M = asScala.apply(session.asScala()).getOrThrow()
}

fun <M> Function<Session, M>.asScala(): Function1<SessionS, Validation<M>> = when (this) {
    is ExpressionFunction<*> ->
        @Suppress("UNCHECKED_CAST")
        this.asScala as Function1<SessionS, Validation<M>>
    else -> Expressions.javaFunctionToExpression(this)
}

// We want that the payload function can be a stand-alone object.
// If this returns a Kotlin function `(Session) -> M`,
// the inline functions cannot inspect it, and have to generate wrapper objects.
// This defeats the purpose - to directly use a `scala.Function1`.
// So this returns a Java `Function`, instead of a Kotlin one.
// The overloads that take a Java `Function` can then inspect and unwrap.
@JvmSynthetic
/**
 * ```
 *  fromSession(YourMessage::newBuilder) { session ->
 *    ...
 *    build()
 *  }
 * ```
 * is mostly equivalent to
 * ```
 * { session ->
 *   YourMessage.newBuilder().apply {
 *     ...
 *   }.build()
 * }
 * ```
 */
inline fun <M : Message, B : Message.Builder> fromSession(
    crossinline newBuilder: () -> B,
    crossinline f: B.(Session) -> M,
): Function<Session, M> {
    return ExpressionFunction { session ->
        validation {
            val s = Session(session)
            val builder = newBuilder()
            builder.f(s)
        }
    }
}
