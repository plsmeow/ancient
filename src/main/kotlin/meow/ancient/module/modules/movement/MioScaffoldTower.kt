package meow.ancient.module.modules.movement

import meow.ancient.client.extensions.mc
import net.minecraft.client.player.LocalPlayer
import net.minecraft.util.Mth
import net.minecraft.world.entity.ai.attributes.AttributeInstance
import net.minecraft.world.entity.ai.attributes.Attributes

object MioScaffoldTower {
    private const val FAST_FACTOR = 2.0f
    private const val GRIM_JUMP_STRENGTH = 0.36813f
    private const val CORRECTION_DELAY_NANOS = 200_000_000L

    private var owner: LocalPlayer? = null
    private var lastMode = "None"
    private var lastMovementSync = false
    private var lastFloorY = Int.MIN_VALUE
    private var modifiedJump: AttributeInstance? = null
    private var previousJumpStrength = 0.0
    @Volatile private var timerActive = false
    @Volatile private var lastCorrectionNanos = Long.MIN_VALUE

    fun reset() {
        restoreJumpStrength()
        timerActive = false
        owner = null
        lastMode = "None"
        lastMovementSync = false
        lastFloorY = Int.MIN_VALUE
    }

    fun onCorrection() {
        lastCorrectionNanos = System.nanoTime()
        timerActive = false
    }

    fun update(player: LocalPlayer, mode: String, hasTargets: Boolean, movementSync: Boolean) {
        restoreJumpStrength()
        timerActive = false
        if (mc.player !== player || mc.level !== player.level() || !player.isAlive || player.isRemoved || player.isPassenger) {
            reset()
            return
        }
        if (owner !== player || lastMode != mode || lastMovementSync != movementSync) {
            lastFloorY = Int.MIN_VALUE
        }
        owner = player
        lastMode = mode
        lastMovementSync = movementSync

        val floorY = Mth.floor(player.y)
        if (lastFloorY == floorY && !movementSync) return
        val fast = mode == "Fast"
        if (fast && correctionPending()) return
        lastFloorY = floorY
        if (mode == "None" || !stationaryJump(player)) return

        if (mode == "GrimV3") {
            if (player.onGround()) {
                val attribute = player.getAttribute(Attributes.JUMP_STRENGTH) ?: return
                previousJumpStrength = attribute.baseValue
                modifiedJump = attribute
                attribute.baseValue = GRIM_JUMP_STRENGTH.toDouble()
            }
        } else if (hasTargets && (mode == "Plain" || fast)) {
            player.setDeltaMovement(0.0, 0.41999998688698, 0.0)
            timerActive = fast
        }
    }

    fun timerMillis(original: Float): Float {
        val player = owner ?: return original
        if (mc.player !== player || mc.level !== player.level() || !player.isAlive || player.isRemoved || player.isPassenger || !ScaffoldModule.isEnabled()) {
            reset()
            return original
        }
        return if (timerActive && !mc.isPaused && stationaryJump(player) && !correctionPending()) original / FAST_FACTOR else original
    }

    private fun stationaryJump(player: LocalPlayer): Boolean =
        player.input.keyPresses.jump() && player.input.moveVector.x == 0.0f && player.input.moveVector.y == 0.0f

    private fun correctionPending(): Boolean {
        val correction = lastCorrectionNanos
        return correction != Long.MIN_VALUE && System.nanoTime() - correction < CORRECTION_DELAY_NANOS
    }

    private fun restoreJumpStrength() {
        val attribute = modifiedJump ?: return
        attribute.baseValue = previousJumpStrength
        modifiedJump = null
    }
}
