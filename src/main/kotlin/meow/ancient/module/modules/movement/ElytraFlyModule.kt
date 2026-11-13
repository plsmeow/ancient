package meow.ancient.module.modules.movement

import meow.ancient.client.extensions.MOVEMENT
import meow.ancient.client.extensions.mc
import meow.ancient.client.util.inventory.SilentSwapUtil
import meow.ancient.client.util.rotation.MoveFixMode
import meow.ancient.client.util.rotation.Rotation
import meow.ancient.client.util.rotation.RotationManager
import meow.ancient.event.events.ClientDisconnectEvent
import meow.ancient.event.events.PlayerUpdateEvent
import meow.ancient.mixin.FireworkRocketEntityAccessor
import meow.ancient.module.Module
import meow.ancient.module.modules.combat.HitAuraModule
import meow.ancient.module.modules.player.PacketMineModule
import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.protocol.game.ServerboundUseItemPacket
import net.minecraft.util.Mth
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.projectile.FireworkRocketEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.SwingAnimation
import net.minecraft.world.phys.Vec3
import sweetie.evaware.flora.api.Commando
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

object ElytraFlyModule : Module(
    name = "ElytraFly",
    category = MOVEMENT,
    description = "Controls elytra motion with movement keys and hovers without input"
) {
    private val rotations by boolean("Rotations", true, "Turns toward the movement keys without moving the camera")
    private val fireworks by boolean("FireWorks", true, "Silently uses rockets from any hotbar slot while moving")
    private val horizontalSpeed by number("Horizontal Speed", 0.8, 0.1, 3.0, 0.05, 2, unit = "b/t")
    private val verticalSpeed by number("Vertical Speed", 0.5, 0.1, 2.0, 0.05, 2, unit = "b/t")
    private val turnSpeed by number("Turn Speed", 35.0, 5.0, 180.0, 5.0, 0, isVisible = { rotations })
    private val fireworkDelay by number("Firework Delay", 20.0, 1.0, 100.0, 1.0, 0, unit = "ticks", isVisible = { fireworks })

    private var owner: LocalPlayer? = null
    private var tick = 0L
    private var nextFireworkTick = 0L

    override fun onEnable() = reset()

    override fun onDisable() {
        InventoryMoveModule.cancelInventoryActions(this)
        reset()
    }

    @Commando
    fun onDisconnect(event: ClientDisconnectEvent) = reset()

    @Commando(priority = -128)
    fun onUpdate(event: PlayerUpdateEvent) {
        val player = mc.player ?: return
        if (owner !== player) {
            reset()
            owner = player
        }
        tick++
        if (!canFly(player)) {
            nextFireworkTick = tick
            return
        }
        if (rotations && !PacketMineModule.hasTarget() &&
            !(HitAuraModule.isEnabled() && HitAuraModule.currentTarget != null)) steer()
        if (getMotion(player)?.lengthSqr() != 0.0) useFirework(player)
    }

    // Applied after vanilla glide physics, immediately before its setDeltaMovement and move calls.
    // Camera-relative WASD is normalized so diagonal movement has the same horizontal speed.
    fun getMotion(player: LocalPlayer): Vec3? {
        if (!isEnabled() || !canFly(player)) return null
        val forward = forwardInput()
        val sideways = sidewaysInput()
        val vertical = verticalInput()
        if (forward == 0 && sideways == 0 && vertical == 0) return Vec3.ZERO

        val length = hypot(forward.toDouble(), sideways.toDouble())
        if (length == 0.0) return Vec3(0.0, vertical * verticalSpeed, 0.0)
        val yaw = Math.toRadians(RotationManager.clientYaw.toDouble())
        val scale = horizontalSpeed / length
        return Vec3(
            (-sin(yaw) * forward - cos(yaw) * sideways) * scale,
            vertical * verticalSpeed,
            (cos(yaw) * forward - sin(yaw) * sideways) * scale
        )
    }

    private fun steer() {
        // Camera-relative input prevents holding A/D from accumulating a turn every tick.
        val forward = forwardInput()
        val sideways = sidewaysInput()
        val vertical = verticalInput()
        if (forward == 0 && sideways == 0 && vertical == 0) return

        val yaw = RotationManager.clientYaw + if (forward != 0 || sideways != 0) {
            Math.toDegrees(atan2(sideways.toDouble(), forward.toDouble())).toFloat()
        } else 0f
        val pitch = -Math.toDegrees(atan2(vertical * verticalSpeed,
            if (forward != 0 || sideways != 0) horizontalSpeed else 0.0)).toFloat()
        val previous = RotationManager.getServerRotation()
        val step = turnSpeed.toFloat()
        RotationManager.setRotation(
            Rotation(previous.yaw + Mth.wrapDegrees(yaw - previous.yaw).coerceIn(-step, step),
                previous.pitch + (pitch - previous.pitch).coerceIn(-step, step)),
            moveFix = MoveFixMode.SILENT,
            keepTicks = 0
        )
    }

    // Swap before the movement packet, so the managed silent-swap mode can accept the hotbar change.
    private fun useFirework(player: LocalPlayer) {
        val level = mc.level ?: return
        if (!fireworks || tick < nextFireworkTick || !canFly(player) || player.isUsingItem) return
        if (level.entitiesForRendering().any {
                it is FireworkRocketEntity && !it.isRemoved &&
                    (it as FireworkRocketEntityAccessor).`ancient$getAttachedToEntity`() === player
            }) return

        val slot = (0..8).firstOrNull { usableRocket(player, player.inventory.getItem(it)) } ?: return
        SilentSwapUtil.withSlot(slot) {
            if (isEnabled() && canFly(player) && tick >= nextFireworkTick &&
                getMotion(player)?.lengthSqr() != 0.0 && usableRocket(player, player.inventory.getItem(slot))) {
                if (launchRocket(player, InteractionHand.MAIN_HAND)) {
                    nextFireworkTick = tick + fireworkDelay.toLong()
                }
            }
        }
    }

    private fun launchRocket(player: LocalPlayer, hand: InteractionHand): Boolean {
        val level = mc.level ?: return false
        val gameMode = mc.gameMode ?: return false
        if (!canFly(player) || player.isUsingItem) return false
        // The visible hand can still hold another item during a silent swap; use the flight rotation explicitly.
        val rotation = RotationManager.getServerRotation()
        RotationManager.lockOutgoingRotation()
        gameMode.startPrediction(level) { sequence ->
            ServerboundUseItemPacket(hand, sequence, rotation.yaw, rotation.pitch)
        }
        player.swing(hand, SwingAnimation.DEFAULT, false)
        return true
    }

    private fun usableRocket(player: LocalPlayer, stack: ItemStack): Boolean =
        !stack.isEmpty && stack.`is`(Items.FIREWORK_ROCKET) && !player.cooldowns.isOnCooldown(stack)

    private fun forwardInput(): Int =
        (if (mc.options.keyUp.isDown) 1 else 0) - (if (mc.options.keyDown.isDown) 1 else 0)

    private fun sidewaysInput(): Int =
        (if (mc.options.keyRight.isDown) 1 else 0) - (if (mc.options.keyLeft.isDown) 1 else 0)

    private fun verticalInput(): Int =
        (if (mc.options.keyJump.isDown) 1 else 0) - (if (mc.options.keyShift.isDown) 1 else 0)

    private fun canFly(player: LocalPlayer): Boolean = mc.player === player && mc.level != null &&
        player.connection.connection.isConnected && player.isAlive && !player.isSpectator && !player.isPassenger &&
        !player.abilities.flying && !player.isInWater && !player.isInLava && player.isFallFlying &&
        mc.gui.screen() == null && !InventoryMoveModule.isInventoryActionSlowed() &&
        player.containerMenu === player.inventoryMenu && player.containerMenu.carried.isEmpty &&
        LivingEntity.canGlideUsing(player.getItemBySlot(EquipmentSlot.CHEST), EquipmentSlot.CHEST)

    private fun reset() {
        owner = null
        tick = 0L
        nextFireworkTick = 0L
    }
}
