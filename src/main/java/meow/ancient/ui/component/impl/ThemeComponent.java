package meow.ancient.ui.component.impl;

import net.minecraft.client.util.math.MatrixStack;
import meow.ancient.module.settings.ThemeSetting;
import meow.ancient.module.settings.impl.Theme;
import meow.ancient.ui.component.Component;
import meow.ancient.util.render.helper.HoverUtil;
import meow.ancient.util.render.providers.ColorProvider;
import meow.ancient.util.render.renderers.DrawUtil;

public class ThemeComponent extends Component {

    private final ThemeSetting option;

    public ThemeComponent(ThemeSetting option) {
        this.option = option;
    }

    @Override
    public void render(MatrixStack matrixStack, int mouseX, int mouseY, float partialTicks) {
        float startX = x + 2;
        float currentX = startX;
        float circleY = y + 8;
        for (Theme theme : option.getThemes()) {
            theme.checkAnimation.run(option.getValue() == theme);
            if (theme.checkAnimation.getValue() > 0.01f) {
                float alphaAnim = theme.checkAnimation.getValue();
                DrawUtil.drawRound(currentX - 1.5f, circleY - 1.5f, 11, 11, 5.5f,
                        ColorProvider.rgba(255, 255, 255, (int)(255 * alphaAnim)));
            }

            theme.x = currentX;
            theme.y = circleY;
            theme.drawTheme(getAlphaAnimSetting().getValue());

            currentX += 14;
        }

        setHeight(20);
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        float currentX = x + 2;
        float circleY = y + 8;

        for (Theme theme : option.getThemes()) {
            if (HoverUtil.isHovered(mouseX, mouseY, currentX - 1, circleY - 1, 10, 10)) {
                if (option.getValue() != theme && button == 0) {
                    option.setValue(theme);
                    theme.animation.setValue(0);
                }
            }
            currentX += 14;
        }
    }

    @Override
    public void mouseReleased(double mouseX, double mouseY, int button) {}
    @Override
    public void mouseScrolled(double mouseX, double mouseY, double delta) {}
    @Override
    public void keyPressed(int keyCode, int scanCode, int modifiers) {}

    @Override
    public boolean isVisible() {
        return true;
    }
}