package meow.ancient.client.gui.material.components

import com.mojang.blaze3d.platform.InputConstants
import meow.ancient.client.config.ConfigManager
import meow.ancient.client.gui.material.ClickGuiScreen
import meow.ancient.client.gui.material.M3Theme
import meow.ancient.client.render.main.ChromaRenderer
import meow.ancient.client.util.render.RenderUtils
import meow.ancient.client.util.render.ScissorUtil
import meow.ancient.client.util.render.engine.controls.ClientRenderPipeline
import meow.ancient.client.util.render.font.Fonts
import meow.ancient.module.setting.HitboxPoint
import meow.ancient.module.setting.HitboxPointsSetting
import net.minecraft.client.input.CharacterEvent
import org.joml.Matrix4f
import java.util.Locale

class HitboxPointsComponent(private val pointsSetting: HitboxPointsSetting) : SettingComponent(pointsSetting) {
    private enum class View(val title: String, val horizontalLabel: String, val verticalLabel: String, val upright: Boolean) {
        FRONT("Front · X/Y", "X →", "Y ↑", true),
        SIDE("Side · Z/Y", "Z →", "Y ↑", true),
        TOP("Top · X/Z", "X →", "Z ↓", false);

        fun horizontal(point: HitboxPoint): Double = if (this == SIDE) point.z else point.x
        fun vertical(point: HitboxPoint): Double = if (upright) 1.0 - point.y else point.z
        fun point(base: HitboxPoint, horizontal: Double, vertical: Double): HitboxPoint = when (this) {
            FRONT -> base.copy(x = horizontal, y = 1.0 - vertical)
            SIDE -> base.copy(z = horizontal, y = 1.0 - vertical)
            TOP -> base.copy(x = horizontal, z = vertical)
        }
    }

    private class Projection(val view: View) {
        var x = 0f
        var y = 0f
        var width = 0f
        var height = 0f
        var plotX = 0f
        var plotY = 0f
        var plotWidth = 0f
        var plotHeight = 0f
    }

    private val projections = View.entries.map(::Projection)
    private val presets = PRESETS.map { it.take(pointsSetting.maxPoints) }
    private val fields = Array(3) { axis ->
        CustomTextField(maxLength = 14, hint = "0…1") { text ->
            val number = text.replace(',', '.').toDoubleOrNull()
            val point = selectedPoint
            if (number != null && number.isFinite() && point != null) {
                val normalized = number.coerceIn(0.0, 1.0)
                moveSelected(when (axis) {
                    0 -> point.copy(x = normalized)
                    1 -> point.copy(y = normalized)
                    else -> point.copy(z = normalized)
                })
            }
        }
    }
    var selectedIndex = 0
        private set
    private val selectedPoint get() = pointsSetting.value.getOrNull(selectedIndex)
    private var dragProjection: Projection? = null
    private var activeView = View.FRONT
    private var gestureStart: List<HitboxPoint>? = null
    private var expanded = false
    private var popup = false
    private var standalone = false
    private var clipX = 0f
    private var clipY = 0f
    private var clipWidth = 0f
    private var clipHeight = 0f
    private var numericY = 0f
    private var controlsY = 0f
    private var presetsY = 0f
    private var inputValue: List<HitboxPoint>? = null
    private var inputIndex = -1
    private var pointCaption = ""
    private var captionSize = -1
    private val rectPipeline get() = if (popup) ClientRenderPipeline.POPUP_RECT else ClientRenderPipeline.WINDOW_RECT
    private val textPipeline get() = if (popup) ClientRenderPipeline.POPUP_TEXT else ClientRenderPipeline.WINDOW_TEXT
    private val projectionColumns get() = when { width >= 280f -> 3; width >= 170f -> 2; else -> 1 }
    private val projectionHeight get() = (width / projectionColumns * 0.8f + 90f).coerceIn(150f, 220f)
    private val presetColumns get() = if (width >= 280f) 4 else 2
    private val innerX get() = x + 10f
    private val innerWidth get() = (width - 20f).coerceAtLeast(1f)
    private val presetWidth get() = (innerWidth - (presetColumns - 1) * 5f) / presetColumns

