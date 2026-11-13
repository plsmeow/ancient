package meow.ancient.client.gui.material.components

import com.mojang.blaze3d.platform.InputConstants
import meow.ancient.client.config.ConfigManager
import meow.ancient.client.gui.material.AnimFloat
import meow.ancient.client.gui.material.ClickGuiScreen
import meow.ancient.client.gui.material.M3Theme
import meow.ancient.client.render.main.ChromaRenderer
import meow.ancient.client.util.other.color.Color4b
import meow.ancient.client.util.render.RenderUtils
import meow.ancient.client.util.render.ScissorUtil
import meow.ancient.client.util.render.engine.controls.ClientRenderPipeline
import meow.ancient.client.util.render.font.Fonts
import meow.ancient.module.Module
import meow.ancient.module.modules.combat.HitAuraModule
import meow.ancient.module.setting.KeybindSetting
import meow.ancient.client.util.bind.SettingBindingManager
import net.minecraft.client.input.CharacterEvent
import org.joml.Matrix4f

class ModuleComponent(val module: Module) : GuiComponent {
    override var x: Float = 0f
    override var y: Float = 0f
    override var width: Float = 0f
    override var height: Float = 22f
    var isBinding: Boolean = false
    var isExpanded: Boolean
        get() = module.isExpanded
        set(value) {
            if (!value) {
                releaseSettingsInput()
            }
            module.isExpanded = value
        }

    private val expandAnim = AnimFloat(0f, speed = 14f)
    private var currentSettingsHeight = 0f
    private var clipLeft = 0f
    private var clipTop = 0f
    private var clipRight = 0f
    private var clipBottom = 0f

    val settingComponents: MutableList<SettingComponent> = ArrayList()

    init {
        for (setting in module.settings) {
            if (module === HitAuraModule && setting in HitAuraModule.advancedSettings.allSettings) continue
            settingComponents.add(SettingComponentFactory.create(setting))
        }
    }

    fun hasSettings(): Boolean = settingComponents.isNotEmpty()

