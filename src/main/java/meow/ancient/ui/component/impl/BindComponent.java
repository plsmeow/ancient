package meow.ancient.ui.component.impl;

import net.minecraft.client.util.math.MatrixStack;
import org.lwjgl.glfw.GLFW;
import meow.ancient.module.settings.BindSetting;
import meow.ancient.ui.component.Component;
import meow.ancient.util.cursor.CursorManager;
import meow.ancient.util.keyboard.KeyStorage;
import meow.ancient.util.render.helper.HoverUtil;
import meow.ancient.util.render.msdf.Fonts;
import meow.ancient.util.render.providers.ColorProvider;
import meow.ancient.util.render.renderers.DrawUtil;

public class BindComponent extends Component {
    private final BindSetting setting;
    private boolean binding;

    public BindComponent(BindSetting setting) {
        this.setting = setting;
    }

    @Override
    public void render(MatrixStack matrixStack, int mouseX, int mouseY, float partialTicks) {
        float alpha = Math.max(Math.min(getAlphaAnimSetting().getValue(), 1), 0);
        int alphaInt = (int) (255 * alpha);

        String key = (setting.getValue() == -1) ? "N/A" : KeyStorage.getKey(setting.getValue());
        if (binding) key = "...";

        float keyWidth = Fonts.SFREGULAR.get().getWidth(key, 6.5f) + 6f;
        float boxX = x + width - keyWidth - 4;
        float boxY = y + 2;
        float boxHeight = 9;

        if (HoverUtil.isHovered(mouseX, mouseY, boxX, boxY, keyWidth, boxHeight)) {
            CursorManager.requestHand();
        }

        DrawUtil.drawText(Fonts.SFREGULAR.get(), setting.getName(), x + 4.5f, y + 3f, ColorProvider.rgba(255, 255, 255, alphaInt), 6.5f);

        DrawUtil.drawRound(boxX - 0.5f, boxY - 0.5f, keyWidth + 1, boxHeight + 1, 2f, ColorProvider.rgba(60, 60, 65, (int)(150 * alpha)));
        DrawUtil.drawRound(boxX, boxY, keyWidth, boxHeight, 2f, ColorProvider.rgba(20, 20, 25, alphaInt));
        DrawUtil.drawText(Fonts.SFREGULAR.get(), key, boxX + 4, boxY + 0.5f, ColorProvider.rgba(200, 200, 200, alphaInt), 6.5f);

        setHeight(13); // Было 15
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        String key = (setting.getValue() == -1) ? "N/A" : KeyStorage.getKey(setting.getValue());
        float keyWidth = Fonts.SFREGULAR.get().getWidth(key, 6.5f) + 6f;
        float boxX = x + width - keyWidth - 4;
        float boxY = y + 2;

        if (binding) {
            if (button != 0) setting.setValue(button);
            binding = false;
        } else if (HoverUtil.isHovered(mouseX, mouseY, boxX, boxY, keyWidth, 9) && button == 0) {
            binding = true;
        }
    }

    @Override
    public void keyPressed(int keyCode, int scanCode, int modifiers) {
        if (binding) {
            if (keyCode != GLFW.GLFW_KEY_ESCAPE && keyCode != GLFW.GLFW_KEY_DELETE) setting.setValue(keyCode);
            if (keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_ESCAPE) setting.setValue(-1);
            binding = false;
        }
    }

    @Override
    public boolean isVisible() {
        return setting.visible.get();
    }
}