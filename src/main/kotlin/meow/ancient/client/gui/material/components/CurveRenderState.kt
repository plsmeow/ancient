package meow.ancient.client.gui.material.components

import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.renderpearl.api.pipeline.RenderPipeline
import meow.ancient.client.util.render.VulkanQuadRenderState
import net.minecraft.client.gui.navigation.ScreenRectangle
import net.minecraft.client.gui.render.TextureSetup
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.state.gui.GuiElementRenderState
import org.joml.Matrix3x2f
import org.joml.Matrix3x2fc
import kotlin.math.sqrt

internal class CurveRenderState(
    pose: Matrix3x2fc,
    private val samples: FloatArray,
    private val x: Float,
    private val y: Float,
    private val width: Float,
    private val height: Float,
    private val color: Int,
    private val fillColor: Int,
    private val thickness: Float,
    private val scissor: ScreenRectangle?
) : GuiElementRenderState {
    private val pose = Matrix3x2f(pose)
    private val bounds = VulkanQuadRenderState.computeBounds(this.pose, x - 2f, y - 2f, x + width + 2f, y + height + 2f, scissor)

    override fun pipeline(): RenderPipeline = RenderPipelines.GUI
    override fun textureSetup(): TextureSetup = TextureSetup.noTexture()
    override fun scissorArea(): ScreenRectangle? = scissor
    override fun bounds(): ScreenRectangle? = bounds

    override fun buildVertices(consumer: VertexConsumer) {
        val transparent = color and 0x00ffffff
        var i = 0
        while (i + 3 < samples.size) {
            val x1 = x + samples[i] * width
            val y1 = y + samples[i + 1] * height
            val x2 = x + samples[i + 2] * width
            val y2 = y + samples[i + 3] * height
            if (fillColor ushr 24 != 0 && x2 > x1) {
                vertex(consumer, x1, y1, fillColor)
                vertex(consumer, x1, y + height, fillColor and 0x00ffffff)
                vertex(consumer, x2, y + height, fillColor and 0x00ffffff)
                vertex(consumer, x2, y2, fillColor)
            }
            val dx = x2 - x1
            val dy = y2 - y1
            val length = sqrt(dx * dx + dy * dy)
            if (length > 0.0001f) {
                val nx = -dy / length
                val ny = dx / length
                val inner = thickness * 0.5f
                val outer = inner + 0.55f
                vertex(consumer, x1 + nx * inner, y1 + ny * inner, color)
                vertex(consumer, x1 - nx * inner, y1 - ny * inner, color)
                vertex(consumer, x2 - nx * inner, y2 - ny * inner, color)
                vertex(consumer, x2 + nx * inner, y2 + ny * inner, color)
                vertex(consumer, x1 + nx * outer, y1 + ny * outer, transparent)
                vertex(consumer, x1 + nx * inner, y1 + ny * inner, color)
                vertex(consumer, x2 + nx * inner, y2 + ny * inner, color)
                vertex(consumer, x2 + nx * outer, y2 + ny * outer, transparent)
                vertex(consumer, x1 - nx * inner, y1 - ny * inner, color)
                vertex(consumer, x1 - nx * outer, y1 - ny * outer, transparent)
                vertex(consumer, x2 - nx * outer, y2 - ny * outer, transparent)
                vertex(consumer, x2 - nx * inner, y2 - ny * inner, color)
            }
            i += 2
        }
    }

    private fun vertex(consumer: VertexConsumer, x: Float, y: Float, color: Int) {
        consumer.addVertexWith2DPose(pose, x, y).setColor(color)
    }
}
