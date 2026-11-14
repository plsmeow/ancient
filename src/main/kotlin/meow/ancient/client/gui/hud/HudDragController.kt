package meow.ancient.client.gui.hud

import com.mojang.blaze3d.platform.InputConstants
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

internal class HudDockSettings(module: Module, element: HudElement, axis: String) {
    val target = module.number("HUD ${element.name} $axis Dock", HudLayout.NONE.toDouble(),
        HudLayout.SCREEN.toDouble(), HudElement.entries.lastIndex.toDouble(), 1.0, 0, isVisible = { false })
    val ownAnchor = module.enum("HUD ${element.name} $axis Dock Anchor", HudAnchor.START, isVisible = { false })
    val targetAnchor = module.enum("HUD ${element.name} $axis Dock Target Anchor", HudAnchor.START, isVisible = { false })
    val gap = module.number("HUD ${element.name} $axis Dock Gap", 0.0, -100.0, 100.0, 0.0, 6, isVisible = { false })

    val offset: Float
        get() = when {
            target.getInt() < 0 -> gap.getFloat()
            ownAnchor.value == HudAnchor.START && targetAnchor.value == HudAnchor.END -> HudLayout.GAP
            ownAnchor.value == HudAnchor.END && targetAnchor.value == HudAnchor.START -> -HudLayout.GAP
            else -> gap.getFloat()
        }

    fun store(snap: HudSnap?) {
        target.set((snap?.target ?: HudLayout.NONE).toDouble())
        ownAnchor.set(snap?.ownAnchor ?: HudAnchor.START)
        targetAnchor.set(snap?.targetAnchor ?: HudAnchor.START)
        gap.set((snap?.gap ?: 0f).toDouble())
    }

    fun reset() = store(null)
}

