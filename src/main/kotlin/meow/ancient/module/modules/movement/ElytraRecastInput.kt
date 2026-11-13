package meow.ancient.module.modules.movement

/** Keeps recasting on vanilla jump edges, with a single attempt after each interrupted glide. */
internal class ElytraRecastInput {
    private var wasGliding = false
    private var pendingRecast = false
    private var airborneTicks = 0
    private var previousJump = false

    fun jumpForTick(available: Boolean, gliding: Boolean, onGround: Boolean,
                    currentJump: Boolean, reportedJump: Boolean): Boolean {
        if (!available || !currentJump) {
            pendingRecast = false
            return currentJump
        }
        // Server metadata can end the glide before input is read. React here,
        // rather than spending an entire ordinary-movement tick before arming.
        if (wasGliding && !gliding) {
            pendingRecast = true
            wasGliding = false
        }
        if (gliding) {
            pendingRecast = false
            // Prepare a real release in the air and reset vanilla's jump delay.
            // On contact with the ground, normal sprint jumping adds momentum.
            return onGround
        }
        if (!pendingRecast || onGround) return currentJump

        // Vanilla sends the glide request before the new input packet. Both local
        // and last-reported input must contain a release, with two airborne ticks completed.
        if (airborneTicks < 2 || previousJump || reportedJump) return false
        pendingRecast = false
        return currentJump
    }

    fun afterMovement(available: Boolean, gliding: Boolean, onGround: Boolean, jump: Boolean) {
        if (!available || gliding) {
            pendingRecast = false
        } else if (wasGliding) {
            pendingRecast = true
        }
        wasGliding = gliding
        airborneTicks = if (onGround) 0 else (airborneTicks + 1).coerceAtMost(2)
        previousJump = jump
    }

    fun reset(gliding: Boolean = false, jump: Boolean = false) {
        wasGliding = gliding
        pendingRecast = false
        airborneTicks = 0
        previousJump = jump
    }
}
