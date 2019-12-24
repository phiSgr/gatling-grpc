package com.github.phisgr.gatling.javapb

import com.google.protobuf.Message

import scala.reflect.macros.whitebox

class BuilderEvidence[M <: Message, B <: Message.Builder with BuilderOf[M]]

object BuilderEvidence {

  def impl[M: c.WeakTypeTag, B](c: whitebox.Context): c.Tree = {
    import c.universe._

    val t = c.weakTypeOf[M].dealias
    val sym = t.typeSymbol
    if (!sym.isClass) c.abort(c.enclosingPosition, s"$sym is not a class")

    val builderT = t.member(TermName("toBuilder")).asMethod.returnType
    q"new _root_.com.github.phisgr.gatling.javapb.BuilderEvidence[$sym, $builderT]"
  }
}
