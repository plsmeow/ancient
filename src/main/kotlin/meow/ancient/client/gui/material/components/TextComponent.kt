package meow.ancient.client.gui.material.components

import meow.ancient.client.gui.material.M3Theme
import meow.ancient.client.util.render.font.Fonts
import meow.ancient.client.util.render.engine.controls.ClientRenderPipeline
import meow.ancient.module.setting.TextSetting
import net.minecraft.client.input.CharacterEvent
import org.joml.Matrix4f

class TextComponent(private val textSetting: TextSetting) : SettingComponent(textSetting) {
    private val field = CustomTextField(textSetting.value, textSetting.maxLength, "Enter text") { textSetting.set(it) }

    override fun render(matrix: Matrix4f, x: Float, y: Float, width: Float, mouseX: Int, mouseY: Int, dt: Float): Float {
        this.x = x
        this.y = y
        this.width = width
        height = 46f
        field.setText(textSetting.value)
        val title = fitText(textSetting.name, width - 10f, 9.5f, 8f)
        Fonts.google_sans_medium.drawString(matrix, title.text, x + 5f, y + 3f, title.size, M3Theme.current.onSurface, false, ClientRenderPipeline.WINDOW_TEXT)
        field.setBounds(x, y + 18f, width, 28f)
        field.render(matrix, dt)
        return height
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean = field.mouseClicked(mouseX, mouseY, button)

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int) {
        field.mouseReleased(button)
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, deltaX: Double, deltaY: Double) {
        field.mouseDragged(mouseX, button)
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean = field.keyPressed(keyCode, scanCode, modifiers)
    override fun charTyped(event: CharacterEvent): Boolean = field.charTyped(event)

    fun blur() {
        field.blur()
    }
}
