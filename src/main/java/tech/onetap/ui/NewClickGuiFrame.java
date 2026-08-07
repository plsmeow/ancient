package tech.onetap.ui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.Window;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import tech.onetap.module.Module;
import tech.onetap.ui.wonderful.ClickGuiInputHandler;
import tech.onetap.ui.wonderful.ClickGuiRenderer;
import tech.onetap.ui.wonderful.ClickGuiSettingRenderer;
import tech.onetap.ui.wonderful.ClickGuiState;
import tech.onetap.util.IMinecraft;
import tech.onetap.util.render.math.Animation;
import tech.onetap.util.render.math.Easing;

import java.util.ArrayList;
import java.util.List;

public class NewClickGuiFrame extends Screen implements IMinecraft {

    private static final ClickGuiState STATE = new ClickGuiState();

    private final ClickGuiState state = STATE;
    private final ClickGuiRenderer renderer = new ClickGuiRenderer(state, new ClickGuiSettingRenderer());
    private final ClickGuiInputHandler inputHandler = new ClickGuiInputHandler(state);

    private final Animation openAnimation;
    private boolean needToClose;

    public NewClickGuiFrame() {
        super(Text.of("Avalora Frame"));
        this.openAnimation = new Animation(Easing.CUBIC_OUT, 200);
        state.refreshModules();
    }

    @Override
    protected void init() {
        super.init();
        this.needToClose = false;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        openAnimation.run(!needToClose);

        float progress = openAnimation.getValue();
        if (needToClose && progress < 0.02F) {
            mc.setScreen(null);
            return;
        }

        Window window = mc.getWindow();
        state.updatePosition(window);
        state.setRenderOffsetY((1.0f - progress) * 15.0f);
        renderer.render(mouseX, mouseY, window, progress);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (needToClose) return true;
        return inputHandler.mouseClicked(mouseX, mouseY, button, IMinecraft.mc.getWindow()) || super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (inputHandler.mouseDragged(mouseX, mouseY, button)) return true;
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (inputHandler.mouseReleased(button)) return true;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (needToClose) return true;
        return inputHandler.mouseScrolled(mouseX, mouseY, verticalAmount) || super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (inputHandler.keyPressed(keyCode, modifiers)) return true;
            this.needToClose = true;
            return true;
        }
        return inputHandler.keyPressed(keyCode, modifiers) || super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (needToClose) return true;
        return inputHandler.charTyped(chr, modifiers) || super.charTyped(chr, modifiers);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
