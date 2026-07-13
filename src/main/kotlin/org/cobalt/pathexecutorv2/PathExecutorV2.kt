package org.cobalt.pathexecutorv2

import java.awt.Color
import net.minecraft.client.player.KeyboardInput
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.cobalt.Cobalt.minecraft
import org.cobalt.event.EventBus
import org.cobalt.event.annotation.SubscribeEvent
import org.cobalt.event.impl.TickEvent
import org.cobalt.event.impl.WorldEvent
import org.cobalt.module.impl.misc.Debug
import org.cobalt.pathfinder.calculate.path.AStarPathfinder
import org.cobalt.ui.theme.ThemeManager
import org.cobalt.util.ChatUtils
import org.cobalt.util.MessageType
import org.cobalt.util.MouseMode
import org.cobalt.util.MouseUtils
import org.cobalt.util.PlayerUtils
import org.cobalt.util.WorldRenderUtils
import org.cobalt.util.helper.Multithreading

object PathExecutorV2 {

  var running = false
    private set
  var path: org.cobalt.pathfinder.calculate.Path? = null
    private set

  private var config: ExecutorConfig? = null
  private var follower: PathFollower? = null
  private var movement: MovementController? = null
  private var rotation: RotationController? = null

  private val pathInput = PathInput()
  private var returnMouseMode = false

  init {
    EventBus.register(this)
  }

  fun goTo(config: ExecutorConfig) {
    stop()

    if (config.useFlyMovement && !PlayerUtils.canFly) {
      ChatUtils.sendSystemMessage("<red>Invalid path config, since player cannot fly!</red>")
      return
    }

    val player = minecraft.player

    if (player != null) {
      player.input = pathInput
    } else {
      return
    }

    this.config = config
    this.running = true

    startCalculation(config)
  }

  fun stop() {
    running = false
    path = null
    config = null
    follower = null
    movement = null
    rotation = null

    minecraft.player?.let {
      it.input = KeyboardInput(minecraft.options)
    }.also {
      pathInput.stopMovement()
    }

    if (returnMouseMode) {
      MouseUtils.mouseMode = MouseMode.DEFAULT
      returnMouseMode = false
    }
  }

  private fun startCalculation(config: ExecutorConfig) {
    val player = minecraft.player ?: return
    val startPos = PlayerUtils.position

    val pathFinder = AStarPathfinder(
      startPos.x, startPos.y, startPos.z,
      config.goal, config.movements,
      config.returnBestNode
    )

    Multithreading.runAsync {
      val result = pathFinder.findPath()

      if (result == null) {
        ChatUtils.sendSystemMessage("<red>Unable to find a path</red>")
        stop()
        return@runAsync
      }

      path = result
      follower = PathFollower(result)
      movement = MovementController(config)
      rotation = RotationController()

      if (MouseUtils.mouseMode == MouseMode.DEFAULT) {
        MouseUtils.mouseMode = MouseMode.LOCK_MOUSE
        returnMouseMode = true
      }

      ChatUtils.sendSystemMessage(
        "Found ${result.nodes.size} node path in ${result.timeElapsed.inWholeMilliseconds}ms",
        MessageType.DEBUG
      )
    }
  }

  @SubscribeEvent
  fun onTick(ignored: TickEvent.Start) {
    if (minecraft.level == null || minecraft.player == null) {
      stop()
      return
    }

    if (minecraft.gui.screen() != null) {
      pathInput.stopMovement()
      return
    }

    if (!running) {
      return
    }

    val f = follower ?: return
    val m = movement ?: return
    val r = rotation ?: return
    val player = PlayerUtils.player ?: return

    f.advance()

    if (f.isComplete) {
      ChatUtils.sendSystemMessage("Path complete")
      stop()
      return
    }

    val speed = player.deltaMovement.length()
    val target = f.getLookAheadTarget(
      player.eyePosition, speed,
      config!!.lookAheadBase, config!!.lookAheadSpeedScale
    )
    r.setTarget(target)

    val input = m.computeInput(player, f)
    pathInput.applyInput(input)
  }

  @SubscribeEvent
  fun onRender(ignored: WorldEvent.Render) {
    if (!running) {
      return
    }

    val r = rotation ?: return
    val player = PlayerUtils.player ?: return

    r.applyRotation(player)

    val f = follower ?: return
    val p = path ?: return

    if (!Debug.enabled) {
      return
    }

    val theme = ThemeManager.activeTheme
    val keyNodes = p.keyNodes

    val targetNode = p.nodes[f.nodeIndex].centerVec
    val playerPos = PlayerUtils.position.centerVec()

    WorldRenderUtils.drawBox(playerPos.smallBox(), Color.GREEN)
    WorldRenderUtils.drawBox(targetNode.smallBox(), Color.RED)

    for (index in keyNodes.indices) {
      val node = keyNodes[index]

      WorldRenderUtils.drawBlockPos(
        if (node.isFly) node.block else node.blockStandingOn,
        color = theme.accentPrimary
      )

      if (index > 0) {
        val prev = keyNodes[index - 1]

        WorldRenderUtils.drawLine(
          if (prev.isFly) prev.centerVec else prev.topCenterVec,
          if (node.isFly) node.centerVec else node.topCenterVec,
          theme.accentSecondary
        )
      }
    }
  }

}

private fun BlockPos.centerVec(): Vec3 {
  return Vec3(x + 0.5, y + 0.5, z + 0.5)
}

private fun Vec3.smallBox(): AABB {
  return AABB(
    x - 0.25,
    y - 0.25,
    z - 0.25,
    x + 0.25,
    y + 0.25,
    z + 0.25
  )
}
