package meow.ancient.client.gui.material

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.input.InputQuirks
import meow.ancient.client.config.ConfigManager
import meow.ancient.client.extensions.categories
import meow.ancient.client.extensions.mc
import meow.ancient.client.gui.material.components.CategoryPanel
import meow.ancient.client.gui.material.components.ColorPickerWindow
import meow.ancient.client.gui.material.components.CustomTextField
import meow.ancient.client.gui.material.components.KeybindComponent
import meow.ancient.client.gui.material.components.ModuleComponent
import meow.ancient.client.gui.material.components.NumberComponent
import meow.ancient.client.gui.material.components.PickerWindow
import meow.ancient.client.gui.material.components.SearchBarComponent
import meow.ancient.client.gui.material.components.SettingBindingWindow
import meow.ancient.client.gui.material.components.TargetPickerComponent
import meow.ancient.client.gui.material.components.TargetPickerWindow
import meow.ancient.client.gui.material.components.ThemePickerComponent
import meow.ancient.client.interfaces.IRenderable
import meow.ancient.client.util.other.color.Color4b
import meow.ancient.client.util.render.RenderUtils
import meow.ancient.client.util.render.engine.controls.ClientRenderPipeline
import meow.ancient.client.util.render.engine.controls.MatrixControl
import meow.ancient.module.Module
import meow.ancient.module.modules.render.ClickGuiModule
import meow.ancient.module.setting.KeybindSetting
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component

object ClickGuiScreen : Screen(Component.literal("Ancient ClickGUI")), IRenderable {
    val panels: MutableList<CategoryPanel> = ArrayList()
    private var initialized = false
    val themePicker = ThemePickerComponent()
    val searchBar = SearchBarComponent()
    val searchQuery: String get() = searchBar.query
    var highlightedModule: Module? = null
    var highlightStartTime: Long = 0L

    val highlightProgress: Float
        get() {
            if (highlightedModule == null) return 0f
            val elapsed = (System.currentTimeMillis() - highlightStartTime) / 1000f
            val duration = 2.5f
            if (elapsed >= duration) {
                highlightedModule = null
                return 0f
            }
            return (1f - elapsed / duration).coerceIn(0f, 1f)
        }

    fun scrollToModule(mod: Module) {
        val panel = panels.find { it.category == mod.category } ?: return
        if (!panel.isExpanded) panel.isExpanded = true
        panels.remove(panel)
        panels.add(panel)

        val comp = panel.moduleComponents.find { it.module === mod } ?: return
        var modY = panel.y + 37f
        for (m in panel.moduleComponents) {
            if (m === comp) break
            modY += m.height + 4f
        }
        val modH = comp.height
        val modW = panel.width

        val screenW = mc.window.guiScaledWidth.toFloat()
        val screenH = mc.window.guiScaledHeight.toFloat()
        val minVisibleY = 48f
        val maxVisibleY = screenH - 20f
        val minVisibleX = 16f
        val maxVisibleX = screenW - 16f

        var dx = 0f
        if (panel.x < minVisibleX || panel.x + modW > maxVisibleX) {
            dx = if (panel.x < minVisibleX) minVisibleX - panel.x else maxVisibleX - (panel.x + modW)
        }

        var dy = 0f
        if (modY < minVisibleY || modY + modH > maxVisibleY) {
            dy = if (modY < minVisibleY) (minVisibleY + 12f) - modY else (maxVisibleY - modH - 12f) - modY
        }

        if (dx != 0f || dy != 0f) {
            for (p in panels) {
                p.x += dx
                p.y += dy
            }
        }

        highlightedModule = mod
        highlightStartTime = System.currentTimeMillis()
    }



    @JvmField
    var parentScreen: Screen? = null
    var activeColorPicker: ColorPickerWindow? = null
        set(value) {
            activeColorPicker?.mouseReleased(-1.0, -1.0, 0)
            if (value != null) {
                closePicker()
                closeTargetPicker()
                if (activeSettingBinding?.owns(value.setting) == false) closeSettingBinding()
                clearAllListening()
                releasePanelInput()
            }
            field = value
        }
    private var activePicker: PickerWindow? = null
    var activeTargetPicker: TargetPickerWindow? = null
        private set
    private var activeSettingBinding: SettingBindingWindow? = null

