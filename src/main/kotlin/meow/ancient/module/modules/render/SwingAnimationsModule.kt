package meow.ancient.module.modules.render

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import meow.ancient.client.extensions.RENDER
import meow.ancient.client.extensions.mc
import meow.ancient.module.Module
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState
import net.minecraft.util.Mth
import net.minecraft.world.entity.HumanoidArm
import kotlin.math.PI
import kotlin.math.sin

object SwingAnimationsModule : Module(
    name = "SwingAnimations",
    category = RENDER,
    description = "Анимация рук"
) {
    private val mode by mode(
        "Мод",
        "None",
        listOf("None", "Smooth", "Self", "Self 2", "Down", "Forward", "Touch", "BlockHit", "Pander", "Curt")
    )
    private val power by number("Сила", 3.0, 0.0, 10.0, 1.0, 0, isVisible = {
        mode != "Pander" && mode != "BlockHit" && mode != "Forward" && mode != "Curt" && mode != "None"
    })
    val speed by number("Скорость", 7.0, 0.0, 10.0, 1.0, 0)
    private val angle by number("Угол", 90.0, 0.0, 360.0, 5.0, 0, isVisible = {
        mode == "Self" || mode == "Self 2"
    })

    @JvmStatic
    fun swingDuration(): Int = 25 - speed.toInt() * 2

    fun renderSwordAnimation(
        matrices: PoseStack,
        swingProgress: Float,
        arm: HumanoidArm,
        handState: FirstPersonHandsAndItemsRenderState
    ) {
        val anim = sin(swingProgress * (PI / 2) * 2).toFloat()
        val sin2 = Mth.sin((Mth.sqrt(swingProgress) * PI.toFloat()).toDouble())
        when (mode) {
            "None" -> {
                val f = -0.4f * Mth.sin((Mth.sqrt(swingProgress) * 3.1415927f).toDouble())
                val g = 0.2f * Mth.sin((Mth.sqrt(swingProgress) * 6.2831855f).toDouble())
                val h = -0.2f * Mth.sin((swingProgress * 3.1415927f).toDouble())
                matrices.translate(f, g, h)
                val i = if (arm == HumanoidArm.RIGHT) 1 else -1
                val f2 = Mth.sin((swingProgress * swingProgress * 3.1415927f).toDouble())
                matrices.rotateDegrees(Axis.YP, i * (45.0f + f2 * -20.0f))
                val g2 = Mth.sin((Mth.sqrt(swingProgress) * 3.1415927f).toDouble())
                matrices.rotateDegrees(Axis.ZP, i * g2 * -20.0f)
                matrices.rotateDegrees(Axis.XP, g2 * -80.0f)
                matrices.rotateDegrees(Axis.YP, i * -45.0f)
            }

            "Smooth" -> {
                val swingPower = power.toFloat() * 10.0f
                val f = Mth.sin((swingProgress * swingProgress * PI.toFloat()).toDouble())
                matrices.rotateDegrees(Axis.YP, 45.0f + f * (-swingPower / 4.0f))
                Mth.sin((Mth.sqrt(swingProgress * swingProgress) * PI.toFloat()).toDouble())
                matrices.rotateDegrees(Axis.ZP, sin2 * -(swingPower / 4.0f))
                matrices.rotateDegrees(Axis.XP, sin2 * -swingPower)
                matrices.rotateDegrees(Axis.YP, -45.0f)
            }

            "Self 2" -> {
                matrices.rotateDegrees(Axis.YP, 90.0f)
                matrices.rotateDegrees(Axis.ZP, -30.0f)
                matrices.rotateDegrees(Axis.XP, -angle.toFloat() - (power.toFloat() * 10) * anim)
            }

            "Forward" -> {
                matrices.translate(0.0, 0.0, -0.3 * sin2)
                matrices.rotateDegrees(Axis.XP, sin2 * -35)
                matrices.rotateDegrees(Axis.ZP, sin2 * 35)
            }

            "Self" -> {
                matrices.rotateDegrees(Axis.YP, 90.0f)
                matrices.rotateDegrees(Axis.ZP, -60.0f)
                matrices.rotateDegrees(Axis.XP, -angle.toFloat() - (power.toFloat() * 10) * anim)
            }

            "Down" -> {
                matrices.translate(0.56f, -0.52f - (anim * power.toFloat() / 24), -0.72f)
                matrices.rotateDegrees(Axis.YP, 90.0f)
                matrices.rotateDegrees(Axis.ZP, -30.0f)
                matrices.rotateDegrees(Axis.XP, -90.0f)
            }

            "Touch" -> {
                matrices.scale(1f, 1f, 1 + anim * power.toFloat() / 4)
                matrices.translate(0.0, 0.0, -0.265)
                matrices.rotateDegrees(Axis.XP, -100.0f)
            }

            "Curt" -> {
                val sqrtSwing = Mth.sqrt(swingProgress)
                val sinMain = Mth.sin((sqrtSwing * PI.toFloat()).toDouble())
                val sinExtra = Mth.sin((swingProgress * PI.toFloat()).toDouble())
                matrices.translate(0.4f - sinMain * 0.2f, -0.2f + sinMain * 0.3f, -0.5f - sinExtra * 0.2f)
                matrices.rotateDegrees(Axis.YP, 91.0f)
                matrices.rotateDegrees(Axis.ZP, -40 + sinMain * -100)
                matrices.rotateDegrees(Axis.XP, -60.0f)
            }

            "Pander" -> {
                matrices.scale(0.8f, 0.8f, 0.8f)
                val anim2 = 1.0f - Mth.lerp(
                    mc.deltaTracker.getGameTimeDeltaPartialTick(true),
                    handState.oldMainHandHeight,
                    handState.mainHandHeight
                )
                matrices.translate(0.3f - anim * 0.15f, 0.2f - anim2 * 0.12f, -0.15f - anim * 0.13f)
                matrices.rotateDegrees(Axis.YP, 76 - (10 * anim))
                matrices.rotateDegrees(Axis.ZP, -16 - (8 * anim))
                matrices.rotateDegrees(Axis.XP, -83 - (26 * anim))
            }

            "BlockHit" -> {
                val f = Mth.sin((swingProgress * swingProgress * PI.toFloat()).toDouble())
                matrices.rotateDegrees(Axis.YP, 45.0f)
                val g = Mth.sin((Mth.sqrt(swingProgress) * PI.toFloat()).toDouble())
                matrices.rotateDegrees(Axis.XP, f * -20.0f)
                matrices.rotateDegrees(Axis.ZP, g * -20.0f)
                matrices.rotateDegrees(Axis.XP, g * -80.0f)
                matrices.translate(0.4f, 0.2f, 0.2f)
                matrices.translate(-0.5f, 0.08f, 0.0f)
                matrices.rotateDegrees(Axis.YP, 20.0f)
                matrices.rotateDegrees(Axis.XP, -80.0f)
                matrices.rotateDegrees(Axis.YP, 20.0f)
            }
        }
    }
}
