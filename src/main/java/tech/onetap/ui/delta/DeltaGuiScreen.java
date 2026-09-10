package tech.onetap.ui.delta;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import org.joml.Vector4f;
import tech.onetap.Onetap;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.settings.impl.ThemeManager;
import tech.onetap.util.render.ColorUtil;
import tech.onetap.util.render.Delta2DHolder;
import tech.onetap.util.render.DeltaAnimation;
import tech.onetap.util.render.DeltaEasing;
import tech.onetap.util.render.DeltaThemeInfo;
import tech.onetap.util.render.ScaleUtil;
import tech.onetap.util.render.ScissorUtil;
import tech.onetap.util.render.font.DeltaFonts;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Delta-styled ClickGUI screen ported from DeltaClient (aethereal.ui.screen.GUIScreen):
 * singleton (state persists between opens), forced GUI scale 2, centered 125x270 panels
 * tooltip above the panels, theme picker row below the search field.
 */
public class DeltaGuiScreen extends Screen {
    private static DeltaGuiScreen instance;

    private final StringBuilder searchText = new StringBuilder();
    private final DeltaAnimation tooltipAnim = new DeltaAnimation();
    private final List<DeltaGuiPanel> panels = new ArrayList<>();
    private final List<ThemeManager.ThemePreset> pickerThemes = new ArrayList<>();
    private final List<Vector4f> pickerRects = new ArrayList<>();
    private boolean searchFocused;
    private String lastTooltip;
    private float searchX;
    private float searchY;

    public DeltaGuiScreen() {
        super(Text.literal("Click Gui"));
        for (ModuleCategory category : ModuleCategory.values()) {
            this.panels.add(new DeltaGuiPanel(category));
        }
    }

    /**
     * Singleton accessor — panel expand/scroll state survives closing the GUI.
     */
    public static DeltaGuiScreen getInstance() {
        if (instance == null) {
            instance = new DeltaGuiScreen();
        }
        return instance;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        MinecraftClient mc = MinecraftClient.getInstance();
        double scaledMouseX = DeltaMath.scaleToGui2(mouseX, mc);
        double scaledMouseY = DeltaMath.scaleToGui2(mouseY, mc);
        ScaleUtil.pushScale2(context);
        double totalWidth = this.panels.stream()
                .mapToDouble(panel -> panel.getBounds().z)
                .sum();
        float size = (this.panels.size() - 1) * 8.0f;
        int windowWidth = mc.getWindow().getScaledWidth();
        float rowWidth = size + ((float) totalWidth);
        float left = (windowWidth - rowWidth) * 0.5f;
        float nextX = left;
        float topY = 0.0f;
        List<Module> modules = Onetap.getInstance().getModuleStorage().getModules();
        for (DeltaGuiPanel panel : this.panels) {
            Vector4f bounds = panel.getBounds();
            panel.setModules(modules.stream()
                    .filter(module -> module.getCategory() == panel.getCategory()
                            && module.getName().toLowerCase().contains(searchText.toString().toLowerCase()))
                    .sorted(Comparator.comparing(Module::getName, String.CASE_INSENSITIVE_ORDER))
                    .toList());
            bounds.x = nextX;
            bounds.y = (mc.getWindow().getScaledHeight() - bounds.w) * 0.5f;
            topY = bounds.y;
            panel.getOpenAnimation().tick(true);
            panel.getOpenAnimation().update(0.0f, 1.0f, 0.3f, DeltaEasing.SINE_IN, delta);
            panel.render(context, (int) scaledMouseX, (int) scaledMouseY, delta);
            nextX += bounds.z + 8.0f;
        }
        for (DeltaGuiPanel panel : this.panels) {
            panel.renderColorPickers(context, scaledMouseX, scaledMouseY, delta);
        }
        float panelBottom = this.panels.getFirst().getBounds().w;
        MatrixStack matrices = context.getMatrices();
        float animValue = this.panels.getFirst().getOpenAnimation().getAnimationValue();
        float backEase = DeltaEasing.BACK_OUT.ease(animValue);
        float expoEase = DeltaEasing.EXPO_OUT.ease(animValue);
        float centerX = (0.5f * rowWidth) + left;
        float panelTop = panelBottom + topY;
        float searchY = 12.0f + panelTop + 10.0f;
        float searchAnimY = ((1.0f - expoEase) * 14.0f) + searchY;
        float searchScale = (0.15f * backEase) + 0.85f;
        matrices.push();
        matrices.translate(centerX, searchAnimY, 0.0f);
        matrices.scale(searchScale, searchScale, 1.0f);
        matrices.translate(-centerX, -searchY, 0.0f);
        renderSearchField(context, centerX, panelTop, (int) scaledMouseX, (int) scaledMouseY);
        matrices.pop();
        renderThemePicker(context, centerX, this.searchY + 26.0f, scaledMouseX, scaledMouseY, animValue, backEase, expoEase);
        renderTooltip(context.getMatrices(), centerX, topY, delta);
        ScaleUtil.popScale(context);
    }

