package meow.ancient.client.gui.hud

import meow.ancient.client.config.ConfigManager
import meow.ancient.client.extensions.currentScreen
import meow.ancient.client.extensions.mc
import meow.ancient.client.gui.material.AnimFloat
import meow.ancient.module.Module
import meow.ancient.module.setting.EnumSetting
import meow.ancient.module.setting.NumberSetting
import meow.ancient.module.setting.Setting
import net.minecraft.client.gui.screens.ChatScreen
import org.lwjgl.sdl.SDLMouse
import kotlin.math.abs

enum class HudAlignment {
    AUTO, LEFT, CENTER, RIGHT
}

class HudDrag internal constructor(
    val element: HudElement,
    internal val positionX: NumberSetting,
    internal val positionY: NumberSetting,
    internal val alignmentSetting: EnumSetting<HudAlignment>,
    internal val anchorSide: EnumSetting<HudAlignment>
) {
    internal val animatedX = AnimFloat(speed = 26f)
    internal val animatedY = AnimFloat(speed = 26f)
    internal var desiredX = 0f
    internal var desiredY = 0f
    internal var initialized = false
    internal var visible = false
    internal var placedFrame = -1L
    internal var order = -1

    val alignment: HudAlignment
        get() = if (alignmentSetting.value != HudAlignment.AUTO) alignmentSetting.value
            else anchorSide.value.takeUnless { it == HudAlignment.AUTO } ?: HudAlignment.LEFT
    val x: Float get() = animatedX.value
    val y: Float get() = animatedY.value
    var hitTest: ((Float, Float) -> Boolean)? = null
    var width = 0f
        internal set
    var height = 0f
        internal set
    var hovered = false
        internal set
    var dragging = false
        internal set
}

class HudDragController(private val module: Module) {
    private val drags = Array(HudElement.entries.size) { index ->
        val element = HudElement.entries[index]
        HudDrag(
            element,
            module.number("HUD ${element.name} X", -1.0, -1.0, 1.0, 0.0, 6, isVisible = { false }),
            module.number("HUD ${element.name} Y", -1.0, -1.0, 1.0, 0.0, 6, isVisible = { false }),
            module.enum("${element.name.replace('_', ' ')} Alignment", HudAlignment.AUTO,
                nameMapper = { it.name.lowercase().replaceFirstChar(Char::titlecase) }, isVisible = { false }),
            module.enum("HUD ${element.name} Anchor Side", HudAlignment.AUTO, isVisible = { false })
        )
    }
    private var screenWidth = 0f
    private var screenHeight = 0f
    private var frame = 0L
    private var nextOrder = 0
    private var active: HudDrag? = null
    private var hovered: HudDrag? = null
    private var pointerX = 0f
    private var pointerY = 0f
    private var offsetX = 0f
    private var offsetY = 0f
    private var startX = 0f
    private var startY = 0f
    private var moved = false

    init {
        for (drag in drags) {
            drag.alignmentSetting.addListener { choice ->
                if (drag.initialized && screenWidth > 0f) {
                    drag.anchorSide.set(if (choice == HudAlignment.AUTO) automaticAlignment(drag.desiredX, drag.width) else choice)
                    storeAnchor(drag)
                }
            }
        }
    }

    val isDragging: Boolean get() = active != null

    fun alignmentSetting(element: HudElement): Setting<*> = drags[element.ordinal].alignmentSetting

    fun prepareConfigLoad() {
        HudSettingsPopup.close()
        clearInteraction()
        for (drag in drags) {
            drag.initialized = false
            drag.anchorSide.reset()
        }
    }

    fun hitElement(x: Double, y: Double): HudElement? {
        if (!canEdit()) return null
        pointerX = x.toFloat()
        pointerY = y.toFloat()
        return findHit(frame)?.element
    }

