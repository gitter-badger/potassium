package com.lynbrookrobotics.potassium.commons.drivetrain

import com.lynbrookrobotics.potassium.clock.Clock
import com.lynbrookrobotics.potassium.{Component, Signal, PeriodicSignal}
import com.lynbrookrobotics.potassium.tasks.{ContinuousTask, FiniteTask}
import squants.{Acceleration, Angle, Dimensionless, Length, Velocity}
import squants.motion.{AngularVelocity, DegreesPerSecond, Distance, FeetPerSecond}
import squants.space.Feet
import squants.Time
import squants.time.Milliseconds

import scala.collection.immutable.Queue

trait UnicycleCoreTasks {
  val controllers: UnicycleCoreControllers with UnicycleMotionProfileControllers with PurePursuitControllers

  type Drivetrain <: Component[controllers.DriveSignal]

  import controllers._

  class ContinuousClosedDrive(forward: Signal[Dimensionless], turn: Signal[Dimensionless])
                             (implicit drive: Drivetrain, hardware: DrivetrainHardware,
                              props: Signal[DrivetrainProperties]) extends ContinuousTask {
    override def onStart(): Unit = {
      val combined = forward.zip(turn).map(t => UnicycleSignal(t._1, t._2))
      drive.setController(lowerLevelVelocityControl(combined))
    }

    override def onEnd(): Unit = {
      drive.resetToDefault()
    }
  }

  class ContinuousVelocityDrive(forward: Signal[Velocity], turn: Signal[AngularVelocity])
                               (implicit drive: Drivetrain,
                                hardware: DrivetrainHardware,
                                props: Signal[DrivetrainProperties]) extends ContinuousTask {
    override def onStart(): Unit = {
      val combined = forward.zip(turn).map(t => UnicycleVelocity(t._1, t._2))
      drive.setController(lowerLevelVelocityControl(velocityControl(combined)))
    }

    override def onEnd(): Unit = {
      drive.resetToDefault()
    }
  }

  class DriveDistance(distance: Length, tolerance: Length)
                     (implicit drive: Drivetrain,
                      hardware: DrivetrainHardware,
                      props: Signal[DrivetrainProperties]) extends FiniteTask {
    override def onStart(): Unit = {
      val absoluteDistance = hardware.forwardPosition.get + distance
      val (controller, error) = forwardPositionControl(absoluteDistance)

      val checkedController = controller.withCheck { _ =>
        if (error.get.abs < tolerance) {
          finished()
        }
      }

      drive.setController(lowerLevelVelocityControl(speedControl(checkedController)))
    }

    override def onEnd(): Unit = {
      drive.resetToDefault()
    }
  }

  class DriveDistanceWithTrapazoidalProfile(cruisingVelocity: Velocity,
                                            finalVelocity: Velocity,
                                            acceleration: Acceleration,
                                            targetDistance: Length,
                                            position: Signal[Length],
                                            tolerance: Length)
                                           (implicit drive: Drivetrain,
                                            hardware: DrivetrainHardware,
                                            properties: Signal[DrivetrainProperties],
                                            clock: Clock) extends FiniteTask {
    if (cruisingVelocity.abs > properties.get.maxForwardVelocity) {
      throw new IllegalArgumentException("Input speed: " +
        cruisingVelocity.abs.toFeetPerSecond +
        " ft/s is greater than max speed")
    }

    override final def onStart(): Unit = {
      val (velocity, error) = trapezoidalDriveControl(
        hardware.forwardVelocity.get, // not map because we need position at this time
        cruisingVelocity,
        finalVelocity,
        acceleration,
        hardware.forwardPosition.get, // not map because we need position at this time
        targetDistance,
        position,
        tolerance
      )

      val unicycleOutput = velocity.map(UnicycleVelocity(_, DegreesPerSecond(0)).toUnicycleSignal)

      drive.setController(lowerLevelVelocityControl(unicycleOutput).withCheck { _ =>
          if (error.get.abs < tolerance) {
            finished()
          }
        })
    }

    override def onEnd(): Unit = {
      drive.resetToDefault()
    }
  }

  /**
    * drives the target distance with default values for acceleration and cruising velocity
    * TODO: finish adding docs
    * @param targetForwardDistance
    * @param finalVelocity
    * @param drive
    * @param hardware
    * @param properties
    */
  class DriveDistanceSmooth(targetForwardDistance: Length, finalVelocity: Velocity)
                           (implicit drive: Drivetrain,
                            hardware: DrivetrainHardware,
                            properties: Signal[DrivetrainProperties], // TODO: clock is temporary for loggin reasons
                            clock: Clock) extends DriveDistanceWithTrapazoidalProfile(
                                0.5 * properties.get.maxForwardVelocity,
                                finalVelocity,
                                properties.get.maxAcceleration,
                                targetForwardDistance,
                                hardware.forwardPosition,
                                Feet(.1))

  class DriveDistanceStraight(distance: Length, toleranceForward: Length, toleranceAngle: Angle)
                             (implicit drive: Drivetrain,
                              hardware: DrivetrainHardware,
                              props: Signal[DrivetrainProperties]) extends FiniteTask {
    override def onStart(): Unit = {
      val absoluteDistance = hardware.forwardPosition.get + distance
      val (forwardController, forwardError) = forwardPositionControl(absoluteDistance)

      val absoluteAngle = hardware.turnPosition.get
      val (turnController, turnError) = turnPositionControl(absoluteAngle)

      val combinedController = forwardController.zip(turnController).map(t => t._1 + t._2)

      val checkedController = combinedController.withCheck { _ =>
        if (forwardError.get.abs < toleranceForward && turnError.get.abs < toleranceAngle) {
          finished()
        }
      }

      drive.setController(lowerLevelVelocityControl(speedControl(checkedController)))
    }

    override def onEnd(): Unit = {
      drive.resetToDefault()
    }
  }

  class RotateByAngle(relativeAngle: Angle, tolerance: Angle)
                     (implicit drive: Drivetrain,
                      hardware: DrivetrainHardware,
                      props: Signal[DrivetrainProperties]) extends FiniteTask {
    override def onStart(): Unit = {
      val absoluteAngle = hardware.turnPosition.get + relativeAngle
      val (controller, error) = turnPositionControl(absoluteAngle)
      val checkedController = controller.withCheck { _ =>
        if (error.get.abs < tolerance) {
          finished()
        }
      }

      drive.setController(lowerLevelVelocityControl(speedControl(checkedController)))
    }

    override def onEnd(): Unit = {
      drive.resetToDefault()
    }
  }

  class CorrectOffsetWithLatency(timestampedOffset: Signal[(Angle, Time)], tolerance: Angle)
    (implicit drive: Drivetrain,
      hardware: DrivetrainHardware,
      props: Signal[DrivetrainProperties]) extends FiniteTask {

    val positionSlide: PeriodicSignal[Queue[(Angle, Time)]] = hardware.turnPosition.toPeriodic.zipWithTime.sliding(
      20, (hardware.turnPosition.get, Milliseconds(System.currentTimeMillis()))
    )

    override def onStart(): Unit = {
      val targetAbsolute = calculateTargetFromOffsetWithLatency(timestampedOffset, positionSlide)

      val (controller, error) = continuousTurnPositionControl(targetAbsolute)
      val checkedController = controller.zip(error).withCheck { t =>
        val (_, e) = t
        if (e.abs < tolerance) {
          finished()
        }
      }.map(_._1)

      drive.setController(lowerLevelVelocityControl(speedControl(checkedController)))
    }

    override def onEnd(): Unit = {
      drive.resetToDefault()
    }
  }

}
