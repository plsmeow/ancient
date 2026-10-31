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
        var enabled = false
        var preview = false
        var createdAt = 0L
        var width = 0f
        var remaining = 1f
        val opacity = AnimFloat(speed = 18f)
        val position = AnimFloat(speed = 18f)

        fun prepare(title: String, detail: String, enabled: Boolean, preview: Boolean, createdAt: Long, y: Float) {
            this.title = fitTitle(title)
            this.detail = detail
            this.enabled = enabled
            this.preview = preview
            this.createdAt = createdAt
            width = maxOf(
                Fonts.google_sans_medium.width(this.title, TITLE_SIZE),
                Fonts.google_sans_regular.width(detail, DETAIL_SIZE)
            ) + TEXT_X + PADDING
            remaining = 1f
            opacity.setImmediate(0f)
            position.setImmediate(y + ENTRY_SLIDE)
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

    var width = 0f
        private set
    var height = 0f
        private set
    val isVisible: Boolean
        get() = count > 0 || exampleVisible

    fun update(deltaSeconds: Float, preview: Boolean, limit: Int) {
        if (mc.player == null || mc.level == null) {
            clear()
            return
        }
        val requestedLimit = limit.coerceIn(1, 20)
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
            entries[count].prepare(message.name, if (message.enabled) "Enabled" else "Disabled",
                message.enabled, false, message.createdAt, count * STACK_STEP)
            entries[count].opacity.update(1f, dt)
            count++
        }
        exampleVisible = preview && count < this.limit
        if (exampleVisible) {
            if (!examplePrepared) {
                example.prepare("Preview", "Enabled", true, true, now, count * STACK_STEP)
                examplePrepared = true
            }
            example.opacity.update(1f, dt)
            example.position.update(count * STACK_STEP, dt)
        } else {
            example.opacity.setImmediate(0f)
            examplePrepared = false
        }
        width = 0f
        height = 0f
        i = 0
        while (i < count) {
            val entry = entries[i]
            entry.position.update(i * STACK_STEP, dt)
            width = maxOf(width, entry.width + HORIZONTAL_SLIDE)
            height = maxOf(height, entry.position.value + CARD_HEIGHT)
            i++
        }
        if (exampleVisible) {
            width = maxOf(width, example.width + HORIZONTAL_SLIDE)
            height = maxOf(height, example.position.value + CARD_HEIGHT)
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
        val drawY = y + entry.position.value * scale
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
        const val MAX_CARD_WIDTH = 152f
        const val HORIZONTAL_SLIDE = 8f
        const val ENTRY_SLIDE = 3f
        const val EXIT_NANOS = 300_000_000L
    }
}
