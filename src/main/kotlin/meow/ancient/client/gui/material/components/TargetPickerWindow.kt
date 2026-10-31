package meow.ancient.client.gui.material.components

import com.mojang.blaze3d.platform.InputConstants
import meow.ancient.client.config.ConfigManager
import meow.ancient.client.extensions.mc
import meow.ancient.client.gui.material.M3Theme
import meow.ancient.client.util.other.color.Color4b
import meow.ancient.client.util.render.RenderUtils
import meow.ancient.client.util.render.ScissorUtil
import meow.ancient.client.util.render.engine.controls.ClientRenderPipeline
import meow.ancient.client.util.render.font.Fonts
import meow.ancient.module.setting.MinecraftPickerCatalogs
import meow.ancient.module.setting.PickerEntry
import meow.ancient.module.setting.TargetSetting
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.MobCategory
import org.joml.Matrix4f
import java.util.Locale
import kotlin.math.floor

enum class TargetCategory(val title: String) {
    PLAYERS("Players"),
    MONSTERS("Monsters"),
    ANIMALS("Animals"),
    WATER_AND_AMBIENT("Water & Ambient"),
    MISC("Other");

    companion object {
        fun fromType(type: EntityType<*>?, id: String): TargetCategory {
            if (id == "minecraft:player") return PLAYERS
            if (type == null) return MISC
            return when (type.category) {
                MobCategory.MONSTER -> MONSTERS
                MobCategory.CREATURE, MobCategory.AXOLOTLS -> ANIMALS
                MobCategory.WATER_CREATURE, MobCategory.WATER_AMBIENT,
                MobCategory.UNDERGROUND_WATER_CREATURE, MobCategory.AMBIENT -> WATER_AND_AMBIENT
                else -> MISC
            }
        }
    }
}

class TargetPickerWindow(val setting: TargetSetting) {
    private val searchField = CustomTextField(hint = "Search entity...") {
        query = it.lowercase(Locale.ROOT)
        rebuild()
        scroll = 0f
    }

    var closed = false
        private set
    private var x = 0f
    private var y = 0f
    private var width = 0f
    private var height = 0f
    private var positioned = false
    private var query = ""
    private var selection: Set<String>? = null
    private var scroll = 0f
    private var drag = false
    private var dragY = 0f
    private var thumbOffset = 0f

    private val headerRowHeight = 24f
    private val entityRowHeight = 28f

    private val listY get() = y + 74f
    private val listHeight get() = (height - 84f).coerceAtLeast(1f)

    sealed class ListItem {
        data class Header(
            val category: TargetCategory,
            val totalCount: Int,
            val selectedCount: Int,
            val allIds: List<String>
        ) : ListItem()

        data class Row(
            val entry: PickerEntry<EntityType<*>>,
            val isSelected: Boolean
        ) : ListItem()
    }

    private val items = ArrayList<ListItem>()
    private var totalContentHeight = 0f

    init {
        bounds()
        rebuild()
    }

    private fun bounds() {
        val screenW = mc.window.guiScaledWidth.toFloat()
        val screenH = mc.window.guiScaledHeight.toFloat()
        width = minOf(440f, (screenW - 16f).coerceAtLeast(80f))
        height = minOf(380f, (screenH - 16f).coerceAtLeast(118f))
        if (!positioned) {
            x = (screenW - width) * 0.5f
            y = (screenH - height) * 0.5f
            positioned = true
        }
        x = x.coerceIn(0f, (screenW - width).coerceAtLeast(0f))
        y = y.coerceIn(0f, (screenH - height).coerceAtLeast(0f))
        searchField.setBounds(x + 10f, y + 36f, width - 20f, 26f)
        clampScroll()
    }

    fun focusSearch() {
        searchField.focus()
    }

    fun close() {
        closed = true
    }