    fun openSettingBinding(window: SettingBindingWindow) {
        activeColorPicker = null
        closePicker()
        closeTargetPicker()
        closeSettingBinding()
        clearAllListening(applyNumberEdits = false)
        releasePanelInput()
        activeSettingBinding = window
    }

    private fun closeSettingBinding() {
        val window = activeSettingBinding ?: return
        activeColorPicker = null
        closePicker()
        closeTargetPicker()
        window.close()
        activeSettingBinding = null
        clearFocus()
        CustomTextField.clearFocus()
    }

    fun openPicker(picker: PickerWindow) {
        if (activeSettingBinding?.owns(picker.setting) == false) closeSettingBinding()
        activeColorPicker = null
        closePicker()
        closeTargetPicker()
        clearAllListening()
        releasePanelInput()
        activePicker = picker
        picker.focusSearch()
    }

    fun openTargetPicker(picker: TargetPickerWindow) {
        if (activeSettingBinding?.owns(picker.setting) == false) closeSettingBinding()
        activeColorPicker = null
        closePicker()
        closeTargetPicker()
        clearAllListening()
        releasePanelInput()
        activeTargetPicker = picker
        picker.focusSearch()
    }

    private fun closePicker() {
        activePicker?.close()
        activePicker = null
        clearFocus()
        CustomTextField.clearFocus()
    }

    fun closeTargetPicker() {
        activeTargetPicker?.close()
        activeTargetPicker = null
        clearFocus()
        CustomTextField.clearFocus()
    }

    private fun releasePanelInput() {
        for (panel in panels) {
            panel.isDragging = false
            panel.mouseReleased(-1.0, -1.0, 0)
        }
    }

    private var lastFrameTime = System.currentTimeMillis()

    private fun initPanels() {
        if (initialized && panels.isNotEmpty()) return

        panels.clear()
        val panelW = 122f
        val gap = 8f
        val totalW = categories.size * panelW + (categories.size - 1) * gap
        val startX = ((mc.window.guiScaledWidth - totalW) * 0.5f).coerceAtLeast(10f)
        val startY = minOf(178f, mc.window.guiScaledHeight * 0.2f).coerceAtLeast(20f)

        categories.forEachIndexed { i, category ->
            panels.add(CategoryPanel(category, startX + i * (panelW + gap), startY, panelW))
        }

        initialized = true
    }

    override fun init() {
        super.init()
        initPanels()
        closePicker()
        closeTargetPicker()
        closeSettingBinding()
        activeColorPicker = null
        releasePanelInput()
        lastFrameTime = System.currentTimeMillis()
        parentScreen?.init(width, height)
    }

    override fun render(mouseX: Int, mouseY: Int) {
        if (RenderUtils.currentGraphics == null) return
        val now = System.currentTimeMillis()
        val dt = ((now - lastFrameTime) / 1000f).coerceIn(0.0001f, 0.1f)
        lastFrameTime = now

        val matrix = MatrixControl.matrix4fStack
        val screenW = mc.window.guiScaledWidth.toFloat()
        val screenH = mc.window.guiScaledHeight.toFloat()

        if (parentScreen != null || (ClickGuiModule.blur && mc.options.getMenuBackgroundBlurriness() == 0)) {
            RenderUtils.rect(
                matrix = matrix,
                x = 0f,
                y = 0f,
                width = screenW,
                height = screenH,
                color = if (parentScreen != null) Color4b(0, 0, 0, 110) else Color4b(0, 0, 0, 80),
                pipeline = ClientRenderPipeline.LOW
            )
        }

        for (panel in panels) {
            RenderUtils.currentGraphics?.nextStratum()
            panel.render(matrix, mouseX, mouseY, dt)
        }

        RenderUtils.currentGraphics?.nextStratum()
        searchBar.render(matrix, mouseX, mouseY, dt)

        RenderUtils.currentGraphics?.nextStratum()
        themePicker.render(matrix, mouseX, mouseY, dt)

        activeSettingBinding?.render(matrix, mouseX, mouseY, dt, activeColorPicker == null && activePicker == null && activeTargetPicker == null)
        activeColorPicker?.let {
            RenderUtils.currentGraphics?.nextStratum()
            it.render(matrix, mouseX, mouseY, dt)
        }
        activePicker?.render(matrix, mouseX, mouseY, dt)
        activeTargetPicker?.render(matrix, mouseX, mouseY, dt)

    }

