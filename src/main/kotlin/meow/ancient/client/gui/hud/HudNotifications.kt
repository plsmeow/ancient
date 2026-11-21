package meow.ancient.client.gui.hud
import meow.ancient.module.modules.render.HudModule

import meow.ancient.client.extensions.mc
import meow.ancient.client.gui.material.AnimFloat
import meow.ancient.client.gui.material.M3Theme
import meow.ancient.client.util.render.RenderUtils
import meow.ancient.client.util.render.engine.controls.ClientRenderPipeline
import meow.ancient.client.util.render.font.Fonts
import org.joml.Matrix4f

class HudNotifications {
    private class Toast {
        var title = ""
        var detail = ""
        var text = ""
        var compactWidth = 0f
        var enabled = false
        var preview = false
        var createdAt = 0L
        var width = 0f
        var remaining = 1f
        val opacity = AnimFloat(speed = 18f)
        val position = AnimFloat(speed = 18f)

        fun prepare(title: String, detail: String, text: String, enabled: Boolean, preview: Boolean, createdAt: Long) {
            this.title = fitTitle(title)
            this.detail = detail
            this.enabled = enabled
            this.preview = preview
            this.createdAt = createdAt
            this.text = hudEllipsize(text, MAX_COMPACT_WIDTH - COMPACT_TEXT_X - PADDING, COMPACT_SIZE)
            compactWidth = Fonts.google_sans_medium.width(this.text, COMPACT_SIZE) + COMPACT_TEXT_X + PADDING
            width = maxOf(
                Fonts.google_sans_medium.width(this.title, TITLE_SIZE),
                Fonts.google_sans_regular.width(detail, DETAIL_SIZE)
            ) + TEXT_X + PADDING
            remaining = 1f
            opacity.setImmediate(0f)
            position.setImmediate(0f)
        }

        private fun fitTitle(text: String): String {
            val font = Fonts.google_sans_medium
            val available = MAX_CARD_WIDTH - TEXT_X - PADDING
            if (font.width(text, TITLE_SIZE) <= available) return text
            val suffix = "..."
            val suffixWidth = font.width(suffix, TITLE_SIZE)
            var used = 0f
            var end = 0
            while (end < text.length) {
                val advance = font.advance(text[end]) * TITLE_SIZE
                if (used + advance + suffixWidth > available) break
                used += advance
                end++
            }
            return text.substring(0, end) + suffix
        }
    }

    private var entries = Array(DEFAULT_LIMIT) { Toast() }
    private var limit = DEFAULT_LIMIT
    private val example = Toast()
    private var count = 0
    private var examplePrepared = false
    private var exampleVisible = false
    private var compact = false
    private var upward = false
    val cardHeight: Float get() = if (compact) COMPACT_HEIGHT else CARD_HEIGHT

    var width = 0f
        private set
    var height = 0f
        private set
    val isVisible: Boolean
        get() = count > 0 || exampleVisible

