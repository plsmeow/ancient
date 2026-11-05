package meow.ancient.client.gui.material.components

import com.mojang.blaze3d.platform.InputConstants
import meow.ancient.client.config.ConfigManager
import meow.ancient.client.extensions.mc
import meow.ancient.client.gui.material.ClickGuiScreen
import meow.ancient.client.gui.material.M3Theme
import meow.ancient.client.util.bind.SettingBindingManager
import meow.ancient.client.util.render.RenderUtils
import meow.ancient.client.util.render.ScissorUtil
import meow.ancient.client.util.render.engine.controls.ClientRenderPipeline
import meow.ancient.client.util.render.font.Fonts
import meow.ancient.module.modules.combat.hitaura.rotation.AdvancedRotationSettings
import meow.ancient.module.setting.BooleanSetting
import meow.ancient.module.setting.CurveSetting
import meow.ancient.module.setting.EnumSetting
import meow.ancient.module.setting.HitboxPointsSetting
import meow.ancient.module.setting.ModeSetting
import meow.ancient.module.setting.MultiModeSetting
import meow.ancient.module.setting.NumberSetting
import meow.ancient.module.setting.Setting
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import org.joml.Matrix4f
import java.util.Locale

class AdvancedSettingsWindow(private val settings: AdvancedRotationSettings) : EditorWindow {
    private class Entry(val component: SettingComponent, val tab: Int, val searchText: String) {
        var visible = false
        var matches = true
        var left = 0f
        var top = 0f
        var width = 0f
        var height = 0f
        var componentHeight = 0f
        var description = ""
        val fullWidth get() = component.setting is HitboxPointsSetting
    }

    override var closed = false
        private set
    private val entries = settings.tabs.flatMapIndexed { tab, group ->
        group.settings.map { setting ->
            val modes = when (setting) {
                is EnumSetting<*> -> setting.modes
                is ModeSetting -> setting.modes
                is MultiModeSetting -> setting.modes
                else -> emptyList()
            }
            Entry(SettingComponentFactory.create(setting), tab,
                "${group.title} ${setting.name} ${setting.description} ${modes.joinToString(" ")}".lowercase(Locale.ROOT))
        }
    }
    private val rows = ArrayList<Entry>()
    private val visibleCounts = IntArray(settings.tabs.size)
    private val matchingCounts = IntArray(settings.tabs.size)
    private val tabLabels = Array(settings.tabs.size) { settings.tabs[it].title }
    private val tabLabelCounts = IntArray(settings.tabs.size) { -1 }
    private val scroll = FloatArray(settings.tabs.size)
    private val filteredScroll = FloatArray(settings.tabs.size)
    private var selectedTab = 0
    private var query = ""
    private val search = CustomTextField(maxLength = 100, hint = "Search settings") { text ->
        val next = text.trim().lowercase(Locale.ROOT)
        if (query != next) {
            releaseComponents()
            query = next
            filteredScroll.fill(0f)
            layoutDirty = true
            layoutReady = false
        }
    }
    private var x = 0f
    private var y = 0f
    private var width = 0f
    private var height = 0f
    private var positioned = false
    private var sidebar = false
    private var layoutDirty = true
    private var layoutReady = false
    private var totalHeight = 0f
    private var columns = 1
    private var draggingWindow = false
    private var draggingScrollbar = false
    private var dragX = 0f
    private var dragY = 0f
    private var thumbOffset = 0f
    private var captured: SettingComponent? = null
    private var capturedButton = -1
    private var keyboardOwner: SettingComponent? = null
    private var inputEpoch = 0
    private var configDirty = false
    private var footerText = ""
    private var footerWidth = -1f
    private var footerLines: List<String> = emptyList()
    private val inset get() = if (width <= 340f) 6f else 12f
    private val searchX get() = x + inset
    private val searchY get() = y + 33f
    private val searchWidth get() = width - inset * 2f - 78f
    private val resetX get() = x + width - inset - 72f
    private val tabsY get() = y + 64f
    private val tabWidth get() = if (sidebar) 108f else (width - inset * 2f - 9f) * 0.25f
    private val tabHeight get() = if (sidebar) 25f else 20f
    private val contentX get() = x + inset + if (sidebar) 120f else 0f
    private val contentY get() = y + if (sidebar) 93f else 126f
    private val contentWidth get() = (x + width - inset - contentX - 8f).coerceAtLeast(1f)
    private val contentHeight get() = (y + height - 33f - contentY).coerceAtLeast(1f)
    private val trackX get() = contentX + contentWidth + 2f
    private val maxScroll get() = (totalHeight - contentHeight).coerceAtLeast(0f)
    private val scrollValues get() = if (query.isEmpty()) scroll else filteredScroll
    private var scrollPosition: Float
        get() = scrollValues[selectedTab]
        set(value) { scrollValues[selectedTab] = value.coerceIn(0f, maxScroll) }
    private val thumbHeight get() = if (totalHeight <= contentHeight) contentHeight else
        (contentHeight * contentHeight / totalHeight).coerceIn(minOf(20f, contentHeight), contentHeight)
    private val thumbY get() = contentY + if (maxScroll > 0f) scrollPosition / maxScroll * (contentHeight - thumbHeight) else 0f