    private void renderSearchField(DrawContext context, float centerX, float panelBottom, int mouseX, int mouseY) {
        MatrixStack matrices = context.getMatrices();
        var draw = Delta2DHolder.get();
        var fonts = DeltaFonts.ONEST_REGULAR.get();
        float x = centerX - 50.0f;
        float y = panelBottom + 12.0f;
        draw.drawBlur(matrices, x, y, 100.0f, 20.0f, 6.0f,
                ColorUtil.applyAlphaToColor(ColorUtil.lerpColor(DeltaThemeInfo.BACKGROUND_GUI.resolve(),
                        DeltaThemeInfo.PRIMARY.resolve(), 0.05f), 0.784f));
        draw.drawOutline(matrices, x, y, 100.0f, 20.0f, 6.0f, 0.5f,
                DeltaThemeInfo.OUTLINE_MEDIUM.resolve());
        String text = searchText.toString();
        float textY = (y + ((20.0f - fonts.getHeight(7.0f)) / 2.0f)) - 0.5f;
        this.searchX = x;
        this.searchY = y;
        if (text.isEmpty() && !searchFocused) {
            fonts.drawText(matrices, "Поиск по модулям", x + 6.0f, textY, 7.0f,
                    DeltaThemeInfo.TEXT_DISABLED.resolve());
        } else {
            float caretBlink = (float) Math.sin(System.currentTimeMillis() / 150.0) * 0.5f + 0.5f;
            ScissorUtil.push(matrices, x, y, 100.0f, 20.0f);
            fonts.drawText(matrices, text, x + 6.0f, textY, 7.0f, DeltaThemeInfo.TEXT.resolve());
            if (searchFocused && caretBlink > 0.5f) {
                draw.drawRounded(matrices, x + 6.0f + fonts.getWidth(text, 7.0f), y + 5.0f, 0.5f,
                        20.0f - 10.0f, 0.0f, ColorUtil.applyAlphaToColor(0xFFFFFF, 0.7f));
            }
            ScissorUtil.pop(matrices);
        }
    }

    private void renderThemePicker(DrawContext context, float centerX, float y, double mouseX, double mouseY,
                                   float animValue, float backEase, float expoEase) {
        MatrixStack matrices = context.getMatrices();
        var draw = Delta2DHolder.get();
        var fonts = DeltaFonts.ONEST_REGULAR.get();
        ThemeManager manager = ThemeManager.getInstance();

        this.pickerThemes.clear();
        this.pickerThemes.addAll(ThemeManager.DEFAULT_THEMES);
        this.pickerThemes.addAll(manager.getCustomThemes());

        float size = 11.0f;
        float gap = 5.0f;
        float totalWidth = (this.pickerThemes.size() * size) + ((this.pickerThemes.size() - 1) * gap);
        float startX = centerX - (totalWidth / 2.0f);
        float fade = DeltaEasing.EXPO_OUT.ease(animValue);
        String activeName = manager.getActivePreset() == null ? null : manager.getActivePreset().name();
        String hoveredName = null;

        this.pickerRects.clear();
        matrices.push();
        matrices.translate(centerX, y + (size / 2.0f) + ((1.0f - expoEase) * 8.0f), 0.0f);
        float scale = (0.15f * backEase) + 0.85f;
        matrices.scale(scale, scale, 1.0f);
        matrices.translate(-centerX, -(y + (size / 2.0f)), 0.0f);

        for (int i = 0; i < this.pickerThemes.size(); i++) {
            ThemeManager.ThemePreset preset = this.pickerThemes.get(i);
            float x = startX + (i * (size + gap));
            this.pickerRects.add(new Vector4f(x, y, size, size));
            boolean active = preset.name().equals(activeName);
            boolean hover = DeltaMath.isHovered(mouseX, mouseY, x, y, size, size);
            if (hover) hoveredName = preset.name();

            draw.drawRounded(matrices, x, y, size, size, size / 2.0f,
                    ColorUtil.applyAlphaToColor(preset.color1(), fade));
            draw.drawRounded(matrices, x + 3.0f, y + 3.0f, size - 6.0f, size - 6.0f, (size - 6.0f) / 2.0f,
                    ColorUtil.applyAlphaToColor(preset.color2(), fade));

            if (active || hover) {
                int outline = ColorUtil.applyAlphaToColor(ColorUtil.convertToARGB(255, 255, 255, 255),
                        (active ? 0.85f : 0.35f) * fade);
                draw.drawOutline(matrices, x - 1.0f, y - 1.0f, size + 2.0f, size + 2.0f,
                        (size + 2.0f) / 2.0f, 0.5f, outline);
            }
        }

        if (hoveredName != null) {
            float textWidth = fonts.getWidth(hoveredName, 6.5f);
            fonts.drawText(matrices, hoveredName, centerX - (textWidth / 2.0f), y + size + 3.5f, 6.5f,
                    ColorUtil.applyAlphaToColor(DeltaThemeInfo.TEXT.resolve(), fade));
        }
        matrices.pop();
    }

