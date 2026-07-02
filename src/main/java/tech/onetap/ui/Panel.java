package tech.onetap.ui;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import tech.onetap.Onetap;
import tech.onetap.module.ModuleCategory;
import tech.onetap.ui.component.Component;
import tech.onetap.util.IMinecraft;
import tech.onetap.util.render.helper.HoverUtil;
import tech.onetap.util.render.math.Animation;
import tech.onetap.util.render.math.Easing;
import tech.onetap.util.render.math.Scissor;
import tech.onetap.util.render.msdf.Fonts;
import tech.onetap.util.render.providers.ColorProvider;
import tech.onetap.util.render.renderers.DrawUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Getter
@Setter
public class Panel implements IMinecraft {
    public float x, y, width, height;
    public final ModuleCategory category;
    public List<ModuleComponent> moduleComponents = new ArrayList<>();
    private Animation animation = new Animation(Easing.QUINTIC_OUT, 350);
    private Animation animationAlpha = new Animation(Easing.BOUNCE_OUT, 350);
    private final Animation scrollbarAnim = new Animation(Easing.CUBIC_IN_OUT, 220);
    float scroll;
    float maxScroll;

    private final ClickGuiFrame parent;

    public Panel(ModuleCategory category, ClickGuiFrame parent) {
        this.category = category;
        this.parent = parent;
        Onetap.getInstance().getModuleStorage().getModules().stream()
                .filter(m -> m.getCategory() == this.category)
                .sorted(Comparator.comparing(m -> m.getName().toLowerCase()))
                .forEach(m -> moduleComponents.add(new ModuleComponent(m, this)));
    }

    public void clampScroll() {
        if (maxScroll > 0) {
            scroll = MathHelper.clamp(scroll, -maxScroll, 0);
        } else {
            scroll = 0;
        }
    }

    public void render(MatrixStack matrixStack, int mouseX, int mouseY, float partialTicks) {
        animationAlpha.setValue(1);
        float alpha = Math.min(255 * animationAlpha.getValue(), 255);
        float alphaRatio = alpha / 255f;
        float cornerRadius = 8f;
        float headerHeight = 20f;

        // ==========================================================
        // ИЗМЕНЕНИЕ: Проверка на Optimization и применение цвета 15, 15, 15
        // ==========================================================
        if (tech.onetap.module.list.render.Optimization.shouldDisableClickGuiBlur()) {
            // Если включена оптимизация — рисуем чистый сплошной фон 15, 15, 15 без блюра
            int optColor = ColorProvider.rgba(15, 15, 15, (int)(255 * alphaRatio));
            DrawUtil.drawRound(x, y, width, height, cornerRadius, optColor);
        } else {
            // Если оптимизация выключена — оставляем ваш дефолтный блюр панели
            DrawUtil.drawRoundBlur(x, y, width, height, cornerRadius, ColorProvider.rgba(75, 75, 75, (int)(255 * alphaRatio)), 20f);
            int panelColor = ColorProvider.rgba(14, 14, 16, (int)(130 * alphaRatio));
            DrawUtil.drawRound(x, y, width, height, cornerRadius, panelColor);
        }

        // Заголовок категории
        String title = category.name();
        String capitalizedTitle = title.substring(0, 1).toUpperCase() + title.substring(1).toLowerCase();
        float titleWidth = Fonts.SFREGULAR.get().getWidth(capitalizedTitle, 8.5f);
        DrawUtil.drawText(Fonts.SFREGULAR.get(), capitalizedTitle, x + width / 2f - titleWidth / 2f, y + 6f, ColorProvider.rgba(255, 255, 255, alpha), 8.5f);

        float offset = 0;
        clampScroll();
        animation.run(scroll);

        Scissor.push();
        Scissor.setFromComponentCoordinates(x, y + headerHeight, width, height - headerHeight - 4);

        for (ModuleComponent component : moduleComponents) {
            if (parent.searchCheck(component.getModule().getName())) {
                continue;
            }

            component.setX(x + 5.2f);
            component.setY((float) (y + headerHeight + offset + animation.getValue()));
            component.setWidth(width - 10.4f);

            float baseHeight = 15f;
            float extraHeight = 0;
            if (component.getAnimation().getValue() > 0.01f) {
                for (Component comp : component.getComponents()) {
                    float visibleProgress = MathHelper.clamp(comp.getAlphaAnimSetting().getValue(), 0f, 1f);
                    if (comp.isVisible() || visibleProgress > 0f) {
                        extraHeight += comp.getHeight() * visibleProgress;
                    }
                }
            }
            component.setHeight(baseHeight + (extraHeight * (float) component.getAnimation().getValue()));

            Scissor.setFromComponentCoordinates(x, y + headerHeight, width, height - headerHeight - 4);
            component.render(matrixStack, mouseX, mouseY, partialTicks);
            Scissor.setFromComponentCoordinates(x, y + headerHeight, width, height - headerHeight - 4);

            offset += component.getHeight() + 0.75f;
        }
        maxScroll = Math.max(0, offset - (height - headerHeight - 8));
        scrollbarAnim.run(maxScroll > 0f);

        if (maxScroll > 0 || scrollbarAnim.getValue() > 0.01f) {
            float viewportHeight = height - headerHeight - 8;
            float safeOffset = Math.max(offset, viewportHeight);
            float scrollbarHeight = MathHelper.clamp((viewportHeight / safeOffset) * viewportHeight, 10, viewportHeight);
            float scrollbarY = y + headerHeight;
            if (maxScroll > 0) {
                scrollbarY += (-animation.getValue() / maxScroll) * (height - headerHeight - scrollbarHeight - 8);
            }
            float scrollAnim = scrollbarAnim.getValue();
            float barWidth = 2f * scrollAnim;
            float barCenterX = x + width - 3f;
            float barX = barCenterX - (barWidth / 2f);
            int barAlpha = (int) (80 * alphaRatio * scrollAnim);
            DrawUtil.drawRound(barX, scrollbarY, barWidth, scrollbarHeight, 1f, ColorProvider.setAlpha(ColorProvider.rgba(255, 255, 255, 255), barAlpha));
        }

        Scissor.unset();
        Scissor.pop();
    }

    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (HoverUtil.isHovered(mouseX, mouseY, x, y + 20, width, height - 20)) {
            for (ModuleComponent moduleComponent : moduleComponents) {
                if (!parent.searchCheck(moduleComponent.getModule().getName())) {
                    moduleComponent.mouseClicked(mouseX, mouseY, button);
                }
            }
        }
    }

    public void mouseReleased(double mouseX, double mouseY, int button) {
        for (ModuleComponent moduleComponent : moduleComponents) {
            if (!parent.searchCheck(moduleComponent.getModule().getName())) {
                moduleComponent.mouseReleased(mouseX, mouseY, button);
            }
        }
    }

    public void mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (HoverUtil.isHovered(mouseX, mouseY, x, y, width, height)) {
            scroll += (float) (verticalAmount * 30f);
            clampScroll();
        }
    }

    public void keyPressed(int keyCode, int scanCode, int modifiers) {
        for (ModuleComponent moduleComponent : moduleComponents) {
            moduleComponent.keyPressed(keyCode, scanCode, modifiers);
        }
    }
}