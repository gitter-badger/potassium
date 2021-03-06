package com.lynbrookrobotics.potassium.commons.drivetrain

import com.lynbrookrobotics.potassium.control.PIDConfig
import com.lynbrookrobotics.potassium.testing.ClockMocking
import com.lynbrookrobotics.potassium.units.GenericValue._
import com.lynbrookrobotics.potassium.units._
import com.lynbrookrobotics.potassium.{Component, PeriodicSignal, Signal, SignalLike}
import squants.motion.{AngularVelocity, DegreesPerSecond, MetersPerSecond, MetersPerSecondSquared}
import squants.motion._
import squants.space.{Degrees, Meters}
import squants.time.{Milliseconds, Seconds}
import squants.{Acceleration, Angle, Length, Percent, Velocity}
import org.scalatest.FunSuite

class UnicycleDriveTaskTest extends FunSuite {
  class TestDrivetrain extends UnicycleDrive {
    override type DriveSignal = UnicycleSignal
    override type DriveVelocity = UnicycleSignal

    override type Hardware = UnicycleHardware
    override type Properties = UnicycleProperties

    override protected def convertUnicycleToDrive(uni: UnicycleSignal): DriveSignal = uni

    override protected def controlMode(implicit hardware: Hardware,
                                       props: Properties): UnicycleControlMode = NoOperation

    override protected def driveClosedLoop(signal: SignalLike[DriveSignal])
                                          (implicit hardware: Hardware,
                                           props: Signal[Properties]): PeriodicSignal[DriveSignal] = signal.toPeriodic

    override type Drivetrain = Component[DriveSignal]
  }

  implicit val hardware: UnicycleHardware = new UnicycleHardware {
    override val forwardVelocity: Signal[Velocity] = Signal(MetersPerSecond(0))
    override val turnVelocity: Signal[AngularVelocity] = Signal(DegreesPerSecond(0))

    override val forwardPosition: Signal[Length] = null
    override val turnPosition: Signal[Angle] = null
  }


  test("Drive distance task sets up correct relative position and ends at target") {
    implicit val (clock, ticker) = ClockMocking.mockedClockTicker

    val drive = new TestDrivetrain

    val props = Signal.constant(new UnicycleProperties {
      override val maxForwardVelocity: Velocity = MetersPerSecond(10)
      override val maxTurnVelocity: AngularVelocity = DegreesPerSecond(10)
      override val maxAcceleration: Acceleration = FeetPerSecondSquared(15)
      override val defaultLookAheadDistance: Length = null

      override val forwardControlGains = PIDConfig(
        Percent(0) / MetersPerSecond(1),
        Percent(0) / Meters(1),
        Percent(0) / MetersPerSecondSquared(1)
      )

      override val turnControlGains = PIDConfig(
        Percent(0) / DegreesPerSecond(1),
        Percent(0) / Degrees(1),
        Percent(0) / (DegreesPerSecond(1).toGeneric / Seconds(1))
      )

      override val forwardPositionControlGains = PIDConfig(
        Percent(100) / Meters(10),
        Percent(0) / (Meters(1).toGeneric * Seconds(1)),
        Percent(0) / MetersPerSecond(1)
      )

      override val turnPositionControlGains = null
    })

    var lastAppliedSignal: UnicycleSignal = null

    val drivetrain = new Component[UnicycleSignal](Milliseconds(5)) {
      override def defaultController: PeriodicSignal[UnicycleSignal] =
        Signal.constant(UnicycleSignal(Percent(0), Percent(0))).toPeriodic

      override def applySignal(signal: UnicycleSignal): Unit = {
        lastAppliedSignal = signal
      }
    }

    var currentPosition = Meters(5)

    val hardware: UnicycleHardware = new UnicycleHardware {
      override val forwardVelocity: Signal[Velocity] = Signal(MetersPerSecond(0))
      override val turnVelocity: Signal[AngularVelocity] = Signal(DegreesPerSecond(0))

      override val forwardPosition: Signal[Length] = Signal(currentPosition)
      override val turnPosition: Signal[Angle] = null
    }

    val task = new drive.unicycleTasks.DriveDistance(
      Meters(5), Meters(0.1))(
      drivetrain,
      hardware,
      props
    )

    task.init()

    ticker(Milliseconds(5))

    assert(lastAppliedSignal.forward.toPercent == 50)

    currentPosition = Meters(7.5)

    ticker(Milliseconds(5))

    assert(lastAppliedSignal.forward.toPercent == 25)

    currentPosition = Meters(10)

    ticker(Milliseconds(5))

    assert(lastAppliedSignal.forward.toPercent == 0 && !task.isRunning)
  }