    private fun bounds() {
        val screenWidth = mc.window.guiScaledWidth.toFloat().coerceAtLeast(1f)
        val screenHeight = mc.window.guiScaledHeight.toFloat().coerceAtLeast(1f)
        val marginX = if (screenWidth <= 340f) 0f else 8f
        val marginY = if (screenHeight <= 260f) 0f else 8f
        val nextWidth = minOf(720f, screenWidth - marginX * 2f).coerceAtLeast(1f)
        val nextHeight = minOf(500f, screenHeight - marginY * 2f).coerceAtLeast(1f)
        val nextSidebar = nextWidth >= 540f && nextHeight >= 320f
        if (width != nextWidth || height != nextHeight || sidebar != nextSidebar) {
            releaseInput()
            width = nextWidth
            height = nextHeight
            sidebar = nextSidebar
            layoutDirty = true
            layoutReady = false
        }
        if (!positioned) {
            x = (screenWidth - width) * 0.5f
            y = (screenHeight - height) * 0.5f
            positioned = true
        }
        val nextX = x.coerceIn(0f, (screenWidth - width).coerceAtLeast(0f))
        val nextY = y.coerceIn(0f, (screenHeight - height).coerceAtLeast(0f))
        if (x != nextX || y != nextY) layoutReady = false
        x = nextX
        y = nextY
        search.setBounds(searchX, searchY, (searchWidth - 24f).coerceAtLeast(1f), 24f)
    }

    private fun refreshLayout() {
        bounds()
        visibleCounts.fill(0)
        matchingCounts.fill(0)
        for (entry in entries) {
            val visible = entry.component.setting.isVisible()
            val matches = query.isEmpty() || entry.searchText.contains(query)
            if (entry.visible != visible || entry.matches != matches) {
                if (entry.tab == selectedTab) layoutDirty = true
                entry.visible = visible
                entry.matches = matches
            }
            if (visible) {
                visibleCounts[entry.tab]++
                if (matches) matchingCounts[entry.tab]++
            }
        }
        for (tab in tabLabels.indices) {
            val count = if (query.isEmpty()) -1 else matchingCounts[tab]
            if (tabLabelCounts[tab] != count) {
                tabLabelCounts[tab] = count
                tabLabels[tab] = if (count < 0) settings.tabs[tab].title else "${settings.tabs[tab].title} ($count)"
            }
        }
        if (!layoutDirty) return
        releaseComponents()
        layoutReady = false
        layoutDirty = false
        columns = if (contentWidth >= 490f) 2 else 1
        rows.clear()
        val columnWidth = (contentWidth - (columns - 1) * 10f) / columns
        var rowTop = 0f
        var rowHeight = 0f
        var column = 0
        for (entry in entries) {
            if (entry.tab != selectedTab || !entry.visible || !entry.matches) continue
            if (entry.fullWidth && column != 0) {
                rowTop += rowHeight + 8f
                rowHeight = 0f
                column = 0
            }
            entry.left = if (entry.fullWidth) 0f else column * (columnWidth + 10f)
            entry.top = rowTop
            entry.width = if (entry.fullWidth) contentWidth else columnWidth
            val controlWidth = if (entry.fullWidth) entry.width else entry.width - 12f
            entry.componentHeight = measure(entry.component, controlWidth)
            entry.height = entry.componentHeight + if (entry.fullWidth) 0f else 24f
            entry.description = elide(entry.component.setting.description, (entry.width - 12f).coerceAtLeast(1f), 7.5f)
            rows.add(entry)
            rowHeight = maxOf(rowHeight, entry.height)
            if (entry.fullWidth || ++column == columns) {
                rowTop += rowHeight + 8f
                rowHeight = 0f
                column = 0
            }
        }
        totalHeight = if (column != 0) rowTop + rowHeight else (rowTop - 8f).coerceAtLeast(0f)
        scrollPosition = scrollPosition
    }

