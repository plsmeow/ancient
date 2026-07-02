package tech.onetap.ui;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.Getter;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;
import tech.onetap.module.Module;
import tech.onetap.module.settings.*;
import tech.onetap.ui.component.Component;
import tech.onetap.ui.component.impl.*;
import tech.onetap.util.cursor.CursorManager;
import tech.onetap.util.render.helper.HoverUtil;
import tech.onetap.util.render.math.Animation;
import tech.onetap.util.render.math.Easing;
import tech.onetap.util.render.msdf.Fonts;
import tech.onetap.util.render.providers.ColorProvider;
import tech.onetap.util.render.renderers.DrawUtil;

@Getter
public class ModuleComponent extends Component {
    private final Module module;
    private final Panel panel;

    private final Animation animation = new Animation(Easing.QUINTIC_OUT, 320);
    private final Animation hoverAnim = new Animation(Easing.QUINTIC_OUT, 300);
    private final Animation enabledAnim = new Animation(Easing.QUINTIC_OUT, 400);

    public boolean open;
    private boolean isHovered;
    private boolean binding;

    private final ObjectArrayList<Component> components = new ObjectArrayList<>();

    public ModuleComponent(Module module, Panel panel) {
        this.module = module;
        this.panel = panel;
        for (Setting setting : module.getSettings()) {
            switch (setting) {
                case BooleanSetting option -> components.add(new BooleanComponent(option));
                case ModeSetting option -> components.add(new ModeComponent(option));
                case ModeListSetting option -> components.add(new ModeListComponent(option));
                case SliderSetting option -> components.add(new SliderComponent(option));
                case BindSetting option -> components.add(new BindComponent(option));
                case ThemeSetting option -> components.add(new ThemeComponent(option));
                case ColorSetting option -> components.add(new ColorPickerComponent(option));
                default -> {}
            }
        }

        if (module.getName().equals("Interface")) {
            components.add(new ThemeActionComponent(this));
        }
    }

    public void render(MatrixStack matrixStack, int mouseX, int mouseY, float partialTicks) {
        isHovered = HoverUtil.isHovered(mouseX, mouseY, x, y, width, 15);

        hoverAnim.run(isHovered);
        animation.run(open);
        enabledAnim.run(module.isEnabled());

        if (HoverUtil.isHovered(mouseX, mouseY, x, y, width, 15)) CursorManager.requestHand();

        float alpha = Math.max(Math.min(panel.getAnimationAlpha().getValue(), 1), 0);

        int textColor = ColorProvider.interpolateColor(
                ColorProvider.rgba(170, 170, 170, (int)(255 * alpha)),
                ColorProvider.rgba(255, 255, 255, (int)(255 * alpha)),
                enabledAnim.getValue()
        );

        float highlightProgress = Math.max(hoverAnim.getValue(), enabledAnim.getValue());
        int outlineAlpha = (int) ((25 + (40 * highlightProgress)) * alpha);

        int outlineColor = ColorProvider.rgba(255, 255, 255, outlineAlpha);
        int innerColor = ColorProvider.rgba(44, 44, 44, (int)(140 * alpha));

        float currentHeight = 15f + ((height - 15f) * animation.getValue());

        DrawUtil.drawRound(x - 0.5f, y - 0.5f, width + 1f, currentHeight + 0.5f, 3.5f, outlineColor);
        DrawUtil.drawRoundBlur(x, y, width, currentHeight - 0.5f, 3f, innerColor, 20f);

        if (binding) {
            DrawUtil.drawText(Fonts.SFREGULAR.get(), "Нажмите клавишу...", x + width / 2f - Fonts.SFREGULAR.get().getWidth("Нажмите клавишу...", 7.5f) / 2f, y + 3.5f, ColorProvider.rgba(255, 255, 255, (int)(255 * alpha)), 7.5f);
        } else {
            DrawUtil.drawText(Fonts.SFREGULAR.get(), module.getName(), x + 4.5f, y + 3.75f, textColor, 7.5f);

            if (!components.isEmpty()) {
                DrawUtil.drawText(Fonts.SFMEDIUM.get(), "...", x + width - 12, y + 1.75f, textColor, 7.5f);
            }
        }

        if (animation.getValue() > 0.01f) {
            float compY = y + 13.5f;
            float panelTop = panel.getY() + 20;
            float panelBottom = panel.getY() + panel.getHeight() - 4;
            float settingsY = y + 15;
            float settingsBottom = y + currentHeight;

            float intersectY = Math.max(settingsY, panelTop);
            float intersectBottom = Math.min(settingsBottom, panelBottom);
            float intersectHeight = Math.max(0, intersectBottom - intersectY);

            float darkHeight = currentHeight - 15f;
            if (darkHeight > 0) {
                DrawUtil.drawRound(x + 1f, y + 15, width - 2f, darkHeight, 0f, ColorProvider.rgba(0, 0, 0, (int)(30 * alpha * animation.getValue())));
            }

            for (Component component : components) {
                component.getAlphaAnim().setValue(Math.min(panel.getAnimationAlpha().getValue(), 1));
                component.getAlphaAnimSetting().run(component.isVisible());

                float visibleProgress = MathHelper.clamp(component.getAlphaAnimSetting().getValue(), 0f, 1f);
                if (component.isVisible() || visibleProgress > 0) {
                    component.setX(x);
                    component.setY(compY);
                    component.setWidth(width - 4);

                    tech.onetap.util.render.math.Scissor.push();
                    tech.onetap.util.render.math.Scissor.setFromComponentCoordinates(x, intersectY, width, intersectHeight);

                    component.render(matrixStack, mouseX, mouseY, partialTicks);

                    tech.onetap.util.render.math.Scissor.unset();
                    tech.onetap.util.render.math.Scissor.pop();

                    compY += component.getHeight() * visibleProgress;
                }
            }
        }
    }

    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (isHovered(mouseX, mouseY, 15)) {
            if (button == 0) module.setEnabled(!module.isEnabled());
            if (button == 1 && !components.isEmpty()) open = !open;
            if (button == 2) binding = !binding;
        }