  test("Drive distance straight task sets up correct relative position and ends at target") {
    val drive = new TestDrivetrain

    val props = Signal.constant(new UnicycleProperties {
      override val maxForwardVelocity: Velocity = MetersPerSecond(10)
      override val maxTurnVelocity: AngularVelocity = DegreesPerSecond(10)
      override val maxAcceleration: Acceleration = FeetPerSecondSquared(10)
      override val defaultLookAheadDistance: Length = null

      override val forwardControlGains = PIDConfig(
        Percent(0) / MetersPerSecond(1),
        Percent(0) / Meters(1),
        Percent(0) / MetersPerSecondSquared(1)
      )

      override val turnControlGains = PIDConfig(
        Percent(0) / DegreesPerSecond(1),
        Percent(0) / Degrees(1),
        Percent(0) / (DegreesPerSecond(1).toGeneric / Seconds(1))
      )

      override val forwardPositionControlGains = PIDConfig(
        Percent(100) / Meters(10),
        Percent(0) / (Meters(1).toGeneric * Seconds(1)),
        Percent(0) / MetersPerSecond(1)
      )

      override val turnPositionControlGains = PIDConfig(
        Percent(100) / Degrees(10),
        Percent(0) / (Degrees(1).toGeneric * Seconds(1)),
        Percent(0) / DegreesPerSecond(1)
      )
    })

    var lastAppliedSignal: UnicycleSignal = null

    implicit val (clock, ticker) = ClockMocking.mockedClockTicker

    val drivetrain = new Component[UnicycleSignal](Milliseconds(5)) {
      override def defaultController: PeriodicSignal[UnicycleSignal] =
        Signal.constant(UnicycleSignal(Percent(0), Percent(0))).toPeriodic

      override def applySignal(signal: UnicycleSignal): Unit = {
        lastAppliedSignal = signal
      }
    }

    var currentPositionForward = Meters(5)
    var currentPositionTurn = Degrees(5)

    val hardware: UnicycleHardware = new UnicycleHardware {
      override val forwardVelocity: Signal[Velocity] = Signal(MetersPerSecond(0))
      override val turnVelocity: Signal[AngularVelocity] = Signal(DegreesPerSecond(0))

      override val forwardPosition: Signal[Length] = Signal(currentPositionForward)
      override val turnPosition: Signal[Angle] = Signal(currentPositionTurn)
    }

    val task = new drive.unicycleTasks.DriveDistanceStraight(
      Meters(5), Meters(0.1), Degrees(0.1), Percent(100))(
      drivetrain,
      hardware,
      props
    )

    task.init()

    ticker(Milliseconds(5))

    assert(lastAppliedSignal.forward.toPercent == 50 &&
      lastAppliedSignal.turn.toPercent == 0)

    currentPositionForward = Meters(7.5)
    currentPositionTurn = Degrees(10)

    ticker(Milliseconds(5))

    assert(lastAppliedSignal.forward.toPercent == 25 &&
      lastAppliedSignal.turn.toPercent == -50)

    currentPositionForward = Meters(10)
    currentPositionTurn = Degrees(0)

    ticker(Milliseconds(5))

    assert(lastAppliedSignal.forward.toPercent == 0 &&
      lastAppliedSignal.turn.toPercent == 50)

    currentPositionForward = Meters(10)
    currentPositionTurn = Degrees(5)

    ticker(Milliseconds(5))

    assert(lastAppliedSignal.forward.toPercent == 0 &&
      lastAppliedSignal.turn.toPercent == 0 &&
      !task.isRunning)
  }

  test("Turn angle task sets up correct relative position and ends at target") {
    val drive = new TestDrivetrain

    val props = Signal.constant(new UnicycleProperties {
      override val maxForwardVelocity: Velocity = MetersPerSecond(10)
      override val maxTurnVelocity: AngularVelocity = DegreesPerSecond(10)
      override val maxAcceleration: Acceleration = FeetPerSecondSquared(10)
      override val defaultLookAheadDistance: Length = null

      override val forwardControlGains = PIDConfig(
        Percent(0) / MetersPerSecond(1),
        Percent(0) / Meters(1),
        Percent(0) / MetersPerSecondSquared(1)
      )

      override val turnControlGains = PIDConfig(
        Percent(0) / DegreesPerSecond(1),
        Percent(0) / Degrees(1),
        Percent(0) / (DegreesPerSecond(1).toGeneric / Seconds(1))
      )

      override val forwardPositionControlGains = null

      override val turnPositionControlGains = PIDConfig(
        Percent(100) / Degrees(10),
        Percent(0) / (Degrees(1).toGeneric * Seconds(1)),
        Percent(0) / DegreesPerSecond(1)
      )
    })

    var lastAppliedSignal: UnicycleSignal = null

    implicit val (clock, ticker) = ClockMocking.mockedClockTicker

    val drivetrain = new Component[UnicycleSignal](Milliseconds(5)) {
      override def defaultController: PeriodicSignal[UnicycleSignal] =
        Signal.constant(UnicycleSignal(Percent(0), Percent(0))).toPeriodic

      override def applySignal(signal: UnicycleSignal): Unit = {
        lastAppliedSignal = signal
      }
    }

    var currentPosition = Degrees(5)

    val hardware: UnicycleHardware = new UnicycleHardware {
      override val forwardVelocity: Signal[Velocity] = Signal(MetersPerSecond(0))
      override val turnVelocity: Signal[AngularVelocity] = Signal(DegreesPerSecond(0))

      override val forwardPosition: Signal[Length] = null
      override val turnPosition: Signal[Angle] = Signal(currentPosition)
    }

    val task = new drive.unicycleTasks.RotateByAngle(
      Degrees(5), Degrees(0.1), 1)(
      drivetrain,
      hardware,
      props
    )

    task.init()

    ticker(Milliseconds(5))

    assert(lastAppliedSignal.turn.toPercent == 50)

    currentPosition = Degrees(7.5)

    ticker(Milliseconds(5))

    assert(lastAppliedSignal.turn.toPercent == 25)

    currentPosition = Degrees(10)

    ticker(Milliseconds(5))

    assert(lastAppliedSignal.turn.toPercent == 0 && !task.isRunning)
  }
}
