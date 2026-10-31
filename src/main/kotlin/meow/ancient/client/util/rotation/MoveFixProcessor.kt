package meow.ancient.client.util.rotation

import meow.ancient.client.extensions.mc
import meow.ancient.client.util.movement.DirectionalInput
import meow.ancient.client.util.movement.MoveUtil
import net.minecraft.client.player.ClientInput
import net.minecraft.util.Mth
import net.minecraft.world.entity.player.Input
import net.minecraft.world.phys.Vec2

typealias MoveFixUtil = MoveFixProcessor

object MoveFixProcessor {
    var hasForwardInput: Boolean = false
        private set

    fun calculateImpulse(positive: Boolean, negative: Boolean): Float {
        return when {
            positive == negative -> 0.0f
            positive -> 1.0f
            else -> -1.0f
        }
    }

    fun shouldOverrideForwardImpulse(input: ClientInput): Boolean {
        return false
    }

    fun reset() {
        hasForwardInput = false
    }

    fun transformDirection(input: DirectionalInput, cameraYaw: Float, serverYaw: Float): DirectionalInput {
        val z = (if (input.forwards) 1.0f else 0.0f) - (if (input.backwards) 1.0f else 0.0f)
        val x = (if (input.left) 1.0f else 0.0f) - (if (input.right) 1.0f else 0.0f)

        if (z == 0.0f && x == 0.0f) {
            return DirectionalInput.NONE
        }

        val deltaYaw = (cameraYaw - serverYaw) * Mth.DEG_TO_RAD

        val cos = Mth.cos(deltaYaw.toDouble())
        val sin = Mth.sin(deltaYaw.toDouble())

        val newX = x * cos - z * sin
        val newZ = z * cos + x * sin

        val movementSideways = Math.round(newX).toFloat()
        val movementForward = Math.round(newZ).toFloat()

        return DirectionalInput(movementForward, movementSideways)
    }

    fun applyMoveFix(
        input: ClientInput,
        mode: MoveFixMode,
        serverYaw: Float,
        cameraYaw: Float
    ) {
        val rawDirection = DirectionalInput(input)
        hasForwardInput = rawDirection.forwards

        when (mode) {
            MoveFixMode.OFF, MoveFixMode.STRICT -> return
            MoveFixMode.SILENT -> {
                if (!rawDirection.isMoving) {
                    hasForwardInput = false
                    return
                }

                val transformed = transformDirection(rawDirection, cameraYaw, serverYaw)
                hasForwardInput = transformed.forwards

                val currentKeys = input.keyPresses
                val wantsSprint = (currentKeys.sprint() || MoveUtil.isKeyPressed(mc.options.keySprint)) && transformed.forwards

                input.keyPresses = Input(
                    transformed.forwards,
                    transformed.backwards,
                    transformed.left,
                    transformed.right,
                    currentKeys.jump(),
                    currentKeys.shift(),
                    wantsSprint
                )

                val f = calculateImpulse(transformed.forwards, transformed.backwards)
                val g = calculateImpulse(transformed.left, transformed.right)
                input.moveVector = Vec2(g, f).normalized()
            }
            MoveFixMode.FREE -> {
                val z = calculateImpulse(rawDirection.forwards, rawDirection.backwards)
                val x = calculateImpulse(rawDirection.left, rawDirection.right)
                if (z == 0.0f && x == 0.0f) return

                val deltaRad = (cameraYaw - serverYaw) * Mth.DEG_TO_RAD
                val cos = Mth.cos(deltaRad.toDouble())
                val sin = Mth.sin(deltaRad.toDouble())

                val newX = x * cos - z * sin
                val newZ = z * cos + x * sin

                input.moveVector = Vec2(newX, newZ).normalized()

                val currentKeys = input.keyPresses
                val wantsSprint = currentKeys.sprint() || MoveUtil.isKeyPressed(mc.options.keySprint) || (mc.player?.isSprinting == true && hasForwardInput)
                if (wantsSprint != currentKeys.sprint()) {
                    input.keyPresses = Input(
                        currentKeys.forward(),
                        currentKeys.backward(),
                        currentKeys.left(),
                        currentKeys.right(),
                        currentKeys.jump(),
                        currentKeys.shift(),
                        wantsSprint
                    )
                }
            }
        }
    }

    fun getDirectionYaw(baseYaw: Float, forward: Float, sideways: Float): Float {
        var yaw = baseYaw
        if (forward != 0.0f) {
            if (sideways > 0.0f) {
                yaw += if (forward > 0.0f) -45.0f else 45.0f
            } else if (sideways < 0.0f) {
                yaw += if (forward > 0.0f) 45.0f else -45.0f
            }
            if (forward < 0.0f) {
                yaw += 180.0f
            }
        } else if (sideways != 0.0f) {
            yaw += if (sideways > 0.0f) -90.0f else 90.0f
        }
        return Mth.wrapDegrees(yaw)
    }
}
