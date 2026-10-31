package meow.ancient.module.modules.combat

import meow.ancient.client.extensions.COMBAT
import meow.ancient.client.extensions.inGame
import meow.ancient.client.extensions.mc
import meow.ancient.client.util.friend.FriendRepository
import meow.ancient.client.util.movement.TeleportPath
import meow.ancient.event.events.PlayerUpdateEvent
import meow.ancient.module.Module
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3
import sweetie.evaware.flora.api.Commando

object TpAuraModule : Module(
    name = "TpAura",
    category = COMBAT,
    description = "Long range killaura using teleportation"
) {
    val tpModeSetting = mode("TP Mode", "Bypass", listOf("Simple", "Bypass", "Vault"))
    val tpMode by tpModeSetting

    val maxDistance by number("Max Distance", 100.0, 5.0, 200.0, 5.0, 0)
    val stepSize by number("Step", 9.9, 0.5, 10.0, 0.1, 1, isVisible = { tpMode == "Simple" || tpMode == "Bypass" })
    val packets by number("Packets", 10.0, 1.0, 50.0, 1.0, 0, isVisible = { tpMode == "Bypass" })
    val bypassOnGround by boolean("Bypass OnGround", true, isVisible = { tpMode == "Bypass" })
    val vaultDelay by number("Vault Delay", 0.0, 0.0, 2000.0, 50.0, 0, unit = "ms", isVisible = { tpMode == "Vault" })

    val targets = targets("Targets", setOf("minecraft:player"))

    private var origin: Vec3? = null
    private var attacking = false
    private var lastVaultTime = 0L

    private fun getPathType(): TeleportPath.Type = when (tpMode) {
        "Simple" -> TeleportPath.Type.Simple
        "Vault" -> TeleportPath.Type.Vault
        else -> TeleportPath.Type.Bypass
    }

    @Commando
    fun onUpdate(event: PlayerUpdateEvent) {
        if (!inGame || attacking) return
        val player = mc.player ?: return
        if (!player.isAlive || player.isPassenger) return

        if (tpMode == "Vault" && System.currentTimeMillis() - lastVaultTime < vaultDelay.toLong()) return
        if (TeleportPath.busy()) return

        val target = findTarget(player) ?: return
        val originPos = player.position()
        origin = originPos
        val destination = target.position()

        attacking = true
        val success = TeleportPath.start(
            requester = this,
            destination = destination,
            type = getPathType(),
            repeats = packets.toInt(),
            distancePerStep = stepSize,
            ground = bypassOnGround,
            yRot = player.yRot,
            xRot = player.xRot
        ) {
            if (target.isAlive && player.isAlive) {
                mc.gameMode?.attack(player, target)
                val anim = player.getItemInHand(InteractionHand.MAIN_HAND).attackAnimation
                player.swing(InteractionHand.MAIN_HAND, anim, false)
            }
            if (tpMode == "Vault") lastVaultTime = System.currentTimeMillis()

            val returnOrigin = origin ?: originPos
            TeleportPath.start(
                requester = this,
                destination = returnOrigin,
                type = getPathType(),
                repeats = packets.toInt(),
                distancePerStep = stepSize,
                ground = bypassOnGround,
                yRot = player.yRot,
                xRot = player.xRot
            ) {
                origin = null
                attacking = false
            }
        }

        if (!success) {
            origin = null
            attacking = false
        }
    }

    private fun findTarget(player: Player): LivingEntity? {
        val level = mc.level ?: return null
        var best: LivingEntity? = null
        var bestDist = maxDistance * maxDistance

        for (entity in level.entitiesForRendering()) {
            if (entity !is LivingEntity || entity === player || !entity.isAlive) continue
            if (!targets.isSelected(entity)) continue
            if (entity is Player && (entity.isCreative || entity.isSpectator)) continue
            if (FriendRepository.isFriend(entity)) continue

            val dist = player.distanceToSqr(entity)
            if (dist <= bestDist) {
                bestDist = dist
                best = entity
            }
        }
        return best
    }

    override fun onDisable() {
        if (TeleportPath.active(this)) {
            TeleportPath.cancel(this)
        }
        origin = null
        attacking = false
    }
}
