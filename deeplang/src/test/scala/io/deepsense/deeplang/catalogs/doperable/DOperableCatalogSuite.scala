/**
 * Copyright (c) 2015, CodiLime, Inc.
 *
 * Owner: Witold Jedrzejewski
 */

package io.deepsense.deeplang.catalogs.doperable

import scala.reflect.runtime.{universe => ru}

import org.scalatest.{FunSuite, Matchers}

import io.deepsense.deeplang.DOperable
import io.deepsense.deeplang.catalogs.doperable.exceptions._
import io.deepsense.deeplang.doperables.Report

object SampleInheritance {
  trait T1 extends DOperable
  trait T2 extends T1
  trait T3 extends T1
  trait T extends DOperable
  abstract class A extends T3
  class B extends A with T {
    override def report: Report = ???
    override def equals(any: Any) = any.isInstanceOf[B]
  }
  class C extends A with T2 {
    override def report: Report = ???
    override def equals(any: Any) = any.isInstanceOf[C]
  }
}

object Parametrized {
  trait T[T] extends DOperable
  abstract class A[T] extends DOperable {
    override def report: Report = ???
  }
  class B extends A[Int]
}

object Constructors {
  class NotParameterLess(val i: Int) extends DOperable {
    override def report: Report = ???
  }
  class AuxiliaryParameterless(val i: Int) extends DOperable {
    def this() = this(1)
    override def report: Report = ???
  }
  class WithDefault(val i: Int = 1) extends DOperable {
    override def report: Report = ???
  }
}

object TraitInheritance {
  class C1 extends DOperable {
    override def report: Report = ???
  }
  trait T1 extends C1
  trait T2 extends T1
  class C2 extends T2

  trait S1 extends DOperable
  trait S2 extends DOperable
  class A1 extends DOperable {
    override def report: Report = ???
  }
  trait S3 extends A1 with S1 with S2
}

class DOperableCatalogSuite extends FunSuite with Matchers {

  def testGettingSubclasses[T <: DOperable : ru.TypeTag](
      h: DOperableCatalog, expected: DOperable*): Unit = {
    h.concreteSubclassesInstances[T] should contain theSameElementsAs expected
  }

  test("Getting concrete subclasses instances") {
    import SampleInheritance._

    val h = new DOperableCatalog
    h.registerDOperable[B]()
    h.registerDOperable[C]()

    val b = new B
    val c = new C

    def check[T <: DOperable : ru.TypeTag](expected: DOperable*) = {
      testGettingSubclasses[T](h, expected:_*)
    }

    check[T with T1](b)
    check[T2 with T3](c)
    check[B](b)
    check[C](c)
    check[A with T2](c)
    check[A with T1](b, c)
    check[A](b, c)
    check[T3](b, c)
    check[T with T2]()
  }

  test("Getting concrete subclasses instances using ru.TypeTag") {
    import SampleInheritance._
    val h = new DOperableCatalog
    h.registerDOperable[B]()
    val t = ru.typeTag[T]
    h.concreteSubclassesInstances(t) should contain theSameElementsAs List(new B)
  }

  test("Listing DTraits and DClasses") {
    import SampleInheritance._
    val h = new DOperableCatalog
    h.registerDOperable[B]()
    h.registerDOperable[C]()

    def name[T: ru.TypeTag]: String = ru.typeOf[T].typeSymbol.fullName

    val traits = (TraitDescriptor(name[DOperable], Nil)::
      TraitDescriptor(name[T2], List(name[T1]))::
      TraitDescriptor(name[T], List(name[DOperable]))::
      TraitDescriptor(name[T1], List(name[DOperable]))::
      TraitDescriptor(name[T3], List(name[T1]))::
      Nil).map(t => t.name -> t).toMap

    val classes = (ClassDescriptor(name[A], None, List(name[T3]))::
      ClassDescriptor(name[B], Some(name[A]), List(name[T]))::
      ClassDescriptor(name[C], Some(name[A]), List(name[T2]))::
      Nil).map(c => c.name -> c).toMap

    val descriptor = h.descriptor
    descriptor.traits should contain theSameElementsAs traits
    descriptor.classes should contain theSameElementsAs classes
  }

  test("Registering class extending parametrized class should produce exception") {
    intercept[ParametrizedTypeException] {
      import io.deepsense.deeplang.catalogs.doperable.Parametrized._
      val p = new DOperableCatalog
      p.registerDOperable[B]()
    }
  }

  test("Registering parametrized class should produce exception") {
    intercept[ParametrizedTypeException] {
      import io.deepsense.deeplang.catalogs.doperable.Parametrized._
      val p = new DOperableCatalog
      p.registerDOperable[A[Int]]()
    }
  }

  test("Registering parametrized trait should produce exception") {
    intercept[ParametrizedTypeException] {
      import io.deepsense.deeplang.catalogs.doperable.Parametrized._
      val p = new DOperableCatalog
      p.registerDOperable[T[Int]]()
    }
  }

  test("Registering concrete class with no parameter-less constructor should produce exception") {
    intercept[NoParameterlessConstructorInClassException] {
      import io.deepsense.deeplang.catalogs.doperable.Constructors._
      val h = new DOperableCatalog
      h.registerDOperable[NotParameterLess]()
    }
  }

  test("Registering class with constructor with default parameters should produce exception") {
    intercept[NoParameterlessConstructorInClassException] {
      import io.deepsense.deeplang.catalogs.doperable.Constructors._
      val h = new DOperableCatalog
      h.registerDOperable[WithDefault]()
    }
  }

  test("Registering class with auxiliary parameterless constructor should succeed") {
    import io.deepsense.deeplang.catalogs.doperable.Constructors._
    val h = new DOperableCatalog
    h.registerDOperable[AuxiliaryParameterless]()
  }

  test("Registering hierarchy with trait extending class should produce exception") {
    intercept[TraitInheritingFromClassException] {
      import io.deepsense.deeplang.catalogs.doperable.TraitInheritance._
      val h = new DOperableCatalog
      h.registerDOperable[C2]()
    }
  }

  test("Registering trait extending class should produce exception") {
    intercept[TraitInheritingFromClassException] {
      import io.deepsense.deeplang.catalogs.doperable.TraitInheritance._
      val h = new DOperableCatalog
      h.registerDOperable[S3]()
    }
  }
}