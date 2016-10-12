package com.lynbrookrobotics.potassium.tasks

sealed trait SequentialPhase
case object Stopped extends SequentialPhase
case object RunningFirst extends SequentialPhase
case object RunningSecond extends SequentialPhase

class SequentialTask(first: FiniteTask, second: FiniteTask) extends FiniteTask
                                                            with FiniteTaskFinishedListener {
  private var currentPhase: SequentialPhase = Stopped

  override def onFinished(task: FiniteTask): Unit = {
    if (currentPhase == RunningFirst && task == first) {
      currentPhase = RunningSecond
      second.init()
    } else if (currentPhase == RunningSecond && task == second) {
      finished()
    }
  }

  first.addFinishedListener(this)
  second.addFinishedListener(this)

  override def onStart(): Unit = {
    currentPhase = RunningFirst
    first.init()
  }

  override def onEnd(): Unit = {
    currentPhase = Stopped
  }
}
