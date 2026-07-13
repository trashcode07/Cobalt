package org.cobalt.pathexecutorv2

import kotlin.math.abs
import kotlin.random.Random
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.phys.Vec3
import org.cobalt.util.PlayerUtils
import org.cobalt.util.RotationUtils
import org.cobalt.util.rotation.Rotation

class RotationController {

  private var lastFrameMs = 0L
  private var target: Vec3? = null

  fun setTarget(target: Vec3) {
    this.target = target
  }

  fun applyRotation(player: LocalPlayer) {
    val targetVec = target ?: return

    val now = System.currentTimeMillis()
    val dt = ((now - lastFrameMs) / 50f).coerceIn(0f, 1f)
    lastFrameMs = now

    val current = PlayerUtils.rotation
    val targetRot = RotationUtils.getRotation(player.eyePosition, targetVec)
    val deltaYaw = RotationUtils.angleDifference(targetRot.yaw, current.yaw)
    val deltaPitch = RotationUtils.angleDifference(targetRot.pitch, current.pitch)

    val distance = abs(deltaYaw) + abs(deltaPitch)
    if (distance < 0.5f) return

    val speed = player.deltaMovement.length()
    val speedFactor = (1.0 + speed * 3.0).coerceIn(1.0, 4.0)

    val randomFactor = (0.88 + Random.nextDouble() * 0.24).toFloat()
    val deceleration = 1f / maxOf(distance / 80f, 1f)
    val trackingStrength = (TRACKING_BASE * speedFactor).toFloat()

    var yawStep = deltaYaw * trackingStrength * randomFactor * dt * deceleration
    var pitchStep = deltaPitch * (trackingStrength * 0.8f) * randomFactor * dt * deceleration

    // Micro-jitter when close to target for human-like imprecision
    if (distance < 8f) {
      val jitterScale = (1f - distance / 8f) * 0.15f
      yawStep += (Random.nextFloat() - 0.5f) * jitterScale * dt
      pitchStep += (Random.nextFloat() - 0.5f) * jitterScale * 0.6f * dt
    }

    PlayerUtils.setRotation(Rotation(
      current.yaw + yawStep,
      (current.pitch + pitchStep).coerceIn(-75f, 75f)
    ))
  }

  companion object {
    private const val TRACKING_BASE = 0.25
  }

}
