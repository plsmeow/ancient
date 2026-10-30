package meow.ancient.client.gui.material

import com.mojang.blaze3d.platform.InputConstants
import meow.ancient.client.extensions.categories
import meow.ancient.client.extensions.mc
import meow.ancient.client.gui.material.components.CategoryPanel
import meow.ancient.client.gui.material.components.ColorPickerWindow
import meow.ancient.client.gui.material.components.CustomTextField
import meow.ancient.client.gui.material.components.KeybindComponent
import meow.ancient.client.gui.material.components.ModuleComponent
import meow.ancient.client.gui.material.components.PickerWindow
import meow.ancient.client.interfaces.IRenderable
import meow.ancient.client.util.other.color.Color4b
import meow.ancient.client.util.render.RenderUtils
import meow.ancient.client.util.render.engine.controls.ClientRenderPipeline
import meow.ancient.client.util.render.engine.controls.MatrixControl
import meow.ancient.module.modules.render.ClickGuiModule
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component

object ClickGuiScreen : Screen(Component.literal("Ancient ClickGUI")), IRenderable {
    val panels: MutableList<CategoryPanel> = ArrayList()
    private var initialized = false

    var parentScreen: Screen? = null
    var activeColorPicker: ColorPickerWindow? = null
        set(value) {
            if (value != null) {
                closePicker()
                clearAllListening()
                releasePanelInput()
            }
            field = value
        }
    private var activePicker: PickerWindow? = null

    fun openPicker(picker: PickerWindow) {
        activeColorPicker = null
        closePicker()
        clearAllListening()
        releasePanelInput()
        activePicker = picker
        picker.focusSearch()
    }

    private fun closePicker() {
        activePicker?.close()
        activePicker = null
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
        activeColorPicker = null
        releasePanelInput()
        lastFrameTime = System.currentTimeMillis()
    }

    override fun render(mouseX: Int, mouseY: Int) {
        if (RenderUtils.currentGraphics == null) return
        val now = System.currentTimeMillis()
        val dt = ((now - lastFrameTime) / 1000f).coerceIn(0.0001f, 0.1f)
        lastFrameTime = now

        val matrix = MatrixControl.matrix4fStack
        val screenW = mc.window.guiScaledWidth.toFloat()
        val screenH = mc.window.guiScaledHeight.toFloat()

        if (ClickGuiModule.blur && mc.options.getMenuBackgroundBlurriness() == 0) {
            RenderUtils.rect(
                matrix = matrix,
                x = 0f,
                y = 0f,
                width = screenW,
                height = screenH,
                color = Color4b(0, 0, 0, 80),
                pipeline = ClientRenderPipeline.LOW
            )
        }

        for (panel in panels) {
            RenderUtils.currentGraphics?.nextStratum()
            panel.render(matrix, mouseX, mouseY, dt)
        }

        activeColorPicker?.let {
            RenderUtils.currentGraphics?.nextStratum()
            it.render(matrix, mouseX, mouseY, dt)
        }
        activePicker?.render(matrix, mouseX, mouseY, dt)
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

    fun clearAllListening(exceptModule: ModuleComponent? = null, exceptKeybind: KeybindComponent? = null) {
        CustomTextField.clearFocus()
        for (panel in panels) {
            for (modComp in panel.moduleComponents) {
                if (modComp !== exceptModule) {
                    modComp.isBinding = false
                }
                for (settingComp in modComp.settingComponents) {
                    if (settingComp is KeybindComponent && settingComp !== exceptKeybind) {
                        settingComp.stopListening()
                    }
                }
            }
        }
    }

    override fun mouseClicked(event: MouseButtonEvent, bl: Boolean): Boolean {
        val mouseX = event.x()
        val mouseY = event.y()
        val button = normalizeButton(event.button())
        activePicker?.let {
            it.mouseClicked(event, button)
            if (it.closed) closePicker()
            return true
        }

        val picker = activeColorPicker
        if (picker != null) {
            if (!picker.mouseClicked(mouseX, mouseY, button)) {
                activeColorPicker = null
            }
            return true
        }

        CustomTextField.clearFocus()
        for (i in panels.indices.reversed()) {
            val panel = panels[i]
            if (panel.mouseClicked(mouseX, mouseY, button)) {
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
        activePicker?.let {
            it.mouseReleased(event)
            return true
        }
        activeColorPicker?.let {
            it.mouseReleased(mouseX, mouseY, button)
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
        activePicker?.let {
            it.mouseDragged(event, button, deltaX, deltaY)
            return true
        }
        activeColorPicker?.let {
            it.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)
            return true
        }
        for (panel in panels) {
            panel.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)
        }
        return super.mouseDragged(event, deltaX, deltaY)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        activePicker?.let {
            it.mouseScrolled(mouseX, mouseY, scrollY)
            return true
        }
        if (activeColorPicker != null) return true
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
        val minX = minOf(4f - left, screenW - 4f - right)
        val maxX = maxOf(4f - left, screenW - 4f - right)
        val minY = minOf(4f - top, screenH - 4f - bottom)
        val maxY = maxOf(4f - top, screenH - 4f - bottom)
        val dx = deltaX.coerceIn(minOf(0f, minX), maxOf(0f, maxX))
        val dy = deltaY.coerceIn(minOf(0f, minY), maxOf(0f, maxY))
        for (panel in panels) {
            panel.x += dx
            panel.y += dy
        }
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        val keyCode = event.key()
        val scanCode = event.keycode()
        val modifiers = event.modifiers()
        activePicker?.let {
            it.keyPressed(event)
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
        activePicker?.let {
            it.charTyped(event)
            return true
        }
        if (activeColorPicker != null) return true
        if (CustomTextField.charTypedFocused(event)) return true
        return super.charTyped(event)
    }

    override fun isPauseScreen(): Boolean = false

    override fun isInputCaptured(): Boolean = activePicker != null || activeColorPicker != null || CustomTextField.hasFocus || super.isInputCaptured()

    override fun removed() {
        closePicker()
        activeColorPicker = null
        clearAllListening()
        releasePanelInput()
        super.removed()
    }

    override fun onClose() {
        clearAllListening()
        activeColorPicker = null
        closePicker()
        releasePanelInput()
        val parent = parentScreen
        parentScreen = null
        ClickGuiModule.setEnabled(false)
        if (mc.gui.screen() === this) {
            mc.gui.setScreen(parent)
        }
    }
}
