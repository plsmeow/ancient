package meow.ancient.module.modules.movement

import meow.ancient.client.extensions.MOVEMENT
import meow.ancient.client.extensions.inGame
import meow.ancient.client.extensions.mc
import meow.ancient.event.events.PlayerUpdateEvent
import meow.ancient.module.Module
import net.minecraft.world.phys.Vec3
import sweetie.evaware.flora.api.Commando
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object GrimGlideModule : Module(
    name = "GrimGlide",
    category = MOVEMENT,
    description = "Elytra boost tailored for GrimAC"
) {
    private var ticksTwo = 0

    @Commando
    fun onUpdate(event: PlayerUpdateEvent) {
        if (!inGame) return
        val player = mc.player ?: return
        if (!player.isFallFlying) return

        ticksTwo++
        val pos = player.position()
        val yaw = player.yRot
        var forward = 0.087

        val dxVel = player.x - player.xo
        val dzVel = player.z - player.zo
        val bps = sqrt(dxVel * dxVel + dzVel * dzVel) * 20.0

        if (bps >= 52.0) {
            forward = 0.0
        }

        val yawRad = Math.toRadians(yaw.toDouble())
        val dx = -sin(yawRad) * forward
        val dz = cos(yawRad) * forward

        val rnd = ThreadLocalRandom.current()
        val mult = rnd.nextDouble(1.1, 1.21)

        if (ticksTwo >= 50) {
            player.setPos(pos.x + dx, pos.y, pos.z + dz)
            ticksTwo = 0
        }

        player.deltaMovement = Vec3(dx * mult, player.deltaMovement.y + 0.016, dz * mult)
    }
}
