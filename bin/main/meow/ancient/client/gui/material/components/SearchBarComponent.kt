package meow.ancient.client.gui.material.components

import com.mojang.blaze3d.platform.InputConstants
import meow.ancient.client.config.ConfigManager
import meow.ancient.client.extensions.mc
import meow.ancient.client.gui.material.AnimFloat
import meow.ancient.client.gui.material.ClickGuiScreen
import meow.ancient.client.gui.material.M3Theme
import meow.ancient.client.util.other.color.Color4b
import meow.ancient.client.util.render.RenderUtils
import meow.ancient.client.util.render.ScissorUtil
import meow.ancient.client.util.render.engine.controls.ClientRenderPipeline
import meow.ancient.client.util.render.font.Fonts
import meow.ancient.module.Module
import meow.ancient.module.ModuleManager
import org.joml.Matrix4f

class SearchBarComponent {
    private val textField = CustomTextField(maxLength = 64, hint = "Search modules...")
    private val widthAnim = AnimFloat(220f, 220f, 16f)
    private val focusAnim = AnimFloat(0f, 0f, 16f)
    private val hoverAnim = AnimFloat(0f, 0f, 16f)
    private val resultsAnim = AnimFloat(0f, 0f, 16f)
    private val clearAnim = AnimFloat(0f, 0f, 16f)

    private var currentX = 0f
    private var currentY = 14f
    private var currentW = 220f
    private val height = 24f
    private var scrollOffset = 0f

    val query: String get() = textField.text.trim()
    val isFocused: Boolean get() = textField.isFocused

    fun openAndFocus() {
        textField.focus()
        focusAnim.setImmediate(1f)
    }

    fun clearAndBlur() {
        textField.setText("")
        textField.blur()
        scrollOffset = 0f
    }

    fun close() {
        clearAndBlur()
    }

    private fun getMatchingModules(): List<Module> {
        val q = query
        if (q.isEmpty()) return emptyList()
        return ModuleManager.modules.filter {
            it.name.contains(q, ignoreCase = true) || it.description.contains(q, ignoreCase = true)
        }
    }