class HudDrag internal constructor(
    val element: HudElement,
    internal val positionX: NumberSetting,
    internal val positionY: NumberSetting,
    internal val alignmentSetting: EnumSetting<HudAlignment>,
    internal val anchorSide: EnumSetting<HudAlignment>,
    internal val dockX: HudDockSettings,
    internal val dockY: HudDockSettings
) {
    internal val animatedX = AnimFloat(speed = 26f)
    internal val animatedY = AnimFloat(speed = 26f)
    internal var desiredX = 0f
    internal var desiredY = 0f
    internal var initialized = false
    internal var visible = false
    internal var placedFrame = -1L
    internal var order = -1
    internal var previousWidth = 0f

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
            module.enum("HUD ${element.name} Anchor Side", HudAlignment.AUTO, isVisible = { false }),
            HudDockSettings(module, element, "X"),
            HudDockSettings(module, element, "Y")
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
    private var snapX: HudSnap? = null
    private var snapY: HudSnap? = null

    val snapGuideX: Float? get() = if (moved) snapX?.guide else null
    val snapGuideY: Float? get() = if (moved) snapY?.guide else null

    operator fun get(element: HudElement): HudDrag = drags[element.ordinal]

    init {
        for (drag in drags) {
            drag.alignmentSetting.addListener { choice ->
                if (drag.initialized && screenWidth > 0f) {
                    drag.dockX.reset()
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
            drag.dockX.reset()
            drag.dockY.reset()
        }
    }

    fun hitElement(x: Double, y: Double): HudElement? {
        if (!canEdit()) return null
        pointerX = x.toFloat()
        pointerY = y.toFloat()
        return findHit(frame)?.element
    }

    fun beginFrame(screenWidth: Float, screenHeight: Float) {
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
            if (!leftButtonDown()) {
                moveActive()
                finishDrag()
            }
        }
        updateHover(frame - 1)
    }

    fun place(
        element: HudElement,
        width: Float,
        height: Float,
        defaultX: Float,
        defaultY: Float,
        visible: Boolean
    ): HudDrag {
        val drag = drags[element.ordinal]
        drag.previousWidth = drag.width
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
        if (drag !== active) {
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
        return drag
    }

    /** All elements must be measured before layout so rendering order cannot affect snapping. */
    fun layoutFrame(deltaSeconds: Float) {
        val ordered = layoutOrder()
        for (drag in ordered) resolveDocks(drag, animated = false)
        if (active != null) moveActive()
        val obstacles = ArrayList<HudBounds>(ordered.size)
        for (drag in ordered) {
            resolveDocks(drag, animated = false)
            val bounds = bounds(drag)
            if (drag !== active) {
                HudLayout.avoidOverlap(bounds, obstacles, screenWidth, screenHeight)
                obstacles.add(bounds)
            }
            drag.desiredX = bounds.x
            drag.desiredY = bounds.y
        }
        obstacles.clear()
        for (drag in ordered) {
            if (!drag.initialized || drag === active) {
                drag.animatedX.setImmediate(drag.desiredX)
                drag.animatedY.setImmediate(drag.desiredY)
                drag.initialized = true
            } else {
                drag.animatedX.value -= (drag.width - drag.previousWidth) * anchorFraction(drag.alignment)
                drag.animatedX.update(drag.desiredX, deltaSeconds)
                drag.animatedY.update(drag.desiredY, deltaSeconds)
            }
            resolveDocks(drag, animated = true)
            val bounds = bounds(drag, animated = true)
            if (drag !== active) {
                HudLayout.avoidOverlap(bounds, obstacles, screenWidth, screenHeight)
                obstacles.add(bounds)
            }
            drag.animatedX.value = bounds.x
            drag.animatedY.value = bounds.y
        }
        updateHover(frame)
    }

    private fun bounds(drag: HudDrag, animated: Boolean = false): HudBounds = HudBounds(drag.element,
        if (animated) drag.x else drag.desiredX, if (animated) drag.y else drag.desiredY, drag.width, drag.height)

    private fun dockTarget(dock: HudDockSettings): HudDrag? = drags.getOrNull(dock.target.getInt())
        ?.takeIf { it.visible && it.placedFrame == frame }

    private fun resolveDocks(drag: HudDrag, animated: Boolean) {
        if (drag === active) return
        fun coordinate(dock: HudDockSettings, horizontal: Boolean): Float? {
            val size = if (horizontal) drag.width else drag.height
            val screenSize = if (horizontal) screenWidth else screenHeight
            val target = dockTarget(dock)
            val position: Float
            val targetSize: Float
            if (dock.target.getInt() == HudLayout.SCREEN) {
                position = 0f
                targetSize = screenSize
            } else if (target != null && target !== drag) {
                position = if (horizontal) {
                    if (animated) target.x else target.desiredX
                } else {
                    if (animated) target.y else target.desiredY
                }
                targetSize = if (horizontal) target.width else target.height
            } else return null
            return (position + targetSize * dock.targetAnchor.value.fraction + dock.offset -
                size * dock.ownAnchor.value.fraction).coerceIn(0f, (screenSize - size).coerceAtLeast(0f))
        }
        coordinate(drag.dockX, true)?.let { if (animated) drag.animatedX.value = it else drag.desiredX = it }
        coordinate(drag.dockY, false)?.let { if (animated) drag.animatedY.value = it else drag.desiredY = it }
    }

    private fun layoutOrder(): List<HudDrag> {
        val ordered = ArrayList<HudDrag>(drags.size)
        val visiting = BooleanArray(drags.size)
        val visited = BooleanArray(drags.size)
        fun visit(drag: HudDrag) {
            val index = drag.element.ordinal
            if (visited[index] || visiting[index] || !drag.visible || drag.placedFrame != frame) return
            visiting[index] = true
            if (drag !== active) {
                for (dock in arrayOf(drag.dockX, drag.dockY)) {
                    val target = dockTarget(dock) ?: continue
                    if (visiting[target.element.ordinal]) dock.reset() else visit(target)
                }
            }
            visiting[index] = false
            visited[index] = true
            ordered.add(drag)
        }
        active?.let(::visit)
        for (drag in drags) visit(drag)
        return ordered
    }

    private fun dependsOn(candidate: HudDrag, element: HudElement): Boolean {
        val visited = BooleanArray(drags.size)
        fun visit(drag: HudDrag): Boolean {
            if (drag.element == element) return true
            if (visited[drag.element.ordinal]) return false
            visited[drag.element.ordinal] = true
            return listOf(drag.dockX, drag.dockY).any { dock ->
                drags.getOrNull(dock.target.getInt())?.let(::visit) == true
            }
        }
        return visit(candidate)
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
        snapX = null
        snapY = null
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
        if (!moved) return
        if (controlDown()) {
            snapX = null
            snapY = null
        } else {
            val targets = drags.filter { it.visible && it.placedFrame >= frame - 1 && !dependsOn(it, drag.element) }
                .map { bounds(it, animated = true) }
            val bounds = bounds(drag)
            snapX = HudLayout.snap(bounds, true, screenWidth, targets)
            snapY = HudLayout.snap(bounds, false, screenHeight, targets)
            snapX?.let { drag.desiredX = it.position }
            snapY?.let { drag.desiredY = it.position }
        }
        if (moved && drag.alignmentSetting.value == HudAlignment.AUTO) {
            drag.anchorSide.set(automaticAlignment(drag.desiredX, drag.width))
        }
    }

    private fun finishDrag() {
        val drag = active ?: return
        active = null
        drag.dragging = false
        val bypassSnapping = controlDown()
        val finalSnapX = if (bypassSnapping) null else snapX
        val finalSnapY = if (bypassSnapping) null else snapY
        snapX = null
        snapY = null
        if (!moved) return
        moved = false
        drag.dockX.store(finalSnapX)
        drag.dockY.store(finalSnapY)
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

    private fun controlDown(): Boolean =
        InputConstants.isKeyDown(InputConstants.KEY_LCONTROL) || InputConstants.isKeyDown(InputConstants.KEY_RCONTROL)

    private fun maxX(drag: HudDrag): Float = (screenWidth - drag.width).coerceAtLeast(0f)
    private fun maxY(drag: HudDrag): Float = (screenHeight - drag.height).coerceAtLeast(0f)
}
