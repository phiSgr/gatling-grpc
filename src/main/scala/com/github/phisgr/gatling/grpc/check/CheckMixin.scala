package com.github.phisgr.gatling.grpc.check

import io.gatling.core.check.{FindCheckBuilder, MultipleFindCheckBuilder}

import scala.collection.breakOut

trait CheckMixin[Self, Check[_], Res] {
  def check(checks: Check[Res]*): Self

  // In fact they can be added to checks using .check
  // but the type Res cannot be inferred there
  def extract[X](
    f: Res => Option[X])(
    ts: (FindCheckBuilder[ResponseExtract, Res, X] => Check[Res])*
  ): Self = {
    val e = ResponseExtract.extract(f)
    check(mapToList(ts)(_.apply(e)): _*)
  }

  def extractMultiple[X](
    f: Res => Option[Seq[X]])(
    ts: (MultipleFindCheckBuilder[ResponseExtract, Res, X] => Check[Res])*
  ): Self = {
    val e = ResponseExtract.extractMultiple[Res, X](f)
    check(mapToList(ts)(_.apply(e)): _*)
  }

  private def mapToList[T, U](s: Seq[T])(f: T => U): List[U] = s.map[U, List[U]](f)(breakOut)

}
