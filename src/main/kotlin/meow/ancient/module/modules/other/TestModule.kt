package meow.ancient.module.modules.other

import meow.ancient.client.extensions.OTHER
import meow.ancient.client.gui.material.MaterialYouShapesRenderer
import meow.ancient.client.gui.debug.TestDiagnosticsPanel
import meow.ancient.client.util.other.color.Color4b
import meow.ancient.module.Module
import meow.ancient.module.setting.MinecraftPickerCatalogs
import meow.ancient.module.setting.Curve
import net.minecraft.client.gui.GuiGraphicsExtractor

object TestModule : Module(
    name = "Test",
    category = OTHER,
    description = "Client diagnostics and Material 3 showcase"
) {
    val debugPanel by boolean("Debug Panel", true, description = "Show action priorities, slots, rotations, mining, motion and server corrections")
    val debugSections by multiMode("Debug Sections", setOf("Actions", "Rotation", "Mining", "Motion", "Corrections"),
        listOf("Actions", "Rotation", "Mining", "Motion", "Corrections"), isVisible = { debugPanel })
    val debugX = number("Debug X", 16.0, 0.0, 4096.0, 1.0, 0, isVisible = { debugPanel })
    val debugY = number("Debug Y", 90.0, 0.0, 4096.0, 1.0, 0, isVisible = { debugPanel })
    val debugScale by number("Debug Scale", 1.0, 0.75, 1.5, 0.05, 2, isVisible = { debugPanel })
    val materialYouShapes by boolean("Material You Shapes", false, description = "Showcase Material You shapes on screen")
    val theSwitch by boolean("The Switch", true)
    var textDemo by text("Text", "Ancient", description = "Editable persistent text")
    val colorPicker by color("Color Picker", Color4b(140, 60, 255, 255), description = "Material 3 color picker setting")
    val select by multiMode("Select", setOf("Sel 1", "Sel 3", "Sel 4"), listOf("Sel 1", "Sel 2", "Sel 3", "Sel 4"))
    val modes by multiMode("Modes", setOf("Default", "Aggressive"), listOf("Default", "Aggressive", "Legit"))
    val group by mode("Group", "Gp 3", listOf("Gp 1", "Gp 2", "Gp 3"))
    val curveDemo = curve(
        "Curve Editor", Curve.EASE_IN_OUT,
        xLabel = "Progress", yLabel = "Value",
        description = "Left click empty space to add a point, drag to move, right click a point to remove, chips apply presets"
    )
    val items by picker("Items", linkedSetOf("minecraft:diamond_sword", "minecraft:totem_of_undying", "minecraft:elytra"), MinecraftPickerCatalogs.items)
    val entities by picker("Entities", linkedSetOf("minecraft:player", "minecraft:zombie", "minecraft:cat"), MinecraftPickerCatalogs.entities)
    val blocks by picker("Blocks", linkedSetOf("minecraft:grass_block", "minecraft:obsidian", "minecraft:chest"), MinecraftPickerCatalogs.blocks)
    val effects by picker("Mob Effects", linkedSetOf("minecraft:speed", "minecraft:strength", "minecraft:regeneration"), MinecraftPickerCatalogs.effects)

    fun renderShapesShowcase(graphics: GuiGraphicsExtractor, partialTicks: Float) {
        TestDiagnosticsPanel.render(graphics)
        val visible = isEnabled() && materialYouShapes
        MaterialYouShapesRenderer.render(graphics, partialTicks, visible)
    }
}