    private fun rebuild() {
        selection = setting.value
        items.clear()

        val allEntries = MinecraftPickerCatalogs.entities.entries
        val grouped = LinkedHashMap<TargetCategory, MutableList<PickerEntry<EntityType<*>>>>()
        for (cat in TargetCategory.entries) {
            grouped[cat] = ArrayList()
        }

        for (entry in allEntries) {
            if (query.isNotEmpty() && !entry.matches(query)) continue
            val cat = TargetCategory.fromType(entry.value, entry.id)
            grouped[cat]?.add(entry)
        }

        var totalH = 0f
        for ((cat, entriesInCat) in grouped) {
            if (entriesInCat.isEmpty()) continue
            val allIds = entriesInCat.map { it.id }
            val selectedCount = allIds.count { setting.isSelected(it) }

            items.add(ListItem.Header(cat, entriesInCat.size, selectedCount, allIds))
            totalH += headerRowHeight

            for (entry in entriesInCat) {
                items.add(ListItem.Row(entry, setting.isSelected(entry.id)))
                totalH += entityRowHeight
            }
        }

        totalContentHeight = totalH
        clampScroll()
    }

    private fun maxScroll(): Float = (totalContentHeight - listHeight).coerceAtLeast(0f)

    private fun clampScroll() {
        scroll = scroll.coerceIn(0f, maxScroll())
    }

    fun render(matrix: Matrix4f, mouseX: Int, mouseY: Int, dt: Float) {
        bounds()
        if (selection !== setting.value) rebuild()
        val palette = M3Theme.current
        RenderUtils.currentGraphics?.nextStratum()
        RenderUtils.rect(matrix, 0f, 0f, mc.window.guiScaledWidth.toFloat(), mc.window.guiScaledHeight.toFloat(), palette.scrim.with(a = 130), ClientRenderPipeline.POPUP_RECT)
        RenderUtils.roundedRect(matrix, x, y, width, height, palette.surface, 12f, ClientRenderPipeline.POPUP_RECT)

        Fonts.google_sans_medium.drawString(matrix, setting.name, x + 14f, y + 12f, 11f, palette.onSurface, false, ClientRenderPipeline.POPUP_TEXT)
        val countLabel = "(${setting.value.size} active)"
        val titleW = Fonts.google_sans_medium.width(setting.name, 11f)
        Fonts.google_sans_regular.drawString(matrix, countLabel, x + 18f + titleW, y + 13.5f, 8.5f, palette.onSurfaceVariant, false, ClientRenderPipeline.POPUP_TEXT)

        Fonts.google_sans_medium.drawCenteredString(matrix, "×", x + width - 18f, y + 9f, 15f, palette.primary, false, ClientRenderPipeline.POPUP_TEXT)
        Fonts.google_sans_medium.drawRightString(matrix, "Clear", x + width - 36f, y + 13f, 8.5f, palette.error, false, ClientRenderPipeline.POPUP_TEXT)
        Fonts.google_sans_medium.drawRightString(matrix, "All", x + width - 68f, y + 13f, 8.5f, palette.primary, false, ClientRenderPipeline.POPUP_TEXT)

        searchField.render(matrix, dt, popup = true)

        val listW = width - 20f
        val lx = x + 10f
        RenderUtils.roundedRect(matrix, lx, listY, listW, listHeight, palette.surfaceContainer, 8f, ClientRenderPipeline.POPUP_RECT)

        ScissorUtil.withScissor(lx + 2f, listY, listW - 4f, listHeight) {
            var curY = listY - scroll
            for (item in items) {
                when (item) {
                    is ListItem.Header -> {
                        if (curY + headerRowHeight >= listY && curY <= listY + listHeight) {
                            renderHeader(matrix, item, lx + 4f, curY, listW - 8f, mouseX, mouseY)
                        }
                        curY += headerRowHeight
                    }
                    is ListItem.Row -> {
                        if (curY + entityRowHeight >= listY && curY <= listY + listHeight) {
                            renderRow(matrix, item, lx + 4f, curY, listW - 8f, mouseX, mouseY)
                        }
                        curY += entityRowHeight
                    }
                }
            }
        }

        renderScrollbar(matrix, mouseX, mouseY)
    }

