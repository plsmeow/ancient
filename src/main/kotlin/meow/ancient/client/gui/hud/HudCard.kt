package meow.ancient.client.gui.hud

import meow.ancient.client.gui.material.AnimFloat
import meow.ancient.client.gui.material.M3Theme
import meow.ancient.client.util.other.color.Color4b
import meow.ancient.client.util.render.RenderUtils
import meow.ancient.client.util.render.engine.controls.ClientRenderPipeline
import meow.ancient.client.util.render.font.Fonts
import net.minecraft.client.gui.components.PlayerFaceExtractor
import net.minecraft.world.entity.player.PlayerSkin
import org.joml.Matrix4f

internal enum class HudForm { WATERMARK, CHIP, LIST, ARRAY, TARGET }

internal class HudRow(var key: String = "") {
    var text = ""
        private set
    var detail = ""
        private set
    var width = 0f
        private set
    var textWidth = 0f
        private set
    var detailWidth = 0f
        private set
    var highlighted = false
    var active = false
    val opacity = AnimFloat(speed = 18f)
    val position = AnimFloat(speed = 18f)
    private var sourceText = ""
    private var sourceDetail = ""

    fun set(text: String, detail: String, highlighted: Boolean) {
        if (sourceText != text) {
            sourceText = text
            this.text = hudEllipsize(text, 132f, 6.9f)
            textWidth = Fonts.google_sans_medium.width(this.text, 6.9f)
        }
        if (sourceDetail != detail) {
            sourceDetail = detail
            this.detail = hudEllipsize(detail, 68f, 6.5f)
            detailWidth = Fonts.google_sans_regular.width(this.detail, 6.5f)
        }
        width = textWidth + if (this.detail.isEmpty()) 0f else detailWidth + 10f
        this.highlighted = highlighted
        active = true
    }

    fun draw(matrix: Matrix4f, x: Float, y: Float, available: Float, scale: Float, alpha: Float) {
        val palette = M3Theme.current
        Fonts.google_sans_medium.drawString(matrix, text, x, y, 6.9f * scale,
            palette.onSurface.fade(alpha), pipeline = ClientRenderPipeline.HUD_TEXT)
        if (detail.isNotEmpty()) Fonts.google_sans_regular.drawString(matrix, detail,
            x + available - detailWidth * scale, y + 0.2f * scale, 6.5f * scale,
            (if (highlighted) palette.tertiary else palette.onSurfaceVariant).fade(alpha),
            pipeline = ClientRenderPipeline.HUD_TEXT)
    }
}

internal fun hudEllipsize(text: String, width: Float, size: Float): String {
    val font = Fonts.google_sans_medium
    if (font.width(text, size) <= width) return text
    val suffix = "…"
    val suffixWidth = font.width(suffix, size)
    if (width < suffixWidth) return ""
    var used = 0f
    var end = 0
    while (end < text.length) {
        if (text[end] == '§' && end + 1 < text.length) {
            end += 2
            continue
        }
        val code = text.codePointAt(end)
        val advance = font.advance(code) * size
        if (used + advance + suffixWidth > width) break
        used += advance
        end += Character.charCount(code)
    }
    return text.substring(0, end) + suffix
}

