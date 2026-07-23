package tech.onetap.ui;

import lombok.Getter;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import tech.onetap.Onetap;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.list.render.ClickGui;
import tech.onetap.ui.component.SearchField;
import tech.onetap.util.IMinecraft;
import tech.onetap.util.cursor.CursorManager;
import tech.onetap.util.render.helper.HoverUtil;
import tech.onetap.util.render.math.Easing;
import tech.onetap.util.render.math.Scissor;
import tech.onetap.util.render.msdf.Fonts;
import tech.onetap.util.render.providers.ColorProvider;
import tech.onetap.util.render.renderers.DrawUtil;

import java.util.ArrayList;
import java.util.List;

@Getter
public class ClickGuiFrame extends Screen implements IMinecraft {

    private final List<Panel> panels = new ArrayList<>();
    private final SearchField searchField;

    // Менеджер тем
    private final ThemeManagerWindow themeManager;

    public ClickGuiFrame() {
        super(Text.of("Avalora Frame"));
        searchField = new SearchField("Search...");
        for (ModuleCategory category : ModuleCategory.values()) {
            panels.add(new Panel(category, this));
        }
        themeManager = new ThemeManagerWindow(this);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        CursorManager.reset();
        CursorManager.resetIBeam();
        CursorManager.resetClick();

        int windowWidth = mc.getWindow().getScaledWidth();
        int windowHeight = mc.getWindow().getScaledHeight();

        boolean panelMode = isPanelMode();
        float panelWidth = 105f;
        float spacing = 4f;
        float panelHeight = 240f;
        float panelBottomY;

        if (!panelMode) {
            // ==========================================================
            // DROPDOWN MODE — 5 отдельных панелей рядом
            // ==========================================================
            float panelTotalWidth = panels.size() * (panelWidth + spacing) - spacing;
            float startX = (windowWidth - panelTotalWidth) / 2f;
            float panelY = (windowHeight - panelHeight) / 2f - 20;
            panelBottomY = panelY + panelHeight;

            for (int i = 0; i < panels.size(); i++) {
                Panel panel = panels.get(i);
                panel.getAnimationAlpha().setDuration(650);
                panel.getAnimationAlpha().run(1);
                panel.getAnimationAlpha().setEasing(Easing.QUINTIC_OUT);

                panel.setX(startX + i * (panelWidth + spacing));
                panel.setY(panelY);
                panel.setWidth(panelWidth);
                panel.setHeight(panelHeight);
                panel.setHeaderVisible(true);
                panel.setBackgroundVisible(true);

                panel.render(context.getMatrices(), mouseX, mouseY, delta);
            }
        } else {
            // ==========================================================
            // PANEL MODE — 1 общая панель с 5 колонками
            // ==========================================================
            float containerHeaderHeight = 20f;
            float containerWidth = panels.size() * (panelWidth + spacing) - spacing;
            float containerHeight = panelHeight;
            float containerX = (windowWidth - containerWidth) / 2f;
            float containerY = (windowHeight - containerHeight) / 2f - 20;
            panelBottomY = containerY + containerHeight;

            float alphaRatio = 1f;
            float cornerRadius = 8f;

            if (tech.onetap.module.list.render.Optimization.shouldDisableClickGuiBlur()) {
                int optColor = ColorProvider.rgba(15, 15, 15, (int) (255 * alphaRatio));
                DrawUtil.drawRound(containerX, containerY, containerWidth, containerHeight, cornerRadius, optColor);
            } else {
                DrawUtil.drawRoundBlur(containerX, containerY, containerWidth, containerHeight, cornerRadius, ColorProvider.rgba(75, 75, 75, (int) (255 * alphaRatio)), 20f);
                int panelColor = ColorProvider.rgba(14, 14, 16, (int) (130 * alphaRatio));
                DrawUtil.drawRound(containerX, containerY, containerWidth, containerHeight, cornerRadius, panelColor);
            }

            // Тонкая разделительная линия под шапкой
            DrawUtil.drawRound(containerX + 4, containerY + containerHeaderHeight - 0.5f, containerWidth - 8, 0.5f, 0, ColorProvider.rgba(255, 255, 255, 18));

            // 5 колонок внутри контейнера
            for (int i = 0; i < panels.size(); i++) {
                Panel panel = panels.get(i);
                panel.getAnimationAlpha().setDuration(650);
                panel.getAnimationAlpha().run(1);
                panel.getAnimationAlpha().setEasing(Easing.QUINTIC_OUT);

                float colX = containerX + i * (panelWidth + spacing);
                float colY = containerY + containerHeaderHeight;
                float colH = containerHeight - containerHeaderHeight - 4;

                panel.setX(colX);
                panel.setY(colY);
                panel.setWidth(panelWidth);
                panel.setHeight(colH);
                panel.setHeaderVisible(false);
                panel.setBackgroundVisible(false);

                // Заголовок колонки рисуем внутри шапки контейнера
                String title = panel.getCategory().name();
                String capitalizedTitle = title.substring(0, 1).toUpperCase() + title.substring(1).toLowerCase();
                float titleW = Fonts.SFREGULAR.get().getWidth(capitalizedTitle, 8.5f);
                DrawUtil.drawText(Fonts.SFREGULAR.get(), capitalizedTitle, colX + panelWidth / 2f - titleW / 2f, containerY + 6f, ColorProvider.rgba(255, 255, 255, 255), 8.5f);

                // Скруглённые клиппинг-зоны колонок
                Scissor.push();
                Scissor.setFromComponentCoordinates(colX, colY, panelWidth, colH);
                panel.render(context.getMatrices(), mouseX, mouseY, delta);
                Scissor.unset();
                Scissor.pop();
            }
        }

        // Поле поиска под панелями
        float searchW = 140;
        float searchH = 18;
        float searchX = windowWidth / 2f - searchW / 2f;
        float searchY = panelBottomY + 15;
        searchField.setBounds(searchX, searchY, searchW, searchH);
        searchField.render(context, mouseX, mouseY, delta);

        // Менеджер тем слева
        themeManager.setX(20);
        themeManager.setY(panelBottomY - panelHeight);
        themeManager.setHeight(panelHeight);
        themeManager.render(context.getMatrices(), mouseX, mouseY, delta);

        // Описание модуля по центру экрана
        for (Panel panel : panels) {
            boolean isMouseInPanel = HoverUtil.isHovered(mouseX, mouseY, panel.getX(), panel.getY(), panel.getWidth(), panel.getHeight());

            for (ModuleComponent component : panel.getModuleComponents()) {
                if (component.isHovered() && isMouseInPanel && searchField.isEmpty()) {
                    String desc = component.getModule().getDesc();
                    if (desc != null && !desc.isEmpty()) {
                        float textWidth = Fonts.SFREGULAR.get().getWidth(desc, 8f) + 2;
                        DrawUtil.drawRound(windowWidth / 2f - textWidth / 2f - 2, windowHeight / 2f - 180, textWidth + 8, 14, 0, ColorProvider.rgba(0, 0, 0, 111));
                        DrawUtil.drawText(Fonts.SFREGULAR.get(), desc, windowWidth / 2f - textWidth / 2f, windowHeight / 2f - 176, ColorProvider.rgba(255, 255, 255, 255), 8f);
                    }
                }
            }
        }

        long window = mc.getWindow().getHandle();
        if (CursorManager.shouldBeHand()) GLFW.glfwSetCursor(window, GLFW.glfwCreateStandardCursor(GLFW.GLFW_HAND_CURSOR));
        else if (CursorManager.shouldIBeam()) GLFW.glfwSetCursor(window, GLFW.glfwCreateStandardCursor(GLFW.GLFW_IBEAM_CURSOR));
        else if (CursorManager.shouldClick()) GLFW.glfwSetCursor(window, GLFW.glfwCreateStandardCursor(GLFW.GLFW_POINTING_HAND_CURSOR));
        else GLFW.glfwSetCursor(window, GLFW.glfwCreateStandardCursor(GLFW.GLFW_ARROW_CURSOR));
    }

