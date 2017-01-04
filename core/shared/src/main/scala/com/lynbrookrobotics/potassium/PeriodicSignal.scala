package com.lynbrookrobotics.potassium

import squants.{Quantity, Time}
import squants.time.{TimeDerivative, TimeIntegral}

import scala.collection.immutable.Queue

/**
  * Represents a signal that requires a fixed update interval
  *
  * This type of signal is used in cases where history must be tracked,
  * as it prevents collisions between multiple value requests and includes
  * a dt value for use in calculations.
  *
  * @tparam T the type of values the signal will produce
  */
abstract class PeriodicSignal[T] { self =>
  private var currentTickSource: Option[AnyRef] = None

  protected val parent: Option[PeriodicSignal[_]]
  protected val check: Option[T => Unit]

  private var lastCalculated: Option[(T, Int)] = None

  protected def calculateValue(dt: Time, token: Int): T

  /**
    * Gets the latest value from the signal
    * @param dt the time since the last value request
    * @param requestToken an optional token to prevent recalculations of a
    *                     value in a branched dependency
    * @return the latest value of the signal
    */
  def currentValue(dt: Time, requestToken: Int = PeriodicSignal.requestTokens.next()): T = {
    lastCalculated match {
      case Some((v, t)) if t == requestToken =>
        v
      case _ =>
        val ret = calculateValue(dt, requestToken)
        check.foreach(_(ret))
        lastCalculated = Some((ret, requestToken))

        ret
    }
  }

  /**
    * Transforms the periodic signal by applying a function to all values
    * @param f the function to transform values with
    * @tparam U the type of the resulting signal
    * @return a new signal with values transformed by the function
    */
  def map[U](f: (T, Time) => U): PeriodicSignal[U] = new PeriodicSignal[U] {
    val parent = Some(self)
    val check = None

    def calculateValue(dt: Time, token: Int): U = {
      f(self.currentValue(dt, token), dt)
    }
  }

  /**
    * Combines the signal with another signal into a signal of tuples
    * @param other the signal to combine with
    * @tparam O the type of values of the other signal
    * @return a new signal that returns tuples with one value from each signal
    */
  def zip[O](other: PeriodicSignal[O]): PeriodicSignal[(T, O)] = new PeriodicSignal[(T, O)] {
    val parent = Some(self)
    val check = None

    def calculateValue(dt: Time, token: Int): (T, O) = {
      (self.currentValue(dt, token), other.currentValue(dt, token))
    }
  }

  /**
    * Applies a fixed size sliding window over the signal
    * @param size the size of the window
    * @param filler the element to use to fill the window until elements are available
    * @return
    */
  def sliding(size: Int, filler: T): PeriodicSignal[Queue[T]] = {
    var last = Queue.fill(size)(filler)

    new PeriodicSignal[Queue[T]] {
      val parent = Some(self)
      val check = None

      def calculateValue(dt: Time, token: Int): Queue[T] = {
        last = last.tail :+ self.currentValue(dt, token)
        last
      }
    }
  }

  def scanLeft[U](initialValue: U)(f: (U, T, Time) => U): PeriodicSignal[U] = new PeriodicSignal[U] {
    val parent = Some(self)
    val check = None

    var latest = initialValue

    def calculateValue(dt: Time, token: Int): U = {
      latest = f(latest, self.currentValue(dt, token), dt)
      latest
    }
  }

  /**
    * Calculates the derivative of the signal, producing units of
    * the derivative of the signal's units
    * @return a signal producing values that are the derivative of the signal
    */
  def derivative[D <: Quantity[D] with TimeDerivative[_], U <: Quantity[U]](implicit unitEv: T => U, intEv: U => TimeIntegral[D]): PeriodicSignal[D] = {
    // We use null here for a cheap way to handle non-complete sliding windows without a perf hit
    // from mapping to Options first

    // scalastyle:off
    sliding(2, null.asInstanceOf[T]).map { (q, dt) =>
      if (q.head != null && q.last != null) {
        (q.last - q.head) / dt
      } else {
        (q.last * 0) / dt
      }
    }
    // scalastyle:on
  }

  /**
    * Calculates the integral of the signal, producing units of
    * the integral of the signal's units
    * @return a signal producing values that are the integral of the signal
    */
  def integral[I <: Quantity[I] with TimeIntegral[_]](implicit derivEv: T => TimeDerivative[I]): PeriodicSignal[I] = {
    // Just like derivative, we use null as a cheap way to handle the initial value since there is
    // no way to get a "zero" for I

    // scalastyle:off
    scanLeft(null.asInstanceOf[I]) { (acc, cur, dt) =>
      if (acc != null) {
        acc + (cur: TimeDerivative[I]) * dt
      } else {
        (cur: TimeDerivative[I]) * dt
      }
    }
    // scalastyle:on
  }

  /**
    * Creates a new periodic signal that returns the same value but also invokes the given callback
    * @param checkCallback the callback to run with each value calculated by the signal
    * @return a periodic signal with the callback invocations
    */
  def withCheck(checkCallback: T => Unit): PeriodicSignal[T] = new PeriodicSignal[T] {
    val parent = Some(self)
    val check = Some(checkCallback)

    def calculateValue(dt: Time, token: Int): T = self.currentValue(dt, token)
  }

  /**
    * Marks the object that is producing ticks for the signal.
    *
    * This is needed in order to prevent tick collisions with multiple sources of ticks.
    *
    * @param source an object to mark as a tick source
    */
  def attachTickSource(source: AnyRef): Unit = {
    if (currentTickSource.isEmpty) {
      currentTickSource = Some(source)
      parent.foreach(_.attachTickSource(source))
    } else if (!currentTickSource.get.eq(source)) {
      throw new IllegalStateException("Cannot attach a periodic signal to two different clocks")
    }
  }

  /**
    * Removes any attached tick source from the signal and its parents.
    * @param source the tick source to remove
    */
  def detachTickSource(source: AnyRef): Unit = {
    currentTickSource.foreach { s =>
      if (s.eq(source)) {
        currentTickSource = None
        parent.foreach(_.detachTickSource(source))
      }
    }
  }
}

object PeriodicSignal {
  private[PeriodicSignal] val requestTokens = Iterator.from(1)
}