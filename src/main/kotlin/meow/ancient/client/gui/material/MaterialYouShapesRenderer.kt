package meow.ancient.client.gui.material

import meow.ancient.client.extensions.currentScreen
import meow.ancient.client.extensions.mc
import meow.ancient.client.util.other.color.Color4b
import meow.ancient.client.util.render.RenderUtils
import meow.ancient.client.util.render.VulkanShapes
import meow.ancient.client.util.render.engine.controls.ClientRenderPipeline
import meow.ancient.client.util.render.engine.controls.MatrixControl
import meow.ancient.client.util.render.font.Fonts
import meow.ancient.client.util.render.rect.RectStyle
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.ChatScreen
import org.lwjgl.sdl.SDLMouse
import kotlin.math.max

object MaterialYouShapesRenderer {
    var posX = -1f
    var posY = -1f
    private var isDragging = false
    private var dragOffsetX = 0f
    private var dragOffsetY = 0f

    val fade = AnimFloat(0f, speed = 14f)
    private var lastTimeNanos = 0L

    private const val COLS = 7
    private const val ROWS = 5
    private const val CELL_WIDTH = 60f
    private const val CELL_HEIGHT = 48f
    private const val SHAPE_SIZE = 28f
    private const val CARD_PADDING_X = 14f
    private const val CARD_PADDING_Y = 14f

    private const val CARD_WIDTH = CARD_PADDING_X * 2f + COLS * CELL_WIDTH
    private const val CARD_HEIGHT = CARD_PADDING_Y * 2f + ROWS * CELL_HEIGHT

    // Официальный акцентный цвет Material 3 Expressive
    private val SHAPE_COLOR = Color4b(138, 106, 196, 255)
    // Цвет подписей (мягкий оттенок M3 onSurface)
    private val TEXT_COLOR = Color4b(222, 218, 226, 255)

    private fun leftButtonDown(): Boolean =
        (SDLMouse.SDL_GetMouseState(null, null) and SDLMouse.SDL_BUTTON_LMASK) != 0

    fun render(graphics: GuiGraphicsExtractor, deltaTracker: Float, visible: Boolean) {
        val now = System.nanoTime()
        val dt = if (lastTimeNanos == 0L) 1f / 60f else ((now - lastTimeNanos) * 1e-9).toFloat().coerceIn(0.001f, 0.1f)
        lastTimeNanos = now

        fade.update(if (visible) 1f else 0f, dt)
        val alpha = fade.value
        if (alpha <= 0.001f) return

        val screenW = graphics.guiWidth().toFloat()
        val screenH = graphics.guiHeight().toFloat()

        if (posX < 0f || posY < 0f) {
            posX = max(10f, (screenW - CARD_WIDTH) * 0.5f)
            posY = max(10f, (screenH - CARD_HEIGHT) * 0.5f)
        }

        val mouseX = mc.mouseHandler.getScaledXPos(mc.window).toFloat()
        val mouseY = mc.mouseHandler.getScaledYPos(mc.window).toFloat()
        val isChat = currentScreen is ChatScreen
        val isLeftDown = leftButtonDown()

        // Возможность перемещения карточки при открытом чате
        if (isChat) {
            if (isLeftDown) {
                if (!isDragging && mouseX in posX..(posX + CARD_WIDTH) && mouseY in posY..(posY + CARD_HEIGHT)) {
                    isDragging = true
                    dragOffsetX = mouseX - posX
                    dragOffsetY = mouseY - posY
                }
                if (isDragging) {
                    posX = (mouseX - dragOffsetX).coerceIn(0f, max(0f, screenW - CARD_WIDTH))
                    posY = (mouseY - dragOffsetY).coerceIn(0f, max(0f, screenH - CARD_HEIGHT))
                }
            } else {
                isDragging = false
            }
        } else {
            isDragging = false
        }

        val matrix = MatrixControl.matrix4fStack

        // Сплошной черный скругленный фон без рамок, заголовка и ячеек
        val blackBg = Color4b(0, 0, 0, 255).fade(alpha)
        RenderUtils.rect(
            matrix = matrix,
            x = posX,
            y = posY,
            width = CARD_WIDTH,
            height = CARD_HEIGHT,
            style = RectStyle.rounded(blackBg, 16f)
        )

        // Отрисовка всех 35 форм Material You с названиями в сетке 7x5
        val shapes = M3Shape.entries
        val startX = posX + CARD_PADDING_X
        val startY = posY + CARD_PADDING_Y
        val shapeColor = SHAPE_COLOR.fade(alpha)
        val labelColor = TEXT_COLOR.fade(alpha)

        for (i in shapes.indices) {
            val shape = shapes[i]
            val col = i % COLS
            val row = i / COLS

            val cellX = startX + col * CELL_WIDTH
            val cellY = startY + row * CELL_HEIGHT

            val sx = cellX + (CELL_WIDTH - SHAPE_SIZE) * 0.5f
            val sy = cellY + 2f

            VulkanShapes.drawM3Shape(
                gfx = graphics,
                x = sx,
                y = sy,
                width = SHAPE_SIZE,
                height = SHAPE_SIZE,
                shape = shape,
                color = shapeColor.argb,
                borderColor = 0,
                borderWidth = 0f,
                opacity = alpha
            )

            val textCenterX = cellX + CELL_WIDTH * 0.5f
            val textY = sy + SHAPE_SIZE + 3f

            Fonts.google_sans_medium.drawCenteredString(
                matrix = matrix,
                text = shape.shapeName,
                centerX = textCenterX,
                y = textY,
                size = 6.2f,
                color = labelColor,
                shadow = false,
                pipeline = ClientRenderPipeline.HUD_TEXT
            )
        }
    }
}