    fun update(deltaSeconds: Float, preview: Boolean, limit: Int, compact: Boolean, upward: Boolean, maxHeight: Float) {
        this.compact = compact
        this.upward = upward
        if (mc.player == null || mc.level == null) {
            clear()
            return
        }
        val stackStep = cardHeight + if (compact) COMPACT_GAP else STACK_STEP - CARD_HEIGHT
        val fittingLimit = ((maxHeight - cardHeight) / stackStep).toInt() + 1
        val requestedLimit = minOf(limit.coerceIn(1, 20), fittingLimit.coerceAtLeast(1))
        if (this.limit != requestedLimit) {
            this.limit = requestedLimit
            if (requestedLimit > entries.size) {
                val previous = entries
                entries = Array(requestedLimit) { index -> if (index < previous.size) previous[index] else Toast() }
            }
            while (count > requestedLimit) remove(0)
        }
        val now = System.nanoTime()
        val dt = deltaSeconds.coerceIn(0.0001f, 0.1f)
        var i = 0
        while (i < count) {
            val entry = entries[i]
            val age = (now - entry.createdAt).coerceAtLeast(0L)
            val exiting = age >= HUD_NOTIFICATION_LIFETIME_NANOS - EXIT_NANOS
            entry.opacity.update(if (exiting) 0f else 1f, dt, if (exiting) 12f else 18f)
            entry.remaining = (1.0 - age.toDouble() / HUD_NOTIFICATION_LIFETIME_NANOS).toFloat().coerceIn(0f, 1f)
            if (age >= HUD_NOTIFICATION_LIFETIME_NANOS && entry.opacity.value < 0.04f ||
                age >= HUD_NOTIFICATION_LIFETIME_NANOS + 100_000_000L) {
                remove(i)
            } else {
                i++
            }
        }
        while (true) {
            val message = HudNotificationCenter.poll(now) ?: break
            if (now - message.createdAt >= HUD_NOTIFICATION_LIFETIME_NANOS - EXIT_NANOS) continue
            if (count == this.limit) remove(0)
            entries[count].prepare(message.name, message.detail, message.text,
                message.enabled, false, message.createdAt)
            entries[count].opacity.update(1f, dt)
            count++
        }
        exampleVisible = preview && count < this.limit
        if (exampleVisible) {
            if (!examplePrepared) {
                example.prepare("Preview", "Enabled", "Preview · Enabled", true, true, now)
                examplePrepared = true
            }
            example.opacity.update(1f, dt)
            example.position.update(0f, dt)
        } else {
            example.opacity.setImmediate(0f)
            examplePrepared = false
        }
        width = 0f
        height = 0f
        i = 0
        while (i < count) {
            val entry = entries[i]
            val slot = count - 1 - i + if (exampleVisible) 1 else 0
            entry.position.update(slot * stackStep, dt)
            width = maxOf(width, if (compact) entry.compactWidth else entry.width + HORIZONTAL_SLIDE)
            height = maxOf(height, entry.position.value + cardHeight)
            i++
        }
        if (exampleVisible) {
            width = maxOf(width, if (compact) example.compactWidth else example.width + HORIZONTAL_SLIDE)
            height = maxOf(height, example.position.value + cardHeight)
        }
    }

    fun draw(matrix: Matrix4f, x: Float, y: Float, scale: Float, alignment: HudAlignment) {
        if (RenderUtils.currentGraphics == null || scale <= 0f) return
        var i = 0
        while (i < count) {
            drawToast(matrix, entries[i], x, y, scale, alignment)
            i++
        }
        if (exampleVisible) drawToast(matrix, example, x, y, scale, alignment)
    }

    private fun drawToast(matrix: Matrix4f, entry: Toast, x: Float, y: Float, scale: Float, alignment: HudAlignment) {
        val alpha = entry.opacity.value
        if (alpha <= 0.001f) return
        val palette = M3Theme.current
        val accent = when {
            entry.preview -> palette.tertiary
            entry.enabled -> palette.primary
            else -> palette.secondary
        }
        if (compact) {
            drawCompact(matrix, entry, x, y, scale, alignment, alpha)
            return
        }
        val rowOffset = when (alignment) {
            HudAlignment.CENTER -> (width - entry.width) * 0.5f
            HudAlignment.RIGHT -> width - entry.width
            else -> 0f
        }
        val slideDirection = when (alignment) {
            HudAlignment.RIGHT -> -1f
            HudAlignment.CENTER -> 0f
            else -> 1f
        }
        val drawX = x + (rowOffset + (1f - alpha) * HORIZONTAL_SLIDE * slideDirection) * scale
        val drawY = y + rowY(entry, alpha) * scale
        val drawWidth = entry.width * scale
        RenderUtils.hudBackground(matrix, drawX, drawY, drawWidth, CARD_HEIGHT * scale,
            palette.surfaceContainerLow.alpha(if (HudModule.blur) 140 else 255), 4f * scale, opacity = alpha)
        RenderUtils.roundedRect(matrix, drawX + PADDING * scale, drawY + 6f * scale,
            2.5f * scale, 2.5f * scale, accent.fade(alpha), 1.25f * scale, ClientRenderPipeline.HUD_RECT)
        Fonts.google_sans_medium.drawString(matrix, entry.title, drawX + TEXT_X * scale, drawY + 4.5f * scale,
            TITLE_SIZE * scale, palette.onSurface.fade(alpha), pipeline = ClientRenderPipeline.HUD_TEXT)
        Fonts.google_sans_regular.drawString(matrix, entry.detail, drawX + TEXT_X * scale, drawY + 13f * scale,
            DETAIL_SIZE * scale, palette.onSurfaceVariant.fade(alpha), pipeline = ClientRenderPipeline.HUD_TEXT)
        val barWidth = (entry.width - PADDING * 2f) * scale
        val barX = drawX + PADDING * scale
        val barY = drawY + 22.5f * scale
        RenderUtils.rect(matrix, barX, barY, barWidth, 0.75f * scale,
            palette.outlineVariant.fade(alpha), ClientRenderPipeline.HUD_RECT)
        if (entry.remaining > 0f) {
            RenderUtils.rect(matrix, barX, barY, barWidth * entry.remaining, 0.75f * scale,
                accent.fade(alpha), ClientRenderPipeline.HUD_RECT)
        }
    }

