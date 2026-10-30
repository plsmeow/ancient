package meow.ancient.event.events

import com.mojang.blaze3d.vertex.PoseStack
import meow.ancient.event.types.Event
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.Camera

class WorldRenderEvent(
    val poseStack: PoseStack,
    val camera: Camera,
    val tickDelta: Float,
    val context: LevelRenderContext? = null
) : Event()