internal class HudCard(
    val title: String,
    private val form: HudForm,
    private val emptyPreview: String = "",
    previewDetail: String = ""
) {
    val rows = ArrayList<HudRow>()
    var rowCount = 0
        private set
    var enabled = false
        private set
    var width = 0f
        private set
    var height = 0f
        private set
    var healthFraction = 0f
    var absorptionFraction = 0f
    var skin: PlayerSkin? = null
    var initial = ""
    var hasHealth = true
    var defaultX = 0f
    var defaultY = 0f
    private val fade = AnimFloat(speed = 14f)
    private val animatedWidth = AnimFloat(speed = 16f)
    private val animatedHeight = AnimFloat(speed = 16f)
    private val health = AnimFloat(speed = 12f)
    private val absorption = AnimFloat(speed = 12f)
    private val titleWidth = Fonts.google_sans_medium.width(title, 6.7f)
    private val previewRow = HudRow("preview").apply { set(emptyPreview, previewDetail, false) }
    private val previewOpacity = AnimFloat(speed = 18f)
    private var preview = false
    private var drawScale = 1f
    private var rowAlignment = HudAlignment.RIGHT
    val hitTest: ((Float, Float) -> Boolean)? = if (form == HudForm.ARRAY) {
        { x, y -> containsArrayRow(x, y) }
    } else null

    val isVisible: Boolean get() = fade.value > 0.002f && width > 0f && height > 0f

    fun begin(enabled: Boolean) {
        this.enabled = enabled
        rowCount = 0
        var i = 0
        while (i < rows.size) rows[i++].active = false
    }

    fun add(text: String, detail: String = "", highlighted: Boolean = false, key: String = text) {
        var index = rowCount
        while (index < rows.size && rows[index].key != key) index++
        if (index == rows.size) {
            index = rowCount
            while (index < rows.size && rows[index].opacity.value > 0.002f) index++
            if (index < rows.size) {
                rows[index].key = key
                rows[index].position.setImmediate(rowCount * 12f + 3f)
            }
        }
        val row: HudRow
        if (index == rows.size) {
            row = HudRow(key)
            rows.add(row)
            if (rowCount < rows.lastIndex) {
                rows[rows.lastIndex] = rows[rowCount]
                rows[rowCount] = row
            }
            row.position.setImmediate(rowCount * 12f + 3f)
        } else {
            row = rows[index]
            if (index != rowCount) {
                rows[index] = rows[rowCount]
                rows[rowCount] = row
            }
        }
        row.set(text, detail, highlighted)
        rowCount++
    }

    fun setPreview(text: String, detail: String = "") {
        previewRow.set(text, detail, false)
    }

    fun animate(deltaSeconds: Float, editing: Boolean, maxWidth: Float, maxHeight: Float) {
        preview = editing && rowCount == 0 && emptyPreview.isNotEmpty()
        val shown = enabled && (rowCount > 0 || preview)
        fade.update(if (shown) 1f else 0f, deltaSeconds)
        previewOpacity.update(if (preview && enabled) 1f else 0f, deltaSeconds)
        var naturalWidth = 0f
        var listHeight = 0f
        var i = 0
        while (i < rows.size) {
            val row = rows[i]
            val active = i < rowCount && row.active
            val opacity = row.opacity.update(if (active && enabled) 1f else 0f, deltaSeconds)
            if (active) row.position.update(i * 12f, deltaSeconds)
            if (active || opacity > 0.002f) {
                if (form == HudForm.WATERMARK) {
                    if (active) naturalWidth += row.width + (if (i > 0) 9f else 0f)
                } else naturalWidth = maxOf(naturalWidth, row.width)
            }
            listHeight += if (active) 12f else 12f * opacity
            i++
        }
        if (previewOpacity.value > 0.002f) {
            naturalWidth = maxOf(naturalWidth, previewRow.width)
            listHeight = maxOf(listHeight, 12f)
        }
        val desiredWidth: Float
        val desiredHeight: Float
        when (form) {
            HudForm.WATERMARK -> {
                desiredWidth = naturalWidth + 14f
                desiredHeight = 19f
            }
            HudForm.CHIP -> {
                desiredWidth = titleWidth + naturalWidth + 23f
                desiredHeight = 18f
            }
            HudForm.LIST -> {
                desiredWidth = maxOf(titleWidth + 22f, naturalWidth + 16f)
                desiredHeight = 21f + listHeight
            }
            HudForm.ARRAY -> {
                desiredWidth = naturalWidth + 14f
                desiredHeight = maxOf(12f, listHeight)
            }
            HudForm.TARGET -> {
                val row = rows.firstOrNull()
                desiredWidth = 42f + maxOf(row?.textWidth ?: 0f, row?.detailWidth ?: 0f, 38f)
                desiredHeight = 35f
            }
        }
        if (shown) {
            val targetWidth = desiredWidth.coerceIn(1f, maxWidth.coerceAtLeast(1f))
            val targetHeight = desiredHeight.coerceIn(1f, maxHeight.coerceAtLeast(1f))
            if (animatedWidth.value == 0f) animatedWidth.setImmediate(targetWidth)
            if (animatedHeight.value == 0f) animatedHeight.setImmediate(targetHeight)
            width = animatedWidth.update(targetWidth, deltaSeconds)
            height = animatedHeight.update(targetHeight, deltaSeconds)
        }
        health.update(healthFraction, deltaSeconds)
        absorption.update(absorptionFraction, deltaSeconds)
    }

    fun reset() {
        begin(false)
        fade.setImmediate(0f)
        previewOpacity.setImmediate(0f)
        animatedWidth.setImmediate(0f)
        animatedHeight.setImmediate(0f)
        health.setImmediate(0f)
        absorption.setImmediate(0f)
        width = 0f
        height = 0f
        skin = null
        initial = ""
        var i = 0
        while (i < rows.size) rows[i++].opacity.setImmediate(0f)
    }

    fun draw(matrix: Matrix4f, x: Float, y: Float, scale: Float, alignment: HudAlignment) {
        if (!isVisible) return
        val gfx = RenderUtils.currentGraphics ?: return
        drawScale = scale
        rowAlignment = alignment
        val drawWidth = width * scale
        val drawHeight = height * scale
        val alpha = fade.value
        val palette = M3Theme.current
        if (form != HudForm.ARRAY) {
            RenderUtils.roundedRectWithBorder(matrix, x, y, drawWidth, drawHeight,
                palette.surfaceContainerLowest.alpha(190).fade(alpha),
                (if (form == HudForm.WATERMARK || form == HudForm.CHIP) 8f else 5f) * scale,
                0.55f * scale, palette.outlineVariant.alpha(95).fade(alpha), ClientRenderPipeline.HUD_RECT)
        }
        gfx.enableScissor(x.toInt(), y.toInt(), kotlin.math.ceil(x + drawWidth).toInt(),
            kotlin.math.ceil(y + drawHeight).toInt())
        val contentY = y + (1f - alpha) * 3f * scale
        when (form) {
            HudForm.WATERMARK -> drawWatermark(matrix, x, contentY, scale, alpha)
            HudForm.CHIP -> drawChip(matrix, x, contentY, scale, alpha)
            HudForm.LIST -> drawList(matrix, x, contentY, scale, alpha)
            HudForm.ARRAY -> drawArray(matrix, x, contentY, scale, alpha)
            HudForm.TARGET -> drawTarget(matrix, x, contentY, scale, alpha)
        }
        gfx.disableScissor()
    }

    private fun drawWatermark(matrix: Matrix4f, x: Float, y: Float, scale: Float, alpha: Float) {
        val palette = M3Theme.current
        var offset = 7f
        var i = 0
        while (i < rowCount) {
            val row = rows[i]
            if (i > 0) {
                RenderUtils.rect(matrix, x + (offset + 4f) * scale, y + 6f * scale,
                    0.65f * scale, 7f * scale, palette.outlineVariant.fade(alpha), ClientRenderPipeline.HUD_RECT)
                offset += 9f
            }
            Fonts.google_sans_medium.drawString(matrix, row.text, x + offset * scale, y + 5.5f * scale,
                6.9f * scale, (if (row.highlighted) palette.primary else palette.onSurface).fade(alpha),
                pipeline = ClientRenderPipeline.HUD_TEXT)
            offset += row.textWidth
            i++
        }
    }

    private fun drawChip(matrix: Matrix4f, x: Float, y: Float, scale: Float, alpha: Float) {
        val palette = M3Theme.current
        Fonts.google_sans_medium.drawString(matrix, title, x + 7f * scale, y + 5.3f * scale,
            6.7f * scale, palette.primary.fade(alpha), pipeline = ClientRenderPipeline.HUD_TEXT)
        val row = rows.firstOrNull() ?: return
        RenderUtils.rect(matrix, x + (titleWidth + 11f) * scale, y + 5.5f * scale,
            0.65f * scale, 7f * scale, palette.outlineVariant.fade(alpha), ClientRenderPipeline.HUD_RECT)
        row.draw(matrix, x + (titleWidth + 16f) * scale, y + 5.1f * scale,
            (width - titleWidth - 23f) * scale, scale, alpha)
    }

    private fun drawList(matrix: Matrix4f, x: Float, y: Float, scale: Float, alpha: Float) {
        val palette = M3Theme.current
        Fonts.google_sans_medium.drawString(matrix, title, x + 8f * scale, y + 5.8f * scale,
            6.7f * scale, palette.primary.fade(alpha), pipeline = ClientRenderPipeline.HUD_TEXT)
        RenderUtils.rect(matrix, x + 8f * scale, y + 17f * scale, (width - 16f) * scale,
            0.5f * scale, palette.outlineVariant.alpha(85).fade(alpha), ClientRenderPipeline.HUD_RECT)
        var i = 0
        while (i < rows.size) {
            val row = rows[i++]
            val opacity = row.opacity.value
            if (opacity <= 0.002f) continue
            row.draw(matrix, x + (8f + (1f - opacity) * 4f) * scale,
                y + (22f + row.position.value) * scale, (width - 16f) * scale, scale, alpha * opacity)
        }
        if (previewOpacity.value > 0.002f) previewRow.draw(matrix, x + 8f * scale,
            y + 22f * scale, (width - 16f) * scale, scale, alpha * previewOpacity.value)
    }

    private fun drawArray(matrix: Matrix4f, x: Float, y: Float, scale: Float, alpha: Float) {
        val palette = M3Theme.current
        var i = 0
        while (i < rows.size) {
            val row = rows[i++]
            val opacity = row.opacity.value
            if (opacity <= 0.002f) continue
            val rowWidth = minOf(row.width + 14f, width) * scale
            val rowX = x + arrayRowLeft(rowWidth / scale, opacity) * scale
            val rowY = y + row.position.value * scale
            val rowAlpha = alpha * opacity
            RenderUtils.roundedRect(matrix, rowX, rowY, rowWidth, 11f * scale,
                palette.surfaceContainerLowest.alpha(158).fade(rowAlpha), 3f * scale,
                ClientRenderPipeline.HUD_RECT)
            RenderUtils.roundedRect(matrix, rowX + rowWidth - 1.5f * scale, rowY + 2.5f * scale,
                1.5f * scale, 6f * scale, palette.primary.fade(rowAlpha), 0.7f * scale,
                ClientRenderPipeline.HUD_RECT)
            row.draw(matrix, rowX + 6f * scale, rowY + 2f * scale, rowWidth - 14f * scale,
                scale, rowAlpha)
        }
        if (previewOpacity.value > 0.002f) {
            RenderUtils.roundedRect(matrix, x, y, width * scale, 12f * scale,
                palette.surfaceContainerLowest.alpha(170).fade(alpha * previewOpacity.value),
                3f * scale, ClientRenderPipeline.HUD_RECT)
            previewRow.draw(matrix, x + 7f * scale, y + 2f * scale, (width - 14f) * scale,
                scale, alpha * previewOpacity.value)
        }
    }

    private fun containsArrayRow(x: Float, y: Float): Boolean {
        val localX = x / drawScale
        val localY = y / drawScale - (1f - fade.value) * 3f
        if (previewOpacity.value > 0.002f && localY >= 0f && localY < 12f) return true
        var i = 0
        while (i < rows.size) {
            val row = rows[i++]
            val opacity = row.opacity.value
            if (opacity <= 0.002f) continue
            val rowWidth = minOf(row.width + 14f, width)
            val left = arrayRowLeft(rowWidth, opacity)
            if (localX >= left && localX < left + rowWidth && localY >= row.position.value &&
                localY < row.position.value + 11f) return true
        }
        return false
    }

    private fun arrayRowLeft(rowWidth: Float, opacity: Float): Float = when (rowAlignment) {
        HudAlignment.LEFT -> -(1f - opacity) * 5f
        HudAlignment.CENTER -> (width - rowWidth) * 0.5f
        else -> width - rowWidth + (1f - opacity) * 5f
    }

    private fun drawTarget(matrix: Matrix4f, x: Float, y: Float, scale: Float, alpha: Float) {
        val palette = M3Theme.current
        val row = rows.firstOrNull() ?: return
        val avatarY = y + 6f * scale
        RenderUtils.roundedRect(matrix, x + 6f * scale, avatarY, 22f * scale, 22f * scale,
            palette.primaryContainer.fade(alpha), 4f * scale, ClientRenderPipeline.HUD_RECT)
        val playerSkin = skin
        val gfx = RenderUtils.currentGraphics
        if (playerSkin != null && gfx != null) {
            val pose = gfx.pose()
            pose.pushMatrix()
            pose.translate(x + 7f * scale, avatarY + scale)
            pose.scale(scale, scale)
            PlayerFaceExtractor.extractRenderState(gfx, playerSkin, 0, 0, 20,
                Color4b.WHITE.fade(alpha).argb)
            pose.popMatrix()
        } else {
            Fonts.google_sans_medium.drawCenteredString(matrix, initial, x + 17f * scale,
                avatarY + 5f * scale, 10f * scale, palette.onPrimaryContainer.fade(alpha),
                pipeline = ClientRenderPipeline.HUD_TEXT)
        }
        val contentX = x + 34f * scale
        val contentY = y + (if (hasHealth) 6f else 13f) * scale
        Fonts.google_sans_medium.drawString(matrix, row.text, contentX, contentY,
            6.9f * scale, palette.onSurface.fade(alpha), pipeline = ClientRenderPipeline.HUD_TEXT)
        if (!hasHealth) return
        Fonts.google_sans_regular.drawString(matrix, row.detail, contentX, contentY + 10f * scale,
            6.5f * scale, palette.onSurfaceVariant.fade(alpha), pipeline = ClientRenderPipeline.HUD_TEXT)
        val barY = contentY + 21f * scale
        val barWidth = (width - 42f) * scale
        val healthWidth = barWidth * health.value.coerceIn(0f, 1f)
        val absorptionWidth = barWidth * absorption.value.coerceIn(0f, 1f - health.value.coerceIn(0f, 1f))
        RenderUtils.roundedRect(matrix, contentX, barY, barWidth, 3f * scale,
            palette.surfaceContainerHighest.fade(alpha), 1.5f * scale, ClientRenderPipeline.HUD_RECT)
        if (healthWidth > 0.1f) RenderUtils.roundedRect(matrix, contentX, barY, healthWidth, 3f * scale,
            palette.primary.fade(alpha), 1.5f * scale, ClientRenderPipeline.HUD_RECT)
        if (absorptionWidth > 0.1f) RenderUtils.roundedRect(matrix, contentX + healthWidth, barY,
            absorptionWidth, 3f * scale, palette.tertiary.fade(alpha), 1.5f * scale,
            ClientRenderPipeline.HUD_RECT)
    }
}