    private boolean isPanelMode() {
        try {
            return Onetap.getInstance().getModuleStorage().getModules().stream()
                    .filter(m -> m instanceof ClickGui)
                    .map(m -> (ClickGui) m)
                    .findFirst()
                    .map(ClickGui::isPanelMode)
                    .orElse(false);
        } catch (Exception e) {
            return false;
        }
    }

    public boolean searchCheck(String text) {
        return !searchField.isEmpty() && !text.replaceAll(" ", "").toLowerCase().contains(searchField.text.replaceAll(" ", "").toLowerCase());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        themeManager.mouseClicked(mouseX, mouseY, button);
        searchField.mouseClicked(mouseX, mouseY, button);

        if (searchField.isEmpty()) {
            for (Panel panel : panels) {
                if (HoverUtil.isHovered(mouseX, mouseY, panel.getX(), panel.getY(), panel.getWidth(), panel.getHeight())) {
                    panel.mouseClicked(mouseX, mouseY, button);
                }
            }
        } else {
            for (Panel panel : panels) {
                panel.mouseClicked(mouseX, mouseY, button);
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        themeManager.mouseReleased(mouseX, mouseY, button);
        for (Panel panel : panels) {
            panel.mouseReleased(mouseX, mouseY, button);
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        themeManager.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        for (Panel panel : panels) {
            panel.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            long window = mc.getWindow().getHandle();
            GLFW.glfwSetCursor(window, GLFW.glfwCreateStandardCursor(GLFW.GLFW_ARROW_CURSOR));
        }
        searchField.keyPressed(keyCode, scanCode, modifiers);
        for (Panel panel : panels) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                panel.getAnimationAlpha().setValue(0);
                panel.getAnimationAlpha().reset();
            }
            panel.keyPressed(keyCode, scanCode, modifiers);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        searchField.charTyped(chr, modifiers);
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
