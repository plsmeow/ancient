package tech.onetap.ui.component.impl;

import net.minecraft.client.util.math.MatrixStack;
import org.lwjgl.glfw.GLFW;
import tech.onetap.module.settings.BooleanSetting;
import tech.onetap.ui.component.Component;
import tech.onetap.util.cursor.CursorManager;
import tech.onetap.util.render.helper.HoverUtil;
import tech.onetap.util.render.msdf.Fonts;
import tech.onetap.util.render.providers.ColorProvider;
import tech.onetap.util.render.renderers.DrawUtil;

public class BooleanComponent extends Component {
    private final BooleanSetting setting;
    private boolean binding;

    public BooleanComponent(BooleanSetting setting) {
        this.setting = setting;
    }

    @Override
    public void render(MatrixStack matrixStack, int mouseX, int mouseY, float partialTicks) {
        float alpha = Math.max(Math.min(getAlphaAnimSetting().getValue(), 1), 0);
        int alphaInt = (int) (255 * alpha);

        float boxSize = 8f;
        float boxX = x + width - boxSize - 2.5f;
        float boxY = y + 3.5f;

        if (HoverUtil.isHovered(mouseX, mouseY, boxX - 2, boxY - 2, boxSize + 4, boxSize + 4)) {
            CursorManager.requestHand();
        }

        // ==========================================
        // ИСПРАВЛЕНИЕ ТУТ: Поменяли y + 3.5f на y + 2.5f, чтобы поднять текст ВВЕРХ
        // ==========================================
        DrawUtil.drawText(Fonts.SFREGULAR.get(), binding ? "Binding..." : setting.getName(),
                x + 4.5f, y + 5f, ColorProvider.rgba(255, 255, 255, alphaInt), 6.5f,0.4f,1f,width);

        // Отрисовка подложки чекбокса
        DrawUtil.drawRound(boxX - 0.5f, boxY - 0.5f, boxSize + 1, boxSize + 1, 2f, ColorProvider.rgba(60, 60, 65, (int)(150 * alpha)));
        DrawUtil.drawRound(boxX, boxY, boxSize, boxSize, 2f, ColorProvider.rgba(20, 20, 25, alphaInt));

        // Отрисовка заливки (галочки)
        if (setting.getAnimation().getValue() > 0.05f) {
            int checkAlpha = (int) (255 * alpha * setting.getAnimation().getValue());
            DrawUtil.drawRound(boxX + 1.5f, boxY + 1.5f, boxSize - 3f, boxSize - 3f, 1f, ColorProvider.setAlpha(ColorProvider.getThemeColor(), checkAlpha));
        }

        setHeight(13);
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        float boxSize = 8f;
        float boxX = x + width - boxSize - 2.5f;
        float boxY = y + 3.5f;

        if (HoverUtil.isHovered(mouseX, mouseY, boxX - 2, boxY - 2, boxSize + 4, boxSize + 4)) {
            if (binding) {
                if (button == 2) {
                    binding = false;
                    return;
                }
                setting.bindTo(button);
                binding = false;
                return;
            }

            if (button == 0) setting.setValue(!setting.getValue());
            if (button == 2) binding = true;
        }
    }

    @Override
    public void keyPressed(int keyCode, int scanCode, int modifiers) {
        if (binding) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                binding = false;
                return;
            }
            if (keyCode == GLFW.GLFW_KEY_DELETE) {
                setting.unbind();
                binding = false;
                return;
            }
            setting.setKey(keyCode);
            binding = false;
        }
    }

    @Override
    public boolean isVisible() {
        return setting.visible.get();
    }
}