    override fun extractRenderState(guiGraphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        val previousGraphics = RenderUtils.currentGraphics
        RenderUtils.currentGraphics = guiGraphics
        try {
            render(mouseX, mouseY)
        } finally {
            RenderUtils.currentGraphics = previousGraphics
        }
    }

    override fun extractBackground(guiGraphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        val parent = parentScreen
        if (parent != null) {
            parent.extractRenderState(guiGraphics, -1, -1, partialTick)
            return
        }
        if (mc.level == null) {
            extractPanorama(guiGraphics, partialTick)
        }
        if (mc.options.getMenuBackgroundBlurriness() > 0) {
            extractBlurredBackground(guiGraphics)
        }
    }

    private fun normalizeButton(button: Int): Int = when (button) {
        InputConstants.MOUSE_BUTTON_LEFT -> 0
        InputConstants.MOUSE_BUTTON_RIGHT -> 1
        InputConstants.MOUSE_BUTTON_MIDDLE -> 2
        else -> button
    }

    fun clearAllListening(exceptModule: ModuleComponent? = null, exceptKeybind: KeybindComponent? = null, exceptNumber: NumberComponent? = null, applyNumberEdits: Boolean = true) {
        CustomTextField.clearFocus()
        activeSettingBinding?.releaseEditorInput(exceptNumber, applyNumberEdits)
        for (panel in panels) {
            for (modComp in panel.moduleComponents) {
                if (modComp !== exceptModule) {
                    modComp.isBinding = false
                }
                for (settingComp in modComp.settingComponents) {
                    if (settingComp is KeybindComponent && settingComp !== exceptKeybind) {
                        settingComp.stopListening()
                    }
                    if (settingComp is NumberComponent && settingComp !== exceptNumber) {
                        settingComp.stopEditing(apply = applyNumberEdits)
                    }
                }
            }
        }
    }