    fun beginFrame(screenWidth: Float, screenHeight: Float, deltaSeconds: Float) {
        if (this.screenWidth != screenWidth || this.screenHeight != screenHeight) {
            finishDrag()
            HudSettingsPopup.close()
        }
        this.screenWidth = screenWidth.coerceAtLeast(0f)
        this.screenHeight = screenHeight.coerceAtLeast(0f)
        frame++
        nextOrder = 0
        pointerX = mc.mouseHandler.getScaledXPos(mc.window).toFloat()
        pointerY = mc.mouseHandler.getScaledYPos(mc.window).toFloat()
        if (!canEdit()) {
            clearInteraction()
            HudSettingsPopup.close()
        } else if (active != null) {
            if (!leftButtonDown()) finishDrag() else moveActive()
        }
        updateHover(frame - 1)
    }

    fun place(
        element: HudElement,
        width: Float,
        height: Float,
        defaultX: Float,
        defaultY: Float,
        visible: Boolean,
        deltaSeconds: Float
    ): HudDrag {
        val drag = drags[element.ordinal]
        val oldWidth = drag.width
        drag.width = width.coerceAtLeast(0f)
        drag.height = height.coerceAtLeast(0f)
        drag.visible = visible && drag.width > 0f && drag.height > 0f
        drag.placedFrame = frame
        drag.order = nextOrder++
        if (!drag.visible && drag === active) finishDrag()
        val maxX = maxX(drag)
        val maxY = maxY(drag)
        if (drag.visible && drag.anchorSide.value == HudAlignment.AUTO && screenWidth > 0f) {
            val legacyX = if (drag.positionX.value >= 0.0) (drag.positionX.value * maxX).toFloat()
                else defaultX.coerceIn(0f, maxX)
            val choice = drag.alignmentSetting.value
            drag.anchorSide.set(if (choice == HudAlignment.AUTO) automaticAlignment(legacyX, drag.width) else choice)
            drag.desiredX = legacyX
            storeAnchor(drag)
        }
        if (drag === active) {
            moveActive()
        } else {
            drag.desiredX = if (drag.positionX.value >= 0.0 && drag.anchorSide.value != HudAlignment.AUTO) {
                (drag.positionX.value * screenWidth - drag.width * anchorFraction(drag.alignment)).toFloat().coerceIn(0f, maxX)
            } else {
                defaultX.coerceIn(0f, maxX)
            }
            drag.desiredY = if (drag.positionY.value >= 0.0) {
                (drag.positionY.value * maxY).toFloat()
            } else {
                defaultY.coerceIn(0f, maxY)
            }
        }
        if (!drag.initialized) {
            drag.animatedX.setImmediate(drag.desiredX)
            drag.animatedY.setImmediate(drag.desiredY)
            drag.initialized = drag.visible
        } else {
            if (drag !== active) drag.animatedX.value -= (drag.width - oldWidth) * anchorFraction(drag.alignment)
            drag.animatedX.value = drag.animatedX.value.coerceIn(0f, maxX)
            drag.animatedY.value = drag.animatedY.value.coerceIn(0f, maxY)
            drag.animatedX.update(drag.desiredX, deltaSeconds)
            drag.animatedY.update(drag.desiredY, deltaSeconds)
        }
        updateHover(frame - 1)
        return drag
    }

    fun clearInteraction() {
        finishDrag()
        hovered = null
        for (drag in drags) {
            drag.hovered = false
            drag.dragging = false
            drag.visible = false
            drag.placedFrame = -1L
        }
    }

    fun mouseClicked(x: Double, y: Double, button: Int): Boolean {
        if (button != 0 || !canEdit() || HudSettingsPopup.isOpen) return false
        if (active != null) return true
        pointerX = x.toFloat()
        pointerY = y.toFloat()
        val drag = findHit(frame) ?: return false
        active = drag
        drag.dragging = true
        offsetX = pointerX - drag.x
        offsetY = pointerY - drag.y
        startX = drag.x
        startY = drag.y
        drag.desiredX = startX
        drag.desiredY = startY
        moved = false
        updateHover(frame)
        return true
    }