    fun measureHeight(width: Float, forceExpanded: Boolean = false): Float {
        if (!forceExpanded && width < 300f) return 108f
        val columns = when { width >= 280f -> 3; width >= 170f -> 2; else -> 1 }
        val rows = (3 + columns - 1) / columns
        val viewHeight = (width / columns * 0.8f + 90f).coerceIn(150f, 220f)
        val presetRows = if (width >= 280f) 1 else 2
        return 57f + rows * viewHeight + (rows - 1) * 7f + 27f + 43f + presetRows * 26f + 12f
    }

    override fun render(matrix: Matrix4f, x: Float, y: Float, width: Float, mouseX: Int, mouseY: Int, dt: Float): Float =
        renderEditor(matrix, x, y, width, mouseX, mouseY, dt)

    fun renderEditor(matrix: Matrix4f, x: Float, y: Float, width: Float, mouseX: Int, mouseY: Int, dt: Float,
                     forceExpanded: Boolean = false, popup: Boolean = false): Float {
        val nextExpanded = forceExpanded || width >= 300f
        if (expanded != nextExpanded) releaseInput()
        this.x = x
        this.y = y
        this.width = width.coerceAtLeast(1f)
        this.popup = popup
        standalone = forceExpanded && popup
        expanded = nextExpanded
        height = measureHeight(this.width, forceExpanded)
        selectedIndex = selectedIndex.coerceIn(0, pointsSetting.value.lastIndex)
        if (captionSize != pointsSetting.value.size) {
            captionSize = pointsSetting.value.size
            pointCaption = "$captionSize/${pointsSetting.maxPoints} points"
        }
        ScissorUtil.start(x, y, this.width, height)
        try {
            val clip = ChromaRenderer.scissorStack.current
            clipX = clip.x()
            clipY = clip.y()
            clipWidth = clip.width()
            clipHeight = clip.height()
            val palette = M3Theme.current
            RenderUtils.roundedRect(matrix, x + 1f, y + 1f, (this.width - 2f).coerceAtLeast(1f), height - 2f,
                palette.surfaceContainer, 8f, rectPipeline)
            RenderUtils.currentGraphics?.nextStratum()
            val titleWidth = if (expanded && !standalone) this.width - 100f else this.width - 30f
            val title = fitText(pointsSetting.name, titleWidth, 10f, 8f)
            Fonts.google_sans_medium.drawString(matrix, title.text, innerX, y + 9f, title.size, palette.onSurface, false, textPipeline)
            if (expanded) {
                if (!standalone) button(matrix, "Open editor", x + this.width - 86f, y + 5f, 76f, 23f, false, mouseX, mouseY)
                renderExpanded(matrix, mouseX, mouseY, dt)
            } else {
                Fonts.google_sans_medium.drawRightString(matrix, "›", x + this.width - 9f, y + 5f, 15f, palette.primary, false, textPipeline)
                renderPreview(matrix, mouseX, mouseY)
                Fonts.google_sans_regular.drawCenteredString(matrix, "$pointCaption · Edit", x + this.width * 0.5f, y + 90f,
                    8f, palette.onSurfaceVariant, false, textPipeline)
            }
        } finally {
            ScissorUtil.end()
        }
        return height
    }

    private fun layoutProjection(projection: Projection, px: Float, py: Float, w: Float, h: Float, compact: Boolean = false) {
        projection.x = px
        projection.y = py
        projection.width = w
        projection.height = h
        val availableWidth = (w - if (compact) 14f else 35f).coerceAtLeast(8f)
        val availableHeight = (h - if (compact) 14f else 48f).coerceAtLeast(8f)
        val aspect = if (projection.view.upright) 1f / 3f else 1f
        projection.plotHeight = minOf(availableHeight, availableWidth / aspect)
        projection.plotWidth = projection.plotHeight * aspect
        projection.plotX = px + (w - projection.plotWidth) * 0.5f + if (compact) 0f else 4f
        projection.plotY = py + (if (compact) 7f else 24f) + (availableHeight - projection.plotHeight) * 0.5f
    }

    private fun renderPreview(matrix: Matrix4f, mouseX: Int, mouseY: Int) {
        val cardWidth = (innerWidth - 8f) / 3f
        for (i in projections.indices) {
            val projection = projections[i]
            layoutProjection(projection, innerX + i * (cardWidth + 4f), y + 29f, cardWidth, 55f, compact = true)
            renderProjection(matrix, projection, mouseX, mouseY, false)
        }
    }

