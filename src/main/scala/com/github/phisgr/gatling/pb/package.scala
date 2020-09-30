package com.github.phisgr.gatling

import io.gatling.commons.validation.{Failure, Success, Validation}
import io.gatling.core.Predef.value2Expression
import io.gatling.core.session._
import scalapb.lenses.Lens._
import scalapb.lenses.{Lens, Mutation, Updatable}

import scala.collection.GenTraversableOnce

package object pb {

  implicit class EpxrLens[A, B](val l: Lens[A, B]) extends AnyVal {
    def :~(e: Expression[B]): Expression[Mutation[A]] = e.map(l := _)
    def modifyExpr(e: Expression[B => B]): Expression[Mutation[A]] = e.map(l.modify)
  }

  // When the container type is polymorphic, type inference does not work well
  implicit class EpxrSeqLens[A, B](val l: Lens[A, Seq[B]]) extends AnyVal {
    private def ll = new EpxrSeqLikeLens(l)
    def :+~(e: Expression[B]): Expression[Mutation[A]] = ll :+~ e
    def :++~(e: Expression[GenTraversableOnce[B]]): Expression[Mutation[A]] = ll :++~ e
    def foreachExpr(f: Lens[B, B] => Expression[Mutation[B]]): Expression[Mutation[A]] = ll.foreachExpr(f)
  }

  implicit class EpxrSeqLikeLens[A, B, Coll[B] <: collection.SeqLike[B, Coll[B]]](
    val l: Lens[A, Coll[B]]
  ) extends AnyVal {
    type CBF = collection.generic.CanBuildFrom[Coll[B], B, Coll[B]]

    def :+~(e: Expression[B])(implicit cbf: CBF): Expression[Mutation[A]] =
      e.map(l :+= _)
    def :++~(e: Expression[GenTraversableOnce[B]])(implicit cbf: CBF): Expression[Mutation[A]] =
      e.map(l :++= _)
    def foreachExpr(f: Lens[B, B] => Expression[Mutation[B]])(implicit cbf: CBF): Expression[Mutation[A]] =
      f(Lens.unit).map(m => l.foreach(_ => m))
  }

  implicit class EpxrSetLens[A, B, Coll[B] <: collection.SetLike[B, Coll[B]] with Set[B]](
    val l: Lens[A, Coll[B]]
  ) extends AnyVal {
    type CBF = collection.generic.CanBuildFrom[Coll[B], B, Coll[B]]

    def :+~(e: Expression[B]): Expression[Mutation[A]] =
      e.map(l :+= _)
    def :++~(e: Expression[GenTraversableOnce[B]]): Expression[Mutation[A]] =
      e.map(l :++= _)
    def foreachExpr(f: Lens[B, B] => Expression[Mutation[B]])(implicit cbf: CBF): Expression[Mutation[A]] =
      f(Lens.unit).map(m => l.foreach(_ => m))
  }

  implicit class EpxrMapLens[A, K, V](
    val l: Lens[A, Map[K, V]]
  ) extends AnyVal {
    def :+~(e: Expression[(K, V)]): Expression[Mutation[A]] =
      e.map(l :+= _)
    def :++~(e: Expression[Iterable[(K, V)]]): Expression[Mutation[A]] =
      e.map(l :++= _)
    def foreachExpr(f: Lens[(K, V), (K, V)] => Expression[Mutation[(K, V)]]): Expression[Mutation[A]] =
      f(Lens.unit).map(m => l.foreach(_ => m))
    def foreachValueExpr(f: Lens[V, V] => Expression[Mutation[V]]): Expression[Mutation[A]] =
      f(Lens.unit).map(m => l.foreachValue(_ => m))
  }

  implicit class ExprUpdatable[A <: Updatable[A]](val e: Expression[A]) extends AnyVal {
    def updateExpr(mEs: (Lens[A, A] => Expression[Mutation[A]])*): Expression[A] = {
      val mutationExprs = mEs.map(_.apply(Lens.unit)).toArray
      val size = mutationExprs.length

      { s =>
        e(s) match {
          case Success(a) =>
            var result = a
            var i = 0
            var ret: Validation[A] = null
            do {
              if (i < size) {
                mutationExprs(i)(s) match {
                  case Success(mutation) =>
                    result = mutation(result)
                    i += 1
                  case f@Failure(_) =>
                    ret = f
                }
              } else {
                ret = Success(result)
              }
            } while (ret eq null)
            ret
          case f@Failure(_) => f
        }
      }
    }
  }

  implicit def value2ExprUpdatable[A <: Updatable[A]](e: A): ExprUpdatable[A] = new ExprUpdatable(value2Expression(e))

}
