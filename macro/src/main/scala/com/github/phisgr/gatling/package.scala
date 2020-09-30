package com.github.phisgr

import io.gatling.commons.validation.Validation

package object gatling {
  /**
   * Generates `match` clauses from `flatMap`/`map` calls to reduce lambda allocations,
   * which the compiler may or may not be able to optimize away.
   */
  def forToMatch[T](tree: Validation[T]): Validation[T] = macro ForToMatch.impl
}
