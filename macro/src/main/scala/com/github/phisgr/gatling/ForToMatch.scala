package com.github.phisgr.gatling

import scala.reflect.macros.blackbox

// This is fun, but is not general enough.
// Only for internal use, where the generated code and bytecode are inspected.
object ForToMatch {
  def toMatch(c: blackbox.Context)(tree: c.Tree): c.Tree = {
    import c.universe._

    def matchValidation(v: c.Tree, name: TermName, result: c.Tree) = {
      q"""($v) match {
        case io.gatling.commons.validation.Success($name) => $result
        case f => f.asInstanceOf[io.gatling.commons.validation.Failure]
      }"""
    }

    tree match {
      case Apply(TypeApply(Select(v, TermName("flatMap")), _), List(Function(List(ValDef(_, name, _, _)), body))) =>
        matchValidation(v, name, toMatch(c)(body))
      case Apply(TypeApply(Select(v, TermName("map")), _), List(Function(List(ValDef(_, name, tpt, _)), body))) =>
        body match {
          case Literal(Constant(())) if tpt.tpe =:= definitions.UnitTpe => v
          case _ => matchValidation(v, name, q"io.gatling.commons.validation.Success { $body }")
        }
      case tree =>
        c.warning(c.enclosingPosition, s"Early stopping with ${tree.getClass}:\n$tree")
        tree
    }
  }
  def impl(c: blackbox.Context)(tree: c.Tree): c.Tree = {
    import c.universe._
    val (wrapping, expr) = tree match {
      case Block(statements, expr) => (Block(statements, _: c.Tree), expr)
      case expr => ( { x: c.Tree => x }, expr)
    }
    val transformed = wrapping(toMatch(c)(c.untypecheck(expr)))
    println(s"transformed:\n$transformed")
    transformed
  }
}
