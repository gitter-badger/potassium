package com.lynbrookrobotics.potassium

import org.scalacheck.Prop.forAll

import org.scalatest.FunSuite
import org.scalatest.prop.Checkers._

class SignalTest extends FunSuite {
  test("Constant value signal") {
    check(forAll { v: AnyVal =>
      val signal = Signal.constant(v)

      signal.get == v
    })
  }

  test("Variable value signal") {
    var value = 0
    val signal = Signal(value)

    assert(signal.get == 0)

    value = 2

    assert(signal.get == 2)
  }

  test("Constant value single map") {
    val signal = Signal.constant(1).map(_ + 1)

    assert(signal.get == 2)
  }

  test("Constant value double map") {
    val signal = Signal.constant(1).map(_ + 1).map(_ + 2)

    assert(signal.get == 4)
  }

  test("Variable value single map") {
    var value = 1

    val signal = Signal(value).map(_ + 1)

    assert(signal.get == 2)

    value += 1

    assert(signal.get == 3)
  }

  test("Variable value double map") {
    var value = 1

    val signal = Signal(value).map(_ + 1).map(_ + 2)

    assert(signal.get == 4)

    value += 1

    assert(signal.get == 5)
  }

  test("Constant signal zipping") {
    val signalA = Signal.constant(1)
    val signalB = Signal.constant(2)

    val zipped = signalA.zip(signalB)

    assert(zipped.get == (1, 2))
  }

  test("Variable signal zipping") {
    var valueA = 1
    var valueB = 2

    val signalA = Signal(valueA)
    val signalB = Signal(valueB)

    val zipped = signalA.zip(signalB)

    assert(zipped.get == (1, 2))

    valueA = 2
    valueB = 4

    assert(zipped.get == (2, 4))
  }
}
