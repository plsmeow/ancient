package meow.ancient.module.modules.movement

import meow.ancient.client.extensions.MOVEMENT
import meow.ancient.client.extensions.mc
import meow.ancient.client.extensions.movementForward
import meow.ancient.client.extensions.movementSideways
import meow.ancient.event.events.PlayerUpdateEvent
import meow.ancient.module.Module
import net.minecraft.core.BlockPos
import net.minecraft.util.Mth
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import sweetie.evaware.flora.api.Commando
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

object NoWebModule : Module(
    name = "NoWeb",
    category = MOVEMENT,
    description = "Prevents cobweb slowdown"
) {
    val strength by number("Strength", 0.8, 0.3, 1.05, 0.05, 2)
    val upSpeed by number("Up Speed", 0.35, 0.05, 2.0, 0.05, 2)
    val downSpeed by number("Down Speed", 0.35, 0.05, 2.0, 0.05, 2)

    fun shouldCancelSlowdown(): Boolean = isEnabled() && isInsideCobweb()

    fun isInsideCobweb(): Boolean {
        val player = mc.player ?: return false
        val level = mc.level ?: return false
        val bb = player.boundingBox

        val minX = floor(bb.minX).toInt()
        val maxX = floor(bb.maxX).toInt()
        val minY = floor(bb.minY).toInt()
        val maxY = floor(bb.maxY).toInt()
        val minZ = floor(bb.minZ).toInt()
        val maxZ = floor(bb.maxZ).toInt()

        val pos = BlockPos.MutableBlockPos()
        for (x in minX..maxX) {
            for (y in minY..maxY) {
                for (z in minZ..maxZ) {
                    pos.set(x, y, z)
                    if (level.getBlockState(pos).`is`(Blocks.COBWEB)) {
                        return true
                    }
                }
            }
        }
        return false
    }

    @Commando
    fun onUpdate(event: PlayerUpdateEvent) {
        val player = mc.player ?: return
        if (mc.level == null) return
        if (player.isSpectator || player.abilities.flying) return
        if (!isInsideCobweb()) return

        val forward = player.input.movementForward
        val strafe = player.input.movementSideways
        val up = mc.options.keyJump.isDown
        val down = mc.options.keyShift.isDown
        if (forward == 0.0f && strafe == 0.0f && !up && !down) return

        val dir = inputDirection(forward, strafe, player.yRot)
        val targetSpeed = 0.2873 * strength
        val y = if (up) upSpeed else if (down) -downSpeed else 0.0
        player.deltaMovement = Vec3(dir.x * targetSpeed, y, dir.z * targetSpeed)
        player.resetFallDistance()
    }

    private fun inputDirection(forward: Float, strafe: Float, yaw: Float): Vec3 {
        val f = Mth.clamp(forward, -1.0f, 1.0f)
        val s = Mth.clamp(strafe, -1.0f, 1.0f)
        val rad = Math.toRadians(yaw.toDouble())
        val sinVal = sin(rad)
        val cosVal = cos(rad)
        val x = s * cosVal - f * sinVal
        val z = s * sinVal + f * cosVal
        val len = sqrt(x * x + z * z)
        if (len < 1.0e-6) return Vec3.ZERO
        return Vec3(x / len, 0.0, z / len)
    }
}
