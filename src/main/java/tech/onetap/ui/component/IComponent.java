package tech.onetap.ui.component;

import net.minecraft.client.util.math.MatrixStack;

public interface IComponent {
    default void render(MatrixStack matrixStack, int mouseX, int mouseY, float partialTicks) {}
    default void mouseClicked(double mouseX, double mouseY, int button) {}
    default void mouseReleased(double mouseX, double mouseY, int button) {}
    default void mouseScrolled(double mouseX, double mouseY, double delta) {}
    default void keyPressed(int keyCode, int scanCode, int modifiers) {}
}