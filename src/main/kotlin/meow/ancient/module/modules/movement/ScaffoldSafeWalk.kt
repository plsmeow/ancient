/* SPDX-License-Identifier: GPL-3.0-only. See docs/scaffold-port.md. */
package meow.ancient.module.modules.movement

import net.minecraft.world.phys.Vec3
import kotlin.math.abs

object ScaffoldSafeWalk {
    /** Matches ordinary sneak edge clipping, using the caller's confirmed support predicate. */
    @JvmStatic
    fun movement(movement: Vec3, supported: (Double, Double) -> Boolean): Vec3 {
        fun reduce(value: Double): Double = when {
            abs(value) <= 0.05 -> 0.0
            value > 0.0 -> value - 0.05
            else -> value + 0.05
        }
        var x = movement.x
        var z = movement.z
        while (x != 0.0 && !supported(x, 0.0)) x = reduce(x)
        while (z != 0.0 && !supported(0.0, z)) z = reduce(z)
        while ((x != 0.0 || z != 0.0) && !supported(x, z)) { x = reduce(x); z = reduce(z) }
        return Vec3(x, movement.y, z)
    }
}
