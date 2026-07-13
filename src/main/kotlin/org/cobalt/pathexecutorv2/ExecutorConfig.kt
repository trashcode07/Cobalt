package org.cobalt.pathexecutorv2

import org.cobalt.pathfinder.calculate.PathMode
import org.cobalt.pathfinder.goal.Goal
import org.cobalt.pathfinder.movement.Movement

class ExecutorConfig(
  val goal: Goal,
  val movements: Array<out Movement> = PathMode.WALK.movements,
  val shouldSprint: Boolean = true,
  val preferShifting: Boolean = false,
  val returnBestNode: Boolean = false,
  val lookAheadBase: Double = 3.0,
  val lookAheadSpeedScale: Double = 8.0,
) {

  val useFlyMovement = movements.any {
    it.type == Movement.Type.FLY
  }

}
