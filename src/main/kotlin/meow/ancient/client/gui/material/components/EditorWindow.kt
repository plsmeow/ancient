package meow.ancient.client.gui.material.components

import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import org.joml.Matrix4f

interface EditorWindow {
    val closed: Boolean
    fun render(matrix: Matrix4f, mouseX: Int, mouseY: Int, dt: Float)
    fun mouseClicked(event: MouseButtonEvent, button: Int)
    fun mouseDragged(event: MouseButtonEvent, button: Int, deltaX: Double, deltaY: Double)
    fun mouseReleased(event: MouseButtonEvent, button: Int)
    fun mouseScrolled(mouseX: Double, mouseY: Double, delta: Double)
    fun keyPressed(event: KeyEvent)
    fun charTyped(event: CharacterEvent)
    fun releaseInput()
    fun close()
}