    private fun measure(component: SettingComponent, width: Float): Float = when (val setting = component.setting) {
        is BooleanSetting -> 18f
        is NumberSetting -> 26f
        is CurveSetting -> 101f
        is HitboxPointsSetting -> (component as HitboxPointsComponent).measureHeight(width)
        is EnumSetting<*> -> modeHeight(setting.modes, width)
        is ModeSetting -> modeHeight(setting.modes, width)
        is MultiModeSetting -> modeHeight(setting.modes, width)
        else -> component.height.coerceAtLeast(28f)
    }

    private fun modeHeight(modes: List<String>, width: Float): Float {
        var right = 3.5f
        var rows = 1
        for (mode in modes) {
            val chipWidth = (Fonts.google_sans_medium.width(mode, 8f) + 20.5f).coerceAtLeast(24f)
            if (right + chipWidth > width - 3.5f && right > 3.5f) {
                right = 3.5f
                rows++
            }
            right += chipWidth + 3f
        }
        return 32.5f + (rows - 1) * 19f
    }

    override fun render(matrix: Matrix4f, mouseX: Int, mouseY: Int, dt: Float) {
        if (closed) return
        refreshLayout()
        if (captured != null && (!insideContent(mouseX.toDouble(), mouseY.toDouble()) || !isInteractive(captured!!))) {
            releaseComponent(captured!!)
            captured = null
            capturedButton = -1
            saveChanges()
        }
        val palette = M3Theme.current
        RenderUtils.rect(matrix, 0f, 0f, mc.window.guiScaledWidth.toFloat(), mc.window.guiScaledHeight.toFloat(),
            palette.scrim.alpha(145), ClientRenderPipeline.POPUP_RECT)
        RenderUtils.roundedRect(matrix, x, y, width, height, palette.surface, 11f, ClientRenderPipeline.POPUP_RECT)
        RenderUtils.currentGraphics?.nextStratum()
        ScissorUtil.withScissor(x, y, width, height) {
            Fonts.google_sans_medium.drawString(matrix, "Advanced rotation", x + inset, y + 11f, 11f, palette.onSurface,
                false, ClientRenderPipeline.POPUP_TEXT)
            button(matrix, "×", x + width - inset - 24f, y + 5f, 24f, 23f, false, mouseX, mouseY)
            search.render(matrix, dt, popup = true)
            button(matrix, "×", searchX + searchWidth - 22f, searchY, 22f, 24f, false, mouseX, mouseY)
            button(matrix, "Reset tab", resetX, searchY, 72f, 24f, false, mouseX, mouseY)
            for (tab in settings.tabs.indices) {
                button(matrix, tabLabels[tab], tabX(tab), tabY(tab), tabWidth, tabHeight,
                    tab == selectedTab, mouseX, mouseY, matchingCounts[tab] == 0 && query.isNotEmpty())
            }
            val headerY = contentY - 20f
            Fonts.google_sans_medium.drawString(matrix, settings.tabs[selectedTab].title, contentX + 3f, headerY,
                10f, palette.onSurface, false, ClientRenderPipeline.POPUP_TEXT)
            val count = if (query.isEmpty()) "${visibleCounts[selectedTab]}/${settings.tabs[selectedTab].settings.size} settings" else
                "${matchingCounts[selectedTab]}/${visibleCounts[selectedTab]} matches"
            Fonts.google_sans_regular.drawRightString(matrix, count, contentX + contentWidth - 2f, headerY + 1f,
                8f, palette.onSurfaceVariant, false, ClientRenderPipeline.POPUP_TEXT)
            renderContent(matrix, mouseX, mouseY, dt)
            if (maxScroll > 0f) {
                RenderUtils.roundedRect(matrix, trackX, contentY, 4f, contentHeight, palette.surfaceContainerHigh, 2f,
                    ClientRenderPipeline.POPUP_RECT)
                RenderUtils.roundedRect(matrix, trackX, thumbY, 4f, thumbHeight,
                    if (draggingScrollbar || over(mouseX.toDouble(), mouseY.toDouble(), trackX - 2f, contentY, 8f, contentHeight)) palette.primary else palette.outline,
                    2f, ClientRenderPipeline.POPUP_RECT)
            }
            renderFooter(matrix, mouseX.toDouble(), mouseY.toDouble())
        }
        layoutReady = true
    }