    private fun renderHeader(
        matrix: Matrix4f,
        header: ListItem.Header,
        hx: Float,
        hy: Float,
        hw: Float,
        mouseX: Int,
        mouseY: Int
    ) {
        val palette = M3Theme.current
        RenderUtils.roundedRect(matrix, hx, hy + 2f, hw, headerRowHeight - 4f, palette.surfaceContainerHigh, 5f, ClientRenderPipeline.POPUP_RECT)

        val label = "${header.category.title} (${header.selectedCount}/${header.totalCount})"
        Fonts.google_sans_medium.drawString(matrix, label, hx + 8f, hy + 6.5f, 9f, palette.onSurface, false, ClientRenderPipeline.POPUP_TEXT)

        val allActive = header.selectedCount == header.totalCount && header.totalCount > 0
        val btnText = if (allActive) "None" else "All"
        val btnColor = if (allActive) palette.error else palette.primary
        val btnHovered = over(mouseX.toDouble(), mouseY.toDouble(), (hx + hw - 36f).toDouble(), hy.toDouble(), 34.0, headerRowHeight.toDouble())
        if (btnHovered) {
            RenderUtils.roundedRect(matrix, hx + hw - 36f, hy + 3f, 32f, headerRowHeight - 6f, palette.stateHover, 4f, ClientRenderPipeline.POPUP_RECT)
        }
        Fonts.google_sans_medium.drawCenteredString(matrix, btnText, hx + hw - 20f, hy + 7f, 8.5f, btnColor, false, ClientRenderPipeline.POPUP_TEXT)
    }

    private fun renderRow(
        matrix: Matrix4f,
        row: ListItem.Row,
        rx: Float,
        ry: Float,
        rw: Float,
        mouseX: Int,
        mouseY: Int
    ) {
        val palette = M3Theme.current
        val hovered = over(mouseX.toDouble(), mouseY.toDouble(), rx.toDouble(), ry.toDouble(), rw.toDouble(), entityRowHeight.toDouble())
        if (hovered) {
            RenderUtils.roundedRect(matrix, rx, ry + 1f, rw, entityRowHeight - 2f, palette.stateHover, 5f, ClientRenderPipeline.POPUP_RECT)
        }

        RenderUtils.currentGraphics?.let {
            row.entry.preview?.render(it, (rx + 6f).toInt(), (ry + 5f).toInt(), 18)
        }

        val name = row.entry.label
        Fonts.google_sans_medium.drawString(matrix, name, rx + 28f, ry + 4f, 9f, palette.onSurface, false, ClientRenderPipeline.POPUP_TEXT)
        Fonts.google_sans_regular.drawString(matrix, row.entry.id, rx + 28f, ry + 15f, 7.5f, palette.onSurfaceVariant, false, ClientRenderPipeline.POPUP_TEXT)

        val switchW = 22f
        val switchH = 12f
        val switchX = rx + rw - switchW - 8f
        val switchY = ry + (entityRowHeight - switchH) * 0.5f

        val trackColor = if (row.isSelected) palette.primary else Color4b(54, 50, 64, 255)
        RenderUtils.roundedRect(matrix, switchX, switchY, switchW, switchH, trackColor, switchH * 0.5f, ClientRenderPipeline.POPUP_RECT)

        val thumbSize = 8f
        val thumbX = if (row.isSelected) switchX + switchW - thumbSize - 2f else switchX + 2f
        val thumbY = switchY + (switchH - thumbSize) * 0.5f
        val thumbColor = if (row.isSelected) Color4b(34, 30, 39, 255) else palette.outline
        RenderUtils.roundedRect(matrix, thumbX, thumbY, thumbSize, thumbSize, thumbColor, thumbSize * 0.5f, ClientRenderPipeline.POPUP_RECT)
    }

    private fun thumbHeight(): Float {
        val track = listHeight
        if (totalContentHeight <= 0f) return track
        return (track * (track / totalContentHeight)).coerceIn(16f, track)
    }

    private fun renderScrollbar(matrix: Matrix4f, mouseX: Int, mouseY: Int) {
        val max = maxScroll()
        if (max <= 0f) return
        val palette = M3Theme.current
        val barX = x + width - 14f
        val trackH = listHeight - thumbHeight()
        val thumbY = listY + (scroll / max) * trackH
        val hovered = over(mouseX.toDouble(), mouseY.toDouble(), barX.toDouble(), thumbY.toDouble(), 4.0, thumbHeight().toDouble())
        RenderUtils.roundedRect(matrix, barX, thumbY, 3.5f, thumbHeight(), if (hovered || drag) palette.primary else palette.outlineVariant, 1.75f, ClientRenderPipeline.POPUP_RECT)
    }

