package meow.ancient.client.gui.hud

import com.mojang.blaze3d.platform.InputConstants
import meow.ancient.client.config.ConfigManager
import meow.ancient.client.extensions.currentScreen
import meow.ancient.client.extensions.mc
import meow.ancient.client.gui.material.M3Theme
import meow.ancient.client.gui.material.components.CustomTextField
import meow.ancient.client.gui.material.components.KeybindComponent
import meow.ancient.client.gui.material.components.SettingComponent
import meow.ancient.client.gui.material.components.SettingComponentFactory
import meow.ancient.module.setting.KeybindSetting
import meow.ancient.client.util.render.RenderUtils
import meow.ancient.client.util.render.ScissorUtil
import meow.ancient.client.util.render.engine.controls.ClientRenderPipeline
import meow.ancient.client.util.render.engine.controls.MatrixControl
import meow.ancient.client.util.render.font.Fonts
import meow.ancient.module.modules.render.HudModule
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent

object HudSettingsPopup {
    private var element: HudElement? = null
    private var title = ""
    private var components: List<SettingComponent> = emptyList()
    private var captured: SettingComponent? = null
    private var x = 0f
    private var y = 0f
    private var width = 116f
    private var height = 0f
    private var contentHeight = 0f
    private var scroll = 0f
    private var viewportWidth = 0
    private var viewportHeight = 0
    private const val HEADER = 19f
    private const val PADDING = 5f
    private const val GAP = 4f

    val isOpen: Boolean get() = element != null

    fun open(element: HudElement, x: Double, y: Double) {
        close()
        if (currentScreen !is ChatScreen || !HudModule.isEnabled()) return
        HudModule.dragController.clearInteraction()
        CustomTextField.clearFocus()
        this.element = element
        components = HudModule.elementSettings(element).map(SettingComponentFactory::create)
        this.x = x.toFloat()
        title = element.name.lowercase().replace('_', ' ').split(' ')
            .joinToString(" ") { it.replaceFirstChar(Char::titlecase) } + " settings"
        this.y = y.toFloat()
        viewportWidth = mc.window.guiScaledWidth
        viewportHeight = mc.window.guiScaledHeight
        contentHeight = if (components.isEmpty()) 0f else components.size * (18f + GAP) - GAP
        bounds()
    }

    private fun bounds() {
        width = minOf(116f, (viewportWidth - 8f).coerceAtLeast(1f))
        height = minOf(HEADER + PADDING + contentHeight, (viewportHeight - 8f).coerceAtLeast(1f))
        x = x.coerceIn(0f, (viewportWidth - width).coerceAtLeast(0f))
        y = y.coerceIn(0f, (viewportHeight - height).coerceAtLeast(0f))
        scroll = scroll.coerceIn(0f, maxScroll())
    }

    fun render(deltaSeconds: Float) {
        val element = element ?: return
        if (currentScreen !is ChatScreen || !HudModule.isEnabled() ||
            viewportWidth != mc.window.guiScaledWidth || viewportHeight != mc.window.guiScaledHeight) {
            close()
            return
        }
        val graphics = RenderUtils.currentGraphics ?: return
        bounds()
        val matrix = MatrixControl.matrix4fStack
        val mouseX = mc.mouseHandler.getScaledXPos(mc.window).toInt()
        val mouseY = mc.mouseHandler.getScaledYPos(mc.window).toInt()
        val palette = M3Theme.current
        graphics.nextStratum()
        RenderUtils.roundedRect(matrix, x - 2f, y - 2f, width + 4f, height + 4f,
            palette.scrim.with(a = 110), 7f, ClientRenderPipeline.POPUP_RECT)
        RenderUtils.roundedRect(matrix, x, y, width, height, palette.surface,
            5f, ClientRenderPipeline.POPUP_RECT)
        ScissorUtil.withScissor(x, y, width, height) {
            Fonts.google_sans_medium.drawString(matrix, title, x + PADDING, y + 4.5f,
                8.5f, palette.onSurface, false, ClientRenderPipeline.POPUP_TEXT)
            Fonts.google_sans_medium.drawCenteredString(matrix, "×", x + width - 10f, y + 3.5f,
                11f, palette.primary, false, ClientRenderPipeline.POPUP_TEXT)
            RenderUtils.rect(matrix, x + PADDING, y + HEADER - 2f, (width - PADDING * 2f).coerceAtLeast(1f),
                0.75f, palette.outlineVariant, ClientRenderPipeline.POPUP_RECT)
            ScissorUtil.withScissor(x + PADDING, y + HEADER, (width - PADDING * 2f).coerceAtLeast(1f),
                (height - HEADER - PADDING).coerceAtLeast(1f)) {
                var nextY = y + HEADER - scroll
                val interactiveY = if (overContent(mouseX.toDouble(), mouseY.toDouble())) mouseY else Int.MIN_VALUE
                for (component in components) {
                    nextY += component.render(matrix, x + PADDING, nextY,
                        (width - PADDING * 2f).coerceAtLeast(1f), mouseX, interactiveY, deltaSeconds) + GAP
                }
                contentHeight = (nextY - y - HEADER + scroll - GAP).coerceAtLeast(0f)
            }
            if (maxScroll() > 0f) {
                val trackHeight = (height - HEADER - PADDING).coerceAtLeast(1f)
                val thumbHeight = (trackHeight * trackHeight / contentHeight).coerceIn(minOf(12f, trackHeight), trackHeight)
                val thumbY = y + HEADER + (trackHeight - thumbHeight) * scroll / maxScroll()
                RenderUtils.roundedRect(matrix, x + width - 5f, thumbY, 2f, thumbHeight,
                    palette.primary.with(a = 170), 1f, ClientRenderPipeline.POPUP_RECT)
            }
        }
    }

