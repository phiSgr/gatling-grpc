package com.github.phisgr.gatling.javapb

import scala.reflect.macros.blackbox

object Uncurry {
  def impl[B: c.WeakTypeTag, F: c.WeakTypeTag](c: blackbox.Context)(setter: c.Tree)(value: c.Tree): c.Tree = {
    import c.universe._
    object UnTypeCheck {
      def unapply(tree: c.Tree): Option[c.Tree] = Some(c.untypecheck(tree))
    }
    object BlockOrEmpty {
      def unapply(tree: c.Tree): Option[(List[c.Tree], c.Tree)] = Some(tree match {
        case Block(stats, expr) => (stats, expr)
        case expr => (Nil, expr)
      })
    }
    // The first and second argument names of the generated lambda can both be `x$1`
    val outerName = TermName(c.freshName())
    (setter match {
      case Function(List(v1: ValDef), innerLambda) => Right(v1 -> innerLambda)
      case _ => Left(s"Unable to uncurry $setter with class ${setter.getClass}.")
    }).flatMap { case (v1, innerLambda) =>
      innerLambda match {
        case BlockOrEmpty(stats, Function(List(valueParam: ValDef), body)) =>
          Right((v1, valueParam, Block(stats, body)))
        case UnTypeCheck(Select(
        BlockOrEmpty(stats, Function(List(ValDef(_, name1, t1, _), ValDef(_, name2, t2, _)), body)),
        TermName("tupled")
        )) =>
          val innerName = TermName(c.freshName())
          Right((
            v1,
            q"val $innerName: ($t1, $t2) = $EmptyTree", // param definition
            Block(
              stats,
              q"""{
                val $name1: $t1 = $innerName._1;
                val $name2: $t2 = $innerName._2;
                $body
              }"""
            )
          ))
        case _ =>
          Left(s"Unable to uncurry inner lambda $innerLambda.")
      }
    }.map {
      case (v1@ValDef(mods, _, tpt, rhs), v2, innerBody) =>
        val renameOuter = new Transformer {
          override def transform(tree: c.Tree): c.Tree = {
            tree match {
              // if we untypecheck setter, the information for this check will be lost
              case ident@Ident(_) if ident.symbol == v1.symbol =>
                Ident(outerName)
              case _ => super.transform(tree)
            }
          }
        }
        val outerParam = ValDef(mods, outerName, tpt, rhs)
        val uncurriedSetter = Function(List(outerParam, v2), renameOuter.transform(innerBody))
        q"${c.prefix.tree}._updateUncurried($uncurriedSetter)($value)"
    } match {
      case Right(tree) =>
        c.untypecheck(tree)
      case Left(message) =>
        c.warning(c.enclosingPosition, s"$message Please consider contacting gatling-javapb author.")
        q"${c.prefix.tree}.update($setter)($value)"
    }
  }
}
