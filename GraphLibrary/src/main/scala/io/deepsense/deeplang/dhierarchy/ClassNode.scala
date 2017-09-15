/**
 * Copyright (c) 2015, CodiLime, Inc.
 *
 * Owner: Witold Jedrzejewski
 */

package io.deepsense.deeplang.dhierarchy

import scala.reflect.runtime.{universe => ru}

import io.deepsense.deeplang.TypeUtils

/**
 * Represents Class in DHierarchy graph.
 */
private[dhierarchy] class ClassNode(protected override val javaType: Class[_]) extends Node {

  private[dhierarchy] override def getParentJavaType(upperBoundType: ru.Type): Option[Class[_]] = {
    val parentJavaType = javaType.getSuperclass
    val parentType = TypeUtils.classToType(parentJavaType)
    if (parentType <:< upperBoundType) Some(parentJavaType) else None
  }

  private[dhierarchy] override def info: TypeInfo = {
    val parentName = if (parent.isDefined) Some(parent.get.displayName) else None
    ClassInfo(displayName, parentName, supertraits.values.map(_.displayName).toList)
  }

  override def toString: String = s"DClass($fullName)"
}

private[dhierarchy] object ClassNode {
  def apply(javaType: Class[_]): ClassNode = {
    if (TypeUtils.isAbstract(javaType))
      new ClassNode(javaType)
    else
      new ConcreteClassNode(javaType)
  }
}