    private fun renderContent(matrix: Matrix4f, mouseX: Int, mouseY: Int, dt: Float) {
        val palette = M3Theme.current
        ScissorUtil.withScissor(contentX, contentY, contentWidth, contentHeight) {
            if (rows.isEmpty()) {
                Fonts.google_sans_medium.drawCenteredString(matrix, "No matching settings", contentX + contentWidth * 0.5f,
                    contentY + 16f, 10f, palette.onSurfaceVariant, false, ClientRenderPipeline.POPUP_TEXT)
                Fonts.google_sans_regular.drawCenteredString(matrix, "Choose another tab or clear search", contentX + contentWidth * 0.5f,
                    contentY + 34f, 8f, palette.onSurfaceVariant, false, ClientRenderPipeline.POPUP_TEXT)
            }
            for (entry in rows) {
                val rowY = contentY + entry.top - scrollPosition
                val component = entry.component
                val padding = if (entry.fullWidth) 0f else 6f
                component.x = contentX + entry.left + padding
                component.y = rowY + padding
                component.width = entry.width - padding * 2f
                component.height = entry.componentHeight
                if (rowY + entry.height <= contentY || rowY >= contentY + contentHeight) continue
                val inside = insideContent(mouseX.toDouble(), mouseY.toDouble())
                val renderMouseX = if (inside) mouseX else -10000
                val renderMouseY = if (inside) mouseY else -10000
                ScissorUtil.withScissor(contentX + entry.left, rowY, entry.width, entry.height) {
                    if (!entry.fullWidth) {
                        RenderUtils.roundedRect(matrix, contentX + entry.left, rowY, entry.width, entry.height,
                            palette.surfaceContainerLow, 7f, ClientRenderPipeline.POPUP_RECT)
                        RenderUtils.currentGraphics?.nextStratum()
                    }
                    component.render(matrix, component.x, component.y, component.width, renderMouseX, renderMouseY, dt)
                    if (!entry.fullWidth && entry.description.isNotEmpty()) {
                        Fonts.google_sans_regular.drawString(matrix, entry.description, component.x, rowY + entry.componentHeight + 11f,
                            7.5f, palette.onSurfaceVariant, false, ClientRenderPipeline.POPUP_TEXT)
                    }
                }
            }
        }
    }

    private fun renderFooter(matrix: Matrix4f, mouseX: Double, mouseY: Double) {
        var text = "Middle-click: setting bind · Ctrl+click a slider: exact value. Search filters each tab; dependent settings follow their options."
        if (insideContent(mouseX, mouseY)) {
            for (entry in rows) {
                if (over(mouseX, mouseY, contentX + entry.left, contentY + entry.top - scrollPosition, entry.width, entry.height)) {
                    text = entry.component.setting.description.ifEmpty { entry.component.setting.name }
                    break
                }
            }
        }
        val availableWidth = width - inset * 2f
        if (footerText != text || footerWidth != availableWidth) {
            footerText = text
            footerWidth = availableWidth
            footerLines = wrap(text, availableWidth, 7.5f, 2)
        }
        val palette = M3Theme.current
        RenderUtils.rect(matrix, x + inset, y + height - 29f, availableWidth, 1f, palette.outlineVariant.alpha(100),
            ClientRenderPipeline.POPUP_RECT)
        for (line in footerLines.indices) {
            Fonts.google_sans_regular.drawString(matrix, footerLines[line], x + inset, y + height - 24f + line * 10f,
                7.5f, palette.onSurfaceVariant, false, ClientRenderPipeline.POPUP_TEXT)
        }
    }