    private fun rowY(entry: Toast, alpha: Float): Float {
        val offset = if (upward) height - cardHeight - entry.position.value else entry.position.value
        return offset + (1f - alpha) * ENTRY_SLIDE * if (upward) 1f else -1f
    }

    private fun drawCompact(matrix: Matrix4f, entry: Toast, x: Float, y: Float, scale: Float,
                            alignment: HudAlignment, alpha: Float) {
        val palette = M3Theme.current
        val accent = when {
            entry.preview -> palette.tertiary
            entry.enabled -> palette.primary
            else -> palette.secondary
        }
        val rowOffset = when (alignment) {
            HudAlignment.CENTER -> (width - entry.compactWidth) * 0.5f
            HudAlignment.RIGHT -> width - entry.compactWidth
            else -> 0f
        }
        val drawX = x + rowOffset * scale
        val drawY = y + rowY(entry, alpha) * scale
        val drawWidth = entry.compactWidth * scale
        RenderUtils.hudBackground(matrix, drawX, drawY, drawWidth, COMPACT_HEIGHT * scale,
            palette.surfaceContainerLow.alpha(if (HudModule.blur) 140 else 255), 4f * scale, opacity = alpha)
        RenderUtils.roundedRect(matrix, drawX + PADDING * scale, drawY + 6f * scale,
            2.5f * scale, 2.5f * scale, accent.fade(alpha), 1.25f * scale, ClientRenderPipeline.HUD_RECT)
        RenderUtils.rect(matrix, drawX + 12f * scale, drawY + 3.5f * scale, 0.6f * scale,
            7.5f * scale, palette.outlineVariant.fade(alpha), ClientRenderPipeline.HUD_RECT)
        Fonts.google_sans_medium.drawString(matrix, entry.text, drawX + COMPACT_TEXT_X * scale,
            drawY + 3.8f * scale, COMPACT_SIZE * scale, palette.onSurface.fade(alpha),
            pipeline = ClientRenderPipeline.HUD_TEXT)
    }

    private fun remove(index: Int) {
        val reusable = entries[index]
        var i = index
        while (i < count - 1) {
            entries[i] = entries[i + 1]
            i++
        }
        entries[--count] = reusable
    }

    fun clear() {
        HudNotificationCenter.clear()
        count = 0
        exampleVisible = false
        examplePrepared = false
        example.opacity.setImmediate(0f)
        width = 0f
        height = 0f
    }

    private companion object {
        const val DEFAULT_LIMIT = 7
        const val TITLE_SIZE = 6.8f
        const val DETAIL_SIZE = 6.1f
        const val PADDING = 6f
        const val TEXT_X = 11f
        const val CARD_HEIGHT = 25f
        const val STACK_STEP = 28f
        const val COMPACT_HEIGHT = 14.5f
        const val COMPACT_GAP = 3f
        const val COMPACT_SIZE = 6.8f
        const val COMPACT_TEXT_X = 17f
        const val MAX_COMPACT_WIDTH = 240f
        const val MAX_CARD_WIDTH = 152f
        const val HORIZONTAL_SLIDE = 8f
        const val ENTRY_SLIDE = 3f
        const val EXIT_NANOS = 300_000_000L
    }
}