    fun render(matrix: Matrix4f, mouseX: Int, mouseY: Int, dt: Float) {
        val screenW = mc.window.guiScaledWidth.toFloat()
        val palette = M3Theme.current
        val matches = getMatchingModules()

        val isHovered = mouseX >= currentX && mouseX <= currentX + currentW && mouseY >= currentY && mouseY <= currentY + height
        val isDropdownHovered = mouseX >= currentX && mouseX <= currentX + currentW &&
                mouseY >= currentY + height && mouseY <= currentY + height + 160f

        val targetW = if (textField.isFocused || query.isNotEmpty()) 310f else if (isHovered) 250f else 220f
        currentW = widthAnim.update(targetW, dt)
        currentX = (screenW - currentW) * 0.5f

        val focus = focusAnim.update(if (textField.isFocused) 1f else 0f, dt)
        val hover = hoverAnim.update(if (isHovered) 1f else 0f, dt)

        val targetDropdown = if ((textField.isFocused || isDropdownHovered || isHovered) && query.isNotEmpty()) 1f else 0f
        val dropProgress = resultsAnim.update(targetDropdown, dt)

        val bg = palette.surfaceContainer.interpolateTo(palette.surfaceContainerHigh, (focus * 0.7f + hover * 0.3f).toDouble())
        val borderAlpha = (focus * 180f + hover * 50f).toInt().coerceIn(0, 255)

        RenderUtils.roundedRectWithBorder(
            matrix = matrix,
            x = currentX,
            y = currentY,
            width = currentW,
            height = height,
            color = bg,
            radius = 7f,
            borderWidth = 1f,
            borderColor = palette.primary.alpha(borderAlpha),
            pipeline = ClientRenderPipeline.POPUP_RECT
        )

        val iconCol = palette.onSurfaceVariant.interpolateTo(palette.primary, focus.toDouble())
        val icY = currentY + height * 0.5f - 4.5f
        val icX = currentX + 8.5f
        RenderUtils.roundedRectWithBorder(
            matrix = matrix,
            x = icX,
            y = icY,
            width = 8f,
            height = 8f,
            color = Color4b.TRANSPARENT,
            radius = 4f,
            borderWidth = 1.2f,
            borderColor = iconCol,
            pipeline = ClientRenderPipeline.POPUP_RECT
        )
        RenderUtils.rect(matrix, icX + 6.2f, icY + 6.2f, 1.4f, 1.4f, iconCol, ClientRenderPipeline.POPUP_RECT)
        RenderUtils.rect(matrix, icX + 7.4f, icY + 7.4f, 1.4f, 1.4f, iconCol, ClientRenderPipeline.POPUP_RECT)
        RenderUtils.rect(matrix, icX + 8.6f, icY + 8.6f, 1.4f, 1.4f, iconCol, ClientRenderPipeline.POPUP_RECT)

        val textX = currentX + 22f
        val textW = (currentW - 46f).coerceAtLeast(10f)
        textField.setBounds(textX, currentY + 1f, textW, height - 2f)
        textField.render(matrix, dt, popup = true, drawBackground = false)

        val clearTarget = if (query.isNotEmpty()) 1f else 0f
        val clearProgress = clearAnim.update(clearTarget, dt)

        if (clearProgress > 0.01f) {
            val clearBtnX = currentX + currentW - 19f
            val clearBtnY = currentY + 4f
            val clearHovered = mouseX >= clearBtnX && mouseX <= clearBtnX + 16f && mouseY >= clearBtnY && mouseY <= clearBtnY + 16f

            if (clearHovered) {
                RenderUtils.roundedRect(
                    matrix = matrix,
                    x = clearBtnX,
                    y = clearBtnY,
                    width = 16f,
                    height = 16f,
                    color = palette.stateHover,
                    radius = 8f,
                    pipeline = ClientRenderPipeline.POPUP_RECT
                )
            }

            Fonts.google_sans_medium.drawCenteredString(
                matrix = matrix,
                text = "✕",
                centerX = clearBtnX + 8f,
                y = clearBtnY + 3.5f,
                size = 8.5f,
                color = palette.onSurfaceVariant.alpha((clearProgress * 255f).toInt()),
                shadow = false,
                pipeline = ClientRenderPipeline.POPUP_TEXT
            )
        } else if (focus < 0.2f && query.isEmpty()) {
            val badgeText = "Ctrl+F"
            val badgeFontSize = 7.5f
            val badgeTextW = Fonts.google_sans_regular.width(badgeText, badgeFontSize)
            val badgePadX = 5f
            val badgeW = badgeTextW + badgePadX * 2f
            val badgeH = 14f
            val badgeX = currentX + currentW - badgeW - 6f
            val badgeY = currentY + (height - badgeH) * 0.5f
            val badgeAlpha = ((1f - focus / 0.2f) * 160f).toInt().coerceIn(0, 255)

            RenderUtils.roundedRect(
                matrix = matrix,
                x = badgeX,
                y = badgeY,
                width = badgeW,
                height = badgeH,
                color = palette.surfaceContainerHighest.alpha(badgeAlpha),
                radius = 3.5f,
                pipeline = ClientRenderPipeline.POPUP_RECT
            )

            Fonts.google_sans_regular.drawCenteredString(
                matrix = matrix,
                text = badgeText,
                centerX = badgeX + badgeW * 0.5f,
                y = badgeY + (badgeH - badgeFontSize) * 0.5f - 0.5f,
                size = badgeFontSize,
                color = palette.onSurfaceVariant.alpha(badgeAlpha),
                shadow = false,
                pipeline = ClientRenderPipeline.POPUP_TEXT
            )
        }

        if (dropProgress > 0.005f) {
            val dropX = currentX
            val dropY = currentY + height + 4f
            val dropW = currentW
            val rowH = 24f
            val maxVisible = 6
            val totalRows = matches.size
            val fullH = if (totalRows == 0) 32f else (minOf(totalRows, maxVisible) * rowH + 8f)
            val drawH = fullH * dropProgress

            RenderUtils.roundedRectWithBorder(
                matrix = matrix,
                x = dropX,
                y = dropY,
                width = dropW,
                height = drawH,
                color = palette.surfaceContainer,
                radius = 8f,
                borderWidth = 1f,
                borderColor = palette.outlineVariant.alpha((dropProgress * 90f).toInt()),
                pipeline = ClientRenderPipeline.POPUP_RECT
            )

            ScissorUtil.withScissor(dropX, dropY, dropW, drawH) {
                if (totalRows == 0) {
                    Fonts.google_sans_regular.drawCenteredString(
                        matrix = matrix,
                        text = "No modules found",
                        centerX = dropX + dropW * 0.5f,
                        y = dropY + 10f,
                        size = 8.5f,
                        color = palette.onSurfaceVariant,
                        shadow = false,
                        pipeline = ClientRenderPipeline.POPUP_TEXT
                    )
                } else {
                    val maxScroll = (totalRows * rowH - maxVisible * rowH).coerceAtLeast(0f)
                    scrollOffset = scrollOffset.coerceIn(0f, maxScroll)

                    for (i in matches.indices) {
                        val mod = matches[i]
                        val rx = dropX + 4f
                        val ry = dropY + 4f + i * rowH - scrollOffset
                        val rw = dropW - 8f
                        val rh = 22f

                        if (ry + rh < dropY || ry > dropY + drawH) continue

                        val rowHover = mouseX >= rx && mouseX <= rx + rw && mouseY >= ry && mouseY <= ry + rh
                        if (rowHover) {
                            RenderUtils.roundedRect(
                                matrix = matrix,
                                x = rx,
                                y = ry,
                                width = rw,
                                height = rh,
                                color = palette.stateHover,
                                radius = 5f,
                                pipeline = ClientRenderPipeline.POPUP_RECT
                            )
                        }

                        val isEnabled = mod.isEnabled()
                        val pillW = 28f
                        val pillH = 14f
                        val pillX = rx + rw - pillW - 4f
                        val pillY = ry + (rh - pillH) * 0.5f

                        val pillBg = if (isEnabled) palette.primary else palette.surfaceContainerHighest
                        val pillTextCol = if (isEnabled) palette.onPrimary else palette.onSurfaceVariant
                        val pillText = if (isEnabled) "ON" else "OFF"

                        RenderUtils.roundedRect(
                            matrix = matrix,
                            x = pillX,
                            y = pillY,
                            width = pillW,
                            height = pillH,
                            color = pillBg,
                            radius = 4f,
                            pipeline = ClientRenderPipeline.POPUP_RECT
                        )
                        Fonts.google_sans_bold.drawCenteredString(
                            matrix = matrix,
                            text = pillText,
                            centerX = pillX + pillW * 0.5f,
                            y = pillY + 3.2f,
                            size = 7f,
                            color = pillTextCol,
                            shadow = false,
                            pipeline = ClientRenderPipeline.POPUP_TEXT
                        )

                        val catText = mod.category.displayName.uppercase()
                        val catW = Fonts.google_sans_medium.width(catText, 7f)
                        val catX = pillX - catW - 8f
                        Fonts.google_sans_medium.drawString(
                            matrix = matrix,
                            text = catText,
                            x = catX,
                            y = ry + 7.5f,
                            size = 7f,
                            color = palette.onSurfaceVariant.alpha(160),
                            shadow = false,
                            pipeline = ClientRenderPipeline.POPUP_TEXT
                        )

                        val modName = mod.name
                        val qIndex = modName.indexOf(query, ignoreCase = true)
                        if (qIndex >= 0) {
                            val before = modName.substring(0, qIndex)
                            val matched = modName.substring(qIndex, qIndex + query.length)
                            val after = modName.substring(qIndex + query.length)

                            var curTextX = rx + 8f
                            if (before.isNotEmpty()) {
                                Fonts.google_sans_medium.drawString(
                                    matrix = matrix,
                                    text = before,
                                    x = curTextX,
                                    y = ry + 6.5f,
                                    size = 8.5f,
                                    color = palette.onSurface,
                                    shadow = false,
                                    pipeline = ClientRenderPipeline.POPUP_TEXT
                                )
                                curTextX += Fonts.google_sans_medium.width(before, 8.5f)
                            }
                            Fonts.google_sans_bold.drawString(
                                matrix = matrix,
                                text = matched,
                                x = curTextX,
                                y = ry + 6.5f,
                                size = 8.5f,
                                color = palette.primary,
                                shadow = false,
                                pipeline = ClientRenderPipeline.POPUP_TEXT
                            )
                            curTextX += Fonts.google_sans_bold.width(matched, 8.5f)
                            if (after.isNotEmpty()) {
                                Fonts.google_sans_medium.drawString(
                                    matrix = matrix,
                                    text = after,
                                    x = curTextX,
                                    y = ry + 6.5f,
                                    size = 8.5f,
                                    color = palette.onSurface,
                                    shadow = false,
                                    pipeline = ClientRenderPipeline.POPUP_TEXT
                                )
                            }
                        } else {
                            Fonts.google_sans_medium.drawString(
                                matrix = matrix,
                                text = modName,
                                x = rx + 8f,
                                y = ry + 6.5f,
                                size = 8.5f,
                                color = palette.onSurface,
                                shadow = false,
                                pipeline = ClientRenderPipeline.POPUP_TEXT
                            )
                        }
                    }
                }
            }
        }
    }

    fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == 0) {
            if (query.isNotEmpty()) {
                val clearBtnX = currentX + currentW - 19f
                val clearBtnY = currentY + 4f
                if (mouseX >= clearBtnX && mouseX <= clearBtnX + 16f && mouseY >= clearBtnY && mouseY <= clearBtnY + 16f) {
                    textField.setText("")
                    textField.focus()
                    return true
                }
            }

            if (textField.mouseClicked(mouseX, mouseY, button)) {
                return true
            }

            if (mouseX >= currentX && mouseX <= currentX + currentW && mouseY >= currentY && mouseY <= currentY + height) {
                textField.focus()
                return true
            }
        }
        if (resultsAnim.value > 0.1f && query.isNotEmpty()) {
            val dropX = currentX
            val dropY = currentY + height + 4f
            val dropW = currentW
            val matches = getMatchingModules()
            val rowH = 24f
            val maxVisible = 6
            val totalRows = matches.size
            val fullH = if (totalRows == 0) 32f else (minOf(totalRows, maxVisible) * rowH + 8f)
            val drawH = fullH * resultsAnim.value

            if (mouseX >= dropX && mouseX <= dropX + dropW && mouseY >= dropY && mouseY <= dropY + drawH) {
                for (i in matches.indices) {
                    val rx = dropX + 4f
                    val ry = dropY + 4f + i * rowH - scrollOffset
                    val rw = dropW - 8f
                    val rh = 22f

                    if (mouseX >= rx && mouseX <= rx + rw && mouseY >= ry && mouseY <= ry + rh) {
                        val mod = matches[i]
                        if (button == 0) {
                            mod.toggle()
                            ConfigManager.saveConfig("default")
                            return true
                        } else if (button == 1) {
                            ClickGuiScreen.scrollToModule(mod)
                            return true
                        }
                    }
                }
                return true
            }
        }

        return false
    }

    fun mouseScrolled(mouseX: Double, mouseY: Double, scrollY: Double): Boolean {
        if (resultsAnim.value > 0.1f && query.isNotEmpty()) {
            val dropX = currentX
            val dropY = currentY + height + 4f
            val dropW = currentW
            val matches = getMatchingModules()
            val rowH = 24f
            val maxVisible = 6
            val totalRows = matches.size
            val fullH = if (totalRows == 0) 32f else (minOf(totalRows, maxVisible) * rowH + 8f)
            val drawH = fullH * resultsAnim.value

            if (mouseX >= dropX && mouseX <= dropX + dropW && mouseY >= dropY && mouseY <= dropY + drawH) {
                val maxScroll = (totalRows * rowH - maxVisible * rowH).coerceAtLeast(0f)
                scrollOffset = (scrollOffset - (scrollY * 24f).toFloat()).coerceIn(0f, maxScroll)
                return true
            }
        }
        return false
    }
}