    private fun button(matrix: Matrix4f, text: String, bx: Float, by: Float, bw: Float, bh: Float,
                       selected: Boolean, mouseX: Int, mouseY: Int, muted: Boolean = false) {
        val palette = M3Theme.current
        val hovered = over(mouseX.toDouble(), mouseY.toDouble(), bx, by, bw, bh)
        RenderUtils.roundedRect(matrix, bx, by, bw, bh, when {
            selected -> palette.primaryContainer
            hovered -> palette.surfaceContainerHighest
            else -> palette.surfaceContainer
        }, 6f, ClientRenderPipeline.POPUP_RECT)
        Fonts.google_sans_medium.drawCenteredString(matrix, elide(text, bw - 8f, 8.5f, true), bx + bw * 0.5f,
            by + (bh - 8.5f) * 0.5f - 0.5f, 8.5f, when {
                selected -> palette.onPrimaryContainer
                muted -> palette.onSurfaceVariant
                else -> palette.onSurface
            }, false, ClientRenderPipeline.POPUP_TEXT)
    }

    override fun mouseClicked(event: MouseButtonEvent, button: Int) {
        if (closed) return
        refreshLayout()
        val mx = event.x()
        val my = event.y()
        if (!over(mx, my, x, y, width, height)) { close(); return }
        if (button == 0 && over(mx, my, x + width - inset - 24f, y + 5f, 24f, 23f)) { close(); return }
        CustomTextField.blurOutside(mx, my)
        if (button == 0 && over(mx, my, searchX + searchWidth - 22f, searchY, 22f, 24f)) {
            releaseInput()
            search.setText("")
            query = ""
            layoutDirty = true
            layoutReady = false
            search.focus()
            return
        }
        if (search.contains(mx, my)) {
            releaseComponents()
            search.mouseClicked(mx, my, button)
            return
        }
        if (button == 0 && over(mx, my, resetX, searchY, 72f, 24f)) {
            releaseInput()
            for (setting in settings.tabs[selectedTab].settings) setting.reset()
            ConfigManager.saveCurrentConfig()
            layoutDirty = true
            layoutReady = false
            return
        }
        if (button == 0) {
            for (tab in settings.tabs.indices) {
                if (over(mx, my, tabX(tab), tabY(tab), tabWidth, tabHeight)) {
                    selectTab(tab)
                    return
                }
            }
            if (over(mx, my, x, y, width - inset - 28f, 29f)) {
                releaseInput()
                draggingWindow = true
                dragX = mx.toFloat() - x
                dragY = my.toFloat() - y
                return
            }
            if (maxScroll > 0f && over(mx, my, trackX - 2f, contentY, 8f, contentHeight)) {
                val clickedThumb = over(mx, my, trackX - 2f, thumbY, 8f, thumbHeight)
                val offset = if (clickedThumb) my.toFloat() - thumbY else thumbHeight * 0.5f
                releaseInput()
                draggingScrollbar = true
                thumbOffset = offset
                dragScroll(my)
                return
            }
        }
        if (!layoutReady || !insideContent(mx, my)) {
            releaseComponents()
            return
        }
        val listening = keyboardOwner as? KeybindComponent
        if (button >= 4 && listening?.isListening == true && isInteractive(listening)) {
            listening.mouseClicked(mx, my, button, event.modifiers())
            configDirty = true
            saveChanges()
            return
        }
        for (entry in rows) {
            val component = entry.component
            if (!isInteractive(component) || !over(mx, my, component.x, component.y, component.width, component.height)) continue
            if (button == 2 && SettingBindingManager.supports(component.setting)) {
                releaseInput()
                ClickGuiScreen.openSettingBinding(SettingBindingWindow(component.setting, mx.toFloat(), my.toFloat()))
                return
            }
            val nativePopup = component is ColorComponent || component is PickerComponent || component is TargetPickerComponent
            if (nativePopup) releaseInput() else releaseComponents(component)
            search.blur()
            val epoch = inputEpoch
            if (component.mouseClicked(mx, my, button, event.modifiers())) {
                if (inputEpoch == epoch && !nativePopup) {
                    keyboardOwner = component
                    captured = component
                    capturedButton = button
                    if (component !is HitboxPointsComponent && component !is CurveEditorComponent) configDirty = true
                }
                return
            }
        }
        releaseComponents()
    }