    fun mouseReleased(x: Double, y: Double, button: Int): Boolean {
        if (button != 0 || active == null) return false
        pointerX = x.toFloat()
        pointerY = y.toFloat()
        if (canEdit()) moveActive()
        finishDrag()
        updateHover(frame)
        return true
    }

    fun mouseDragged(x: Double, y: Double, button: Int): Boolean {
        if (button != 0 || active == null) return false
        if (!canEdit() || !leftButtonDown()) {
            clearInteraction()
            return true
        }
        pointerX = x.toFloat()
        pointerY = y.toFloat()
        moveActive()
        updateHover(frame)
        return true
    }

    private fun moveActive() {
        val drag = active ?: return
        drag.desiredX = (pointerX - offsetX).coerceIn(0f, maxX(drag))
        drag.desiredY = (pointerY - offsetY).coerceIn(0f, maxY(drag))
        if (abs(drag.desiredX - startX) > 0.01f || abs(drag.desiredY - startY) > 0.01f) moved = true
        if (moved && drag.alignmentSetting.value == HudAlignment.AUTO) {
            drag.anchorSide.set(automaticAlignment(drag.desiredX, drag.width))
        }
    }

    private fun finishDrag() {
        val drag = active ?: return
        active = null
        drag.dragging = false
        if (!moved) return
        moved = false
        if (drag.alignmentSetting.value == HudAlignment.AUTO) drag.anchorSide.set(automaticAlignment(drag.desiredX, drag.width))
        storeAnchor(drag)
        val maxY = maxY(drag)
        if (maxY > 0f) drag.positionY.set((drag.desiredY / maxY).toDouble().coerceIn(0.0, 1.0))
        ConfigManager.saveCurrentConfig()
    }

    private fun storeAnchor(drag: HudDrag) {
        if (screenWidth > 0f) {
            drag.positionX.set(((drag.desiredX + drag.width * anchorFraction(drag.alignment)) / screenWidth).toDouble().coerceIn(0.0, 1.0))
        }
    }

    private fun automaticAlignment(x: Float, width: Float): HudAlignment {
        val center = x + width * 0.5f
        return when {
            center < screenWidth / 3f -> HudAlignment.LEFT
            center > screenWidth * 2f / 3f -> HudAlignment.RIGHT
            else -> HudAlignment.CENTER
        }
    }

    private fun anchorFraction(alignment: HudAlignment): Float = when (alignment) {
        HudAlignment.CENTER -> 0.5f
        HudAlignment.RIGHT -> 1f
        else -> 0f
    }

    private fun updateHover(minFrame: Long) {
        hovered?.hovered = false
        hovered = if (canEdit() && !HudSettingsPopup.isOpen) active ?: findHit(minFrame) else null
        hovered?.hovered = true
    }

    private fun findHit(minFrame: Long): HudDrag? {
        var hit: HudDrag? = null
        for (drag in drags) {
            if (!drag.visible || drag.placedFrame < minFrame) continue
            if (pointerX < drag.x || pointerY < drag.y || pointerX >= drag.x + drag.width || pointerY >= drag.y + drag.height) continue
            if (drag.hitTest?.invoke(pointerX - drag.x, pointerY - drag.y) == false) continue
            if (hit == null || drag.order > hit.order) hit = drag
        }
        return hit
    }

    private fun canEdit(): Boolean = module.isEnabled() && currentScreen is ChatScreen &&
        mc.gui.overlay() == null && mc.player != null && mc.level != null && mc.isWindowActive

    private fun leftButtonDown(): Boolean =
        (SDLMouse.SDL_GetMouseState(null, null) and SDLMouse.SDL_BUTTON_LMASK) != 0

    private fun maxX(drag: HudDrag): Float = (screenWidth - drag.width).coerceAtLeast(0f)
    private fun maxY(drag: HudDrag): Float = (screenHeight - drag.height).coerceAtLeast(0f)
}