    private fun renderExpanded(matrix: Matrix4f, mouseX: Int, mouseY: Int, dt: Float) {
        val palette = M3Theme.current
        val hint = if (width >= 400f) "Left: add / drag · Right: remove · New point keeps selected depth" else "Left: add / drag · Right: remove · Depth kept"
        val fittedHint = fitText(hint, innerWidth, 8f, 7f)
        Fonts.google_sans_regular.drawString(matrix, fittedHint.text, innerX, y + 36f, fittedHint.size, palette.onSurfaceVariant, false, textPipeline)
        val columns = projectionColumns
        val cardWidth = (innerWidth - (columns - 1) * 7f) / columns
        for (i in projections.indices) {
            val projection = projections[i]
            layoutProjection(projection, innerX + (i % columns) * (cardWidth + 7f), y + 57f + (i / columns) * (projectionHeight + 7f), cardWidth, projectionHeight)
            renderProjection(matrix, projection, mouseX, mouseY, true)
        }
        val rows = (3 + columns - 1) / columns
        controlsY = y + 57f + rows * projectionHeight + (rows - 1) * 7f + 6f
        val selectorWidth = minOf(150f, innerWidth - 68f).coerceAtLeast(40f)
        button(matrix, "‹", innerX, controlsY, 22f, 22f, false, mouseX, mouseY)
        button(matrix, "›", innerX + selectorWidth - 22f, controlsY, 22f, 22f, false, mouseX, mouseY)
        val selectionCaption = fitText("#${selectedIndex + 1} · $pointCaption", selectorWidth - 48f, 8f, 7f)
        Fonts.google_sans_regular.drawCenteredString(matrix, selectionCaption.text, innerX + selectorWidth * 0.5f,
            controlsY + 7f, selectionCaption.size, palette.onSurfaceVariant, false, textPipeline)
        button(matrix, "Remove", x + width - 70f, controlsY, 60f, 22f, false, mouseX, mouseY, pointsSetting.value.size > 1)
        numericY = controlsY + 30f
        syncInputs()
        val fieldWidth = (innerWidth - 12f) / 3f
        for (axis in fields.indices) {
            val fieldX = innerX + axis * (fieldWidth + 6f)
            Fonts.google_sans_medium.drawString(matrix, AXIS_LABELS[axis], fieldX + 3f, numericY, 8f, palette.onSurfaceVariant, false, textPipeline)
            fields[axis].setBounds(fieldX, numericY + 12f, fieldWidth, 23f)
            fields[axis].render(matrix, dt, popup)
        }
        presetsY = numericY + 43f
        for (i in PRESET_LABELS.indices) {
            val selected = when (i) {
                0 -> pointsSetting.value == pointsSetting.defaultValue
                else -> pointsSetting.value == presets[i - 1]
            }
            button(matrix, PRESET_LABELS[i], innerX + (i % presetColumns) * (presetWidth + 5f), presetsY + (i / presetColumns) * 26f,
                presetWidth, 22f, selected, mouseX, mouseY)
        }
    }

    private fun renderProjection(matrix: Matrix4f, projection: Projection, mouseX: Int, mouseY: Int, interactive: Boolean) {
        val palette = M3Theme.current
        val view = projection.view
        RenderUtils.roundedRect(matrix, projection.x, projection.y, projection.width, projection.height, palette.surfaceContainerLowest, 6f, rectPipeline)
        RenderUtils.currentGraphics?.nextStratum()
        if (interactive) {
            val title = fitText(view.title, projection.width - 10f, 8.5f, 7f)
            Fonts.google_sans_medium.drawCenteredString(matrix, title.text, projection.x + projection.width * 0.5f, projection.y + 8f,
                title.size, palette.onSurface, false, textPipeline)
        }
        val px = projection.plotX
        val py = projection.plotY
        val pw = projection.plotWidth
        val ph = projection.plotHeight
        for (i in 0..4) {
            val color = if (i == 0 || i == 4) palette.outline else palette.outlineVariant.alpha(100)
            RenderUtils.rect(matrix, px + pw * i / 4f, py, 0.7f, ph, color, rectPipeline)
            RenderUtils.rect(matrix, px, py + ph * i / 4f, pw, 0.7f, color, rectPipeline)
        }
        if (interactive) {
            Fonts.google_sans_regular.drawString(matrix, if (view.upright) "1" else "0", px - 10f, py - 3f, 7f, palette.onSurfaceVariant, false, textPipeline)
            Fonts.google_sans_regular.drawString(matrix, if (view.upright) "0" else "1", px - 10f, py + ph - 5f, 7f, palette.onSurfaceVariant, false, textPipeline)
            Fonts.google_sans_regular.drawString(matrix, "0", px - 2f, py + ph + 5f, 7f, palette.onSurfaceVariant, false, textPipeline)
            Fonts.google_sans_regular.drawRightString(matrix, "1", px + pw + 2f, py + ph + 5f, 7f, palette.onSurfaceVariant, false, textPipeline)
            Fonts.google_sans_regular.drawCenteredString(matrix, view.horizontalLabel, projection.x + projection.width * 0.5f,
                projection.y + projection.height - 12f, 7.5f, palette.onSurfaceVariant, false, textPipeline)
            Fonts.google_sans_regular.drawString(matrix, view.verticalLabel, projection.x + 5f, projection.y + 23f, 7f, palette.onSurfaceVariant, false, textPipeline)
        }
        val hovered = if (interactive && inClip(mouseX.toDouble(), mouseY.toDouble())) pointAt(projection, mouseX.toDouble(), mouseY.toDouble()) else -1
        for (i in pointsSetting.value.indices) {
            if (i != selectedIndex) drawPoint(matrix, projection, i, i == hovered, false, interactive)
        }
        drawPoint(matrix, projection, selectedIndex, selectedIndex == hovered, true, interactive)
    }