    override fun mouseClicked(event: MouseButtonEvent, bl: Boolean): Boolean {
        val rawButton = event.button()
        val mouseX = event.x()
        val mouseY = event.y()
        val button = normalizeButton(event.button())
        activeTargetPicker?.let {
            it.mouseClicked(event, button)
            activeSettingBinding?.commit()
            if (it.closed) closeTargetPicker()
            return true
        }
        activePicker?.let {
            it.mouseClicked(event, button)
            activeSettingBinding?.commit()
            if (it.closed) closePicker()
            return true
        }

        val picker = activeColorPicker
        if (picker != null) {
            if (!picker.mouseClicked(mouseX, mouseY, button)) {
                activeColorPicker = null
            }
            activeSettingBinding?.commit()
            return true
        }
        activeSettingBinding?.let {
            it.mouseClicked(event, button)
            if (it.closed) closeSettingBinding()
            return true
        }
        if (rawButton >= 4) {
            for (panel in panels) {
                for (modComp in panel.moduleComponents) {
                    if (modComp.isBinding) {
                        modComp.module.key = KeybindSetting.fromMouseButton(rawButton)
                        modComp.isBinding = false
                        ConfigManager.saveConfig("default")
                        return true
                    }
                    for (settingComp in modComp.settingComponents) {
                        if (settingComp is KeybindComponent && settingComp.isListening) {
                            settingComp.keySetting.set(KeybindSetting.fromMouseButton(rawButton))
                            settingComp.stopListening()
                            ConfigManager.saveConfig("default")
                            return true
                        }
                    }
                }
            }
            if (ClickGuiModule.key != InputConstants.UNKNOWN.value && KeybindSetting.isMouseButton(ClickGuiModule.key)) {
                if (rawButton == KeybindSetting.toMouseButton(ClickGuiModule.key)) {
                    onClose()
                    return true
                }
            }
        }
        if (themePicker.mouseClicked(mouseX, mouseY, button)) {
            return true
        }

        if (searchBar.mouseClicked(mouseX, mouseY, button)) {
            return true
        }

        CustomTextField.clearFocus()
        for (i in panels.indices.reversed()) {
            val panel = panels[i]
            if (panel.mouseClicked(mouseX, mouseY, button, event.modifiers())) {
                panels.removeAt(i)
                panels.add(panel)
                return true
            }
        }

        clearAllListening()
        return super.mouseClicked(event, bl)
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        val mouseX = event.x()
        val mouseY = event.y()
        val button = normalizeButton(event.button())
        activeTargetPicker?.let {
            it.mouseReleased(event)
            activeSettingBinding?.commit()
            return true
        }
        activePicker?.let {
            it.mouseReleased(event)
            activeSettingBinding?.commit()
            return true
        }
        activeColorPicker?.let {
            it.mouseReleased(mouseX, mouseY, button)
            activeSettingBinding?.commit()
            return true
        }
        activeSettingBinding?.let {
            it.mouseReleased(event, button)
            return true
        }
        for (panel in panels) {
            panel.mouseReleased(mouseX, mouseY, button)
        }
        return super.mouseReleased(event)
    }