    fun render(
        matrix: Matrix4f,
        x: Float,
        y: Float,
        width: Float,
        mouseX: Int,
        mouseY: Int,
        dt: Float
    ): Float {
        this.x = x
        this.y = y
        this.width = width
        val inheritedClip = ChromaRenderer.scissorStack.current
        clipLeft = inheritedClip?.x() ?: x
        clipTop = inheritedClip?.y() ?: y
        clipRight = inheritedClip?.let { it.x() + it.width() } ?: (x + width)
        clipBottom = inheritedClip?.let { it.y() + it.height() } ?: Float.POSITIVE_INFINITY

        val palette = M3Theme.current
        val pillH = 22f
        val pillRadius = 6f
        val curY = y

        val searchQuery = ClickGuiScreen.searchQuery
        val matchesSearch = searchQuery.isEmpty() || module.name.contains(searchQuery, ignoreCase = true) || module.description.contains(searchQuery, ignoreCase = true)
        val isTargeted = ClickGuiScreen.highlightedModule === module && ClickGuiScreen.highlightProgress > 0f
        val alphaMultiplier = if (isTargeted || matchesSearch) 1f else 0.25f

        val isEnabled = module.isEnabled()
        val baseModBg = if (isEnabled) palette.primary else palette.surfaceContainerHigh
        val modBg = baseModBg.fade(alphaMultiplier)
        val baseModTextCol = if (isEnabled) palette.onPrimary else palette.onSurface
        val modTextCol = baseModTextCol.fade(alphaMultiplier)

        if (isTargeted) {
            val hProgress = ClickGuiScreen.highlightProgress
            val pulse = (0.5 + 0.5 * kotlin.math.sin((System.currentTimeMillis() - ClickGuiScreen.highlightStartTime) * 0.008)).toFloat()
            val borderAlpha = (hProgress * (0.5f + pulse * 0.5f) * 255f).toInt().coerceIn(0, 255)
            val glowBg = palette.primary.interpolateTo(modBg, (1f - pulse * hProgress * 0.4f).toDouble())

            RenderUtils.roundedRectWithBorder(
                matrix = matrix,
                x = x,
                y = curY,
                width = width,
                height = pillH,
                color = glowBg,
                radius = pillRadius,
                borderWidth = 1.5f,
                borderColor = palette.primary.alpha(borderAlpha),
                pipeline = ClientRenderPipeline.WINDOW_RECT
            )
        } else if (searchQuery.isNotEmpty() && matchesSearch) {
            RenderUtils.roundedRectWithBorder(
                matrix = matrix,
                x = x,
                y = curY,
                width = width,
                height = pillH,
                color = modBg,
                radius = pillRadius,
                borderWidth = 1f,
                borderColor = palette.primary,
                pipeline = ClientRenderPipeline.WINDOW_RECT
            )
        } else {
            RenderUtils.roundedRect(
                matrix = matrix,
                x = x,
                y = curY,
                width = width,
                height = pillH,
                color = modBg,
                radius = pillRadius,
                pipeline = ClientRenderPipeline.WINDOW_RECT
            )
        }

        val isLockedModule = module is meow.ancient.module.modules.other.AntiCheatModule
        val hasBind = !isLockedModule && module.key != InputConstants.UNKNOWN.value
        val showBindBadge = !isLockedModule && (isBinding || hasBind)
        var keyText = ""
        var badgeX = 0f
        var badgeW = 0f

        if (showBindBadge) {
            keyText = if (isBinding) "..." else KeybindSetting.formatKey(module.key)
            val keyFontSize = 8.5f
            val keyTextW = Fonts.google_sans_medium.width(keyText, keyFontSize)
            val badgePadX = 4.5f
            badgeW = (keyTextW + badgePadX * 2f).coerceAtLeast(18f)
            val badgeH = 14f
            badgeX = x + width - badgeW - 4.5f
            val badgeY = curY + (pillH - badgeH) * 0.5f

            val badgeBg = if (isEnabled) {
                if (isBinding) palette.onPrimary.alpha(75) else palette.onPrimary.alpha(40)
            } else {
                if (isBinding) palette.primaryContainer else palette.surfaceContainerHigh
            }
            val badgeTextCol = if (isEnabled) {
                palette.onPrimary
            } else {
                if (isBinding) palette.onPrimaryContainer else palette.primary
            }

            RenderUtils.roundedRect(
                matrix = matrix,
                x = badgeX,
                y = badgeY,
                width = badgeW,
                height = badgeH,
                color = badgeBg,
                radius = 4f,
                pipeline = ClientRenderPipeline.WINDOW_RECT
            )

            Fonts.google_sans_medium.drawCenteredString(
                matrix = matrix,
                text = keyText,
                centerX = badgeX + badgeW * 0.5f,
                y = badgeY + 2.5f,
                size = keyFontSize,
                color = badgeTextCol,
                shadow = false,
                pipeline = ClientRenderPipeline.WINDOW_TEXT
            )
        }

        val isLongBind = showBindBadge && (keyText.length > 2 && keyText != "..." || badgeW > 24f)
        val nameWAtDefault = Fonts.google_sans_medium.width(module.name, 10.5f)
        val shouldAlignLeft = showBindBadge && (isLongBind || ((x + width * 0.5f) + nameWAtDefault * 0.5f > badgeX - 6f))

        val maxNameWidth = if (shouldAlignLeft) {
            (badgeX - (x + 7f) - 4f).coerceAtLeast(20f)
        } else if (showBindBadge) {
            val spaceToBadge = badgeX - (x + width * 0.5f) - 3f
            if (spaceToBadge > 0f) minOf(width - 14f, (spaceToBadge * 2f).coerceAtLeast(20f)) else width - 14f
        } else {
            width - 14f
        }

        var nameSize = 10.5f
        val origNameW = Fonts.google_sans_medium.width(module.name, nameSize)
        if (origNameW > maxNameWidth && origNameW > 0f) {
            nameSize = (nameSize * (maxNameWidth / origNameW)).coerceIn(8f, 10.5f)
        }

        val textY = curY + (pillH - nameSize) * 0.5f - 0.5f

        if (shouldAlignLeft) {
            Fonts.google_sans_medium.drawString(
                matrix = matrix,
                text = module.name,
                x = x + 7f,
                y = textY,
                size = nameSize,
                color = modTextCol,
                shadow = false,
                pipeline = ClientRenderPipeline.WINDOW_TEXT
            )
        } else {
            Fonts.google_sans_medium.drawCenteredString(
                matrix = matrix,
                text = module.name,
                centerX = x + width * 0.5f,
                y = textY,
                size = nameSize,
                color = modTextCol,
                shadow = false,
                pipeline = ClientRenderPipeline.WINDOW_TEXT
            )
        }

        var totalSettingsHeight = 0f
        for (comp in settingComponents) {
            if (comp.setting.isVisible()) {
                totalSettingsHeight += comp.height + 6f
            } else {
                releaseSettingsInput(comp)
            }
        }

        val targetHeight = if (isExpanded && totalSettingsHeight > 0f) totalSettingsHeight + 6f else 0f
        currentSettingsHeight = expandAnim.update(targetHeight, dt)
        this.height = pillH + currentSettingsHeight

        if (!isExpanded && currentSettingsHeight <= 0f) {
            releaseSettingsInput()
        }

        if (isExpanded || currentSettingsHeight > 0f) {
            ScissorUtil.withScissor(x, y + pillH, width, currentSettingsHeight) {
                var compY = y + pillH + 6f
                for (comp in settingComponents) {
                    if (comp.setting.isVisible()) {
                        comp.x = x
                        comp.y = compY
                        comp.width = width
                        val interactive = isSettingRevealed(comp)
                        if (!interactive) {
                            releaseSettingsInput(comp)
                        }
                        val compH = comp.render(
                            matrix = matrix,
                            x = x,
                            y = compY,
                            width = width,
                            mouseX = if (interactive) mouseX else Int.MIN_VALUE,
                            mouseY = if (interactive) mouseY else Int.MIN_VALUE,
                            dt = dt
                        )
                        if (interactive && !isSettingRevealed(comp)) {
                            releaseSettingsInput(comp)
                        }
                        compY += compH + 6f
                    }
                }
            }
        }

        return height
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean = mouseClicked(mouseX, mouseY, button, 0)

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int, modifiers: Int): Boolean {
        releaseUnrevealedSettings()
        if (isBinding && button >= 4) {
            module.key = KeybindSetting.fromMouseButton(button)
            isBinding = false
            ConfigManager.saveCurrentConfig()
            return true
        }

        val pillH = 22f

        if (isInsideClip(mouseX, mouseY) && isHovered(mouseX, mouseY, x, y, width, pillH)) {
            if (module is meow.ancient.module.modules.other.AntiCheatModule) {
                if (button == 1 && hasSettings()) {
                    ClickGuiScreen.clearAllListening()
                    isExpanded = !isExpanded
                }
                return true
            }
            when (button) {
                0 -> {
                    ClickGuiScreen.clearAllListening()
                    module.toggle()
                    ConfigManager.saveCurrentConfig()
                    return true
                }
                1 -> {
                    ClickGuiScreen.clearAllListening()
                    if (hasSettings()) {
                        isExpanded = !isExpanded
                        ConfigManager.saveCurrentConfig()
                    }
                    return true
                }
                2 -> {
                    val nextBinding = !isBinding
                    ClickGuiScreen.clearAllListening()
                    isBinding = nextBinding
                    return true
                }
            }
        }

        if (isInsideSettingsClip(mouseX, mouseY)) {
            for (comp in settingComponents) {
                if (!isSettingRevealed(comp) || !isHovered(mouseX, mouseY, comp.x, comp.y, comp.width, comp.height)) continue
                if (button == 2 && SettingBindingManager.supports(comp.setting)) {
                    ClickGuiScreen.openSettingBinding(SettingBindingWindow(comp.setting, mouseX.toFloat(), mouseY.toFloat()))
                    return true
                }
                if (comp.mouseClicked(mouseX, mouseY, button, modifiers)) {
                    releaseUnrevealedSettings()
                    return true
                }
            }
        }

        return false
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int) {
        for (comp in settingComponents) {
            comp.mouseReleased(mouseX, mouseY, button)
        }
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, deltaX: Double, deltaY: Double) {
        releaseUnrevealedSettings()
        for (comp in settingComponents) {
            if (isSettingRevealed(comp) && isInsideSettingsClip(mouseX, mouseY)) {
                comp.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)
            } else {
                comp.mouseReleased(mouseX, mouseY, button)
            }
        }
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        releaseUnrevealedSettings()
        if (isBinding) {
            if (keyCode == InputConstants.KEY_ESCAPE || keyCode == InputConstants.KEY_DELETE || keyCode == InputConstants.KEY_BACKSPACE) {
                module.key = InputConstants.UNKNOWN.value
            } else {
                module.key = keyCode
            }
            isBinding = false
            ConfigManager.saveCurrentConfig()
            return true
        }

