package meow.ancient.client.gui.material.components

import meow.ancient.client.gui.material.AnimFloat
import meow.ancient.client.gui.material.ClickGuiScreen
import meow.ancient.client.gui.material.M3Theme
import meow.ancient.client.gui.material.ThemeManager
import meow.ancient.client.util.render.RenderUtils
import meow.ancient.client.util.render.ScissorUtil
import meow.ancient.client.util.render.engine.controls.ClientRenderPipeline
import meow.ancient.client.util.render.font.Fonts
import meow.ancient.module.setting.ColorSetting
import org.joml.Matrix4f

class ThemePickerComponent(
    var x: Float = 14f,
    var y: Float = 14f,
    val width: Float = 76f,
    val height: Float = 24f
) {
    private val expandAnim = AnimFloat(0f, 0f, 14f)
    private val hoverAlphaAnim = AnimFloat(0f, 0f, 16f)
    private val pickerWidth = 148f
    private var lastHoverTime = 0L
    private val hoverGracePeriod = 180L

    val isExpanded: Boolean get() = expandAnim.value > 0.01f

    private fun isHoveringZone(mouseX: Double, mouseY: Double): Boolean {
        if (ClickGuiScreen.activeColorPicker != null) return true

        val margin = 16.0
        val panelHover = mouseX >= x - margin && mouseX <= x + width + margin &&
                mouseY >= y - margin && mouseY <= y + height + 10.0

        if (panelHover) {
            lastHoverTime = System.currentTimeMillis()
            return true
        }

        if (expandAnim.value > 0.05f) {
            val dropdownH = fullDropdownHeight() * expandAnim.value
            val dropdownHover = mouseX >= x - margin && mouseX <= x + pickerWidth + margin &&
                    mouseY >= y + height - 4.0 && mouseY <= y + height + 4.0 + dropdownH + margin
            if (dropdownHover) {
                lastHoverTime = System.currentTimeMillis()
                return true
            }
        }

        if (System.currentTimeMillis() - lastHoverTime < hoverGracePeriod) {
            return true
        }

        return false
    }

    private fun fullDropdownHeight(): Float {
        val presets = ThemeManager.presets
        val headerH = 16f
        val presetsH = presets.size * 20f
        val dividerH = 6f
        val customH = 46f
        return headerH + presetsH + dividerH + customH + 8f
    }

    fun render(matrix: Matrix4f, mouseX: Int, mouseY: Int, dt: Float) {
        val palette = M3Theme.current
        val hovered = isHoveringZone(mouseX.toDouble(), mouseY.toDouble())
        val panelHovered = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height
        val targetExpand = if (hovered) 1f else 0f
        val progress = expandAnim.update(targetExpand, dt)
        val panelHover = hoverAlphaAnim.update(if (panelHovered) 1f else 0f, dt)

        val headerBg = palette.surfaceContainer.interpolateTo(palette.surfaceContainerHigh, panelHover.toDouble())
        RenderUtils.roundedRect(
            matrix = matrix,
            x = x,
            y = y,
            width = width,
            height = height,
            color = headerBg,
            radius = 7f,
            pipeline = ClientRenderPipeline.POPUP_RECT
        )

        val textColor = if (progress > 0.1f) palette.primary else palette.onSurface
        Fonts.google_sans_medium.drawCenteredString(
            matrix = matrix,
            text = "Themes",
            centerX = x + width * 0.5f,
            y = y + 7f,
            size = 9.5f,
            color = textColor,
            shadow = false,
            pipeline = ClientRenderPipeline.POPUP_TEXT
        )

        if (progress > 0.005f) {
            val totalH = fullDropdownHeight()
            val drawH = totalH * progress
            val dropY = y + height + 4f

            RenderUtils.roundedRectWithBorder(
                matrix = matrix,
                x = x,
                y = dropY,
                width = pickerWidth,
                height = drawH,
                color = palette.surfaceContainer,
                radius = 8f,
                borderWidth = 1f,
                borderColor = palette.outlineVariant.alpha((progress * 90f).toInt()),
                pipeline = ClientRenderPipeline.POPUP_RECT
            )

            ScissorUtil.withScissor(x, dropY, pickerWidth, drawH) {
                var curY = dropY + 6f

                Fonts.google_sans_bold.drawString(
                    matrix = matrix,
                    text = "PRESETS",
                    x = x + 8f,
                    y = curY,
                    size = 7.5f,
                    color = palette.onSurfaceVariant,
                    shadow = false,
                    pipeline = ClientRenderPipeline.POPUP_TEXT
                )
                curY += 12f

                for (preset in ThemeManager.presets) {
                    val rowX = x + 4f
                    val rowY = curY
                    val rowW = pickerWidth - 8f
                    val rowH = 19f

                    val isSelected = !ThemeManager.isCustom && ThemeManager.selectedPresetName.equals(preset.name, ignoreCase = true)
                    val isRowHover = mouseX >= rowX && mouseX <= rowX + rowW && mouseY >= rowY && mouseY <= rowY + rowH

                    if (isSelected) {
                        RenderUtils.roundedRect(
                            matrix = matrix,
                            x = rowX,
                            y = rowY,
                            width = rowW,
                            height = rowH,
                            color = palette.primaryContainer.alpha(90),
                            radius = 5f,
                            pipeline = ClientRenderPipeline.POPUP_RECT
                        )
                    } else if (isRowHover) {
                        RenderUtils.roundedRect(
                            matrix = matrix,
                            x = rowX,
                            y = rowY,
                            width = rowW,
                            height = rowH,
                            color = palette.stateHover,
                            radius = 5f,
                            pipeline = ClientRenderPipeline.POPUP_RECT
                        )
                    }

                    RenderUtils.roundedRect(
                        matrix = matrix,
                        x = rowX + 6f,
                        y = rowY + 5f,
                        width = 9f,
                        height = 9f,
                        color = preset.primary,
                        radius = 4.5f,
                        pipeline = ClientRenderPipeline.POPUP_RECT
                    )

                    val nameCol = if (isSelected) palette.primary else palette.onSurface
                    Fonts.google_sans_regular.drawString(
                        matrix = matrix,
                        text = preset.name,
                        x = rowX + 20f,
                        y = rowY + 5f,
                        size = 8.5f,
                        color = nameCol,
                        shadow = false,
                        pipeline = ClientRenderPipeline.POPUP_TEXT
                    )

                    if (isSelected) {
                        RenderUtils.roundedRect(
                            matrix = matrix,
                            x = rowX + rowW - 10f,
                            y = rowY + 7.5f,
                            width = 4f,
                            height = 4f,
                            color = palette.primary,
                            radius = 2f,
                            pipeline = ClientRenderPipeline.POPUP_RECT
                        )
                    }

                    curY += 20f
                }

                curY += 2f
                RenderUtils.rect(
                    matrix = matrix,
                    x = x + 6f,
                    y = curY,
                    width = pickerWidth - 12f,
                    height = 1f,
                    color = palette.outlineVariant.alpha(60),
                    pipeline = ClientRenderPipeline.POPUP_RECT
                )
                curY += 6f

                val isCustomSelected = ThemeManager.isCustom
                val customRowX = x + 4f
                val customRowY = curY
                val customRowW = pickerWidth - 8f
                val customRowH = 19f
                val customHover = mouseX >= customRowX && mouseX <= customRowX + customRowW && mouseY >= customRowY && mouseY <= customRowY + customRowH

                if (isCustomSelected) {
                    RenderUtils.roundedRect(
                        matrix = matrix,
                        x = customRowX,
                        y = customRowY,
                        width = customRowW,
                        height = customRowH,
                        color = palette.primaryContainer.alpha(90),
                        radius = 5f,
                        pipeline = ClientRenderPipeline.POPUP_RECT
                    )
                } else if (customHover) {
                    RenderUtils.roundedRect(
                        matrix = matrix,
                        x = customRowX,
                        y = customRowY,
                        width = customRowW,
                        height = customRowH,
                        color = palette.stateHover,
                        radius = 5f,
                        pipeline = ClientRenderPipeline.POPUP_RECT
                    )
                }

                RenderUtils.roundedRect(
                    matrix = matrix,
                    x = customRowX + 6f,
                    y = customRowY + 5f,
                    width = 9f,
                    height = 9f,
                    color = ThemeManager.customPrimary,
                    radius = 4.5f,
                    pipeline = ClientRenderPipeline.POPUP_RECT
                )

                Fonts.google_sans_medium.drawString(
                    matrix = matrix,
                    text = "Custom",
                    x = customRowX + 20f,
                    y = customRowY + 5f,
                    size = 8.5f,
                    color = if (isCustomSelected) palette.primary else palette.onSurface,
                    shadow = false,
                    pipeline = ClientRenderPipeline.POPUP_TEXT
                )

                if (isCustomSelected) {
                    RenderUtils.roundedRect(
                        matrix = matrix,
                        x = customRowX + customRowW - 10f,
                        y = customRowY + 7.5f,
                        width = 4f,
                        height = 4f,
                        color = palette.primary,
                        radius = 2f,
                        pipeline = ClientRenderPipeline.POPUP_RECT
                    )
                }

                curY += 22f

                val btnW = (pickerWidth - 14f) * 0.5f
                val accentBtnX = x + 5f
                val surfaceBtnX = x + 7f + btnW
                val btnY = curY
                val btnH = 18f

                val accentHover = mouseX >= accentBtnX && mouseX <= accentBtnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH
                val surfaceHover = mouseX >= surfaceBtnX && mouseX <= surfaceBtnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH

                RenderUtils.roundedRect(
                    matrix = matrix,
                    x = accentBtnX,
                    y = btnY,
                    width = btnW,
                    height = btnH,
                    color = if (accentHover) palette.surfaceContainerHighest else palette.surfaceContainerHigh,
                    radius = 4f,
                    pipeline = ClientRenderPipeline.POPUP_RECT
                )
                RenderUtils.roundedRect(
                    matrix = matrix,
                    x = accentBtnX + 3f,
                    y = btnY + 4f,
                    width = 10f,
                    height = 10f,
                    color = ThemeManager.customPrimary,
                    radius = 2f,
                    pipeline = ClientRenderPipeline.POPUP_RECT
                )
                Fonts.google_sans_regular.drawString(
                    matrix = matrix,
                    text = "Accent",
                    x = accentBtnX + 16f,
                    y = btnY + 4.5f,
                    size = 7.5f,
                    color = palette.onSurface,
                    shadow = false,
                    pipeline = ClientRenderPipeline.POPUP_TEXT
                )

                RenderUtils.roundedRect(
                    matrix = matrix,
                    x = surfaceBtnX,
                    y = btnY,
                    width = btnW,
                    height = btnH,
                    color = if (surfaceHover) palette.surfaceContainerHighest else palette.surfaceContainerHigh,
                    radius = 4f,
                    pipeline = ClientRenderPipeline.POPUP_RECT
                )
                RenderUtils.roundedRect(
                    matrix = matrix,
                    x = surfaceBtnX + 3f,
                    y = btnY + 4f,
                    width = 10f,
                    height = 10f,
                    color = ThemeManager.customSurface,
                    radius = 2f,
                    pipeline = ClientRenderPipeline.POPUP_RECT
                )
                Fonts.google_sans_regular.drawString(
                    matrix = matrix,
                    text = "Surface",
                    x = surfaceBtnX + 16f,
                    y = btnY + 4.5f,
                    size = 7.5f,
                    color = palette.onSurface,
                    shadow = false,
                    pipeline = ClientRenderPipeline.POPUP_TEXT
                )
            }
        }
    }

    fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button != 0) return false

        val panelHover = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height
        if (panelHover) return true

        if (expandAnim.value < 0.2f) return false

        val dropY = y + height + 4f
        val totalH = fullDropdownHeight()
        val curH = totalH * expandAnim.value

        if (mouseX < x || mouseX > x + pickerWidth || mouseY < dropY || mouseY > dropY + curH) {
            return false
        }

        var curY = dropY + 6f
        curY += 12f

        for (preset in ThemeManager.presets) {
            val rowX = x + 4f
            val rowY = curY
            val rowW = pickerWidth - 8f
            val rowH = 19f

            if (mouseX >= rowX && mouseX <= rowX + rowW && mouseY >= rowY && mouseY <= rowY + rowH) {
                ThemeManager.selectPreset(preset)
                return true
            }
            curY += 20f
        }

        curY += 8f

        val customRowX = x + 4f
        val customRowY = curY
        val customRowW = pickerWidth - 8f
        val customRowH = 19f

        if (mouseX >= customRowX && mouseX <= customRowX + customRowW && mouseY >= customRowY && mouseY <= customRowY + customRowH) {
            ThemeManager.selectCustom()
            return true
        }

        curY += 22f

        val btnW = (pickerWidth - 14f) * 0.5f
        val accentBtnX = x + 5f
        val surfaceBtnX = x + 7f + btnW
        val btnY = curY
        val btnH = 18f

        if (mouseX >= accentBtnX && mouseX <= accentBtnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH) {
            val setting = ColorSetting("Accent", ThemeManager.customPrimary, hasAlpha = false)
            setting.addListener { color -> ThemeManager.setCustomPrimary(color) }
            ClickGuiScreen.activeColorPicker = ColorPickerWindow(setting, x + pickerWidth + 8f, y)
            return true
        }

        if (mouseX >= surfaceBtnX && mouseX <= surfaceBtnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH) {
            val setting = ColorSetting("Surface", ThemeManager.customSurface, hasAlpha = false)
            setting.addListener { color -> ThemeManager.setCustomSurface(color) }
            ClickGuiScreen.activeColorPicker = ColorPickerWindow(setting, x + pickerWidth + 8f, y)
            return true
        }

        return true
    }
}