    private void renderTooltip(MatrixStack matrices, float centerX, float panelTop, float delta) {
        Module hovered = this.panels.stream()
                .map(panel -> panel.getHovered() == null ? null : panel.getHovered().module)
                .filter(module -> module != null && module.getDesc() != null && !module.getDesc().isEmpty())
                .findFirst().orElse(null);
        if (hovered != null && !hovered.getDesc().equals(this.lastTooltip)) {
            this.lastTooltip = hovered.getDesc();
            this.tooltipAnim.setCurrentValue(0.0f);
        }
        this.tooltipAnim.update(0.0f, 1.0f, 0.3f, DeltaEasing.SINE_IN_OUT, delta);
        this.tooltipAnim.tick(hovered != null);
        float fade = DeltaEasing.EXPO_OUT.ease(this.tooltipAnim.getAnimationValue());
        var fonts = DeltaFonts.ONEST_REGULAR.get();
        if (fade > 0.0f && this.lastTooltip != null) {
            float x = centerX - (fonts.getWidth(this.lastTooltip, 10.0f) / 2.0f);
            float y = ((panelTop - fonts.getHeight(10.0f)) - 8.0f) + ((1.0f - fade) * 4.0f);
            fonts.drawText(matrices, this.lastTooltip, x + 0.5f, y + 0.5f, 10.0f,
                    ColorUtil.applyAlphaToColor(ColorUtil.convertToARGB(0, 0, 0, 255), 0.5f * fade));
            fonts.drawText(matrices, this.lastTooltip, x, y, 10.0f,
                    ColorUtil.applyAlphaToColor(ColorUtil.convertToARGB(255, 255, 255, 255), fade));
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        MinecraftClient mc = MinecraftClient.getInstance();
        double scaledX = DeltaMath.scaleToGui2(mouseX, mc);
        double scaledY = DeltaMath.scaleToGui2(mouseY, mc);
        if (DeltaMath.isHovered(scaledX, scaledY, this.searchX, this.searchY, 100.0f, 20.0f)) {
            this.searchFocused = true;
            return true;
        }
        for (int i = 0; i < this.pickerRects.size(); i++) {
            Vector4f rect = this.pickerRects.get(i);
            if (DeltaMath.isHovered(scaledX, scaledY, rect.x, rect.y, rect.z, rect.w)) {
                ThemeManager.ThemePreset preset = this.pickerThemes.get(i);
                ThemeManager manager = ThemeManager.getInstance();
                manager.getCurrentTheme().setColors(preset.color1(), preset.color2());
                manager.saveThemes(manager.getCustomThemes(), preset.name());
                return true;
            }
        }
        if (this.searchFocused) {
            this.searchFocused = false;
        }
        if (this.panels.stream().anyMatch(panel -> panel.onMouseClick(scaledX, scaledY, button))) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        MinecraftClient mc = MinecraftClient.getInstance();
        double scaledX = DeltaMath.scaleToGui2(mouseX, mc);
        double scaledY = DeltaMath.scaleToGui2(mouseY, mc);
        if (this.panels.stream().anyMatch(panel -> panel.onMouseRelease(scaledX, scaledY, button))) {
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        MinecraftClient mc = MinecraftClient.getInstance();
        double scaledX = DeltaMath.scaleToGui2(mouseX, mc);
        double scaledY = DeltaMath.scaleToGui2(mouseY, mc);
        for (DeltaGuiPanel panel : this.panels) {
            Vector4f bounds = panel.getBounds();
            if (DeltaMath.isHovered(scaledX, scaledY, bounds.x, bounds.y, bounds.z, bounds.w)) {
                return panel.onMouseScroll(scaledX, scaledY, verticalAmount);
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 70 && (modifiers & 2) != 0) {
            this.searchFocused = !this.searchFocused;
            return true;
        }
        if (this.searchFocused) {
            if (keyCode == 259) {
                if (!searchText.isEmpty()) {
                    searchText.deleteCharAt(searchText.length() - 1);
                }
                return true;
            }
            if (keyCode == 256) {
                this.searchFocused = false;
                return true;
            }
            return false;
        }
        if (this.panels.stream().anyMatch(panel -> panel.onKeyPress(keyCode, scanCode, modifiers))) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        if (this.searchFocused) {
            searchText.append(character);
            return true;
        }
        if (this.panels.stream().anyMatch(panel -> panel.onCharTyped(character, modifiers))) {
            return true;
        }
        return super.charTyped(character, modifiers);
    }

    @Override
    public void close() {
        super.close();
        this.searchFocused = false;
        this.panels.forEach(panel -> panel.getOpenAnimation().setCurrentValue(0.0f));
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