    fun mouseClicked(event: MouseButtonEvent, button: Int) {
        bounds()
        val mx = event.x()
        val my = event.y()

        if (!over(mx, my, x.toDouble(), y.toDouble(), width.toDouble(), height.toDouble())) {
            close()
            return
        }

        if (button != 0) return

        if (over(mx, my, (x + width - 26f).toDouble(), (y + 4f).toDouble(), 22.0, 22.0)) {
            close()
            return
        }

        if (over(mx, my, (x + width - 42f).toDouble(), (y + 9f).toDouble(), 28.0, 16.0)) {
            setting.set(emptySet())
            ConfigManager.saveConfig("default")
            rebuild()
            return
        }

        if (over(mx, my, (x + width - 74f).toDouble(), (y + 9f).toDouble(), 28.0, 16.0)) {
            val allLiving = MinecraftPickerCatalogs.entities.entries.map { it.id }
            setting.selectAll(allLiving)
            ConfigManager.saveConfig("default")
            rebuild()
            return
        }

        CustomTextField.blurOutside(mx, my)
        if (searchField.mouseClicked(mx, my, button)) return

        val barX = x + width - 16f
        val max = maxScroll()
        if (max > 0f && over(mx, my, barX.toDouble(), listY.toDouble(), 10.0, listHeight.toDouble())) {
            val th = thumbHeight()
            val trackH = listHeight - th
            val currentThumbY = listY + (scroll / max) * trackH
            if (my >= currentThumbY && my <= currentThumbY + th) {
                drag = true
                dragY = my.toFloat()
                thumbOffset = (my - currentThumbY).toFloat()
            } else {
                val targetY = (my - listY - th * 0.5f).toFloat()
                scroll = (targetY / trackH).coerceIn(0f, 1f) * max
                drag = true
                thumbOffset = th * 0.5f
            }
            return
        }

        val lx = x + 10f
        val listW = width - 20f
        if (over(mx, my, lx.toDouble(), listY.toDouble(), listW.toDouble(), listHeight.toDouble())) {
            var curY = listY - scroll
            for (item in items) {
                when (item) {
                    is ListItem.Header -> {
                        if (over(mx, my, (lx + listW - 40f).toDouble(), curY.toDouble(), 36.0, headerRowHeight.toDouble())) {
                            val allActive = item.selectedCount == item.totalCount && item.totalCount > 0
                            if (allActive) {
                                setting.deselectAll(item.allIds)
                            } else {
                                setting.selectAll(item.allIds)
                            }
                            ConfigManager.saveConfig("default")
                            rebuild()
                            return
                        }
                        curY += headerRowHeight
                    }
                    is ListItem.Row -> {
                        if (over(mx, my, lx.toDouble(), curY.toDouble(), listW.toDouble(), entityRowHeight.toDouble())) {
                            setting.toggle(item.entry.id)
                            ConfigManager.saveConfig("default")
                            rebuild()
                            return
                        }
                        curY += entityRowHeight
                    }
                }
            }
        }
    }

    fun mouseReleased(event: MouseButtonEvent) {
        drag = false
        searchField.mouseReleased(event.button())
    }

    fun mouseDragged(event: MouseButtonEvent, button: Int, deltaX: Double, deltaY: Double) {
        if (button != 0) return
        val max = maxScroll()
        if (drag && max > 0f) {
            val th = thumbHeight()
            val trackH = listHeight - th
            if (trackH > 0f) {
                val target = ((event.y().toFloat() - listY - thumbOffset) / trackH).coerceIn(0f, 1f) * max
                scroll = target
            }
            return
        }
        searchField.mouseDragged(event.x(), button)
    }

    fun mouseScrolled(mouseX: Double, mouseY: Double, delta: Double) {
        if (over(mouseX, mouseY, x.toDouble(), listY.toDouble(), width.toDouble(), listHeight.toDouble())) {
            scroll = (scroll - delta.toFloat() * entityRowHeight * 2f).coerceIn(0f, maxScroll())
        }
    }

    fun keyPressed(event: KeyEvent) {
        if (searchField.keyPressed(event.key(), event.keycode(), event.modifiers())) return
        if (event.key() == InputConstants.KEY_ESCAPE) close()
    }

    fun charTyped(event: CharacterEvent) {
        searchField.charTyped(event)
    }

    private fun over(mouseX: Double, mouseY: Double, ox: Double, oy: Double, ow: Double, oh: Double): Boolean =
        mouseX >= ox && mouseX < ox + ow && mouseY >= oy && mouseY < oy + oh
}