    private fun drawPoint(matrix: Matrix4f, projection: Projection, index: Int, hovered: Boolean, selected: Boolean, interactive: Boolean) {
        val point = pointsSetting.value.getOrNull(index) ?: return
        val palette = M3Theme.current
        val px = projection.plotX + (projection.view.horizontal(point) * projection.plotWidth).toFloat()
        val py = projection.plotY + (projection.view.vertical(point) * projection.plotHeight).toFloat()
        val radius = if (!interactive) 1.7f else if (selected || hovered) 4f else 3f
        if (selected && interactive) {
            RenderUtils.rect(matrix, px - 7f, py - 0.7f, 14f, 1.4f, palette.tertiary.alpha(150), rectPipeline)
            RenderUtils.rect(matrix, px - 0.7f, py - 7f, 1.4f, 14f, palette.tertiary.alpha(150), rectPipeline)
        }
        RenderUtils.roundedRect(matrix, px - radius, py - radius, radius * 2f, radius * 2f,
            if (selected) palette.tertiary else palette.primary, radius, rectPipeline)
        RenderUtils.roundedRect(matrix, px - 1f, py - 1f, 2f, 2f, palette.surfaceContainerLowest, 1f, rectPipeline)
    }

    private fun button(matrix: Matrix4f, text: String, bx: Float, by: Float, bw: Float, bh: Float, selected: Boolean,
                       mouseX: Int, mouseY: Int, enabled: Boolean = true) {
        val palette = M3Theme.current
        val hovered = enabled && inClip(mouseX.toDouble(), mouseY.toDouble()) && isHovered(mouseX.toDouble(), mouseY.toDouble(), bx, by, bw, bh)
        RenderUtils.roundedRect(matrix, bx, by, bw, bh, when {
            selected -> palette.primaryContainer
            hovered -> palette.surfaceContainerHighest
            else -> palette.surfaceContainerHigh
        }, 5f, rectPipeline)
        val fitted = fitText(text, bw - 6f, 8.5f, 7f)
        Fonts.google_sans_medium.drawCenteredString(matrix, fitted.text, bx + bw * 0.5f, by + (bh - fitted.size) * 0.5f - 0.5f,
            fitted.size, if (!enabled) palette.onSurfaceVariant.alpha(110) else if (selected) palette.onPrimaryContainer else palette.onSurface, false, textPipeline)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (!inClip(mouseX, mouseY) || !isHovered(mouseX, mouseY, x, y, width, height)) return false
        if (!expanded) {
            if (button != 0) return false
            releaseInput()
            ClickGuiScreen.openEditor(HitboxPointsWindow(pointsSetting))
            return true
        }
        if (button != 0 && button != 1) return false
        for (field in fields) {
            if (button == 0 && field.contains(mouseX, mouseY)) {
                if (!field.isFocused) commitGesture()
                fields.forEach { if (it !== field) it.blur() }
                field.mouseClicked(mouseX, mouseY, button)
                return true
            }
        }
        fields.forEach { it.blur() }
        commitGesture()
        syncInputs(true)
        if (button == 0) {
            if (!standalone && isHovered(mouseX, mouseY, x + width - 86f, y + 5f, 76f, 23f)) {
                releaseInput()
                ClickGuiScreen.openEditor(HitboxPointsWindow(pointsSetting))
                return true
            }
            val selectorWidth = minOf(150f, innerWidth - 68f).coerceAtLeast(40f)
            if (isHovered(mouseX, mouseY, innerX, controlsY, 22f, 22f)) { select((selectedIndex - 1 + pointsSetting.value.size) % pointsSetting.value.size); return true }
            if (isHovered(mouseX, mouseY, innerX + selectorWidth - 22f, controlsY, 22f, 22f)) { select((selectedIndex + 1) % pointsSetting.value.size); return true }
            if (isHovered(mouseX, mouseY, x + width - 70f, controlsY, 60f, 22f)) { removeSelected(); return true }
            for (i in PRESET_LABELS.indices) {
                if (isHovered(mouseX, mouseY, innerX + (i % presetColumns) * (presetWidth + 5f), presetsY + (i / presetColumns) * 26f, presetWidth, 22f)) {
                    beginGesture()
                    if (i == 0) pointsSetting.reset() else pointsSetting.set(presets[i - 1])
                    selectedIndex = 0
                    syncInputs(true)
                    return true
                }
            }
        }
        for (projection in projections) {
            if (!isHovered(mouseX, mouseY, projection.plotX - 7f, projection.plotY - 7f, projection.plotWidth + 14f, projection.plotHeight + 14f)) continue
            activeView = projection.view
            val index = pointAt(projection, mouseX, mouseY)
            if (button == 1) {
                if (index >= 0) { select(index); removeSelected() }
                return true
            }
            if (index >= 0) {
                select(index)
            } else if (isHovered(mouseX, mouseY, projection.plotX, projection.plotY, projection.plotWidth, projection.plotHeight)) {
                val point = projectedPoint(projection, mouseX, mouseY)
                beginGesture()
                val added = pointsSetting.addPoint(point)
                if (added < 0) return true
                selectedIndex = added
                syncInputs(true)
            } else return true
            dragProjection = projection
            beginGesture()
            return true
        }
        return true
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, deltaX: Double, deltaY: Double) {
        if (button != 0) return
        if (fields.any { it.mouseDragged(mouseX, button) }) return
        val projection = dragProjection ?: return
        val clampedX = mouseX.coerceIn(clipX.toDouble(), (clipX + clipWidth).toDouble())
        val clampedY = mouseY.coerceIn(clipY.toDouble(), (clipY + clipHeight).toDouble())
        moveSelected(projectedPoint(projection, clampedX, clampedY))
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int) {
        if (button != 0 && button != 1) return
        dragProjection = null
        fields.forEach { it.mouseReleased(button) }
        if (fields.none { it.isFocused }) commitGesture()
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (!expanded) return false
        val focused = fields.indexOfFirst { it.isFocused }
        if (focused >= 0) {
            if (keyCode == InputConstants.KEY_TAB) {
                commitGesture()
                val direction = if (modifiers and InputConstants.MOD_SHIFT != 0) -1 else 1
                fields[focused].blur()
                syncInputs(true)
                fields[(focused + direction + fields.size) % fields.size].focus()
                return true
            }
            val handled = fields[focused].keyPressed(keyCode, scanCode, modifiers)
            if (!fields[focused].isFocused) { commitGesture(); syncInputs(true) }
            return handled
        }
        if (keyCode == InputConstants.KEY_DELETE || keyCode == InputConstants.KEY_BACKSPACE) {
            removeSelected()
            commitGesture()
            return true
        }
        val step = if (modifiers and InputConstants.MOD_SHIFT != 0) 0.05 else 0.005
        val dx = when (keyCode) { InputConstants.KEY_LEFT -> -step; InputConstants.KEY_RIGHT -> step; else -> 0.0 }
        val dy = when (keyCode) { InputConstants.KEY_UP -> -step; InputConstants.KEY_DOWN -> step; else -> 0.0 }
        if (dx == 0.0 && dy == 0.0) return false
        val point = selectedPoint ?: return false
        moveSelected(activeView.point(point, (activeView.horizontal(point) + dx).coerceIn(0.0, 1.0), (activeView.vertical(point) + dy).coerceIn(0.0, 1.0)))
        commitGesture()
        return true
    }

