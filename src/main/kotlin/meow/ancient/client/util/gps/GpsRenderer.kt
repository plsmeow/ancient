package meow.ancient.client.util.gps

import meow.ancient.client.extensions.mc
import meow.ancient.client.gui.material.M3Theme
import meow.ancient.client.util.other.color.Color4b
import meow.ancient.client.util.render.RenderUtils
import meow.ancient.client.util.render.engine.controls.ClientRenderPipeline
import meow.ancient.client.util.render.engine.controls.MatrixControl
import meow.ancient.client.util.render.font.Fonts
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.resources.Identifier
import net.minecraft.util.Mth
import kotlin.math.atan2
import kotlin.math.sqrt

object GpsRenderer {
    private val ARROW_TEXTURE = Identifier.fromNamespaceAndPath("ancient", "images/triangle.png")

    private const val ARROW_SIZE = 14f
    private const val ARROW_RADIUS = 22f
    private const val ANCHOR_Y = 34f

    @JvmField
    var enabled = false

    private var targetX = 0.0
    private var targetZ = 0.0

    fun setTarget(x: Double, z: Double) {
        targetX = x
        targetZ = z
        enabled = true
    }

    fun disable() {
        enabled = false
    }

    fun render(partialTicks: Float) {
        if (!enabled) return
        val player = mc.player ?: return
        if (mc.gui.hud.isHidden) return
        val graphics = RenderUtils.currentGraphics ?: return

        val selfX = Mth.lerp(partialTicks.toDouble(), player.xOld, player.x)
        val selfZ = Mth.lerp(partialTicks.toDouble(), player.zOld, player.z)
        val yaw = Mth.rotLerp(partialTicks, player.yRotO, player.yRot)

        val dx = targetX - selfX
        val dz = targetZ - selfZ
        val distance = sqrt(dx * dx + dz * dz).toInt()

        val angle = Math.toDegrees(atan2(dz, dx)).toFloat() - 90f
        val rotation = Mth.wrapDegrees(angle - yaw)

        val centerX = graphics.guiWidth().toFloat() / 2f
        val color = M3Theme.current.primary.argb

        val pose = graphics.pose()
        pose.pushMatrix()
        pose.translate(centerX, ANCHOR_Y)
        pose.rotate(Math.toRadians(rotation.toDouble()).toFloat())
        pose.translate(0f, -ARROW_RADIUS)
        graphics.blit(
            RenderPipelines.GUI_TEXTURED,
            ARROW_TEXTURE,
            (-ARROW_SIZE / 2f).toInt(),
            (-ARROW_SIZE / 2f).toInt(),
            0f,
            0f,
            ARROW_SIZE.toInt(),
            ARROW_SIZE.toInt(),
            ARROW_SIZE.toInt(),
            ARROW_SIZE.toInt(),
            color
        )
        pose.popMatrix()

        Fonts.google_sans_medium.drawCenteredString(
            MatrixControl.matrix4fStack,
            "${distance}m",
            centerX,
            ANCHOR_Y + 4f,
            7f,
            Color4b.WHITE,
            pipeline = ClientRenderPipeline.HUD_TEXT
        )
    }
}
