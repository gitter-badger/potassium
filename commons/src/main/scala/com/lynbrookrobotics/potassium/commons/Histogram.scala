package com.lynbrookrobotics.potassium.commons

/**
  * Created by the-magical-llamicorn on 4/19/17.
  */
class Histogram(min: Double, max: Double, bins: Int) {
  val histogram = Array[Long](bins + 2)
  val interval = (max - min) / bins

  def apply(value: Double): Unit = {
    if (value < min) histogram(0) += 1
    else if (value > max) histogram(bins + 1) += 1
    else histogram(1 + ((value - min) / interval).toInt) += 1
  }

  override def toString(): String = {
    val sb = new StringBuilder()
    sb.append("<")
    sb.append(min)
    sb.append(" : ")
    sb.append(histogram(0))
    sb.append('\n')

    (1 to bins).foreach { i =>
      sb.append((min + (interval * (i - 1))).toFloat)
      sb.append(" to ")
      sb.append((min + (interval * i)).toFloat)
      sb.append(" : ")
      sb.append(histogram(i))
      sb.append('\n')
    }

    sb.append(">")
    sb.append(max)
    sb.append(" : ")
    sb.append(histogram(bins + 1))
    sb.append('\n')

    sb.toString()
  }
}
