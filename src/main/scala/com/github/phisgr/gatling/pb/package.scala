package com.github.phisgr.gatling

import com.github.phisgr.gatling.util._
import io.gatling.commons.validation.SuccessWrapper
import io.gatling.core.Predef.value2Expression
import io.gatling.core.session._
import scalapb.lenses.{Lens, Mutation, Updatable}

package object pb {

  implicit class EpxrLens[A, B](val l: Lens[A, B]) extends AnyVal {
    def :~(e: Expression[B]): Expression[Mutation[A]] = e.map(l := _)
  }

  implicit class ExprUpdatable[A <: Updatable[A]](val e: Expression[A]) extends AnyVal {
    def updateExpr(mEs: (Lens[A, A] => Expression[Mutation[A]])*): Expression[A] = {
      type LMA = Lens[A, A] => Mutation[A]

      val mutationExprs: Seq[Expression[LMA]] = mEs.map(_.apply(Lens.unit).map(
        m => (_: Lens[A, A]) => m
      ))
      val cbfExpr = { _: Session => Seq.canBuildFrom[LMA](mutationExprs).success }

      // c.f. Future.sequence
      val mutationsExpr = mutationExprs.foldLeft(cbfExpr) {
        (builderExpr, mExpr) => builderExpr.zipWith(mExpr)(_ += _)
      }.map(_.result())

      e.zipWith(mutationsExpr)(_.update(_: _*))

    }

  }

  implicit def value2ExprUpdatable[A <: Updatable[A]](e: A): ExprUpdatable[A] = new ExprUpdatable(value2Expression(e))

}
