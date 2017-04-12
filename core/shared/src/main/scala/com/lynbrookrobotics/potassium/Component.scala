package com.lynbrookrobotics.potassium

import java.io.{FileWriter, PrintWriter}
import java.util

import com.lynbrookrobotics.potassium.clock.Clock
import edu.wpi.first.wpilibj.Timer
import squants.Time

object Counter {
  var globalOVerrunCountThingy = 0
}

/**
  * Represents a single robotic component, which translates signal data into action
  *
  * Components can be though of as a function of command => Unit, which
  * is implemented in applySignal. The function is expected to take the latest
  * command and send it to hardware interfaces. In addition, the applySignal method
  * is the place to implement safeties, as it is the last layer of signal transformation.
  *
  * @param period the update rate of the component
  * @param clock  the clock to use to schedule periodic updates
  * @tparam T the type of values produced by signals for the component
  */

abstract class Component[T](period: Time)(implicit val clock: Clock) {


  def defaultController: PeriodicSignal[T]

  private var currentController: PeriodicSignal[T] = defaultController

  private var lastOutput: Option[T] = None

  val peekedController: Signal[Option[T]] = Signal {
    lastOutput
  }

  /**
    * Sets the controller to be used by the component during updates.
    *
    * @param controller the new controller to use
    */
  def setController(controller: PeriodicSignal[T]): Unit = {
    //    println("setting controller")
    currentController.detachTickSource(this)
    controller.attachTickSource(this)
    currentController = controller
  }

  /**
    * Resets the component to use its default controller.
    */
  def resetToDefault(): Unit = {
    //    println(s"reseting to default $lastOutput")
    //    println(Thread.currentThread().getStackTrace)
    setController(defaultController)
  }

  /**
    * Applies the latest control signal value.
    *
    * @param signal the signal value to act on
    */
  def applySignal(signal: T): Unit

  var ticksPassedCounter = 0
  val maxTicksPassed = 3
  val numbers = new util.ArrayList[(Double, Double)]
  clock(period) { dt =>

    var tStart = 0.0
    if (!(ticksPassedCounter > maxTicksPassed)) {
      tStart = Timer.getFPGATimestamp
    }

    // Because of unusual initialization orders, currentController may sometimes be null,
    // so we make sure to have it initialized here.

    // scalastyle:off
    if (currentController == null) {
      currentController = defaultController
    }
    // scalastyle:on

    val value = currentController.currentValue(dt)

    lastOutput = Some(value)

    if (!(ticksPassedCounter > maxTicksPassed)) {
      val tEnd = Timer.getFPGATimestamp
      import Counter._
      if (tEnd - tStart > 5) globalOVerrunCountThingy += 1
      numbers.add((tStart, tEnd))
    }
    else {
      val output = new PrintWriter(new FileWriter("timings.tsv"))
      numbers.forEach(t => output.println(t._1 + "\t" + t._2))
      output.flush()
      output.close()
    }

    applySignal(value)
  }
}
