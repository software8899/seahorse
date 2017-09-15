/**
 * Copyright (c) 2015, CodiLime, Inc.
 *
 * Owner: Witold Jedrzejewski
 */

package io.deepsense.deeplang

import scala.reflect.runtime.{universe => ru}

private[deeplang] object TypeUtils {
  private val mirror = ru.runtimeMirror(getClass.getClassLoader)

  def classToType(c: Class[_]): ru.Type = mirror.classSymbol(c).toType

  def typeToClass(t: ru.Type): Class[_] = mirror.runtimeClass(t.typeSymbol.asClass)

  def symbolToType(s: ru.Symbol): ru.Type = s.asClass.toType

  def isParametrized(t: ru.Type): Boolean = t.typeSymbol.asClass.typeParams.nonEmpty

  def isAbstract(c: Class[_]): Boolean = classToType(c).typeSymbol.asClass.isAbstractClass
}
