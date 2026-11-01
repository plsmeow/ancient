package meow.ancient.client.util.render

import meow.ancient.client.render.texture.texture.GLTexture
import meow.ancient.client.util.other.color.Color4b
import meow.ancient.client.util.render.engine.controls.ClientRenderPipeline
import meow.ancient.client.util.render.rect.RectBorder
import meow.ancient.client.util.render.rect.RectColors
import meow.ancient.client.util.render.rect.RectCorners
import meow.ancient.client.util.render.rect.RectRenderer
import meow.ancient.client.util.render.rect.RectStyle
import meow.ancient.client.util.render.rect.RectType
import meow.ancient.client.util.render.rect.RectUv
import net.minecraft.client.gui.GuiGraphicsExtractor
import org.joml.Matrix4f
import meow.ancient.module.modules.render.HudModule

object RenderUtils {
    var currentGraphics: GuiGraphicsExtractor? = null

    private fun renderVulkan(
        gfx: GuiGraphicsExtractor,
        matrix: Matrix4f,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        style: RectStyle
    ) {
        VulkanShapes.drawRect(gfx, x, y, width, height, style)
    }

    fun rect(
        matrix: Matrix4f,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        color: Color4b,
        pipeline: ClientRenderPipeline = ClientRenderPipeline.GUI_RECT
    ) {
        rect(
            matrix = matrix,
            x = x,
            y = y,
            width = width,
            height = height,
            style = RectStyle.solid(color),
            pipeline = pipeline
        )
    }

    fun hudBackground(
        matrix: Matrix4f,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        color: Color4b,
        radius: Float,
        borderWidth: Float = 0f,
        borderColor: Color4b = Color4b.TRANSPARENT,
        pipeline: ClientRenderPipeline = ClientRenderPipeline.HUD_RECT,
        opacity: Float = 1f
    ) {
        val border = if (borderWidth > 0f && borderColor.a > 0) RectBorder(borderWidth, borderColor) else RectBorder.NONE
        val style = RectStyle(
            colors = RectColors.solid(color),
            corners = RectCorners.all(radius),
            border = border,
            alpha = opacity
        )
        val gfx = currentGraphics
        if (HudModule.isEnabled() && HudModule.blur && gfx != null) {
            VulkanShapes.drawBlurredRect(gfx, x, y, width, height, style)
        } else {
            rect(matrix, x, y, width, height, style, pipeline)
        }
    }

    fun roundedRect(
        matrix: Matrix4f,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        color: Color4b,
        radius: Float,
        pipeline: ClientRenderPipeline = ClientRenderPipeline.GUI_RECT
    ) {
        rect(
            matrix = matrix,
            x = x,
            y = y,
            width = width,
            height = height,
            style = RectStyle.rounded(color, radius),
            pipeline = pipeline
        )
    }

    fun m3Shape(
        matrix: Matrix4f,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        shape: meow.ancient.client.gui.material.M3Shape,
        color: Color4b,
        borderColor: Color4b = Color4b.TRANSPARENT,
        borderWidth: Float = 0f,
        opacity: Float = 1f
    ) {
        val gfx = currentGraphics
        if (gfx != null) {
            VulkanShapes.drawM3Shape(
                gfx = gfx,
                x = x,
                y = y,
                width = width,
                height = height,
                shape = shape,
                color = color.argb,
                borderColor = borderColor.argb,
                borderWidth = borderWidth,
                opacity = opacity
            )
        }
    }

    fun gradientRect(
        matrix: Matrix4f,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        topLeft: Color4b,
        topRight: Color4b,
        bottomRight: Color4b,
        bottomLeft: Color4b,
        pipeline: ClientRenderPipeline = ClientRenderPipeline.GUI_RECT
    ) {
        rect(
            matrix = matrix,
            x = x,
            y = y,
            width = width,
            height = height,
            style = RectStyle.gradient(topLeft, topRight, bottomRight, bottomLeft),
            pipeline = pipeline
        )
    }

