package com.github.phisgr.gatling.kt.javapb

import com.github.phisgr.gatling.kt.grpc.internal.validation
import com.google.protobuf.Message
import io.gatling.commons.validation.Validation
import io.gatling.javaapi.core.Session
import io.gatling.javaapi.core.internal.Expressions
import scala.Function1
import java.util.function.Function
import io.gatling.core.session.Session as SessionS

/**
 * Method references in Kotlin can only be inferred to this type.
 * but in Java they can conform to this Kotlin type.
 */
typealias KotlinFunction<T, R> = (T) -> R

class Mutation<B : Message.Builder, Field>(
    private val setter: KotlinFunction<B, KotlinFunction<Field, Any>>,
    private val value: Function<Session, Field>,
) {
    fun mutate(builder: B, session: Session) {
        setter(builder)(value.apply(session))
    }
}

class MessageUpdater<M : Message, B : Message.Builder>(
    private val message: Function<Session, M>,
    private val mutations: List<Mutation<B, *>>,
) : Function<Session, M> {
    /**
     * Pipes the dynamic values from [Session] to the message builder.
     *
     * `.update({ it::setField }, { it.getString("attribute") })` in Kotlin
     * `.update(b -> b::setField, session -> session.getString("attribute"))` in Java
     */
    fun <Field> update(
        setter: KotlinFunction<B, KotlinFunction<Field, Any>>,
        v: Function<Session, Field>,
    ): MessageUpdater<M, B> =
        MessageUpdater(message, mutations + Mutation(setter, v))

    /**
     * Pipes the dynamic values from [Session] to the message builder.
     *
     * `.update({ it::setField }, "#{attribute}")` in Kotlin
     * `.update(b -> b::setField, "#{attribute}")` in Java
     */
    fun <Field> update(
        setter: KotlinFunction<B, KotlinFunction<Field, Any>>,
        el: String,
        clazz: Class<Field>,
    ): MessageUpdater<M, B> {
        val v = Expressions.expressionToJavaFunction(
            Expressions.toExpression<Field>(el, clazz)
        )
        return MessageUpdater(message, mutations + Mutation(setter, v))
    }

    @JvmName("_KtUpdate")
    inline fun <reified Field> update(
        noinline setter: KotlinFunction<B, KotlinFunction<Field, Any>>,
        el: String,
    ): MessageUpdater<M, B> =
        update(setter, el, Field::class.java)

    @Suppress("UNCHECKED_CAST")
    override fun apply(session: Session): M {
        val builder = message.apply(session).toBuilder() as B
        mutations.forEach { it.mutate(builder, session) }
        return builder.build() as M
    }

    fun asScala(): Function1<SessionS, Validation<M>> {
        @Suppress("UNCHECKED_CAST")
        return Function1 { sessionS ->
            val session = Session(sessionS)
            validation {
                val builder = message.apply(session).toBuilder() as B
                mutations.forEach { it.mutate(builder, session) }
                builder.build() as M
            }
        }
    }

    companion object {

        /**
         * In Java protobuf there is no type-level linkage between a message type and its builder.
         * To help type inference, supply a lambda that calls `M#toBuilder`.
         *
         * `.updateWith { it.toBuilder() }` in Kotlin
         * `.updateWith(m -> m.toBuilder)` in Java
         */
        @JvmStatic
        fun <M : Message, B : Message.Builder> Function<Session, M>.updateWith(@Suppress("UNUSED_PARAMETER") toBuilder: Function<M, B>) =
            MessageUpdater<M, B>(this, emptyList())

        @JvmStatic
        fun <M : Message, B : Message.Builder> M.updateWith(@Suppress("UNUSED_PARAMETER") toBuilder: Function<M, B>) =
            MessageUpdater<M, B>({ this }, emptyList())

    }
}
