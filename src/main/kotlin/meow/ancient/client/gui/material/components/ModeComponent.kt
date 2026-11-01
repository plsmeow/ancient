package meow.ancient.client.gui.material.components

import meow.ancient.client.gui.material.AnimFloat
import meow.ancient.client.gui.material.M3Shape
import meow.ancient.client.gui.material.M3Theme
import meow.ancient.client.util.other.color.Color4b
import meow.ancient.client.util.render.RenderUtils
import meow.ancient.client.util.render.VulkanShapes
import meow.ancient.client.util.render.engine.controls.ClientRenderPipeline
import meow.ancient.client.util.render.font.Fonts
import meow.ancient.client.util.render.rect.RectBorder
import meow.ancient.client.util.render.rect.RectColors
import meow.ancient.client.util.render.rect.RectCorners
import meow.ancient.client.util.render.rect.RectStyle
import meow.ancient.module.setting.EnumSetting
import meow.ancient.module.setting.ModeSetting
import meow.ancient.module.setting.MultiModeSetting
import meow.ancient.module.setting.Setting
import org.joml.Matrix4f

class ModeComponent(
    setting: Setting<*>,
    private val modes: List<String>,
    private val isSelected: (String) -> Boolean,
    private val onSelect: (String) -> Unit
) : SettingComponent(setting) {

    constructor(modeSetting: ModeSetting) : this(
        setting = modeSetting,
        modes = modeSetting.modes,
        isSelected = modeSetting::isMode,
        onSelect = modeSetting::set
    )

    constructor(multiModeSetting: MultiModeSetting) : this(
        setting = multiModeSetting,
        modes = multiModeSetting.modes,
        isSelected = multiModeSetting::isSelected,
        onSelect = multiModeSetting::toggle
    )

    constructor(enumSetting: EnumSetting<*>) : this(
        setting = enumSetting,
        modes = enumSetting.modes,
        isSelected = { enumSetting.isMode(it) },
        onSelect = { enumSetting.selectByName(it) }
    )

    private data class ChipRect(
        val mode: String,
        val x: Float,
        val y: Float,
        val w: Float,
        val h: Float
    )

    private class ChipAnim(
        val selectAnim: AnimFloat = AnimFloat(0f, speed = 14f),
        val hoverAnim: AnimFloat = AnimFloat(0f, speed = 14f)
    )

    companion object {
        // Красивые, симметричные и скругленные фигуры Material You
        private val EXPRESSIVE_SHAPES = arrayOf(
            M3Shape.SUNNY,        // ☀️ 8-конечное солнце Material You
            M3Shape.COOKIE_4,     // 🍪 4-лопастная скругленная печенька
            M3Shape.CLOVER_4,     // 🍀 4-лепестковый скругленный клевер
            M3Shape.BURST,        // ✨ 12-лучевая геометрическая звезда
            M3Shape.COOKIE_9,     // 🍪 9-гранная фигурная печенька
            M3Shape.VERY_SUNNY,   // ☀️ Яркое солнце с лучами
            M3Shape.GEM,          // 💎 Ограненный кристалл
            M3Shape.DIAMOND,      // 🔷 Скругленный ромб
            M3Shape.COOKIE_6,     // 🍪 6-лепестковая печенька
            M3Shape.SOFT_BURST,   // ✨ Мягкая звезда со скругленными лучами
            M3Shape.COOKIE_12     // 🍪 12-лепестковое фигурное печенье
        )

        private fun shapeForMode(mode: String, index: Int): M3Shape {
            val lower = mode.lowercase()
            return when {
                "gui" in lower || "sun" in lower || "vanilla" in lower -> M3Shape.SUNNY
                "use" in lower || "cook" in lower || "sloth" in lower || "fov" in lower -> M3Shape.COOKIE_4
                "switch" in lower || "clover" in lower || "multi" in lower -> M3Shape.CLOVER_4
                "snap" in lower || "burst" in lower || "packet" in lower || "fast" in lower -> M3Shape.BURST
                "single" in lower || "target" in lower || "armor" in lower -> M3Shape.DIAMOND
                "gem" in lower || "dist" in lower -> M3Shape.GEM
                "hurt" in lower || "time" in lower -> M3Shape.VERY_SUNNY
                "health" in lower -> M3Shape.COOKIE_9
                else -> EXPRESSIVE_SHAPES[index % EXPRESSIVE_SHAPES.size]
            }
        }
    }

    private val chipRects = mutableListOf<ChipRect>()
    private val anims = mutableMapOf<String, ChipAnim>()

    override fun render(
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
        chipRects.clear()

        val palette = M3Theme.current

        // Заголовок настройки
        Fonts.google_sans_medium.drawString(
            matrix = matrix,
            text = setting.name,
            x = x + 3.5f,
            y = y + 2f,
            size = 9f,
            color = palette.onSurfaceVariant,
            shadow = false,
            pipeline = ClientRenderPipeline.WINDOW_TEXT
        )

        val chipH = 15.5f
        val chipRadius = 5f
        val gapX = 3f
        val gapY = 3.5f
        val startX = x + 3.5f
        val maxRight = x + width - 3.5f
        val sidePad = 5f
        val iconSize = 7.5f
        val iconGap = 3f
        val targetIconW = iconSize + iconGap

        var curX = startX
        var curY = y + 14f

        val gfx = RenderUtils.currentGraphics

        for ((idx, mode) in modes.withIndex()) {
            val selected = isSelected(mode)

            val anim = anims.getOrPut(mode) {
                ChipAnim(
                    selectAnim = AnimFloat(if (selected) 1f else 0f, speed = 14f),
                    hoverAnim = AnimFloat(0f, speed = 14f)
                )
            }

            val selectProgress = anim.selectAnim.update(if (selected) 1f else 0f, dt)
            val textW = Fonts.google_sans_medium.width(mode, 8f)

            // Фиксированная стабильная ширина: не зависит от состояния выбора,
            // чипы размещаются свободно по X по несколько штук в строке и не дергаются
            val chipW = (textW + sidePad * 2f + targetIconW).coerceAtLeast(24f)

            if (curX + chipW > maxRight && curX > startX) {
                curX = startX
                curY += chipH + gapY
            }

            chipRects.add(ChipRect(mode, curX, curY, chipW, chipH))

            val hovered = isHovered(mouseX.toDouble(), mouseY.toDouble(), curX, curY, chipW, chipH)
            val hoverProgress = anim.hoverAnim.update(if (hovered) 1f else 0f, dt)

            // Плавная цветовая интерполяция Material Design 3
            val baseBg = palette.surfaceContainerHigh.interpolateTo(palette.surfaceContainerHighest, hoverProgress.toDouble())
            val activeBg = palette.primary
            val chipBg = baseBg.interpolateTo(activeBg, selectProgress.toDouble())

            val baseTextCol = palette.onSurfaceVariant.interpolateTo(palette.onSurface, hoverProgress.toDouble())
            val activeTextCol = palette.onPrimary
            val textCol = baseTextCol.interpolateTo(activeTextCol, selectProgress.toDouble())

            val unselectedBorder = palette.outlineVariant.alpha(65).interpolateTo(palette.outline.alpha(125), hoverProgress.toDouble())
            val selectedBorder = palette.primary.alpha(255)
            val borderCol = unselectedBorder.interpolateTo(selectedBorder, selectProgress.toDouble())

            val hasBorder = selectProgress < 0.95f
            val border = if (hasBorder) {
                val bw = (1f - selectProgress * 0.4f).coerceIn(0.6f, 1f)
                RectBorder(bw, borderCol)
            } else RectBorder.NONE

            // 1. Фон чипа
            RenderUtils.rect(
                matrix = matrix,
                x = curX,
                y = curY,
                width = chipW,
                height = chipH,
                style = RectStyle(
                    colors = RectColors.solid(chipBg),
                    corners = RectCorners.all(chipRadius),
                    border = border
                ),
                pipeline = ClientRenderPipeline.WINDOW_RECT
            )

            // Центрирование контента по X:
            val curIconW = targetIconW * selectProgress
            val curContentW = textW + curIconW
            val contentStartX = curX + (chipW - curContentW) * 0.5f

            // 2. Всплывающая фигура Material You
            if (gfx != null && selectProgress > 0.02f) {
                val shape = shapeForMode(mode, idx)
                val curShapeSize = iconSize * selectProgress
                val shapeX = contentStartX + (targetIconW - iconGap - curShapeSize) * 0.5f
                val shapeY = curY + (chipH - curShapeSize) * 0.5f
                val shapeColor = textCol.fade(selectProgress)

                VulkanShapes.drawM3Shape(
                    gfx = gfx,
                    x = shapeX,
                    y = shapeY,
                    width = curShapeSize,
                    height = curShapeSize,
                    shape = shape,
                    color = shapeColor.argb,
                    borderColor = 0,
                    borderWidth = 0f,
                    opacity = selectProgress
                )
            }

            // 3. Текст: математически выверенное центрирование по Y (оптический центр по cap-height)
            val textX = contentStartX + curIconW
            val textH = Fonts.google_sans_medium.height(8f)
            val textY = curY + (chipH - textH) * 0.5f - 1.0f

            Fonts.google_sans_medium.drawString(
                matrix = matrix,
                text = mode,
                x = textX,
                y = textY,
                size = 8f,
                color = textCol,
                shadow = false,
                pipeline = ClientRenderPipeline.WINDOW_TEXT
            )

            curX += chipW + gapX
        }

        this.height = (curY + chipH + 3f) - y
        return height
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == 0) {
            for (rect in chipRects) {
                if (isHovered(mouseX, mouseY, rect.x, rect.y, rect.w, rect.h)) {
                    onSelect(rect.mode)
                    return true
                }
            }
        }
        return false
    }
}
