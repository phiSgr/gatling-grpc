package com.github.phisgr.gatling

import com.google.protobuf.Message
import io.gatling.commons.validation.{Failure, Success, Validation}
import io.gatling.core.session.{Expression, Session, StaticValueExpression}

package object javapb {
  private type BuilderMutation[B] = (B, Session) => Failure // nullable

  private case class MutationWithExpression[B <: Message.Builder, F](setter: B => F => Any, e: Expression[F])
    extends BuilderMutation[B] {
    override def apply(builder: B, session: Session): Failure = {
      e(session) match {
        case Success(value) =>
          setter(builder)(value)
          null
        case f@Failure(_) =>
          f
      }
    }
  }

  private[javapb] class MessageExpressionUpdater[M <: Message, B <: Message.Builder](
    private[javapb] val original: Expression[M],
    private[javapb] val reversedMutations: List[BuilderMutation[B]]
  ) {
    def update[Field](setter: B => Field => Any)(value: Expression[Field]) =
      new MessageExpressionUpdater[M, B](original, MutationWithExpression(setter, value) :: reversedMutations)
  }

  implicit def toExpression[M <: Message, B <: Message.Builder](u: MessageExpressionUpdater[M, B]): Expression[M] = {
    val mutations = u.reversedMutations.reverse.toArray
    val size = mutations.length
    val original = u.original

    s =>
      original(s) match {
        case Success(message) =>
          val builder = message.toBuilder.asInstanceOf[B]
          var ret: Validation[M] = null
          var i = 0
          do {
            if (i < size) {
              ret = mutations(i)(builder, s)
              i += 1
            } else {
              ret = Success(builder.build().asInstanceOf[M])
            }
          } while (ret eq null)
          ret
        case f@Failure(_) => f
      }
  }

  type BuilderOf[M <: Message] = {def build(): M}

  implicit def message2ExprUpdater[M <: Message, B <: Message.Builder with BuilderOf[M]](e: M)(implicit evidence: BuilderEvidence[M, B]): MessageExpressionUpdater[M, B] =
    expr2exprUpdater(StaticValueExpression(e))

  implicit def expr2exprUpdater[M <: Message, B <: Message.Builder with BuilderOf[M]](e: Expression[M])(implicit evidence: BuilderEvidence[M, B]): MessageExpressionUpdater[M, B] =
    new MessageExpressionUpdater[M, B](e, Nil)

  implicit def builderEvidence[M <: Message, B]: BuilderEvidence[M, B] = macro BuilderEvidence.impl[M, B]

}