        if (open) {
            for (Component component : components) {
                if (component.isVisible() && component.getAlphaAnimSetting().getValue() > 0.5f) {
                    component.mouseClicked(mouseX, mouseY, button);
                }
            }
        }
    }

    public void mouseReleased(double mouseX, double mouseY, int button) {
        if (open) {
            for (Component component : components) {
                component.mouseReleased(mouseX, mouseY, button);
            }
        }
    }

    public void keyPressed(int keyCode, int scanCode, int modifiers) {
        if (binding) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_DELETE) {
                module.setKey(-1);
            } else {
                module.setKey(keyCode);
            }
            binding = false;
            return;
        }

        if (open) {
            for (Component component : components) {
                component.keyPressed(keyCode, scanCode, modifiers);
            }
        }
    }

    private boolean isHovered(double mouseX, double mouseY, float heightCheck) {
        return HoverUtil.isHovered(mouseX, mouseY, x, y, width, heightCheck);
    }

    public static class ThemeActionComponent extends Component {
        private final ModuleComponent parent;
        private final Animation hoverAnim = new Animation(Easing.QUINTIC_OUT, 300);

        public ThemeActionComponent(ModuleComponent parent) {
            this.parent = parent;
        }

        @Override
        public void render(MatrixStack matrixStack, int mouseX, int mouseY, float partialTicks) {
            float btnX = x + 2f;
            float btnY = y + 1f;
            float btnW = width - 4f;
            float btnH = 14f;

            boolean isHovered = HoverUtil.isHovered(mouseX, mouseY, btnX, btnY, btnW, btnH);
            hoverAnim.run(isHovered);
            if (isHovered) CursorManager.requestHand();

            float alpha = (float) getAlphaAnim().getValue();

            int outlineAlpha = (int) ((25 + (40 * hoverAnim.getValue())) * alpha);
            int outlineColor = ColorProvider.rgba(255, 255, 255, outlineAlpha);
            int innerColor = ColorProvider.rgba(44, 44, 44, (int)(140 * alpha));

            DrawUtil.drawRound(btnX - 0.5f, btnY - 0.5f, btnW + 1f, btnH + 0.5f, 3.5f, outlineColor);
            DrawUtil.drawRoundBlur(btnX, btnY, btnW, btnH - 0.5f, 3f, innerColor, 20f);

            int textColor = ColorProvider.rgba(255, 255, 255, (int)(255 * alpha));
            DrawUtil.drawText(Fonts.SFREGULAR.get(), "Открыть менеджер тем", btnX + 3.5f, btnY + 3.25f, textColor, 7.35f);
        }

        @Override
        public void mouseClicked(double mouseX, double mouseY, int button) {
            float btnX = x + 2f;
            float btnY = y + 1f;
            float btnW = width - 4f;
            float btnH = 14f;

            if (HoverUtil.isHovered(mouseX, mouseY, btnX, btnY, btnW, btnH) && button == 0) {
                ThemeManagerWindow tm = parent.getPanel().getParent().getThemeManager();
                tm.setOpen(!tm.isOpen());
            }
        }

        @Override
        public void mouseReleased(double mouseX, double mouseY, int button) {}

        @Override
        public void keyPressed(int keyCode, int scanCode, int modifiers) {}

        @Override
        public float getHeight() {
            return 16f;
        }

        @Override
        public boolean isVisible() {
            return true;
        }
    }
}