    override fun mouseDragged(event: MouseButtonEvent, deltaX: Double, deltaY: Double): Boolean {
        val mouseX = event.x()
        val mouseY = event.y()
        val button = normalizeButton(event.button())
        activeTargetPicker?.let {
            it.mouseDragged(event, button, deltaX, deltaY)
            return true
        }
        activePicker?.let {
            it.mouseDragged(event, button, deltaX, deltaY)
            return true
        }
        activeColorPicker?.let {
            it.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)
            return true
        }
        activeSettingBinding?.let {
            it.mouseDragged(event, button, deltaX, deltaY)
            return true
        }
        for (panel in panels) {
            panel.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)
        }
        return super.mouseDragged(event, deltaX, deltaY)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        activeTargetPicker?.let {
            it.mouseScrolled(mouseX, mouseY, scrollY)
            return true
        }
        activePicker?.let {
            it.mouseScrolled(mouseX, mouseY, scrollY)
            return true
        }
        if (activeColorPicker != null) return true
        activeSettingBinding?.let {
            it.mouseScrolled(mouseX, mouseY, scrollY)
            return true
        }
        if (searchBar.mouseScrolled(mouseX, mouseY, scrollY)) return true
        if (panels.isEmpty()) return true
        val horizontal = scrollX + if (mc.hasShiftDown()) scrollY else 0.0
        val vertical = if (mc.hasShiftDown()) 0.0 else scrollY
        movePanelGroup(horizontal.toFloat() * 32f, vertical.toFloat() * 32f)
        return true
    }

    private fun movePanelGroup(deltaX: Float, deltaY: Float) {
        val left = panels.minOf { it.x }
        val right = panels.maxOf { it.x + it.width }
        val top = panels.minOf { it.y }
        val bottom = panels.maxOf { it.y + it.height }
        val screenW = mc.window.guiScaledWidth.toFloat()
        val screenH = mc.window.guiScaledHeight.toFloat()
        val padY = (screenH * 0.65f).coerceAtLeast(240f)
        val padX = 120f
        val minX = padX - right
        val maxX = screenW - padX - left
        val minY = padY - bottom
        val maxY = screenH - padY - top
        val allowedMinX = minOf(minX, maxX)
        val allowedMaxX = maxOf(minX, maxX)
        val allowedMinY = minOf(minY, maxY)
        val allowedMaxY = maxOf(minY, maxY)
        val dx = deltaX.coerceIn(allowedMinX, allowedMaxX)
        val dy = deltaY.coerceIn(allowedMinY, allowedMaxY)
        for (panel in panels) {
            panel.x += dx
            panel.y += dy
        }
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        val keyCode = event.key()
        val scanCode = event.keycode()
        val modifiers = event.modifiers()
        activeTargetPicker?.let {
            it.keyPressed(event)
            activeSettingBinding?.commit()
            if (it.closed) closeTargetPicker()
            return true
        }
        activePicker?.let {
            it.keyPressed(event)
            activeSettingBinding?.commit()
            if (it.closed) closePicker()
            return true
        }

        val picker = activeColorPicker
        if (picker != null) {
            if (keyCode == InputConstants.KEY_ESCAPE) {
                activeColorPicker = null
                return true
            }
            picker.keyPressed(keyCode, scanCode, modifiers)
            return true
        }
        activeSettingBinding?.let {
            it.keyPressed(event)
            if (it.closed) closeSettingBinding()
            return true
        }
        val isCtrl = (modifiers and InputQuirks.EDIT_SHORTCUT_KEY_MODIFIER != 0) || (modifiers and InputConstants.MOD_CONTROL != 0)
        if (keyCode == InputConstants.KEY_F && isCtrl) {
            searchBar.openAndFocus()
            return true
        }

        if (keyCode == InputConstants.KEY_ESCAPE && (searchBar.isFocused || searchBar.query.isNotEmpty())) {
            searchBar.clearAndBlur()
            return true
        }

        if (CustomTextField.keyPressedFocused(keyCode, scanCode, modifiers)) return true

        for (panel in panels) {
            if (panel.keyPressed(keyCode, scanCode, modifiers)) {
                return true
            }
        }

        if (keyCode == InputConstants.KEY_RSHIFT || (ClickGuiModule.key != InputConstants.UNKNOWN.value && keyCode == ClickGuiModule.key)) {
            onClose()
            return true
        }

        return super.keyPressed(event)
    }

    override fun charTyped(event: CharacterEvent): Boolean {
        activeTargetPicker?.let {
            it.charTyped(event)
            activeSettingBinding?.commit()
            return true
        }
        activePicker?.let {
            it.charTyped(event)
            activeSettingBinding?.commit()
            return true
        }
        if (activeColorPicker != null) return true
        activeSettingBinding?.let {
            it.charTyped(event)
            return true
        }
        if (CustomTextField.charTypedFocused(event)) return true
        for (panel in panels) {
            if (panel.charTyped(event)) {
                return true
            }
        }
        return super.charTyped(event)
    }

    override fun isPauseScreen(): Boolean = parentScreen?.isPauseScreen ?: false

    override fun tick() {
        super.tick()
        parentScreen?.tick()
    }

    fun isBindingKey(): Boolean {
        if (activeSettingBinding?.isListening == true) return true
        for (panel in panels) {
            for (modComp in panel.moduleComponents) {
                if (modComp.isBinding) return true
                for (settingComp in modComp.settingComponents) {
                    if (settingComp is KeybindComponent && settingComp.isListening) return true
                }
            }
        }
        return false
    }

    override fun isInputCaptured(): Boolean = activeSettingBinding != null || activeTargetPicker != null || activePicker != null || activeColorPicker != null || CustomTextField.hasFocus || super.isInputCaptured()

    override fun removed() {
        closeSettingBinding()
        closePicker()
        closeTargetPicker()
        activeColorPicker = null
        clearAllListening()
        releasePanelInput()
        searchBar.clearAndBlur()
    }

    override fun onClose() {
        closeSettingBinding()
        clearAllListening()
        activeColorPicker = null
        closePicker()
        closeTargetPicker()
        releasePanelInput()
        searchBar.clearAndBlur()
        val parent = parentScreen
        parentScreen = null
        ClickGuiModule.setEnabled(false)
        if (mc.gui.screen() === this) {
            mc.gui.setScreen(parent)
        }
    }

}