    override fun mouseDragged(event: MouseButtonEvent, button: Int, deltaX: Double, deltaY: Double) {
        if (closed) return
        if (button == 0 && draggingWindow) {
            x = event.x().toFloat() - dragX
            y = event.y().toFloat() - dragY
            bounds()
            layoutReady = false
            return
        }
        if (button == 0 && draggingScrollbar) { dragScroll(event.y()); return }
        if (search.mouseDragged(event.x(), button)) return
        val component = captured ?: return
        if (button != capturedButton) return
        if (!insideContent(event.x(), event.y()) || !isInteractive(component)) {
            releaseComponent(component)
            captured = null
            capturedButton = -1
            saveChanges()
            return
        }
        component.mouseDragged(event.x(), event.y(), button, deltaX, deltaY)
    }

    override fun mouseReleased(event: MouseButtonEvent, button: Int) {
        if (button == 0) {
            draggingWindow = false
            draggingScrollbar = false
        }
        search.mouseReleased(button)
        if (button == capturedButton) {
            captured?.mouseReleased(event.x(), event.y(), button)
            captured = null
            capturedButton = -1
            saveChanges()
        }
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, delta: Double) {
        if (closed || delta == 0.0) return
        refreshLayout()
        if (!over(mouseX, mouseY, contentX, contentY, contentWidth + 8f, contentHeight) || maxScroll <= 0f) return
        releaseInput()
        setScroll(scrollPosition - delta.toFloat() * 32f)
    }

    override fun keyPressed(event: KeyEvent) {
        if (closed) return
        refreshLayout()
        if (search.keyPressed(event.key(), event.keycode(), event.modifiers())) return
        val owner = keyboardOwner
        if (owner != null && isInteractive(owner) && owner.keyPressed(event.key(), event.keycode(), event.modifiers())) {
            if (owner !is HitboxPointsComponent) configDirty = true
            if (owner is NumberComponent && !owner.isEditing || owner is KeybindComponent && !owner.isListening) saveChanges()
            return
        }
        if (event.key() == InputConstants.KEY_ESCAPE) { close(); return }
        if (event.modifiers() and InputConstants.MOD_CONTROL != 0) {
            when (event.key()) {
                InputConstants.KEY_F -> { releaseInput(); search.focus(); return }
                InputConstants.KEY_TAB -> {
                    val direction = if (event.modifiers() and InputConstants.MOD_SHIFT != 0) -1 else 1
                    selectTab((selectedTab + direction + settings.tabs.size) % settings.tabs.size)
                    return
                }
            }
        }
        when (event.key()) {
            InputConstants.KEY_PAGEUP -> { releaseInput(); setScroll(scrollPosition - contentHeight * 0.85f) }
            InputConstants.KEY_PAGEDOWN -> { releaseInput(); setScroll(scrollPosition + contentHeight * 0.85f) }
            InputConstants.KEY_HOME -> { releaseInput(); setScroll(0f) }
            InputConstants.KEY_END -> { releaseInput(); setScroll(maxScroll) }
        }
    }

    override fun charTyped(event: CharacterEvent) {
        if (closed) return
        refreshLayout()
        if (search.charTyped(event)) return
        val owner = keyboardOwner ?: return
        if (isInteractive(owner) && owner.charTyped(event) && owner !is HitboxPointsComponent) configDirty = true
    }