        for (comp in settingComponents) {
            if (isSettingRevealed(comp) && comp.keyPressed(keyCode, scanCode, modifiers)) {
                releaseUnrevealedSettings()
                return true
            }
        }
        return false
    }
    fun charTyped(event: CharacterEvent): Boolean {
        releaseUnrevealedSettings()
        for (comp in settingComponents) {
            if (isSettingRevealed(comp) && comp.charTyped(event)) {
                return true
            }
        }
        return false
    }

    fun releaseInput() {
        isBinding = false
        releaseSettingsInput()
    }

    private fun releaseSettingsInput(comp: SettingComponent) {
        comp.mouseReleased(0.0, 0.0, 0)
        when (comp) {
            is KeybindComponent -> comp.stopListening()
            is NumberComponent -> comp.stopEditing()
            is TextComponent -> comp.blur()
        }
    }

    private fun releaseSettingsInput() {
        for (comp in settingComponents) {
            releaseSettingsInput(comp)
        }
    }

    private fun releaseUnrevealedSettings() {
        for (comp in settingComponents) {
            if (!isSettingRevealed(comp)) {
                releaseSettingsInput(comp)
            }
        }
    }

    private fun isSettingRevealed(comp: SettingComponent): Boolean {
        return isExpanded && comp.setting.isVisible() && comp.height > 0f &&
            comp.x >= maxOf(x, clipLeft) && comp.x + comp.width <= minOf(x + width, clipRight) &&
            comp.y >= maxOf(y + 28f, clipTop) &&
            comp.y + comp.height <= minOf(y + 22f + currentSettingsHeight, clipBottom)
    }

    private fun isInsideClip(mouseX: Double, mouseY: Double): Boolean {
        return mouseX >= clipLeft && mouseX < clipRight && mouseY >= clipTop && mouseY < clipBottom
    }

    private fun isInsideSettingsClip(mouseX: Double, mouseY: Double): Boolean {
        return isExpanded && isInsideClip(mouseX, mouseY) &&
            mouseX >= x && mouseX < x + width &&
            mouseY >= y + 28f && mouseY < y + 22f + currentSettingsHeight
    }

    private fun isHovered(mouseX: Double, mouseY: Double, x: Float, y: Float, w: Float, h: Float): Boolean {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h
    }
}
