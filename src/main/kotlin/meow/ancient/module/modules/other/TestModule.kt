package meow.ancient.module.modules.other

import meow.ancient.client.extensions.OTHER
import meow.ancient.client.gui.material.MaterialYouShapesRenderer
import meow.ancient.client.util.other.color.Color4b
import meow.ancient.module.Module
import meow.ancient.module.setting.MinecraftPickerCatalogs
import net.minecraft.client.gui.GuiGraphicsExtractor

object TestModule : Module(
    name = "Test",
    category = OTHER,
    description = "Material 3 showcase module"
) {
    val materialYouShapes by boolean("Material You Shapes", false, description = "Showcase Material You shapes on screen")
    val theSwitch by boolean("The Switch", true)
    var textDemo by text("Text", "Ancient", description = "Editable persistent text")
    val colorPicker by color("Color Picker", Color4b(140, 60, 255, 255), description = "Material 3 color picker setting")
    val select by multiMode("Select", setOf("Sel 1", "Sel 3", "Sel 4"), listOf("Sel 1", "Sel 2", "Sel 3", "Sel 4"))
    val modes by multiMode("Modes", setOf("Default", "Aggressive"), listOf("Default", "Aggressive", "Legit"))
    val group by mode("Group", "Gp 3", listOf("Gp 1", "Gp 2", "Gp 3"))
    val items by picker("Items", linkedSetOf("minecraft:diamond_sword", "minecraft:totem_of_undying", "minecraft:elytra"), MinecraftPickerCatalogs.items)
    val entities by picker("Entities", linkedSetOf("minecraft:player", "minecraft:zombie", "minecraft:cat"), MinecraftPickerCatalogs.entities)
    val blocks by picker("Blocks", linkedSetOf("minecraft:grass_block", "minecraft:obsidian", "minecraft:chest"), MinecraftPickerCatalogs.blocks)
    val effects by picker("Mob Effects", linkedSetOf("minecraft:speed", "minecraft:strength", "minecraft:regeneration"), MinecraftPickerCatalogs.effects)

    fun renderShapesShowcase(graphics: GuiGraphicsExtractor, partialTicks: Float) {
        val visible = isEnabled() && materialYouShapes
        MaterialYouShapesRenderer.render(graphics, partialTicks, visible)
    }
}
