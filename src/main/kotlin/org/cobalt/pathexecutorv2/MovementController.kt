package org.cobalt.pathexecutorv2

import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.SlabBlock
import net.minecraft.world.level.block.state.properties.SlabType
import net.minecraft.world.phys.Vec3
import org.cobalt.Cobalt.minecraft
import org.cobalt.pathfinder.calculate.PathNode
import org.cobalt.util.PlayerUtils
import org.cobalt.util.RotationUtils
import org.cobalt.util.helper.Clock

class MovementController(private val config: ExecutorConfig) {

  private val jumpDelay = Clock()
  private var flyStartStage = -1

  fun computeInput(player: LocalPlayer, follower: PathFollower): PlayerInput {
    if (follower.needsFlyStart) {
      return handleFlyStart()
    }
    flyStartStage = -1

    val input = PlayerInput()
    val node = follower.currentNode
    val playerPos = PlayerUtils.position

    val sameXZ = node.block.x == playerPos.x && node.block.z == playerPos.z

    if (!node.isFly || !sameXZ) {
      val neededKeys = getNeededKeys(
        PlayerUtils.rotation.yaw,
        RotationUtils.getRotation(node.centerVec).yaw
      )
      input.apply(neededKeys)
    }

    if (!node.isFly && config.shouldSprint) {
      input.sprint = shouldSprint(player, follower)
    }

    if (!node.isFly && shouldJump(player, follower)) {
      input.jump = true
    }

    if (node.isFly) {
      val diffY = node.block.y + 0.5 - player.y
      when {
        diffY > 0.3 -> input.jump = true
        diffY < -0.3 -> input.sneak = true
      }
    }

    return input
  }

  private fun handleFlyStart(): PlayerInput {
    val input = PlayerInput()

    when (flyStartStage) {
      -1 -> {
        input.jump = true
        flyStartStage = 0
      }

      0 -> {
        flyStartStage = 1
      }

      1 -> {
        input.jump = true
        flyStartStage = 2
      }

      2 -> {
        flyStartStage = 3
      }
    }

    return input
  }

  private fun shouldSprint(player: LocalPlayer, follower: PathFollower): Boolean {
    val node = follower.currentNode
    val targetYaw = RotationUtils.getRotation(node.centerVec).yaw
    val angleDiff = abs(RotationUtils.angleDifference(player.yRot, targetYaw))
    if (angleDiff > 50f) return false

    val path = follower.path
    val idx = follower.nodeIndex
    if (idx + 2 < path.nodes.size) {
      val a = path.nodes[idx]
      val b = path.nodes[idx + 1]
      val c = path.nodes[idx + 2]

      if (isSharpTurn(a, b, c)) {
        val distToTurn = xzDistance(player, b)
        if (distToTurn < 4.0) return false
      }
    }

    return true
  }

  private fun shouldJump(player: LocalPlayer, follower: PathFollower): Boolean {
    val level = minecraft.level ?: return false

    if (!PlayerUtils.onGround || !jumpDelay.passed()) {
      return false
    }

    val node = follower.currentNode
    val playerPos = PlayerUtils.position

    if (node.block.y - playerPos.y < 1) {
      return false
    }

    val blockState = level.getBlockState(node.blockStandingOn)
    if (blockState.block is SlabBlock && blockState.getValue(SlabBlock.TYPE) == SlabType.BOTTOM) {
      return false
    }

    val nodeCenter = node.centerVec
    val playerVec = playerPos.centerVec()
    val dx = abs(nodeCenter.x - playerVec.x)
    val dz = abs(nodeCenter.z - playerVec.z)

    if (dx + dz > 1.2) {
      return false
    }

    if (minOf(dx, dz) > 0.2) {
      return false
    }

    if (PlayerUtils.canFly) {
      jumpDelay.schedule(Random.nextLong(350, 450))
    }

    return true
  }

  private fun isSharpTurn(a: PathNode, b: PathNode, c: PathNode): Boolean {
    val d1x = (b.x - a.x).toDouble()
    val d1z = (b.z - a.z).toDouble()
    val d2x = (c.x - b.x).toDouble()
    val d2z = (c.z - b.z).toDouble()

    val len1 = sqrt(d1x * d1x + d1z * d1z)
    val len2 = sqrt(d2x * d2x + d2z * d2z)

    if (len1 <= 0.0 || len2 <= 0.0) return false

    val dot = (d1x * d2x + d1z * d2z) / (len1 * len2)
    return dot < 0.5
  }

  private fun xzDistance(player: LocalPlayer, node: PathNode): Double {
    val dx = player.x - (node.x + 0.5)
    val dz = player.z - (node.z + 0.5)
    return sqrt(dx * dx + dz * dz)
  }

}

private fun BlockPos.centerVec(): Vec3 {
  return Vec3(x + 0.5, y + 0.5, z + 0.5)
}

private fun getNeededKeys(playerYaw: Float, idealYaw: Float): PlayerInput {
  val diff = net.minecraft.util.Mth.wrapDegrees(idealYaw - playerYaw)

  return when {
    diff >= -22.5f && diff < 22.5f -> PlayerInput(forward = true)
    diff in 22.5f..<67.5f -> PlayerInput(forward = true, right = true)
    diff in 67.5f..<112.5f -> PlayerInput(right = true)
    diff in 112.5f..<157.5f -> PlayerInput(backward = true, right = true)
    diff >= 157.5f || diff < -157.5f -> PlayerInput(backward = true)
    diff >= -157.5f && diff < -112.5f -> PlayerInput(backward = true, left = true)
    diff >= -112.5f && diff < -67.5f -> PlayerInput(left = true)
    else -> PlayerInput(forward = true, left = true)
  }
}
