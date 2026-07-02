package tech.onetap.ui.component.impl;

import net.minecraft.client.util.math.MatrixStack;
import org.lwjgl.glfw.GLFW;
import tech.onetap.module.settings.ModeSetting;
import tech.onetap.ui.component.Component;
import tech.onetap.util.cursor.CursorManager;
import tech.onetap.util.keyboard.KeyStorage;
import tech.onetap.util.render.helper.HoverUtil;
import tech.onetap.util.render.math.Animation;
import tech.onetap.util.render.math.Easing;
import tech.onetap.util.render.math.Scissor;
import tech.onetap.util.render.msdf.Fonts;
import tech.onetap.util.render.providers.ColorProvider;
import tech.onetap.util.render.renderers.DrawUtil;

public class ModeComponent extends Component {
    private final ModeSetting setting;
    private boolean opened;
    private boolean binding;
    private final Animation openAnim = new Animation(Easing.QUINTIC_OUT, 300);

    public ModeComponent(ModeSetting setting) {
        this.setting = setting;
    }

    @Override
    public void render(MatrixStack matrixStack, int mouseX, int mouseY, float partialTicks) {
        float animValue = getAlphaAnimSetting().getValue();
        float alpha = Math.max(Math.min(animValue * getAlphaAnim().getValue(), 1), 0);
        int alphaInt = (int) (255 * alpha);

        openAnim.run(opened);
        float itemHeight = 11f;
        float dropHeight = setting.getModes().size() * itemHeight;

        float btnY = y + 9.5f;
        float btnHeight = 11f;
        float animHeight = openAnim.getValue() * dropHeight;
        float totalBoxHeight = btnHeight + animHeight;

        setHeight((22 + animHeight) * animValue);

        if (alpha < 0.02f) return;

        String title = binding ? "Нажмите клавишу..." : setting.getName();
        DrawUtil.drawText(Fonts.SFREGULAR.get(), title, x + 4.5f, y + 1.5f, ColorProvider.rgba(255, 255, 255, alphaInt), 6.5f);

        if (!binding && setting.isBound()) {
            String keyText = "[" + KeyStorage.getKey(setting.getKey()) + "]";
            float nameWidth = Fonts.SFREGULAR.get().getWidth(setting.getName(), 6.5f);
            DrawUtil.drawText(Fonts.SFREGULAR.get(), keyText, x + 4.5f + nameWidth + 4f, y + 1.5f, ColorProvider.setAlpha(ColorProvider.getThemeColor(), alphaInt), 6.5f);
        }

        if (HoverUtil.isHovered(mouseX, mouseY, x + 4, btnY, width - 8, btnHeight)) CursorManager.requestHand();

        DrawUtil.drawRound(x + 3.5f, btnY - 0.5f, width - 7, totalBoxHeight + 2.5f, 2.5f, ColorProvider.rgba(60, 60, 65, (int)(150 * alpha)));
        DrawUtil.drawRound(x + 4, btnY, width - 8, totalBoxHeight + 1.5f, 2.5f, ColorProvider.rgba(20, 20, 25, alphaInt));

        String textClosed = setting.getValue();
        String textOpened = "...";

        int textAlphaClosed = (int) (alphaInt * (1f - openAnim.getValue()));
        int textAlphaOpened = (int) (alphaInt * openAnim.getValue());

        if (textAlphaClosed > 0) {
            DrawUtil.drawText(Fonts.SFREGULAR.get(), textClosed, x + 7, btnY + 2.5f, ColorProvider.rgba(200, 200, 200, textAlphaClosed), 6.5f);
        }
        if (textAlphaOpened > 0) {
            DrawUtil.drawText(Fonts.SFREGULAR.get(), textOpened, x + 7, btnY + 2.5f, ColorProvider.rgba(200, 200, 200, textAlphaOpened), 6.5f);
        }

        if (openAnim.getValue() > 0.01f) {
            float listY = btnY + btnHeight;

            Scissor.push();
            Scissor.setFromComponentCoordinates(x, listY, width, animHeight);

            float currentY = listY;
            for (String mode : setting.getModes()) {
                if (HoverUtil.isHovered(mouseX, mouseY, x + 4, currentY, width - 8, itemHeight)) {
                    DrawUtil.drawRound(x + 4.5f, currentY, width - 9, itemHeight, 0, ColorProvider.rgba(255, 255, 255, (int)(15 * alpha)));
                }

                int textColor = setting.is(mode) ? ColorProvider.getThemeColor() : ColorProvider.rgba(180, 180, 180, 255);
                DrawUtil.drawText(Fonts.SFREGULAR.get(), mode, x + 7, currentY + 2f, ColorProvider.setAlpha(textColor, (int)(255 * alpha * openAnim.getValue())), 6.5f);

                currentY += itemHeight;
            }
            Scissor.unset();
            Scissor.pop();
        }
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (binding) {
            if (button == 2) {
                binding = false;
                return;
            }
            setting.bindTo(button);
            binding = false;
            return;
        }

        float btnY = y + 9.5f;
        float btnHeight = 11f;

        // Бинд по нажатию колёсика на заголовке настройки
        if (button == 2 && HoverUtil.isHovered(mouseX, mouseY, x, y, width, btnY + btnHeight - y)) {
            binding = true;
            return;
        }

        if (button != 0) return;

        if (HoverUtil.isHovered(mouseX, mouseY, x + 4, btnY, width - 8, btnHeight)) {
            opened = !opened;
            return;
        }

        if (opened) {
            float listY = btnY + btnHeight;
            float currentY = listY;
            float itemHeight = 11f;

            for (String mode : setting.getModes()) {
                if (HoverUtil.isHovered(mouseX, mouseY, x + 4, currentY, width - 8, itemHeight)) {
                    setting.setValue(mode);
                    opened = false;
                    break;
                }
                currentY += itemHeight;
            }
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
            setting.bindTo(keyCode);
            binding = false;
        }
    }

    @Override
    public boolean isVisible() {
        return setting.visible.get();
    }
}