    fun texturedRect(
        matrix: Matrix4f,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        textureId: Int,
        tint: Color4b = Color4b.WHITE,
        mix: Float = 0f,
        alpha: Float = 1f,
        uv: RectUv = RectUv.DEFAULT,
        pipeline: ClientRenderPipeline = ClientRenderPipeline.GUI_RECT
    ) {
        rect(
            matrix = matrix,
            x = x,
            y = y,
            width = width,
            height = height,
            style = RectStyle.textured(
                textureId = textureId,
                tint = tint,
                mix = mix,
                alpha = alpha,
                uv = uv
            ),
            pipeline = pipeline
        )
    }

    fun texturedRect(
        matrix: Matrix4f,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        texture: GLTexture,
        tint: Color4b = Color4b.WHITE,
        mix: Float = 0f,
        alpha: Float = 1f,
        uv: RectUv = RectUv.DEFAULT,
        pipeline: ClientRenderPipeline = ClientRenderPipeline.GUI_RECT
    ) {
        texturedRect(
            matrix = matrix,
            x = x,
            y = y,
            width = width,
            height = height,
            textureId = texture.texId,
            tint = tint,
            mix = mix,
            alpha = alpha,
            uv = uv,
            pipeline = pipeline
        )
    }

    fun rect(
        matrix: Matrix4f,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        style: RectStyle,
        pipeline: ClientRenderPipeline = ClientRenderPipeline.GUI_RECT
    ) {
        val gfx = currentGraphics
        if (gfx != null) {
            renderVulkan(gfx, matrix, x, y, width, height, style)
            return
        }

        RectRenderer.emit(
            matrix = matrix,
            x = x,
            y = y,
            width = width,
            height = height,
            style = style,
            pipeline = pipeline
        )
    }

    fun rect(
        matrix: Matrix4f,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        colors: RectColors,
        corners: RectCorners = RectCorners.ZERO,
        border: RectBorder = RectBorder.NONE,
        mode: Float = 0f,
        textureId: Int = 0,
        mix: Float = 0f,
        alpha: Float = 1f,
        uv: RectUv = RectUv.DEFAULT,
        type: RectType = RectType.BASIC,
        pipeline: ClientRenderPipeline = ClientRenderPipeline.GUI_RECT
    ) {
        rect(
            matrix = matrix,
            x = x,
            y = y,
            width = width,
            height = height,
            style = RectStyle(
                colors = colors,
                corners = corners,
                border = border,
                mode = mode,
                textureId = textureId,
                mix = mix,
                alpha = alpha,
                uv = uv,
                type = type
            ),
            pipeline = pipeline
        )
    }

    fun roundedRectWithBorder(
        matrix: Matrix4f,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        color: Color4b,
        radius: Float,
        borderWidth: Float,
        borderColor: Color4b,
        pipeline: ClientRenderPipeline = ClientRenderPipeline.GUI_RECT
    ) {
        rect(
            matrix = matrix,
            x = x,
            y = y,
            width = width,
            height = height,
            colors = RectColors.solid(color),
            corners = RectCorners.all(radius),
            border = if (borderWidth > 0f) RectBorder(borderWidth, borderColor) else RectBorder.NONE,
            pipeline = pipeline
        )
    }

    fun customRoundedRect(
        matrix: Matrix4f,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        color: Color4b,
        topLeft: Float,
        topRight: Float,
        bottomRight: Float,
        bottomLeft: Float,
        borderWidth: Float = 0f,
        borderColor: Color4b = Color4b.TRANSPARENT,
        pipeline: ClientRenderPipeline = ClientRenderPipeline.GUI_RECT
    ) {
        rect(
            matrix = matrix,
            x = x,
            y = y,
            width = width,
            height = height,
            colors = RectColors.solid(color),
            corners = RectCorners(topLeft, topRight, bottomRight, bottomLeft),
            border = if (borderWidth > 0f) RectBorder(borderWidth, borderColor) else RectBorder.NONE,
            pipeline = pipeline
        )
    }
}