    fun mouseClicked(x: Double, y: Double, button: Int): Boolean {
        if (!isOpen) return false
        if (button >= 4) {
            for (component in components) {
                if (component is KeybindComponent && component.isListening) {
                    component.keySetting.set(KeybindSetting.fromMouseButton(button))
                    component.stopListening()
                    ConfigManager.saveConfig("default")
                    return true
                }
            }
        }
        if (!over(x, y) || button == 0 && x >= this.x + width - 18f && y < this.y + HEADER) {
            close()
            return true
        }
        CustomTextField.blurOutside(x, y)
        captured?.mouseReleased(x, y, 0)
        captured = null
        if (overContent(x, y)) {
            for (component in components) {
                if (component.mouseClicked(x, y, button)) {
                    captured = component
                    break
                }
            }
        }
        return true
    }

    fun mouseReleased(x: Double, y: Double, button: Int) {
        if (!isOpen) return
        captured?.mouseReleased(x, y, button)
        captured = null
        ConfigManager.saveConfig("default")
    }

    fun mouseDragged(x: Double, y: Double, button: Int, dx: Double, dy: Double): Boolean {
        if (!isOpen) return false
        captured?.mouseDragged(x, y, button, dx, dy)
        return true
    }

    fun keyPressed(event: KeyEvent): Boolean {
        if (!isOpen) return false
        if (event.key() == InputConstants.KEY_ESCAPE) {
            close()
            return true
        }
        if (CustomTextField.keyPressedFocused(event.key(), event.keycode(), event.modifiers())) return true
        for (component in components) {
            if (component.keyPressed(event.key(), event.keycode(), event.modifiers())) break
        }
        return true
    }

    fun charTyped(event: CharacterEvent): Boolean {
        if (!isOpen) return false
        if (CustomTextField.charTypedFocused(event)) return true
        for (component in components) {
            if (component.charTyped(event)) break
        }
        return true
    }

    fun mouseScrolled(x: Double, y: Double, dx: Double, dy: Double): Boolean {
        if (!isOpen) return false
        if (overContent(x, y)) scroll = (scroll - dy.toFloat() * 22f).coerceIn(0f, maxScroll())
        return true
    }

    fun close() {
        if (!isOpen) return
        captured?.mouseReleased(0.0, 0.0, 0)
        captured = null
        CustomTextField.clearFocus()
        element = null
        components = emptyList()
        scroll = 0f
        ConfigManager.saveConfig("default")
    }

    private fun over(x: Double, y: Double): Boolean =
        x >= this.x && y >= this.y && x < this.x + width && y < this.y + height

    private fun overContent(x: Double, y: Double): Boolean =
        over(x, y) && y >= this.y + HEADER && y < this.y + height - PADDING

    private fun maxScroll(): Float = (contentHeight - (height - HEADER - PADDING).coerceAtLeast(0f)).coerceAtLeast(0f)
}
