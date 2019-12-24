package com.github.phisgr.gatling.javapbintellij

import org.jetbrains.plugins.scala.lang.macros.evaluator.{MacroContext, MacroImpl, ScalaMacroTypeable}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.types.{ScParameterizedType, ScType}

class MessageBuilderTyper extends ScalaMacroTypeable {
  private val PackageName = "com.github.phisgr.gatling.javapb"
  private val EvidenceType = s"_root_.$PackageName.BuilderEvidence"

  override def checkMacro(macros: ScFunction, context: MacroContext): Option[ScType] = {
    context.expectedType
      .collect {
        case t: ScParameterizedType if t.canonicalText.startsWith(EvidenceType) => t
      }
      .flatMap { t =>
        val typeString = t.typeArguments.head.canonicalText
        ScalaPsiElementFactory.createTypeFromText(
          s"$EvidenceType[$typeString, $typeString.Builder]", context.place, null)
      }
  }

  override val boundMacro: Seq[MacroImpl] = List(MacroImpl("builderEvidence", PackageName))
}
