package meow.ancient.module.modules.render

import com.mojang.blaze3d.vertex.PoseStack
import meow.ancient.client.extensions.RENDER
import meow.ancient.module.Module
import net.minecraft.world.entity.HumanoidArm

object ViewModelModule : Module(
    name = "ViewModel",
    category = RENDER,
    description = "Adjust first-person hand and item positions"
) {
    val offsetLeftX = number("Left Hand X", 0.0, -2.0, 2.0, 0.05, 2)
    val offsetLeftY = number("Left Hand Y", 0.0, -2.0, 2.0, 0.05, 2)
    val offsetLeftZ = number("Left Hand Z", 0.0, -2.0, 2.0, 0.05, 2)

    val offsetRightX = number("Right Hand X", 0.0, -2.0, 2.0, 0.05, 2)
    val offsetRightY = number("Right Hand Y", 0.0, -2.0, 2.0, 0.05, 2)
    val offsetRightZ = number("Right Hand Z", 0.0, -2.0, 2.0, 0.05, 2)

    fun applyHandPosition(poseStack: PoseStack, arm: HumanoidArm) {
        if (!isEnabled()) return
        if (arm == HumanoidArm.LEFT) {
            poseStack.translate(offsetLeftX.value.toFloat(), offsetLeftY.value.toFloat(), offsetLeftZ.value.toFloat())
        } else {
            poseStack.translate(offsetRightX.value.toFloat(), offsetRightY.value.toFloat(), offsetRightZ.value.toFloat())
        }
    }
}
