package org.cobalt.pathexecutorv2

import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.SlabBlock
import net.minecraft.world.level.block.state.properties.SlabType
import net.minecraft.world.phys.Vec3
import org.cobalt.Cobalt.minecraft
import org.cobalt.pathfinder.calculate.Path
import org.cobalt.pathfinder.calculate.PathNode
import org.cobalt.util.PlayerUtils

class PathFollower(val path: Path) {

  var nodeIndex: Int = 0
    private set

  val isComplete: Boolean
    get() = nodeIndex + 1 >= path.nodes.size

  val currentNode: PathNode
    get() = path.nodes[nodeIndex]

  val needsFlyStart: Boolean
    get() {
      val node = currentNode
      return node.isFly && !PlayerUtils.isFlying && PlayerUtils.canFly
    }

  fun advance() {
    val playerPos = PlayerUtils.position
    val nodes = path.nodes

    while (
      nodeIndex + 1 < nodes.size &&
      hasReached(playerPos, nodes[nodeIndex])
    ) {
      nodeIndex++
    }
  }

  private fun BlockPos.centerVec(): Vec3 {
    return Vec3(x + 0.5, y + 0.5, z + 0.5)
  }

  fun getLookAheadTarget(eyePos: Vec3, speed: Double, base: Double, scale: Double): Vec3 {
    val lookAhead = base + speed * scale
    return computeRotationTarget(eyePos, path.nodes, nodeIndex, lookAhead)
  }

  private fun hasReached(
    playerPos: BlockPos,
    node: PathNode,
  ): Boolean {
    val level = minecraft.level ?: return false

    if (node.isFly) {
      if (!PlayerUtils.isFlying) return false
      val nodeCenter = node.centerVec
      val playerFeet = PlayerUtils.player?.position() ?: return false
      return playerFeet.distanceToSqr(nodeCenter) < 0.8 * 0.8
    }

    val nodeCenter = node.centerVec
    val playerVec = playerPos.centerVec()

    if (playerVec.distanceToSqr(nodeCenter) < 0.3 * 0.3) {
      return true
    }

    if (nodeIndex + 1 >= path.nodes.size) {
      return false
    }

    val isSlab = isBottomSlab(level.getBlockState(node.blockStandingOn))

    if (!isSlab && (node.block.y > playerPos.y || !PlayerUtils.onGround)) {
      return false
    }

    val segment = path.nodes[nodeIndex + 1].centerVec.subtract(nodeCenter)
    val toPlayer = playerVec.subtract(nodeCenter)

    if (toPlayer.dot(segment) < 0.0) {
      return false
    }

    val perpDistSq = toPlayer.cross(segment).lengthSqr() / segment.lengthSqr()
    return perpDistSq < 1.0
  }

  private fun isBottomSlab(state: net.minecraft.world.level.block.state.BlockState): Boolean {
    return state.block is SlabBlock &&
      state.getValue(SlabBlock.TYPE) == SlabType.BOTTOM
  }

  private fun computeRotationTarget(
    playerEyePos: Vec3,
    nodes: List<PathNode>,
    currentIndex: Int,
    lookAhead: Double,
  ): Vec3 {
    val playerXZ = Vec3(playerEyePos.x, 0.0, playerEyePos.z)

    var bestDistSq = Double.MAX_VALUE
    var bestSegIndex = currentIndex
    var bestT = 0.0

    val searchStart = (currentIndex - 1).coerceAtLeast(0)
    val searchEnd = (currentIndex + 3).coerceAtMost(nodes.size - 1)

    for (i in searchStart until searchEnd) {
      val from = nodes[i].centerVec
      val to = nodes[i + 1].centerVec
      val seg = Vec3(to.x - from.x, 0.0, to.z - from.z)
      val segLenSq = seg.x * seg.x + seg.z * seg.z

      if (segLenSq <= 0.0) continue

      val t = ((playerXZ.x - from.x) * seg.x + (playerXZ.z - from.z) * seg.z)
        .coerceIn(0.0, segLenSq) / segLenSq

      val px = from.x + seg.x * t
      val pz = from.z + seg.z * t
      val dx = playerXZ.x - px
      val dz = playerXZ.z - pz
      val distSq = dx * dx + dz * dz

      if (distSq < bestDistSq) {
        bestDistSq = distSq
        bestSegIndex = i
        bestT = t
      }
    }

    var remaining = lookAhead

    val from = nodes[bestSegIndex].centerVec
    val to = nodes[bestSegIndex + 1].centerVec
    val seg = Vec3(to.x - from.x, 0.0, to.z - from.z)
    val segLen = seg.length()

    if (segLen > 0.0) {
      val remainingSeg = (1.0 - bestT) * segLen
      if (remaining <= remainingSeg) {
        val point = from.add(seg.scale(bestT + remaining / segLen))
        return Vec3(point.x, playerEyePos.y, point.z)
      }
      remaining -= remainingSeg
    }

    for (i in (bestSegIndex + 1) until nodes.size - 1) {
      val sFrom = nodes[i].centerVec
      val sTo = nodes[i + 1].centerVec
      val sSeg = Vec3(sTo.x - sFrom.x, 0.0, sTo.z - sFrom.z)
      val sLen = sSeg.length()

      if (sLen <= 0.0) continue

      if (remaining <= sLen) {
        val point = sFrom.add(sSeg.scale(remaining / sLen))
        return Vec3(point.x, playerEyePos.y, point.z)
      }

      remaining -= sLen
    }

    val last = nodes.last()
    val prev = if (nodes.size > 1) nodes[nodes.size - 2] else last
    val dir = Vec3((last.x - prev.x).toDouble(), 0.0, (last.z - prev.z).toDouble())
    val dirLen = dir.length()
    val extrapolated = if (dirLen > 0.0) {
      last.centerVec.add(dir.scale(remaining / dirLen))
    } else {
      last.centerVec
    }

    return Vec3(extrapolated.x, playerEyePos.y, extrapolated.z)
  }

}

private fun BlockPos.centerVec(): Vec3 {
  return Vec3(x + 0.5, y + 0.5, z + 0.5)
}