    override fun charTyped(event: CharacterEvent): Boolean = fields.any { it.charTyped(event) }

    fun releaseInput() {
        dragProjection = null
        fields.forEach { it.blur() }
        commitGesture()
        syncInputs(true)
    }

    private fun projectedPoint(projection: Projection, mouseX: Double, mouseY: Double): HitboxPoint {
        val base = selectedPoint ?: pointsSetting.value.first()
        val horizontal = ((mouseX - projection.plotX) / projection.plotWidth).coerceIn(0.0, 1.0)
        val vertical = ((mouseY - projection.plotY) / projection.plotHeight).coerceIn(0.0, 1.0)
        return projection.view.point(base, horizontal, vertical)
    }

    private fun pointAt(projection: Projection, mouseX: Double, mouseY: Double): Int {
        var closest = -1
        var bestDistance = 49.0
        for (index in pointsSetting.value.indices) {
            val point = pointsSetting.value[index]
            val dx = mouseX - projection.plotX - projection.view.horizontal(point) * projection.plotWidth
            val dy = mouseY - projection.plotY - projection.view.vertical(point) * projection.plotHeight
            val distance = dx * dx + dy * dy
            if (distance <= bestDistance) {
                if (index == selectedIndex && distance <= 49.0) return index
                closest = index
                bestDistance = distance
            }
        }
        return closest
    }