    private fun selectTab(tab: Int) {
        if (selectedTab == tab) return
        releaseInput()
        selectedTab = tab
        layoutDirty = true
        layoutReady = false
    }

    private fun dragScroll(mouseY: Double) {
        val travel = contentHeight - thumbHeight
        if (travel <= 0f) return
        setScroll(((mouseY.toFloat() - contentY - thumbOffset) / travel).coerceIn(0f, 1f) * maxScroll)
    }

    private fun setScroll(value: Float) {
        val previous = scrollPosition
        scrollPosition = value
        if (previous != scrollPosition) layoutReady = false
    }

    private fun isInteractive(component: SettingComponent): Boolean {
        if (!component.setting.isVisible() || component.height <= 0f) return false
        val entry = rows.firstOrNull { it.component === component } ?: return false
        if (!entry.visible || !entry.matches || component.y >= contentY + contentHeight || component.y + component.height <= contentY) return false
        return component is HitboxPointsComponent || component is CurveEditorComponent ||
            component.y >= contentY && component.y + component.height <= contentY + contentHeight
    }

    private fun releaseComponent(component: SettingComponent) {
        component.mouseReleased(-1.0, -1.0, 0)
        when (component) {
            is NumberComponent -> component.stopEditing()
            is KeybindComponent -> component.stopListening()
            is TextComponent -> component.blur()
            is HitboxPointsComponent -> component.releaseInput()
        }
        if (keyboardOwner === component) keyboardOwner = null
    }

    private fun releaseComponents(except: SettingComponent? = null) {
        inputEpoch++
        for (entry in entries) if (entry.component !== except) releaseComponent(entry.component)
        if (captured !== except) {
            captured = null
            capturedButton = -1
        }
        if (keyboardOwner !== except) keyboardOwner = null
        saveChanges()
    }

    override fun releaseInput() {
        draggingWindow = false
        draggingScrollbar = false
        search.mouseReleased(0)
        search.blur()
        releaseComponents()
    }

    private fun saveChanges() {
        if (!configDirty) return
        configDirty = false
        ConfigManager.saveCurrentConfig()
    }

    override fun close() {
        if (closed) return
        releaseInput()
        ConfigManager.saveCurrentConfig()
        closed = true
    }

    private fun tabX(tab: Int) = if (sidebar) x + inset else x + inset + (tab % 4) * (tabWidth + 3f)
    private fun tabY(tab: Int) = tabsY + if (sidebar) tab * 28f else (tab / 4) * 23f
    private fun insideContent(mx: Double, my: Double) = over(mx, my, contentX, contentY, contentWidth, contentHeight)
    private fun over(mx: Double, my: Double, bx: Float, by: Float, bw: Float, bh: Float) =
        mx >= bx && mx < bx + bw && my >= by && my < by + bh

    private fun elide(text: String, maxWidth: Float, size: Float, medium: Boolean = false): String {
        if (text.isEmpty() || maxWidth <= 0f) return ""
        val font = if (medium) Fonts.google_sans_medium else Fonts.google_sans_regular
        if (font.width(text, size) <= maxWidth) return text
        var end = text.length
        while (end > 0 && font.width(text.subSequence(0, end), size) + font.width("…", size) > maxWidth) {
            end = text.offsetByCodePoints(end, -1)
        }
        return if (end == 0) "" else text.substring(0, end) + "…"
    }

    private fun wrap(text: String, maxWidth: Float, size: Float, maxLines: Int): List<String> {
        val lines = ArrayList<String>(maxLines)
        val words = text.split(' ')
        var line = ""
        for (index in words.indices) {
            val next = if (line.isEmpty()) words[index] else "$line ${words[index]}"
            if (Fonts.google_sans_regular.width(next, size) <= maxWidth || line.isEmpty()) {
                line = next
            } else {
                if (lines.size == maxLines - 1) {
                    lines.add(elide(line + " " + words.subList(index, words.size).joinToString(" "), maxWidth, size))
                    return lines
                }
                lines.add(elide(line, maxWidth, size))
                line = words[index]
            }
        }
        if (line.isNotEmpty() && lines.size < maxLines) lines.add(elide(line, maxWidth, size))
        return lines
    }
}
