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
    private boolean headerVisible = true;
    private boolean backgroundVisible = true;

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

    public void setCompactMode(boolean compactMode) {
        this.headerVisible = !compactMode;
        this.backgroundVisible = !compactMode;
    }

    public void setHeaderVisible(boolean headerVisible) {
        this.headerVisible = headerVisible;
    }

    public void setBackgroundVisible(boolean backgroundVisible) {
        this.backgroundVisible = backgroundVisible;
    }

    public float getContentTop() {
        return headerVisible ? y + 20 : y;
    }

    public float getContentBottom() {
        return headerVisible ? y + height - 4 : y + height - 4;
    }

    public void render(MatrixStack matrixStack, int mouseX, int mouseY, float partialTicks) {
        animationAlpha.setValue(1);
        float alpha = Math.min(255 * animationAlpha.getValue(), 255);
        float alphaRatio = alpha / 255f;
        float cornerRadius = 8f;
        float headerHeight = 20f;

        if (backgroundVisible) {
            if (tech.onetap.module.list.render.Optimization.shouldDisableClickGuiBlur()) {
                int optColor = ColorProvider.rgba(15, 15, 15, (int) (255 * alphaRatio));
                DrawUtil.drawRound(x, y, width, height, cornerRadius, optColor);
            } else {
                DrawUtil.drawRoundBlur(x, y, width, height, cornerRadius, ColorProvider.rgba(75, 75, 75, (int) (255 * alphaRatio)), 20f);
                int panelColor = ColorProvider.rgba(14, 14, 16, (int) (130 * alphaRatio));
                DrawUtil.drawRound(x, y, width, height, cornerRadius, panelColor);
            }
        }

        if (headerVisible) {
            String title = category.name();
            String capitalizedTitle = title.substring(0, 1).toUpperCase() + title.substring(1).toLowerCase();
            float titleWidth = Fonts.SFREGULAR.get().getWidth(capitalizedTitle, 8.5f);
            DrawUtil.drawText(Fonts.SFREGULAR.get(), capitalizedTitle, x + width / 2f - titleWidth / 2f, y + 6f, ColorProvider.rgba(255, 255, 255, alpha), 8.5f);
        }

        float offset = 0;
        clampScroll();
        animation.run(scroll);

        Scissor.push();
        float contentTop = getContentTop();
        float contentHeight = headerVisible ? height - headerHeight - 4 : height - 4;
        Scissor.setFromComponentCoordinates(x, contentTop, width, contentHeight);

        for (ModuleComponent component : moduleComponents) {
            if (parent.searchCheck(component.getModule().getName())) {
                continue;
            }

            component.setX(x + 5.2f);
            component.setY((float) (contentTop + offset + animation.getValue()));
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

            Scissor.setFromComponentCoordinates(x, contentTop, width, contentHeight);
            component.render(matrixStack, mouseX, mouseY, partialTicks);
            Scissor.setFromComponentCoordinates(x, contentTop, width, contentHeight);

            offset += component.getHeight() + 0.75f;
        }
        maxScroll = Math.max(0, offset - (contentHeight - 4));
        scrollbarAnim.run(maxScroll > 0f);

        if (maxScroll > 0 || scrollbarAnim.getValue() > 0.01f) {
            float viewportHeight = contentHeight - 4;
            float safeOffset = Math.max(offset, viewportHeight);
            float scrollbarHeight = MathHelper.clamp((viewportHeight / safeOffset) * viewportHeight, 10, viewportHeight);
            float scrollbarY = contentTop;
            if (maxScroll > 0) {
                scrollbarY += (-animation.getValue() / maxScroll) * (contentHeight - scrollbarHeight - 4);
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
        float contentTop = getContentTop();
        float contentHeight = headerVisible ? height - 20 : height;
        if (HoverUtil.isHovered(mouseX, mouseY, x, contentTop, width, contentHeight)) {
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
