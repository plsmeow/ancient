package meow.ancient.module.modules.movement

import meow.ancient.client.extensions.MOVEMENT
import meow.ancient.client.extensions.inGame
import meow.ancient.client.extensions.mc
import meow.ancient.event.events.PlayerUpdateEvent
import meow.ancient.module.Module
import meow.ancient.module.modules.player.ElytraHelperModule
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ServerboundPlayerAbilitiesPacket
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity
import net.minecraft.world.phys.Vec3
import sweetie.evaware.flora.api.Commando
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object HighJumpModule : Module(
    name = "High Jump",
    category = MOVEMENT,
    description = "Various high jump exploits"
) {
    val modeSetting = mode("Mode", "Funtime", listOf("Funtime", "Grim Shulker", "Funsky Elytra"))
    val mode by modeSetting

    val funtimeJumpY = number("Funtime JumpY", 0.42, 0.1, 1.0, 0.01, 2, isVisible = { mode == "Funtime" })
    val shulkerJumpY = number("Shulker JumpY", 1.0, 0.5, 5.0, 0.1, 1, isVisible = { mode == "Grim Shulker" })
    val funskyVelocityY = number("Funsky Velocity Y", 1.5, 0.5, 5.0, 0.1, 1, isVisible = { mode == "Funsky Elytra" })
    val funskyVelocityForward = number("Funsky Forward", 0.0, 0.0, 5.0, 0.1, 1, isVisible = { mode == "Funsky Elytra" })
    val funskyDelay = number("Funsky Delay", 2.0, 1.0, 10.0, 1.0, 0, isVisible = { mode == "Funsky Elytra" })
    val funskyWaitTicks = number("Wait Ticks", 10.0, 1.0, 60.0, 1.0, 0, isVisible = { mode == "Funsky Elytra" })

    private enum class FunskyStage { IDLE, AFTER_COMMAND, AFTER_ABILITY, LAUNCHED }
    private var funskyStage = FunskyStage.IDLE
    private var funskyTickCounter = 0
    private var funskySwappedToElytra = false

    override fun onEnable() {
        funskyStage = FunskyStage.IDLE
        funskyTickCounter = 0
        funskySwappedToElytra = false
    }

    override fun onDisable() {
        val player = mc.player ?: return
        if (funskySwappedToElytra) {
            ElytraHelperModule.swapChestEquipment(player)
            funskySwappedToElytra = false
        }
        player.abilities.flying = false
        player.connection.send(ServerboundPlayerAbilitiesPacket(player.abilities))
    }

    @Commando
    fun onUpdate(event: PlayerUpdateEvent) {
        if (!inGame) return
        when (mode) {
            "Funtime" -> handleFuntime()
            "Grim Shulker" -> handleGrimShulker()
            "Funsky Elytra" -> handleFunskyElytra()
        }
    }

    private fun handleFuntime() {
        val player = mc.player ?: return
        if (player.onGround()) {
            player.deltaMovement = Vec3(player.deltaMovement.x, funtimeJumpY.value, player.deltaMovement.z)
        }
    }

    private fun handleGrimShulker() {
        val player = mc.player ?: return
        val level = mc.level ?: return
        val origin = player.blockPosition()

        for (pos in BlockPos.betweenClosed(origin.offset(-1, -1, -1), origin.offset(1, 1, 1))) {
            val be = level.getBlockEntity(pos) as? ShulkerBoxBlockEntity ?: continue
            val stage = be.animationStatus
            if (stage != ShulkerBoxBlockEntity.AnimationStatus.OPENING && stage != ShulkerBoxBlockEntity.AnimationStatus.OPENED) continue

            val dx = player.x - (pos.x + 0.5)
            val dz = player.z - (pos.z + 0.5)
            if (sqrt(dx * dx + dz * dz) > 1.0) continue

            player.deltaMovement = Vec3(player.deltaMovement.x, shulkerJumpY.value, player.deltaMovement.z)
            break
        }
    }

    private fun handleFunskyElytra() {
        val player = mc.player ?: return
        when (funskyStage) {
            FunskyStage.IDLE -> {
                if (!player.getItemBySlot(EquipmentSlot.CHEST).`is`(Items.ELYTRA)) {
                    if (funskyTickCounter == 0) {
                        ElytraHelperModule.swapChestEquipment(player)
                        funskySwappedToElytra = true
                    }
                    funskyTickCounter++
                    if (funskyTickCounter >= 20) {
                        setEnabled(false)
                    }
                    return
                }

                player.connection.sendCommand("fly")
                funskyStage = FunskyStage.AFTER_COMMAND
                funskyTickCounter = 0
            }
            FunskyStage.AFTER_COMMAND -> {
                funskyTickCounter++
                if (funskyTickCounter >= funskyDelay.value.toInt()) {
                    player.abilities.flying = true
                    player.connection.send(ServerboundPlayerAbilitiesPacket(player.abilities))
                    funskyStage = FunskyStage.AFTER_ABILITY
                    funskyTickCounter = 0
                }
            }
            FunskyStage.AFTER_ABILITY -> {
                funskyTickCounter++
                if (funskyTickCounter >= funskyDelay.value.toInt()) {
                    var vx = player.deltaMovement.x
                    var vz = player.deltaMovement.z
                    val forward = funskyVelocityForward.value
                    if (forward > 0) {
                        val yawRad = Math.toRadians(player.yRot.toDouble())
                        vx = -sin(yawRad) * forward
                        vz = cos(yawRad) * forward
                    }
                    player.deltaMovement = Vec3(vx, funskyVelocityY.value, vz)
                    funskyStage = FunskyStage.LAUNCHED
                    funskyTickCounter = 0
                }
            }
            FunskyStage.LAUNCHED -> {
                funskyTickCounter++
                if (funskyTickCounter >= funskyWaitTicks.value.toInt()) {
                    setEnabled(false)
                }
            }
        }
    }
}
