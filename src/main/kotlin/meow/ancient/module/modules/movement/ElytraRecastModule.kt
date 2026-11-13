package meow.ancient.module.modules.movement

import meow.ancient.client.extensions.MOVEMENT
import meow.ancient.client.extensions.mc
import meow.ancient.client.util.rotation.MoveFixMode
import meow.ancient.client.util.rotation.Rotation
import meow.ancient.client.util.rotation.RotationManager
import meow.ancient.event.events.ClientDisconnectEvent
import meow.ancient.event.events.PlayerUpdateEvent
import meow.ancient.module.Module
import meow.ancient.module.modules.combat.HitAuraModule
import meow.ancient.module.modules.combat.SpearKillModule
import meow.ancient.module.modules.player.PacketMineModule
import net.minecraft.client.player.ClientInput
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Input
import sweetie.evaware.flora.api.Commando
import kotlin.math.atan2

object ElytraRecastModule : Module(
    name = "ElytraRecast",
    category = MOVEMENT,
    description = "Reopens the elytra on jump edges and builds momentum through sprint jumping"
) {
    private val rotations by boolean("Rotations", true, "Keeps the flight angle low for faster bouncing")
    private val pitch by number("Pitch", 75.0, 45.0, 85.0, 1.0, 0, isVisible = { rotations })
    private var owner: LocalPlayer? = null
    private val recastInput = ElytraRecastInput()

    override fun onEnable() {
        reset()
        owner = mc.player
        recastInput.reset(owner?.isFallFlying == true, owner?.input?.keyPresses?.jump() == true)
    }

    override fun onDisable() = reset()

    @Commando
    fun onDisconnect(event: ClientDisconnectEvent) = reset()

    @Commando(priority = -128)
    fun onUpdate(event: PlayerUpdateEvent) {
        val player = mc.player ?: return
        if (!rotations || !canRecast(player) || !mc.options.keyJump.isDown ||
            (!player.isFallFlying && player.onGround()) || ElytraFlyModule.isEnabled() ||
            ElytraBoosterModule.isGrimSteering() ||
            PacketMineModule.hasTarget() || (HitAuraModule.isEnabled() && HitAuraModule.currentTarget != null) ||
            (SpearKillModule.isEnabled() && SpearKillModule.target != null)) return

        val forward = (if (mc.options.keyUp.isDown) 1 else 0) - (if (mc.options.keyDown.isDown) 1 else 0)
        val sideways = (if (mc.options.keyRight.isDown) 1 else 0) - (if (mc.options.keyLeft.isDown) 1 else 0)
        if (forward == 0 && sideways == 0) return
        val yaw = RotationManager.clientYaw + Math.toDegrees(atan2(sideways.toDouble(), forward.toDouble())).toFloat()
        RotationManager.setRotation(Rotation(yaw, pitch.toFloat()), moveFix = MoveFixMode.SILENT, keepTicks = 0)
    }

    // Change the actual input before vanilla handles the jump edge and glide request.
    // sendChanges then reports the same input used by the local movement simulation.
    fun onInputUpdate(input: ClientInput) {
        val player = mc.player ?: return
        if (!isEnabled() || player.input !== input || owner !== player) return
        val keys = input.keyPresses
        val jump = recastInput.jumpForTick(canRecast(player) && mc.options.keyJump.isDown,
            player.isFallFlying, player.onGround(), keys.jump(), player.lastSentInput.jump())
        if (jump != keys.jump()) {
            input.keyPresses = Input(keys.forward(), keys.backward(), keys.left(), keys.right(),
                jump, keys.shift(), keys.sprint())
        }
    }

    // Called after the entire LocalPlayer.aiStep, including landing and glide-state updates.
    fun onAfterMovement(player: LocalPlayer) {
        if (!isEnabled() || mc.player !== player) return
        if (owner !== player) {
            owner = player
            recastInput.reset(player.isFallFlying, player.input.keyPresses.jump())
            return
        }
        recastInput.afterMovement(canRecast(player) && mc.options.keyJump.isDown,
            player.isFallFlying, player.onGround(), player.input.keyPresses.jump())
    }

    private fun canRecast(player: LocalPlayer): Boolean = player.connection.connection.isConnected &&
        player.isAlive && !player.isSpectator && !player.abilities.flying && !player.isPassenger &&
        !player.onClimbable() && !player.isInWater && !player.isInLava &&
        !player.hasEffect(MobEffects.LEVITATION) && mc.gui.screen() == null &&
        !InventoryMoveModule.isInventoryActionSlowed() &&
        LivingEntity.canGlideUsing(player.getItemBySlot(EquipmentSlot.CHEST), EquipmentSlot.CHEST)

    private fun reset() {
        owner = null
        recastInput.reset()
    }
}