    private fun select(index: Int) {
        selectedIndex = index.coerceIn(0, pointsSetting.value.lastIndex)
        syncInputs(true)
    }

    private fun moveSelected(point: HitboxPoint) {
        if (selectedPoint == null || selectedPoint == point) return
        beginGesture()
        pointsSetting.movePoint(selectedIndex, point)
        selectedIndex = pointsSetting.value.indexOf(point).takeIf { it >= 0 } ?: selectedIndex.coerceIn(0, pointsSetting.value.lastIndex)
        syncInputs()
    }

    private fun removeSelected() {
        if (pointsSetting.value.size <= 1) return
        beginGesture()
        pointsSetting.removePoint(selectedIndex)
        selectedIndex = selectedIndex.coerceIn(0, pointsSetting.value.lastIndex)
        syncInputs(true)
    }

    private fun beginGesture() {
        if (gestureStart == null) gestureStart = pointsSetting.value
    }

    private fun commitGesture() {
        val before = gestureStart ?: return
        gestureStart = null
        if (before != pointsSetting.value) ConfigManager.saveCurrentConfig()
    }

    private fun syncInputs(force: Boolean = false) {
        if (!force && inputValue === pointsSetting.value && inputIndex == selectedIndex) return
        val point = selectedPoint ?: return
        if (!fields[0].isFocused) fields[0].setText(String.format(Locale.ROOT, "%.4f", point.x))
        if (!fields[1].isFocused) fields[1].setText(String.format(Locale.ROOT, "%.4f", point.y))
        if (!fields[2].isFocused) fields[2].setText(String.format(Locale.ROOT, "%.4f", point.z))
        inputValue = pointsSetting.value
        inputIndex = selectedIndex
    }

    private fun inClip(mouseX: Double, mouseY: Double): Boolean =
        clipWidth > 0f && clipHeight > 0f && mouseX >= clipX && mouseX < clipX + clipWidth && mouseY >= clipY && mouseY < clipY + clipHeight


    private companion object {
        val AXIS_LABELS = arrayOf("X · 0…1", "Y · 0…1", "Z · 0…1")
        val PRESET_LABELS = arrayOf("Reset", "Center", "Body", "Corners")
        val PRESETS = listOf(
            listOf(HitboxPoint(0.5, 0.5, 0.5)),
            listOf(HitboxPoint(0.5, 0.55, 0.5), HitboxPoint(0.5, 0.85, 0.5), HitboxPoint(0.5, 0.25, 0.5), HitboxPoint(0.25, 0.55, 0.5), HitboxPoint(0.75, 0.55, 0.5)),
            listOf(HitboxPoint(0.0, 0.0, 0.0), HitboxPoint(1.0, 1.0, 1.0), HitboxPoint(1.0, 0.0, 0.0), HitboxPoint(0.0, 1.0, 1.0),
                HitboxPoint(0.0, 1.0, 0.0), HitboxPoint(1.0, 0.0, 1.0), HitboxPoint(1.0, 1.0, 0.0), HitboxPoint(0.0, 0.0, 1.0))
        )
    }
}
