package meow.ancient.module.modules.render

import meow.ancient.client.extensions.RENDER
import meow.ancient.client.extensions.mc
import meow.ancient.client.gui.material.M3Theme
import meow.ancient.client.util.friend.FriendRepository
import meow.ancient.client.util.other.color.Color4b
import meow.ancient.client.util.render.ProjectionUtil
import meow.ancient.client.util.render.RenderUtils
import meow.ancient.event.events.OverlayRenderEvent
import meow.ancient.module.Module
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.resources.Identifier
import net.minecraft.util.Mth
import net.minecraft.world.entity.player.Player
import sweetie.evaware.flora.api.Commando
import kotlin.math.atan2

object ArrowsModule : Module(
    name = "Arrows",
    category = RENDER,
    description = "Directional arrow indicators pointing towards other players"
) {
    val radius = number("Distance", 45.0, 10.0, 150.0, 1.0, 0)
    val size = number("Size", 12.0, 4.0, 40.0, 1.0, 0)
    val onlyNotVisible = boolean("Only Invisible", false)
    val friendColor = color("Friend Color", Color4b(0, 255, 0, 255))

    private val ARROW_TEXTURE = Identifier.fromNamespaceAndPath("ancient", "images/triangle.png")

    @Commando
    fun onOverlayRender(event: OverlayRenderEvent) {
        val player = mc.player ?: return
        val level = mc.level ?: return
        if (mc.gui.hud.isHidden) return

        val graphics = RenderUtils.currentGraphics ?: return

        val tickDelta = event.partialTicks
        val centerX = mc.window.guiScaledWidth / 2f
        val centerY = mc.window.guiScaledHeight / 2f

        val selfX = Mth.lerp(tickDelta.toDouble(), player.xOld, player.x)
        val selfZ = Mth.lerp(tickDelta.toDouble(), player.zOld, player.z)
        val yaw = Mth.rotLerp(tickDelta, player.yRotO, player.yRot)

        val arrowSize = size.value.toFloat()
        val arrowRadius = radius.value.toFloat()
        val palette = M3Theme.current
        val themeColor = palette.primary

        val pose = graphics.pose()

        for (entity in level.entitiesForRendering()) {
            if (entity !is Player || entity === player || !entity.isAlive) continue
            if (mc.connection?.getPlayerInfo(entity.uuid) == null) continue

            if (onlyNotVisible.value) {
                val proj = ProjectionUtil.worldToScreen(entity.x, entity.y + entity.bbHeight * 0.5, entity.z)
                if (proj != null && proj.x in 0f..mc.window.guiScaledWidth.toFloat() && proj.y in 0f..mc.window.guiScaledHeight.toFloat()) continue
            }

            val targetX = Mth.lerp(tickDelta.toDouble(), entity.xOld, entity.x)
            val targetZ = Mth.lerp(tickDelta.toDouble(), entity.zOld, entity.z)

            val dx = targetX - selfX
            val dz = targetZ - selfZ

            val angle = Math.toDegrees(atan2(dz, dx)).toFloat() - 90f
            val rotation = Mth.wrapDegrees(angle - yaw)

            val color = if (FriendRepository.isFriend(entity.scoreboardName)) {
                friendColor.value.argb
            } else {
                themeColor.argb
            }

            pose.pushMatrix()
            pose.translate(centerX, centerY)
            pose.rotate(Math.toRadians(rotation.toDouble()).toFloat())
            pose.translate(0f, -arrowRadius)

            graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                ARROW_TEXTURE,
                (-arrowSize / 2f).toInt(),
                (-arrowSize / 2f).toInt(),
                0f,
                0f,
                arrowSize.toInt(),
                arrowSize.toInt(),
                arrowSize.toInt(),
                arrowSize.toInt(),
                color
            )

            pose.popMatrix()
        }
    }
}
