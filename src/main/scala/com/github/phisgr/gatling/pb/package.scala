package com.github.phisgr.gatling

import io.gatling.core.Predef.value2Expression
import io.gatling.core.session._
import scalapb.lenses.{Lens, Mutation, Updatable}

package object pb {

  implicit class EpxrLens[A, B](val l: Lens[A, B]) extends AnyVal {
    def :~(e: Expression[B]): Expression[Mutation[A]] = e.map(l := _)
  }

  implicit class ExprUpdatable[A <: Updatable[A]](val e: Expression[A]) extends AnyVal {
    def updateExpr(mEs: (Lens[A, A] => Expression[Mutation[A]])*): Expression[A] = {
      val mutationExprs = mEs.map(_.apply(Lens.unit))

      s =>
        mutationExprs.foldLeft(e(s)) { (aVal, mExpr) =>
          for {
            a <- aVal
            m <- mExpr(s)
          } yield m(a)
        }
    }
  }

  implicit def value2ExprUpdatable[A <: Updatable[A]](e: A): ExprUpdatable[A] = new ExprUpdatable(value2Expression(e))

}
