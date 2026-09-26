package meow.ancient.ui.celestial;

import meow.ancient.Ancient;
import meow.ancient.module.Module;
import meow.ancient.module.ModuleCategory;
import meow.ancient.module.list.render.ClickGui;
import meow.ancient.module.settings.BindSetting;
import meow.ancient.module.settings.BooleanSetting;
import meow.ancient.module.settings.ColorSetting;
import meow.ancient.module.settings.ModeListSetting;
import meow.ancient.module.settings.ModeSetting;
import meow.ancient.module.settings.Setting;
import meow.ancient.module.settings.SliderSetting;
import meow.ancient.module.settings.impl.Theme;
import meow.ancient.module.settings.impl.ThemeManager;
import meow.ancient.util.KeyUtil;
import meow.ancient.util.base.Instance;
import meow.ancient.util.render.Delta2DHolder;
import meow.ancient.util.render.RenderShaders;
import meow.ancient.util.render.renderers.DrawUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import meow.ancient.util.render.builders.Builder;
import meow.ancient.util.render.builders.states.QuadColorState;
import meow.ancient.util.render.builders.states.QuadRadiusState;
import meow.ancient.util.render.builders.states.SizeState;
import org.joml.Matrix4f;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class CelestialScreen extends Screen {
    private static final ModuleCategory[] LEGACY_ORDER = {
            ModuleCategory.COMBAT, ModuleCategory.MOVEMENT, ModuleCategory.RENDER,
            ModuleCategory.PLAYER, ModuleCategory.MISC
    };

    private static final Identifier FONT_11 = font(11);
    private static final Identifier FONT_12 = font(12);
    private static final Identifier FONT_13 = font(13);
    private static final Identifier FONT_14 = font(14);
    private static final Identifier FONT_15 = font(15);
    private static final Identifier FONT_16 = font(16);
    private static final Identifier FONT_17 = font(17);
    private static final Identifier FONT_21 = font(21);
    private static final Identifier FONT_25 = font(25);

    private static final LegacyNameMetrics LEGACY_NAME_METRICS = LegacyNameMetrics.load();
    private static final Map<String, Integer> LEGACY_SORT_WIDTH_CORRECTIONS = Map.of(
            "Prediction", 2,
            "Anti Levitation", 2,
            "AutoArmor", 1
    );

    private static final int COLUMN_WIDTH = 110;
    private static final int COLUMN_GAP = 18;
    private static final int COLUMN_STEP = COLUMN_WIDTH + COLUMN_GAP;
    private static final int HEADER_HEIGHT = 17;
    private static final int PANEL_HEIGHT = 250;
    private static final int CONFIG_HEIGHT = 168;
    private static final int THEMES_HEIGHT = 250;
    private static final int MODULE_HEIGHT = 18;

    private static final int PANEL_BODY = 0x96FFFFFF;
    private static final int DISABLED_ROW = 0xFFFFFFFF;
    private static final int DISABLED_HOVER = 0xFFFFFFFF;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int BLACK = 0xFF000000;
    private static final int MUTED = 0xFF9A9A9A;

    private final CelestialConfigProfiles profiles;
    private final UiLayoutStore layoutStore;
    private final List<Panel> panels = new ArrayList<>();
    private final List<HitTarget> hitTargets = new ArrayList<>();
    private final Set<String> expandedModules = new HashSet<>();
    private final Set<String> expandedSettings = new HashSet<>();
    private final Map<String, Float> moduleAnimations = new HashMap<>();
    private final Map<String, Float> settingAnimations = new HashMap<>();
    private final Map<String, Float> hoverAnimations = new HashMap<>();
    private final Map<String, Float> toggleAnimations = new HashMap<>();
    private final Map<ModuleCategory, List<Module>> sortedModules = new EnumMap<>(ModuleCategory.class);

    private List<String> cachedProfileNames = List.of();
    private long nextProfileRefreshNanos;
    private boolean profileCacheDirty = true;

    private Panel draggedPanel;
    private double dragOffsetX;
    private double dragOffsetY;
    private SliderSetting draggedSlider;
    private Rect draggedSliderBounds;
    private ColorSetting draggedColor;
    private Rect draggedColorBounds;
    private ColorDragPart draggedColorPart;
    private BindSetting editingBind;
    private Module bindingModule;
    private String configName = "";
    private String selectedProfile = "";
    private boolean editingConfigName;
    private float configScroll;
    private float configTargetScroll;
    private float themesScroll;
    private float themesTargetScroll;
    private String themeName = "";
    private String selectedTheme = "";
    private String editingCustomTheme = null;
    private int themeColorTab = 1;
    private boolean editingThemeName;
    private final ColorSetting themeColor1 = new ColorSetting("Theme Color 1", 0xFFBF68FF);
    private final ColorSetting themeColor2 = new ColorSetting("Theme Color 2", 0xFF110122);
    private String lastProfileClick = "";
    private long lastProfileClickAt;
    private String status = "LMB toggle  RMB settings  MMB bind";
    private long statusVisibleUntilNanos;
    private Tooltip tooltip;
    private float frameAnimationStep = 0.22f;

    private long lastClickTime;
    private double lastClickX;
    private double lastClickY;

    public CelestialScreen() {
        super(Text.literal("Celestial"));
        this.profiles = new CelestialConfigProfiles();
        this.layoutStore = new UiLayoutStore(Paths.get(".options"));

        for (int index = 0; index < LEGACY_ORDER.length; index++) {
            ModuleCategory category = LEGACY_ORDER[index];
            panels.add(new Panel(category, legacyTitle(category), 18 + COLUMN_STEP * index, 10, PANEL_HEIGHT));
        }
        panels.add(new Panel(PanelKind.CONFIGS, null, "Configs", 18 + COLUMN_STEP * 5, 10, CONFIG_HEIGHT));
        panels.add(new Panel(PanelKind.THEMES, null, "Themes", 18 + COLUMN_STEP * 6, 10, THEMES_HEIGHT));

        Theme activeTheme = ThemeManager.getInstance().getCurrentTheme();
        if (activeTheme != null) {
            themeColor1.setValue(activeTheme.color1);
            themeColor2.setValue(activeTheme.color2);
            ThemeManager.ThemePreset activePreset = ThemeManager.getInstance().getActivePreset();
            selectedTheme = activePreset != null ? activePreset.name() : (activeTheme.name != null ? activeTheme.name : "");
            themeName = isCustomTheme(selectedTheme) ? selectedTheme : "";
        }

        applyStoredPanelPositions();
    }

    @Override
    public void removed() {
        super.removed();
        savePanelPositions();
    }

    @Override
    public void close() {
        super.close();
        meow.ancient.util.config.ConfigManager.save("autocfg");
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        if (client == null || client.world == null) {
            super.renderBackground(context, mouseX, mouseY, delta);
            return;
        }
        if (blurEnabled()) {
            applyBlur();
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        hitTargets.clear();
        tooltip = null;
        float animationStep = Math.min(1.0f, Math.max(0.12f, delta * 0.22f));
        frameAnimationStep = animationStep;
        for (Panel panel : panels) {
            panel.scroll += (panel.targetScroll - panel.scroll) * animationStep;
            drawPanel(context, panel, mouseX, mouseY, animationStep);
        }

        if (tooltip != null) drawTooltip(context, tooltip);
        if (System.nanoTime() < statusVisibleUntilNanos) drawPrompt(context, status);
    }

    private void drawPanel(
            DrawContext context,
            Panel panel,
            int mouseX,
            int mouseY,
            float animationStep
    ) {
        if (blurEnabled()) {
            fillLegacyShadow(context, panel.x, panel.y + 1,
                    COLUMN_WIDTH, panel.height + 1, 5.5f, 15.0f, 0x7DFFFFFF);
        }

        SmoothRoundedGui.fillNoise(context, panel.x, panel.y + 1,
                COLUMN_WIDTH, panel.height + 1, 5.5f, PANEL_BODY);

        Rect clip = new Rect(panel.x, panel.y + 4, COLUMN_WIDTH,
                panel.kind == PanelKind.CATEGORY ? 247 : panel.height - 35);
        context.enableScissor(clip.x, clip.y, clip.right(), clip.bottom());
        if (panel.kind == PanelKind.THEMES) {
            drawThemePresets(context, panel, clip, mouseX, mouseY, animationStep);
        } else if (panel.kind == PanelKind.CONFIGS) {
            drawConfigProfiles(context, panel, clip, mouseX, mouseY, animationStep);
        } else {
            Rect content = new Rect(panel.x, panel.y + HEADER_HEIGHT + 2,
                    COLUMN_WIDTH, 231);
            drawModules(context, panel, content, mouseX, mouseY, animationStep);
        }
        context.disableScissor();

        if (panel.kind == PanelKind.CONFIGS) {
            drawConfigControls(context, panel, mouseX, mouseY);
        } else if (panel.kind == PanelKind.THEMES) {
            drawThemeControls(context, panel, mouseX, mouseY);
        }

        drawGradientHeader(context, panel.x, panel.y, COLUMN_WIDTH, 15,
                primaryColor(), shade(secondaryColor(), 0.7f));

        drawCenteredFont(context, panel.title, panel.x + COLUMN_WIDTH / 2.0f + 0.5f,
                panel.y + 4.0f, WHITE, FONT_21, true);
        hitTargets.add(new HitTarget(new Rect(panel.x, panel.y, COLUMN_WIDTH, HEADER_HEIGHT),
                Action.PANEL_HEADER, panel, null, null, null));
    }

    private void drawModules(
            DrawContext context,
            Panel panel,
            Rect viewport,
            int mouseX,
            int mouseY,
            float animationStep
    ) {
        List<Module> modules = sortedModules.computeIfAbsent(panel.category, category ->
                Ancient.getInstance().getModuleStorage().getModules().stream()
                        .filter(m -> m.getCategory() == category)
                        .sorted((first, second) -> {
                            int byWidth = legacyNameWidth(second.getName())
                                    - legacyNameWidth(first.getName());
                            return byWidth != 0 ? byWidth
                                    : second.getName().compareTo(first.getName());
                        })
                        .toList());
        int enabledEndColor = shade(secondaryColor(), 0.7f);

        int y = viewport.y - (int) Math.round(panel.scroll);
        float measuredHeight = 0;
        for (Module module : modules) {
            updateSettingAnimations(module, animationStep);
            float target = expandedModules.contains(module.getName()) ? 1.0f : 0.0f;
            float progress = moduleAnimations.getOrDefault(module.getName(), 0.0f);
            progress += (target - progress) * animationStep;
            if (Math.abs(target - progress) < 0.008f) progress = target;
            moduleAnimations.put(module.getName(), progress);

            int fullSettingsHeight = target > 0.0f || progress > 0.0f
                    ? settingsHeight(module) + 3 : 0;
            int animatedHeight = Math.round(fullSettingsHeight * progress);
            Rect row = new Rect(panel.x + 1, y, COLUMN_WIDTH - 2, MODULE_HEIGHT);
            Rect card = new Rect(row.x + 2, row.y + 2, row.width - 4,
                    Math.max(14, 14 + animatedHeight));
            boolean hovered = row.contains(mouseX, mouseY) && viewport.contains(mouseX, mouseY);
            if (card.intersects(viewport)) {
                if (shadowsEnabled()) {
                    fillLegacyShadow(context, card.x, card.y,
                            card.width + 1, card.height, 0, 5, 0x5A000000);
                }
                if (module.isEnabled()) {
                    drawGradientCard(context, card, primaryColor(), enabledEndColor);
                } else {
                    fillRounded(context, card.x, card.y, card.width, card.height, 1,
                            hovered ? DISABLED_HOVER : DISABLED_ROW);
                }
            }
            if (row.intersects(viewport)) {
                drawModuleRow(context, panel, module, row, viewport, mouseX, mouseY, progress);
            }
            y += MODULE_HEIGHT;
            measuredHeight += MODULE_HEIGHT;

            if (animatedHeight > 0) {
                Rect settingClip = new Rect(viewport.x, y, viewport.width, animatedHeight + 4);
                Rect clipped = settingClip.intersection(viewport);
                if (clipped.height > 0) {
                    context.enableScissor(clipped.x, clipped.y, clipped.right(), clipped.bottom());
                    drawSettings(context, panel, module, clipped, y, mouseX, mouseY);
                    context.disableScissor();
                }
                y += animatedHeight;
                measuredHeight += animatedHeight;
            }
        }

        panel.maxScroll = Math.max(0, measuredHeight - viewport.height);
        panel.targetScroll = clamp(panel.targetScroll, 0, panel.maxScroll);
        panel.scroll = clamp(panel.scroll, 0, panel.maxScroll);
    }

    private void drawModuleRow(
            DrawContext context,
            Panel panel,
            Module module,
            Rect row,
            Rect viewport,
            int mouseX,
            int mouseY,
            float openProgress
    ) {
        boolean hovered = row.contains(mouseX, mouseY) && viewport.contains(mouseX, mouseY);
        float hoverProgress = hoverAnimations.getOrDefault(module.getName(), 0.0f);
        hoverProgress += ((hovered ? 1.0f : 0.0f) - hoverProgress)
                * Math.min(1.0f, frameAnimationStep * 1.35f);
        if (Math.abs((hovered ? 1.0f : 0.0f) - hoverProgress) < 0.008f) {
            hoverProgress = hovered ? 1.0f : 0.0f;
        }
        hoverAnimations.put(module.getName(), hoverProgress);

        String label = bindingModule == module ? "Press a key..." : module.getName();
        String shownLabel = trim(label, row.width - 20, FONT_16);

        drawCenteredFont(context, shownLabel, row.x + row.width - 54.5f, row.y + 3.0f,
                module.isEnabled() ? WHITE : BLACK, FONT_16, module.isEnabled());

        if (hasVisibleSettings(module)) {
            drawModuleArrow(context, row.right() - 6, row.y + 9.0f, openProgress,
                    module.isEnabled() ? WHITE : BLACK);
        }

        Rect hit = row.intersection(viewport);
        if (hit.width > 0 && hit.height > 0) {
            hitTargets.add(new HitTarget(hit, Action.MODULE, panel, module, null, null));
        }
        if (hoverProgress > 0.01f && (tooltip == null || hoverProgress >= tooltip.alpha)) {
            tooltip = new Tooltip(panel.x + COLUMN_WIDTH + 5, row.y + 10.0f / 3.0f,
                    module.getDesc().isBlank() ? module.getName() : module.getDesc(),
                    hoverProgress);
        }
    }

    private void drawSettings(
            DrawContext context,
            Panel panel,
            Module module,
            Rect viewport,
            int y,
            int mouseX,
            int mouseY
    ) {
        for (Setting setting : module.getSettings()) {
            if (!isSettingVisible(setting)) continue;
            y = drawSetting(context, panel, module, setting, y, viewport, mouseX, mouseY);
        }
    }

    private int drawSetting(
            DrawContext context,
            Panel panel,
            Module module,
            Setting setting,
            int y,
            Rect viewport,
            int mouseX,
            int mouseY
    ) {
        int x = panel.x + 3;
        int w = COLUMN_WIDTH - 6;
        String key = settingKey(module, setting);
        int foreground = module.isEnabled() ? WHITE : BLACK;
        int faded = module.isEnabled() ? 0xFFD2D2D2 : 0xFF565656;

        if (setting instanceof BooleanSetting value) {
            Rect row = new Rect(x - 1, y, w + 2, 22);
            drawFontFractional(context, trim(setting.getName(), 72, FONT_14),
                    row.x + 3.0f, row.y + 3.5f, foreground, FONT_14, false);
            Rect toggle = new Rect(row.right() - 22, row.y + 4, 18, 8);
            if (shadowsEnabled()) {
                fillLegacyShadow(context, toggle.x, toggle.y,
                        toggle.width, toggle.height, 0, 6, 0xCD000000);
            }
            if (value.getValue()) {
                drawHorizontalGradient(context,
                        toggle.x, toggle.y, toggle.width, toggle.height, 3.5f,
                        primaryColor(), secondaryColor());
            } else {
                fillRounded(context, toggle.x, toggle.y, toggle.width, toggle.height,
                        4, 0xFF7C939B);
            }
            float target = value.getValue() ? 1.0f : 0.0f;
            float knob = toggleAnimations.getOrDefault(key, target);
            knob += (target - knob) * Math.min(1.0f, frameAnimationStep);
            if (Math.abs(target - knob) < 0.008f) knob = target;
            toggleAnimations.put(key, knob);
            float knobCenter = toggle.x + 4.0f + knob * 10.0f;
            fillLegacyPoint(context, knobCenter, toggle.y + 4.0f, 7.0f, WHITE);
            addVisibleTarget(row, viewport, Action.BOOLEAN, panel, module, setting, null);
            return y + 22;
        }

        if (setting instanceof SliderSetting value) {
            Rect component = new Rect(panel.x + 22, y, 63, 30);
            drawFontFractional(context, trim(setting.getName(), 65, FONT_14),
                    component.x - 16.0f, component.y + 0.5f,
                    foreground, FONT_14, false);
            String number = legacySliderValue(value.getValue(), value.getStep());
            int trackY = component.y + 15;
            context.fill(component.x, trackY, component.right(), trackY + 1, foreground);
            double range = value.getMax() - value.getMin();
            double ratio = range == 0 ? 0 : (value.getValue() - value.getMin()) / range;
            float fill = (float) (component.width * clamp(ratio, 0, 1));
            float centerX = component.x + fill;
            float centerY = trackY + 0.5f;
            int outerColor = module.isEnabled() ? WHITE : primaryColor();
            int innerColor = module.isEnabled() ? primaryColor() : WHITE;
            int filledRight = Math.round(component.x + fill);
            context.fill(component.x, trackY, filledRight, trackY + 1, outerColor);
            if (shadowsEnabled()) {
                fillLegacyShadow(context, Math.round(centerX) - 3,
                        trackY - 3, 7, 7, 0, 10, outerColor);
            }
            fillLegacyPoint(context, centerX, centerY, 7.0f, outerColor);
            fillLegacyPoint(context, centerX, centerY, 5.0f, innerColor);
            String minimum = legacyFloat(value.getMin());
            String maximum = legacyFloat(value.getMax());
            drawFont(context, minimum, component.x - 16, component.y + 11,
                    faded, FONT_12, false);
            drawFont(context, maximum,
                    component.right() + 20 - fontWidth(maximum, FONT_12), component.y + 11,
                    faded, FONT_12, false);
            drawCenteredFont(context, number, centerX, component.y + 19.0f,
                    foreground, FONT_11, false);

            Rect interaction = new Rect(component.x - 3, component.y, component.width + 6, 30);
            addVisibleTarget(interaction, viewport, Action.NUMBER,
                    panel, module, setting, null, component);
            return y + 30;
        }

        if (setting instanceof ModeSetting value) {
            Rect row = new Rect(x, y, w + 1, 29);
            drawFont(context, trim(setting.getName() + ":", row.width - 10, FONT_14),
                    row.x + 3, row.y, foreground, FONT_14, false);
            float selectedX = row.x + 2.5f;
            int selectedY = row.y + 11;
            int selectedWidth = row.width - 6;
            int selectedHeight = 12;
            context.getMatrices().push();
            context.getMatrices().translate(selectedX, selectedY, 0.0f);
            if (shadowsEnabled()) {
                fillLegacyShadow(context, 0, 0,
                        selectedWidth, selectedHeight, 0, 7, 0x96000000);
            }
            context.fill(0, 0, selectedWidth, selectedHeight, 0x8C1E1E1E);
            context.getMatrices().pop();
            String current = trim(value.getValue(), selectedWidth - 14, FONT_15);
            drawFontFractional(context, current, row.x + 5.0f, row.y + 11.5f,
                    WHITE, FONT_15, true);
            float progress = settingProgress(key);
            if (value.getModes().size() > 1) {
                drawModuleArrow(context, row.x + 97, row.y + 16.5f, progress, WHITE);
            }
            addVisibleTarget(row, viewport, Action.MODE_HEADER, panel, module, setting, null);
            int optionsY = y + 29;
            int fullExtra = modeExtraHeight(value);
            int visibleExtra = Math.round(fullExtra * progress);
            if (visibleExtra > 0) {
                int visualTop = y + 23;
                int fullVisualHeight = fullExtra + 2;
                int visibleVisualHeight = Math.round(fullVisualHeight * progress);
                Rect optionsClip = new Rect(row.x, visualTop, row.width, visibleVisualHeight)
                        .intersection(viewport);
                if (optionsClip.height > 0) {
                    context.enableScissor(optionsClip.x, optionsClip.y, optionsClip.right(), optionsClip.bottom());
                    Rect options = new Rect(row.x + 4, visualTop,
                            row.width - 8, fullVisualHeight);
                    if (shadowsEnabled()) {
                        fillLegacyShadow(context, options.x - 1, options.y,
                                options.width + 3, options.height + 1,
                                0, 5, 0x96000000);
                    }
                    fillRounded(context, options.x, options.y, options.width, options.height, 4, 0x8C1E1E1E);
                    for (int index = 0; index < value.getModes().size(); index++) {
                        String option = value.getModes().get(index);
                        int textY = y + 27 + index * 14;
                        Rect optionRow = new Rect(row.x + 15, y + 27 + index * 14,
                                row.width - 30, 11);
                        String shown = trim(option, row.width - 16, FONT_16);
                        drawCenteredFont(context, shown, row.x + row.width / 2.0f,
                                textY + 0.5f,
                                value.is(option) ? primaryColor() : WHITE,
                                FONT_16, true);
                        addVisibleTarget(optionRow, optionsClip, Action.MODE_OPTION,
                                panel, module, setting, option);
                    }
                    context.disableScissor();
                }
            }
            return optionsY + visibleExtra;
        }

        if (setting instanceof ModeListSetting value) {
            Rect row = new Rect(x - 1, y, w + 2, 29);
            drawFont(context, trim(setting.getName() + ":", row.width - 10, FONT_15),
                    row.x + 3, row.y, foreground, FONT_15, false);
            float selectedX = row.x + 2.5f;
            int selectedY = row.y + 11;
            int selectedWidth = row.width - 6;
            int selectedHeight = 12;
            context.getMatrices().push();
            context.getMatrices().translate(selectedX, selectedY, 0.0f);
            if (shadowsEnabled()) {
                fillLegacyShadow(context, 0, 0,
                        selectedWidth, selectedHeight, 0, 7, 0x96000000);
            }
            context.fill(0, 0, selectedWidth, selectedHeight, 0x8C1E1E1E);
            context.getMatrices().pop();
            String summary = value.getEnabledModules().isEmpty() ? "Нету :(" : String.join(", ", value.getEnabledModules());
            drawFont(context, trim(summary, selectedWidth - 14, FONT_14),
                    row.x + 5, row.y + 12, WHITE, FONT_14, true);
            float progress = settingProgress(key);
            if (value.getSettings().size() > 1) {
                drawModuleArrow(context, row.x + 97, row.y + 16.5f, progress, WHITE);
            }
            addVisibleTarget(row, viewport, Action.MULTI_HEADER, panel, module, setting, null);
            int optionsY = y + 29;
            int fullExtra = multiExtraHeight(value);
            int visibleExtra = Math.round(fullExtra * progress);
            if (visibleExtra > 0) {
                int visualTop = y + 23;
                int fullVisualHeight = fullExtra + 1;
                int visibleVisualHeight = Math.round((fullExtra + 3) * progress);
                Rect optionsClip = new Rect(row.x, visualTop, row.width, visibleVisualHeight)
                        .intersection(viewport);
                if (optionsClip.height > 0) {
                    context.enableScissor(optionsClip.x, optionsClip.y, optionsClip.right(), optionsClip.bottom());
                    Rect options = new Rect(row.x + 4, visualTop,
                            row.width - 9, Math.max(1, fullVisualHeight));
                    if (shadowsEnabled()) {
                        fillLegacyShadow(context, options.x - 1, options.y,
                                options.width + 3, fullExtra + 3,
                                0, 5, 0x96000000);
                    }
                    fillRounded(context, options.x, options.y, options.width, options.height, 4, 0x8C1E1E1E);
                    for (int index = 0; index < value.getSettings().size(); index++) {
                        BooleanSetting subSetting = value.getSettings().get(index);
                        String option = subSetting.getName();
                        int textY = y + 26 + index * 14;
                        Rect optionRow = new Rect(row.x + 15, y + 28 + index * 14,
                                row.width - 30, 12);
                        boolean selectedOption = subSetting.getValue();
                        String shown = trim(option, row.width - 16, FONT_15);
                        drawCenteredFont(context, shown, row.x + row.width / 2.0f,
                                textY,
                                selectedOption ? primaryColor() : WHITE,
                                FONT_15, true);
                        addVisibleTarget(optionRow, optionsClip, Action.MULTI_OPTION,
                                panel, module, setting, option);
                    }
                    context.disableScissor();
                }
            }
            return optionsY + visibleExtra;
        }

        if (setting instanceof ColorSetting value) {
            Rect row = new Rect(x - 1, y, w + 2, 18);
            drawFont(context, trim(setting.getName() + ":", 74, FONT_15),
                    row.x + 3, row.y + 2, foreground, FONT_15, false);
            Rect swatch = new Rect(row.right() - 15, row.y + 6, 12, 6);
            if (shadowsEnabled()) {
                fillLegacyShadow(context, swatch.x - 1, swatch.y - 1,
                        13, 7, 0, 15, 0x96000000);
            }
            fillRounded(context, swatch.x, swatch.y, swatch.width, swatch.height,
                    1, value.getValue());
            addVisibleTarget(row, viewport, Action.COLOR_HEADER, panel, module, setting, null);
            int pickerY = y + 18;
            float progress = settingProgress(key);
            int visibleExtra = Math.round(80.0f * progress);
            if (visibleExtra > 0) {
                Rect pickerClip = new Rect(row.x, pickerY, row.width, visibleExtra)
                        .intersection(viewport);
                if (pickerClip.height > 0) {
                    context.enableScissor(pickerClip.x, pickerClip.y, pickerClip.right(), pickerClip.bottom());
                    Rect sv = new Rect(row.x + 3, pickerY, 70, 70);
                    Rect hue = new Rect(sv.right() + 4, pickerY, 10, 70);
                    Rect alpha = new Rect(hue.right() + 4, pickerY, 10, 70);
                    if (shadowsEnabled()) {
                        fillLegacyShadow(context, sv.x, sv.y,
                                sv.width, sv.height, 0, 10, 0x96000000);
                        fillLegacyShadow(context, hue.x, hue.y,
                                hue.width, hue.height - 1, 0, 10, 0x96000000);
                    }
                    drawColorPicker(context, sv, hue, alpha, value);
                    addVisibleTarget(sv, pickerClip, Action.COLOR_SV,
                            panel, module, setting, null, sv);
                    addVisibleTarget(hue, pickerClip, Action.COLOR_HUE,
                            panel, module, setting, null, hue);
                    addVisibleTarget(alpha, pickerClip, Action.COLOR_ALPHA,
                            panel, module, setting, null, alpha);
                    context.disableScissor();
                }
            }
            return pickerY + visibleExtra;
        }

        if (setting instanceof BindSetting value) {
            Rect row = new Rect(x, y, w + 2, 16);
            String shown = editingBind == value
                    ? "Press a key..." : setting.getName() + ": " + keyName(value.getValue());
            drawFontFractional(context, trim(shown, row.width - 4, FONT_14),
                    row.x + 2.0f, row.y + 4.0f / 3.0f,
                    foreground, FONT_14, false);
            addVisibleTarget(row, viewport, Action.BIND, panel, module, setting, null);
            return y + 16;
        }

        Rect row = settingRow(x, y, w, 18, viewport);
        drawFont(context, trim(setting.getName(), row.width - 10), row.x + 5, row.y + 5, foreground);
        return y + 18;
    }

    private void drawConfigProfiles(
            DrawContext context, Panel panel, Rect viewport, int mouseX, int mouseY,
            float animationStep
    ) {
        List<String> names = profileNames();
        float minimumScroll = Math.min(0, -names.size() * 18 + (panel.height - 48));
        configTargetScroll = (float) clamp(configTargetScroll, minimumScroll, 0);
        configScroll += (configTargetScroll - configScroll)
                * Math.min(1.0f, Math.max(0.12f, animationStep));
        if (Math.abs(configTargetScroll - configScroll) < 0.05f) {
            configScroll = configTargetScroll;
        }
        int y = panel.y + 22 + Math.round(configScroll);

        if (!names.isEmpty()) {
            for (String name : names) {
                Rect row = new Rect(panel.x + 3, y, COLUMN_WIDTH - 6, 14);
                boolean hovered = row.contains(mouseX, mouseY) && viewport.contains(mouseX, mouseY);
                boolean selected = name.equalsIgnoreCase(selectedProfile);
                if (shadowsEnabled()) {
                    fillLegacyShadow(context, row.x, row.y,
                            row.width, row.height, 0, 8, 0x5A000000);
                }
                if (selected) {
                    drawGradientCard(context, row, primaryColor(),
                            shade(secondaryColor(), 0.7f));
                } else {
                    fillRounded(context, row.x, row.y, row.width, row.height, 1,
                            hovered ? 0xFFF5F5F5 : 0xFFFFFFFF);
                }
                String shown = trim(name, row.width - 10, FONT_16);
                drawCenteredFont(context, shown, row.x + row.width / 2.0f,
                        row.y + 3.5f, selected ? WHITE : BLACK, FONT_16, false);
                if (row.intersects(viewport)) {
                    Rect hit = row.intersection(viewport);
                    if (hit.width > 0 && hit.height > 0) {
                        hitTargets.add(new HitTarget(hit,
                                Action.CONFIG_PROFILE, panel, null, null, name));
                    }
                }
                y += 18;
            }
        }
    }

    private void drawConfigControls(
            DrawContext context, Panel panel, int mouseX, int mouseY
    ) {
        int buttonsY = panel.y + panel.height - 28;
        Rect add = new Rect(panel.x + 13, buttonsY, 24, 10);
        Rect remove = new Rect(add.x + 29, buttonsY, 24, 10);
        Rect files = new Rect(remove.x + 29, buttonsY, 25, 10);
        Rect input = new Rect(panel.x + 13, panel.y + panel.height - 14, COLUMN_WIDTH - 27, 10);

        fillRounded(context, input.x, input.y, input.width, input.height, 1, 0x4B323232);
        String shown = configName.isEmpty() && !editingConfigName ? "Config name..." : configName;
        if (editingConfigName) shown += "_";
        drawFont(context, trim(shown, input.width - 8, FONT_17), input.x + 4, input.y + 1,
                WHITE, FONT_17, true);
        hitTargets.add(new HitTarget(input, Action.CONFIG_INPUT, panel, null, null, null));

        boolean canSave = !configName.isBlank() || !selectedProfile.isBlank();
        boolean canDelete = !selectedProfile.isBlank();
        drawConfigButton(context, add, "+", mouseX, mouseY, panel, Action.CONFIG_SAVE, canSave);
        drawConfigButton(context, remove, "-", mouseX, mouseY, panel, Action.CONFIG_DELETE, canDelete);
        drawConfigButton(context, files, "Files", mouseX, mouseY, panel, Action.CONFIG_FILES, true);

        panel.maxScroll = 0;
        panel.scroll = 0;
        panel.targetScroll = 0;
    }

    private void drawConfigButton(
            DrawContext context, Rect button, String label, int mouseX, int mouseY,
            Panel panel, Action action, boolean enabled
    ) {
        boolean hovered = button.contains(mouseX, mouseY);
        fillRounded(context, button.x, button.y, button.width, button.height, 1,
                0x4B323232);
        Identifier buttonFont = label.equals("Files") ? FONT_16 : FONT_25;
        int labelColor = !enabled ? 0xFFC3C3C3
                : hovered ? primaryColor() : WHITE;
        drawCenteredFont(context, label, button.x + button.width / 2.0f, button.y + 1.0f,
                labelColor, buttonFont, true);
        if (enabled) {
            hitTargets.add(new HitTarget(button, action, panel, null, null, null));
        }
    }

    private int themeContentHeight() {
        ThemeManager manager = ThemeManager.getInstance();
        int count = ThemeManager.DEFAULT_THEMES.size() + manager.getCustomThemes().size();
        int height = count * 18;
        if (editingCustomTheme != null && isCustomTheme(editingCustomTheme)) {
            height += 84;
        }
        return height;
    }

    private boolean isCustomTheme(String name) {
        if (name == null || name.isBlank()) return false;
        if (ThemeManager.isDefaultTheme(name)) return false;
        for (ThemeManager.ThemePreset p : ThemeManager.getInstance().getCustomThemes()) {
            if (p.name().equalsIgnoreCase(name.strip())) return true;
        }
        return false;
    }

    private void drawThemeControls(
            DrawContext context, Panel panel, int mouseX, int mouseY
    ) {
        int buttonsY = panel.y + panel.height - 28;
        Rect add = new Rect(panel.x + 13, buttonsY, 24, 10);
        Rect remove = new Rect(add.x + 29, buttonsY, 24, 10);
        Rect color1Box = new Rect(remove.x + 29, buttonsY, 11, 10);
        Rect color2Box = new Rect(color1Box.x + 14, buttonsY, 11, 10);
        Rect input = new Rect(panel.x + 13, panel.y + panel.height - 14, COLUMN_WIDTH - 27, 10);

        fillRounded(context, input.x, input.y, input.width, input.height, 1, 0x4B323232);
        String shown = themeName.isEmpty() && !editingThemeName ? "Theme name..." : themeName;
        if (editingThemeName) shown += "_";
        drawFont(context, trim(shown, input.width - 8, FONT_17), input.x + 4, input.y + 1,
                WHITE, FONT_17, true);
        hitTargets.add(new HitTarget(input, Action.THEME_INPUT, panel, null, null, null));

        boolean canSave = true;
        boolean canDelete = isCustomTheme(selectedTheme);
        drawConfigButton(context, add, "+", mouseX, mouseY, panel, Action.THEME_CREATE, canSave);
        drawConfigButton(context, remove, "-", mouseX, mouseY, panel, Action.THEME_DELETE, canDelete);

        boolean custom = isCustomTheme(selectedTheme);
        fillRounded(context, color1Box.x, color1Box.y, color1Box.width, color1Box.height, 1, themeColor1.getValue());
        fillRounded(context, color2Box.x, color2Box.y, color2Box.width, color2Box.height, 1, themeColor2.getValue());

        if (custom && editingCustomTheme != null && editingCustomTheme.equalsIgnoreCase(selectedTheme)) {
            if (themeColorTab == 1) {
                fillLegacyPoint(context, color1Box.x + color1Box.width / 2.0f, color1Box.y + color1Box.height / 2.0f, 3.0f, WHITE);
            } else {
                fillLegacyPoint(context, color2Box.x + color2Box.width / 2.0f, color2Box.y + color2Box.height / 2.0f, 3.0f, WHITE);
            }
        }

        hitTargets.add(new HitTarget(color1Box, Action.THEME_COLOR1_HEADER, panel, null, null, selectedTheme));
        hitTargets.add(new HitTarget(color2Box, Action.THEME_COLOR2_HEADER, panel, null, null, selectedTheme));

        panel.maxScroll = 0;
        panel.scroll = 0;
        panel.targetScroll = 0;
    }

    private void drawThemePresets(
            DrawContext context, Panel panel, Rect viewport, int mouseX, int mouseY,
            float animationStep
    ) {
        ThemeManager manager = ThemeManager.getInstance();
        List<ThemeManager.ThemePreset> presets = new ArrayList<>(ThemeManager.DEFAULT_THEMES);
        presets.addAll(manager.getCustomThemes());

        float minimumScroll = Math.min(0, -themeContentHeight() + (panel.height - 48));
        themesTargetScroll = (float) clamp(themesTargetScroll, minimumScroll, 0);
        themesScroll += (themesTargetScroll - themesScroll)
                * Math.min(1.0f, Math.max(0.12f, animationStep));
        if (Math.abs(themesTargetScroll - themesScroll) < 0.05f) {
            themesScroll = themesTargetScroll;
        }
        int y = panel.y + 22 + Math.round(themesScroll);

        Theme active = manager.getCurrentTheme();
        ThemeManager.ThemePreset activePreset = manager.getActivePreset();
        String activeName = activePreset != null ? activePreset.name() : (active != null ? active.name : "");

        for (ThemeManager.ThemePreset preset : presets) {
            Rect row = new Rect(panel.x + 3, y, COLUMN_WIDTH - 6, 15);
            boolean hovered = row.contains(mouseX, mouseY) && viewport.contains(mouseX, mouseY);
            boolean selected = preset.name().equalsIgnoreCase(activeName)
                    || (selectedTheme != null && selectedTheme.equalsIgnoreCase(preset.name()));
            boolean custom = isCustomTheme(preset.name());
            boolean isEditingThis = custom && editingCustomTheme != null
                    && editingCustomTheme.equalsIgnoreCase(preset.name());

            if (shadowsEnabled()) {
                fillLegacyShadow(context, row.x, row.y,
                        row.width, row.height, 0, 8, 0x5A000000);
            }
            if (selected) {
                drawGradientCard(context, row, primaryColor(),
                        shade(secondaryColor(), 0.7f));
            } else {
                fillRounded(context, row.x, row.y, row.width, row.height, 1,
                        hovered ? 0xFFEEEEEE : 0xFFFFFFFF);
            }

            fillLegacyPoint(context, row.x + 7.5f, row.y + 7.5f, 6.0f, preset.color1());
            fillLegacyPoint(context, row.x + 14.5f, row.y + 7.5f, 6.0f, preset.color2());

            String tag = isEditingThis ? " ▲" : (custom ? " *" : "");
            String shown = trim(preset.name() + tag, row.width - 26, FONT_15);
            drawFont(context, shown, row.x + 22, (int) Math.round(row.y + 2.5f),
                    selected ? WHITE : BLACK, FONT_15, false);

            if (row.intersects(viewport)) {
                hitTargets.add(new HitTarget(row.intersection(viewport),
                        Action.THEME_SELECT, panel, null, null, preset.name()));
            }
            y += 18;

            if (isEditingThis) {
                Rect editorBox = new Rect(panel.x + 4, y, COLUMN_WIDTH - 8, 80);
                fillRounded(context, editorBox.x, editorBox.y, editorBox.width, editorBox.height, 2, 0x55000000);

                int tabW = (editorBox.width - 6) / 2;
                int tabH = 13;
                Rect tab1 = new Rect(editorBox.x + 2, editorBox.y + 2, tabW, tabH);
                Rect tab2 = new Rect(tab1.right() + 2, editorBox.y + 2, tabW, tabH);

                // Tab 1 (Color 1)
                fillRounded(context, tab1.x, tab1.y, tab1.width, tab1.height, 1,
                        themeColorTab == 1 ? 0x90404040 : 0x40202020);
                fillLegacyPoint(context, tab1.x + 6.0f, tab1.y + 6.5f, 5.0f, themeColor1.getValue());
                drawFont(context, "Цвет 1", tab1.x + 13, (int) Math.round(tab1.y + 2.0f),
                        themeColorTab == 1 ? WHITE : MUTED, FONT_14, false);
                if (tab1.intersects(viewport)) {
                    hitTargets.add(new HitTarget(tab1.intersection(viewport),
                            Action.THEME_COLOR1_HEADER, panel, null, null, preset.name()));
                }

                // Tab 2 (Color 2)
                fillRounded(context, tab2.x, tab2.y, tab2.width, tab2.height, 1,
                        themeColorTab == 2 ? 0x90404040 : 0x40202020);
                fillLegacyPoint(context, tab2.x + 6.0f, tab2.y + 6.5f, 5.0f, themeColor2.getValue());
                drawFont(context, "Цвет 2", tab2.x + 13, (int) Math.round(tab2.y + 2.0f),
                        themeColorTab == 2 ? WHITE : MUTED, FONT_14, false);
                if (tab2.intersects(viewport)) {
                    hitTargets.add(new HitTarget(tab2.intersection(viewport),
                            Action.THEME_COLOR2_HEADER, panel, null, null, preset.name()));
                }

                // Color Picker for the selected tab
                ColorSetting activeColor = themeColorTab == 1 ? themeColor1 : themeColor2;
                int pickerY = tab1.bottom() + 3;
                int svSize = 58;
                int barW = 8;
                int barGap = 3;
                int pickerX = editorBox.x + (editorBox.width - (svSize + barGap + barW + barGap + barW)) / 2;
                Rect sv = new Rect(pickerX, pickerY, svSize, svSize);
                Rect hue = new Rect(sv.right() + barGap, pickerY, barW, svSize);
                Rect alpha = new Rect(hue.right() + barGap, pickerY, barW, svSize);

                drawColorPicker(context, sv, hue, alpha, activeColor);

                if (sv.intersects(viewport)) {
                    hitTargets.add(new HitTarget(sv.intersection(viewport),
                            Action.COLOR_SV, panel, null, activeColor, null, sv));
                }
                if (hue.intersects(viewport)) {
                    hitTargets.add(new HitTarget(hue.intersection(viewport),
                            Action.COLOR_HUE, panel, null, activeColor, null, hue));
                }
                if (alpha.intersects(viewport)) {
                    hitTargets.add(new HitTarget(alpha.intersection(viewport),
                            Action.COLOR_ALPHA, panel, null, activeColor, null, alpha));
                }

                y += 84;
            }
        }

        panel.maxScroll = 0;
        panel.scroll = 0;
        panel.targetScroll = 0;
    }

    private void drawColorPicker(
            DrawContext context, Rect sv, Rect hue, Rect alpha, ColorSetting setting
    ) {
        int argb = setting.getValue();
        float[] hsb = Color.RGBtoHSB((argb >>> 16) & 0xFF,
                (argb >>> 8) & 0xFF, argb & 0xFF, null);
        for (int column = 0; column < sv.width; column++) {
            float saturation = (float) column / Math.max(1, sv.width - 1);
            int top = 0xFF000000 | (Color.HSBtoRGB(hsb[0], saturation, 1.0f) & 0xFFFFFF);
            context.fillGradient(sv.x + column, sv.y,
                    sv.x + column + 1, sv.bottom(), top, 0xFF000000);
        }
        for (int y = 0; y < hue.height; y++) {
            float h = (float) y / Math.max(1, hue.height - 1);
            int top = 0xFF000000 | (Color.HSBtoRGB(h, 1.0f, 1.0f) & 0xFFFFFF);
            int bottom = 0xFF000000 | (Color.HSBtoRGB((float) (y + 1) / Math.max(1, hue.height - 1), 1.0f, 1.0f) & 0xFFFFFF);
            context.fillGradient(hue.x, hue.y + y, hue.right(), hue.y + y + 1, top, bottom);
        }
        context.draw();

        int markerX = sv.x + Math.round(hsb[1] * (sv.width - 1));
        int markerY = sv.y + Math.round((1.0f - hsb[2]) * (sv.height - 1));

        fillLegacyPoint(context, markerX, markerY, 7.0f, BLACK);
        fillLegacyPoint(context, markerX, markerY, 5.0f, WHITE);

        int hueY = hue.y + Math.round(hsb[0] * (hue.height - 1));
        context.fill(hue.x - 1, hueY - 2, hue.right() + 1, hueY + 2, BLACK);
        context.fill(hue.x, hueY - 1, hue.right(), hueY + 1, WHITE);

        drawCheckerboard(context, alpha);
        int rgb = argb & 0x00FFFFFF;
        context.fillGradient(alpha.x, alpha.y, alpha.right(), alpha.bottom(),
                0x32000000 | rgb, 0xFF000000 | rgb);
        float alphaRatio = (argb >>> 24) / 255.0f;
        int alphaY = alpha.y + Math.round(alphaRatio * (alpha.height - 1));
        context.fill(alpha.x - 1, alphaY - 2, alpha.right() + 1, alphaY + 2, BLACK);
        context.fill(alpha.x, alphaY - 1, alpha.right(), alphaY + 1, WHITE);
        context.draw();
    }

    private static void drawCheckerboard(DrawContext context, Rect bounds) {
        context.fill(bounds.x, bounds.y, bounds.right(), bounds.bottom(), 0xFF808080);
        for (int y = bounds.y; y < bounds.bottom(); y += 2) {
            for (int x = bounds.x + ((y - bounds.y) & 2); x < bounds.right(); x += 4) {
                context.fill(x, y, Math.min(bounds.right(), x + 2),
                        Math.min(bounds.bottom(), y + 2), WHITE);
            }
        }
    }

    private void addVisibleTarget(
            Rect row, Rect viewport, Action action, Panel panel,
            Module module, Setting setting, String value
    ) {
        addVisibleTarget(row, viewport, action, panel, module, setting, value, row);
    }

    private void addVisibleTarget(
            Rect row, Rect viewport, Action action, Panel panel,
            Module module, Setting setting, String value, Rect valueBounds
    ) {
        if (row.intersects(viewport)) {
            Rect hit = row.intersection(viewport);
            if (hit.width > 0 && hit.height > 0) {
                hitTargets.add(new HitTarget(hit, action, panel, module, setting, value, valueBounds));
            }
        }
    }

    private int settingsHeight(Module module) {
        int height = 0;
        for (Setting setting : module.getSettings()) {
            if (!isSettingVisible(setting)) continue;
            if (setting instanceof BooleanSetting) height += 22;
            else if (setting instanceof SliderSetting) height += 30;
            else if (setting instanceof ModeSetting mode) {
                height += 29 + Math.round(modeExtraHeight(mode)
                        * settingProgress(settingKey(module, setting)));
            } else if (setting instanceof ModeListSetting multi) {
                height += 29 + Math.round(multiExtraHeight(multi)
                        * settingProgress(settingKey(module, setting)));
            } else if (setting instanceof ColorSetting) {
                height += 18 + Math.round(80.0f
                        * settingProgress(settingKey(module, setting)));
            } else if (setting instanceof BindSetting) height += 16;
            else height += 18;
        }
        return height;
    }

    private void updateSettingAnimations(Module module, float animationStep) {
        for (Setting setting : module.getSettings()) {
            if (!(setting instanceof ModeSetting)
                    && !(setting instanceof ModeListSetting)
                    && !(setting instanceof ColorSetting)) {
                continue;
            }
            String key = settingKey(module, setting);
            float target = expandedSettings.contains(key) && isSettingVisible(setting) ? 1.0f : 0.0f;
            float progress = settingAnimations.getOrDefault(key, 0.0f);
            progress += (target - progress) * Math.min(1.0f, animationStep);
            if (Math.abs(target - progress) < 0.008f) progress = target;
            settingAnimations.put(key, progress);
        }
    }

    private float settingProgress(String key) {
        return settingAnimations.getOrDefault(key, 0.0f);
    }

    private static float modeOptionStep(ModeSetting setting) {
        return setting.getModes().size() > 5 ? 14.5f : 15.0f;
    }

    private static int modeExtraHeight(ModeSetting setting) {
        return Math.round(setting.getModes().size() * modeOptionStep(setting));
    }

    private static int multiExtraHeight(ModeListSetting setting) {
        return Math.round(1.0f + setting.getSettings().size() * 14.1f);
    }

    private boolean hasVisibleSettings(Module module) {
        return module.getSettings().stream().anyMatch(this::isSettingVisible);
    }

    private boolean isSettingVisible(Setting setting) {
        return setting.visible == null || setting.visible.get();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        long now = System.currentTimeMillis();
        boolean doubled = (button == 0 && now - lastClickTime < 300
                && Math.abs(mouseX - lastClickX) < 8 && Math.abs(mouseY - lastClickY) < 8);
        lastClickTime = now;
        lastClickX = mouseX;
        lastClickY = mouseY;

        if (bindingModule != null) {
            int bindKey = button == 0 ? -100 : button == 1 ? -99 : button == 2 ? -98 : -(button + 98);
            bindingModule.setKey(bindKey);
            setStatus(bindingModule.getName() + ": " + keyName(bindingModule.getKey()));
            bindingModule = null;
            return true;
        }
        if (editingBind != null) {
            int bindKey = button == 0 ? -100 : button == 1 ? -99 : button == 2 ? -98 : -(button + 98);
            editingBind.setValue(bindKey);
            setStatus(editingBind.getName() + ": " + keyName(editingBind.getValue()));
            editingBind = null;
            return true;
        }
        for (int index = hitTargets.size() - 1; index >= 0; index--) {
            HitTarget target = hitTargets.get(index);
            if (!target.bounds.contains(mouseX, mouseY)) continue;
            if (handleTarget(target, mouseX, mouseY, button, doubled)) return true;
        }
        editingBind = null;
        editingConfigName = false;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean handleTarget(HitTarget target, double mouseX, double mouseY, int button, boolean doubled) {
        switch (target.action) {
            case PANEL_HEADER -> {
                if (button == 0) {
                    draggedPanel = target.panel;
                    dragOffsetX = mouseX - target.panel.x;
                    dragOffsetY = mouseY - target.panel.y;
                    return true;
                }
            }
            case MODULE -> {
                if (button == 0) {
                    target.module.toggle();
                    setStatus(target.module.getName()
                            + (target.module.isEnabled() ? " enabled" : " disabled"));
                    return true;
                }
                if (button == 1) {
                    if (hasVisibleSettings(target.module)) {
                        toggleSet(expandedModules, target.module.getName());
                        setStatus(target.module.getName() + " settings");
                    } else {
                        setStatus(target.module.getName() + " has no settings");
                    }
                    return true;
                }
                if (button == 2) {
                    bindingModule = target.module;
                    editingBind = null;
                    editingConfigName = false;
                    return true;
                }
            }
            case BOOLEAN -> {
                if (button == 0 && target.setting instanceof BooleanSetting setting) {
                    setting.toggle();
                    setStatus(setting.getName() + ": " + (setting.getValue() ? "on" : "off"));
                    return true;
                }
            }
            case NUMBER -> {
                if (target.setting instanceof SliderSetting setting) {
                    if (button == 2) {
                        setting.setValue(setting.getMin());
                        setStatus(setting.getName() + " reset");
                        return true;
                    }
                    if (button == 0) {
                        draggedSlider = setting;
                        draggedSliderBounds = target.valueBounds;
                        updateSlider(setting, target.valueBounds, mouseX);
                        return true;
                    }
                }
            }
            case MODE_HEADER, MULTI_HEADER -> {
                if (button == 0 || button == 1) {
                    toggleSet(expandedSettings, settingKey(target.module, target.setting));
                    return true;
                }
            }
            case MODE_OPTION -> {
                if (button == 0 && target.setting instanceof ModeSetting setting) {
                    setting.setValue(target.value);
                    setStatus(setting.getName() + ": " + setting.getValue());
                    return true;
                }
            }
            case MULTI_OPTION -> {
                if (button == 0 && target.setting instanceof ModeListSetting setting) {
                    for (BooleanSetting sub : setting.getSettings()) {
                        if (sub.getName().equalsIgnoreCase(target.value)) {
                            sub.toggle();
                            break;
                        }
                    }
                    setStatus(setting.getName() + ": " + setting.getEnabledModules().size() + " selected");
                    return true;
                }
            }
            case COLOR_HEADER -> {
                if (button == 0 || button == 1) {
                    toggleSet(expandedSettings, settingKey(target.module, target.setting));
                    return true;
                }
            }
            case COLOR_SV, COLOR_HUE, COLOR_ALPHA -> {
                if (button == 0 && target.setting instanceof ColorSetting setting) {
                    draggedColor = setting;
                    draggedColorBounds = target.valueBounds;
                    draggedColorPart = switch (target.action) {
                        case COLOR_HUE -> ColorDragPart.HUE;
                        case COLOR_ALPHA -> ColorDragPart.ALPHA;
                        default -> ColorDragPart.SV;
                    };
                    updateColor(setting, target.valueBounds,
                            mouseX, mouseY, draggedColorPart);
                    if (setting == themeColor1 || setting == themeColor2) {
                        saveCurrentCustomThemeColors();
                    }
                    return true;
                }
            }
            case BIND -> {
                if (button == 0 && target.setting instanceof BindSetting setting) {
                    editingBind = setting;
                    bindingModule = null;
                    editingConfigName = false;
                    return true;
                }
            }
            case CONFIG_PROFILE -> {
                if (button == 0) {
                    long now = System.currentTimeMillis();
                    boolean activate = doubled || (target.value.equalsIgnoreCase(lastProfileClick)
                            && now - lastProfileClickAt <= 300L);
                    selectedProfile = target.value;
                    configName = target.value;
                    lastProfileClick = target.value;
                    lastProfileClickAt = now;
                    if (activate) {
                        try {
                            setStatus(profiles.load(target.value)
                                    ? "Loaded " + target.value : "Missing " + target.value);
                        } catch (IOException | RuntimeException exception) {
                            setStatus("Load failed: " + concise(exception));
                        }
                    } else {
                        setStatus("Selected " + target.value + " (double click to load)");
                    }
                    return true;
                }
            }
            case CONFIG_INPUT -> {
                if (button == 0) {
                    editingConfigName = true;
                    editingBind = null;
                    bindingModule = null;
                    return true;
                }
            }
            case CONFIG_SAVE -> {
                if (button == 0) {
                    saveProfile();
                    return true;
                }
            }
            case CONFIG_DELETE -> {
                if (button == 0) {
                    String name = selectedProfile;
                    try {
                        if (name.isBlank()) {
                            setStatus("Select a config first");
                            return true;
                        }
                        setStatus(profiles.delete(name) ? "Deleted " + name : "Missing " + name);
                        profileCacheDirty = true;
                        selectedProfile = "";
                        if (name.equalsIgnoreCase(configName)) configName = "";
                    } catch (IOException | RuntimeException exception) {
                        setStatus("Delete failed: " + concise(exception));
                    }
                    return true;
                }
            }
            case CONFIG_FILES -> {
                if (button == 0) {
                    try {
                        profiles.openDirectory();
                        setStatus("Opened profiles folder");
                    } catch (IOException | RuntimeException exception) {
                        setStatus("Open failed: " + concise(exception));
                    }
                    return true;
                }
            }
            case THEME_SELECT -> {
                ThemeManager manager = ThemeManager.getInstance();
                ThemeManager.ThemePreset found = manager.findPreset(target.value);
                if (found != null) {
                    boolean custom = isCustomTheme(found.name());
                    if (button == 0) {
                        selectedTheme = found.name();
                        themeColor1.setValue(found.color1());
                        themeColor2.setValue(found.color2());
                        manager.getCurrentTheme().setColors(found.color1(), found.color2());
                        manager.saveThemes(manager.getCustomThemes(), found.name());
                        if (custom) {
                            themeName = found.name();
                            setStatus("Theme: " + found.name());
                        } else {
                            editingCustomTheme = null;
                            themeName = "";
                            setStatus("Default theme: " + found.name());
                        }
                    } else if (button == 1) {
                        if (!custom) {
                            setStatus("Default themes cannot be edited");
                        } else {
                            if (editingCustomTheme != null && editingCustomTheme.equalsIgnoreCase(found.name())) {
                                editingCustomTheme = null;
                                setStatus("Closed editor: " + found.name());
                            } else {
                                selectedTheme = found.name();
                                editingCustomTheme = found.name();
                                themeName = found.name();
                                themeColor1.setValue(found.color1());
                                themeColor2.setValue(found.color2());
                                manager.getCurrentTheme().setColors(found.color1(), found.color2());
                                manager.saveThemes(manager.getCustomThemes(), found.name());
                                themeColorTab = 1;
                                setStatus("Editing: " + found.name());
                            }
                        }
                    }
                }
                return true;
            }
            case THEME_INPUT -> {
                if (button == 0) {
                    editingThemeName = true;
                    editingConfigName = false;
                    editingBind = null;
                    bindingModule = null;
                    return true;
                }
            }
            case THEME_CREATE -> {
                if (button == 0) {
                    createTheme();
                    return true;
                }
            }
            case THEME_DELETE -> {
                if (button == 0) {
                    deleteTheme();
                    return true;
                }
            }
            case THEME_COLOR1_HEADER -> {
                if (button == 0 || button == 1) {
                    String theme = target.value != null && !target.value.isBlank() ? target.value : selectedTheme;
                    if (isCustomTheme(theme)) {
                        editingCustomTheme = theme;
                        themeColorTab = 1;
                        setStatus("Color 1: " + theme);
                    } else {
                        setStatus("Default themes cannot be edited");
                    }
                    return true;
                }
            }
            case THEME_COLOR2_HEADER -> {
                if (button == 0 || button == 1) {
                    String theme = target.value != null && !target.value.isBlank() ? target.value : selectedTheme;
                    if (isCustomTheme(theme)) {
                        editingCustomTheme = theme;
                        themeColorTab = 2;
                        setStatus("Color 2: " + theme);
                    } else {
                        setStatus("Default themes cannot be edited");
                    }
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (bindingModule != null) {
            bindingModule.setKey(clearKey(keyCode) ? -1 : keyCode);
            setStatus(bindingModule.getName() + ": " + keyName(bindingModule.getKey()));
            bindingModule = null;
            return true;
        }
        if (editingBind != null) {
            editingBind.setValue(clearKey(keyCode) ? -1 : keyCode);
            setStatus(editingBind.getName() + ": " + keyName(editingBind.getValue()));
            editingBind = null;
            return true;
        }
        if (editingConfigName) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                saveProfile();
                editingConfigName = false;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                editingConfigName = false;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !configName.isEmpty()) {
                configName = removeLastCodePoint(configName);
                return true;
            }
        }
        if (editingThemeName) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                createTheme();
                editingThemeName = false;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                editingThemeName = false;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !themeName.isEmpty()) {
                themeName = removeLastCodePoint(themeName);
                return true;
            }
        }
        if (keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_RIGHT) {
            int direction = keyCode == GLFW.GLFW_KEY_LEFT ? -1 : 1;
            for (Panel panel : panels) panel.x += direction * 15;
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (editingConfigName && configName.length() < 40) {
            if (Character.isLetterOrDigit(chr) || chr == ' ' || chr == '_' || chr == '-' || chr == '.') {
                configName += chr;
                return true;
            }
        }
        if (editingThemeName && themeName.length() < 30) {
            if (Character.isLetterOrDigit(chr) || chr == ' ' || chr == '_' || chr == '-' || chr == '.') {
                themeName += chr;
                return true;
            }
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (draggedPanel != null && button == 0) {
            draggedPanel.x = (int) Math.round(mouseX - dragOffsetX);
            draggedPanel.y = (int) Math.round(mouseY - dragOffsetY);
            return true;
        }
        if (draggedSlider != null && draggedSliderBounds != null && button == 0) {
            updateSlider(draggedSlider, draggedSliderBounds, mouseX);
            return true;
        }
        if (draggedColor != null && draggedColorBounds != null && button == 0) {
            updateColor(draggedColor, draggedColorBounds,
                    mouseX, mouseY, draggedColorPart);
            if (draggedColor == themeColor1 || draggedColor == themeColor2) {
                saveCurrentCustomThemeColors();
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && (draggedPanel != null || draggedSlider != null || draggedColor != null)) {
            if (draggedColor == themeColor1 || draggedColor == themeColor2) {
                saveCurrentCustomThemeColors();
            }
            boolean movedPanel = draggedPanel != null;
            draggedPanel = null;
            draggedSlider = null;
            draggedSliderBounds = null;
            draggedColor = null;
            draggedColorBounds = null;
            draggedColorPart = null;
            if (movedPanel) savePanelPositions();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        for (Panel panel : panels) {
            Rect body = new Rect(panel.x, panel.y, COLUMN_WIDTH, panel.height + 3);
            if (!body.contains(mouseX, mouseY)) continue;
            if (panel.kind == PanelKind.THEMES) {
                float minimum = Math.min(0, -themeContentHeight() + (panel.height - 52));
                themesTargetScroll = (float) clamp(
                        themesTargetScroll + Math.signum(verticalAmount) * 20,
                        minimum, 0);
            } else if (panel.kind == PanelKind.CONFIGS || panel.category == null) {
                float minimum = Math.min(0, -profileNames().size() * 18 + (panel.height - 48));
                configTargetScroll = (float) clamp(
                        configTargetScroll + Math.signum(verticalAmount) * 20,
                        minimum, 0);
            } else {
                panel.targetScroll = clamp(panel.targetScroll - verticalAmount * 20, 0, panel.maxScroll);
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private void saveProfile() {
        try {
            String name = configName.isBlank() ? selectedProfile : configName.strip();
            if (name.isBlank()) {
                setStatus("Enter or select a config");
                return;
            }
            if (name.equalsIgnoreCase("autocfg")) {
                setStatus("autocfg is reserved");
                return;
            }
            profiles.save(name);
            profileCacheDirty = true;
            selectedProfile = name;
            configName = "";
            setStatus("Saved " + selectedProfile);
        } catch (IOException | RuntimeException exception) {
            setStatus("Save failed: " + concise(exception));
        }
    }

    private void createTheme() {
        ThemeManager manager = ThemeManager.getInstance();
        List<ThemeManager.ThemePreset> customThemes = manager.getCustomThemes();
        String name = themeName.isBlank() ? "Custom " + (customThemes.size() + 1) : themeName.strip();
        if (ThemeManager.isDefaultTheme(name)) {
            setStatus("Theme '" + name + "' is a default theme");
            return;
        }
        for (ThemeManager.ThemePreset p : customThemes) {
            if (p.name().equalsIgnoreCase(name)) {
                setStatus("Theme '" + name + "' already exists");
                return;
            }
        }
        int c1 = themeColor1.getValue();
        int c2 = themeColor2.getValue();
        customThemes.add(new ThemeManager.ThemePreset(name, c1, c2));
        manager.saveThemes(customThemes, name);
        manager.getCurrentTheme().setColors(c1, c2);
        selectedTheme = name;
        editingCustomTheme = name;
        themeColorTab = 1;
        themeName = "";
        editingThemeName = false;
        setStatus("Created theme: " + name);
    }

    private void deleteTheme() {
        ThemeManager manager = ThemeManager.getInstance();
        if (selectedTheme.isBlank() || !isCustomTheme(selectedTheme)) {
            setStatus("Default themes cannot be deleted");
            return;
        }
        List<ThemeManager.ThemePreset> customThemes = manager.getCustomThemes();
        boolean removed = customThemes.removeIf(p -> p.name().equalsIgnoreCase(selectedTheme));
        if (removed) {
            ThemeManager.ThemePreset fb = ThemeManager.DEFAULT_THEMES.get(0);
            manager.saveThemes(customThemes, fb.name());
            manager.getCurrentTheme().setColors(fb.color1(), fb.color2());
            selectedTheme = fb.name();
            themeName = "";
            editingCustomTheme = null;
            themeColor1.setValue(fb.color1());
            themeColor2.setValue(fb.color2());
            setStatus("Deleted theme");
        }
    }

    private void saveCurrentCustomThemeColors() {
        if (editingCustomTheme == null || !isCustomTheme(editingCustomTheme)) return;
        ThemeManager manager = ThemeManager.getInstance();
        List<ThemeManager.ThemePreset> customThemes = manager.getCustomThemes();
        int c1 = themeColor1.getValue();
        int c2 = themeColor2.getValue();
        boolean found = false;
        for (int i = 0; i < customThemes.size(); i++) {
            if (customThemes.get(i).name().equalsIgnoreCase(editingCustomTheme)) {
                customThemes.set(i, new ThemeManager.ThemePreset(customThemes.get(i).name(), c1, c2));
                found = true;
                break;
            }
        }
        if (found) {
            manager.saveThemes(customThemes, editingCustomTheme);
            manager.getCurrentTheme().setColors(c1, c2);
        }
    }

    private void updateSlider(SliderSetting setting, Rect bounds, double mouseX) {
        double ratio = clamp((mouseX - bounds.x) / Math.max(1, bounds.width), 0, 1);
        double value = setting.getMin() + (setting.getMax() - setting.getMin()) * ratio;
        if (setting.getStep() > 0) {
            double steps = Math.round((value - setting.getMin()) / setting.getStep());
            value = setting.getMin() + steps * setting.getStep();
        }
        setting.setValue(value);
        setStatus(setting.getName() + ": " + formatNumber(setting.getValue()));
    }

    private List<String> profileNames() {
        long now = System.nanoTime();
        if (profileCacheDirty || now >= nextProfileRefreshNanos) {
            cachedProfileNames = profiles.names().stream()
                    .filter(name -> !name.equalsIgnoreCase("autocfg"))
                    .sorted(Comparator.<String>comparingInt(value ->
                                    fontWidth(value, FONT_16)).reversed()
                            .thenComparing(String.CASE_INSENSITIVE_ORDER.reversed()))
                    .toList();
            profileCacheDirty = false;
            nextProfileRefreshNanos = now + 500_000_000L;
        }
        return cachedProfileNames;
    }

    private void updateColor(
            ColorSetting setting, Rect bounds, double mouseX, double mouseY, ColorDragPart part
    ) {
        int argb = setting.getValue();
        float[] hsb = Color.RGBtoHSB((argb >>> 16) & 0xFF, (argb >>> 8) & 0xFF, argb & 0xFF, null);
        if (part == ColorDragPart.ALPHA) {
            int alpha = Math.round(255.0f * (float) clamp(
                    (mouseY - bounds.y) / Math.max(1, bounds.height - 1), 0, 1));
            setting.setValue(alpha << 24 | argb & 0x00FFFFFF);
            return;
        }
        if (part == ColorDragPart.HUE) {
            hsb[0] = (float) clamp(
                    (mouseY - bounds.y) / Math.max(1, bounds.height - 1), 0, 1);
        } else {
            hsb[1] = (float) clamp(
                    (mouseX - bounds.x) / Math.max(1, bounds.width - 1), 0, 1);
            hsb[2] = 1.0f - (float) clamp(
                    (mouseY - bounds.y) / Math.max(1, bounds.height - 1), 0, 1);
        }
        int alpha = argb & 0xFF000000;
        setting.setValue(alpha | (Color.HSBtoRGB(hsb[0], hsb[1], hsb[2]) & 0xFFFFFF));
    }

    private void applyStoredPanelPositions() {
        Map<String, UiLayoutStore.Position> stored = layoutStore.loadSection("clickGui");
        List<Rect> accepted = new ArrayList<>();
        for (int index = 0; index < panels.size(); index++) {
            Panel panel = panels.get(index);
            UiLayoutStore.Position position = stored.get(panel.layoutId());
            if (position == null) continue;
            int storedHeight = panel.category == null ? CONFIG_HEIGHT : PANEL_HEIGHT;
            Rect candidate = new Rect(position.x(), position.y(), COLUMN_WIDTH, storedHeight);

            boolean collapsed = accepted.stream().anyMatch(existing ->
                    overlapArea(existing, candidate) > COLUMN_WIDTH * HEADER_HEIGHT);
            if (!collapsed) {
                panel.x = position.x();
                panel.y = position.y();
                accepted.add(candidate);
            } else {
                panel.x = 18 + COLUMN_STEP * index;
                panel.y = 10;
                accepted.add(new Rect(panel.x, panel.y, COLUMN_WIDTH, storedHeight));
            }
        }
    }

    private static int overlapArea(Rect first, Rect second) {
        int overlapWidth = Math.max(0, Math.min(first.right(), second.right())
                - Math.max(first.x, second.x));
        int overlapHeight = Math.max(0, Math.min(first.bottom(), second.bottom())
                - Math.max(first.y, second.y));
        return overlapWidth * overlapHeight;
    }

    private void savePanelPositions() {
        Map<String, UiLayoutStore.Position> positions = new LinkedHashMap<>();
        for (Panel panel : panels) {
            positions.put(panel.layoutId(), new UiLayoutStore.Position(panel.x, panel.y));
        }
        try {
            layoutStore.saveSection("clickGui", positions);
        } catch (IOException exception) {
            setStatus("Layout save failed: " + concise(exception));
        }
    }

    private void setStatus(String message) {
        status = message;
        statusVisibleUntilNanos = System.nanoTime() + 2_500_000_000L;
    }

    private void drawTooltip(DrawContext context, Tooltip value) {
        int tooltipWidth = fontWidth(value.text, FONT_15) + 5;
        int wholeY = (int) Math.floor(value.y);
        context.getMatrices().push();
        context.getMatrices().translate(0.0f, value.y - wholeY, 0.0f);
        fillRounded(context, value.x, wholeY, tooltipWidth, 10, 1,
                multiplyAlpha(0xFF1E1E1E, value.alpha));
        context.getMatrices().pop();
        drawFontFractional(context, value.text, value.x + 1.0f, value.y - 0.5f,
                multiplyAlpha(WHITE, value.alpha), FONT_15, true);
    }

    private void drawPrompt(DrawContext context, String message) {
        String shown = trim(message, Math.max(40, width - 30));
        int boxWidth = fontWidth(shown) + 14;
        int x = (width - boxWidth) / 2;
        int y = height - 20;
        fillRounded(context, x, y, boxWidth, 16, 3, 0xDD090909);
        drawFont(context, shown, x + 7, y + 4, WHITE);
    }

    private void drawGradientHeader(DrawContext context, int x, int y, int width, int height, int first, int second) {
        SmoothRoundedGui.fillTopRounded(context, x, y, width, height, 5.5f, first, second);
    }

    private void drawGradientCard(DrawContext context, Rect row, int first, int second) {
        drawHorizontalGradient(context,
                row.x, row.y, row.width, row.height, 1, first, second);
    }

    private static void drawHorizontalGradient(
            DrawContext context, int x, int y, int width, int height,
            float radius, int left, int right
    ) {
        SmoothRoundedGui.fillFourCorners(context, x, y, width, height, radius,
                left, right, right, left);
    }

    private static int shade(int color, float brightness) {
        float value = Math.max(0.0f, brightness);
        int alpha = color >>> 24;
        int red = Math.min(255, (int) (((color >>> 16) & 0xFF) * value));
        int green = Math.min(255, (int) (((color >>> 8) & 0xFF) * value));
        int blue = Math.min(255, (int) ((color & 0xFF) * value));
        return alpha << 24 | red << 16 | green << 8 | blue;
    }

    private static int multiplyAlpha(int color, float multiplier) {
        int alpha = Math.round((color >>> 24) * (float) clamp(multiplier, 0.0f, 1.0f));
        return alpha << 24 | color & 0x00FFFFFF;
    }

    private void fillLegacyShadow(
            DrawContext context, int x, int y, int width, int height,
            float radius, float glowRadius, int color
    ) {
        if (!shadowsEnabled() || width <= 0 || height <= 0 || glowRadius <= 0 || (color >>> 24) == 0
                || meow.ancient.module.list.render.Optimization.isActive()) return;
        LegacyHudShadow.Batch batch = LegacyHudShadow.batch();
        batch.add(x, y, width, height, (int) glowRadius, color);
        batch.submit(context);
    }

    private static void drawModuleArrow(
            DrawContext context, float centerX, float centerY, float openProgress, int color
    ) {
        context.getMatrices().push();
        context.getMatrices().translate(centerX - 0.25f, centerY, 0.0f);
        context.getMatrices().multiply(RotationAxis.POSITIVE_Z.rotation((float) (Math.PI * clamp(openProgress, 0.0f, 1.0f))));
        context.getMatrices().scale(0.5f, 0.5f, 1.0f);
        context.fill(-3, -2, 4, -1, color);
        context.fill(-2, -1, -1, 0, multiplyAlpha(color, 0.77f));
        context.fill(-1, -1, 3, 0, color);
        context.fill(-1, 0, 0, 1, multiplyAlpha(color, 0.77f));
        context.fill(0, 0, 2, 1, color);
        context.fill(0, 1, 1, 2, multiplyAlpha(color, 0.64f));
        context.getMatrices().pop();
    }

    private static void fillRounded(
            DrawContext context, int x, int y, int width, int height, float radius, int color
    ) {
        SmoothRoundedGui.fill(context, x, y, width, height, radius, color);
    }

    private static void fillFractionalRect(
            DrawContext context, float x, float y, float width, float height, int color
    ) {
        if (width <= 0.0f || height <= 0.0f) return;
        context.fill((int) Math.floor(x), (int) Math.floor(y),
                (int) Math.ceil(x + width), (int) Math.ceil(y + height), color);
    }

    private static Identifier CIRCLE_TEXTURE_ID;

    private static void ensureCircleTexture() {
        if (CIRCLE_TEXTURE_ID != null) return;
        int size = 128;
        NativeImage image = new NativeImage(NativeImage.Format.RGBA, size, size, false);
        float center = size * 0.5f;
        float radius = center - 1.5f;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float dx = x + 0.5f - center;
                float dy = y + 0.5f - center;
                float dist = (float) Math.hypot(dx, dy);
                float alpha = Math.clamp(radius - dist + 0.5f, 0.0f, 1.0f);
                int a = Math.round(alpha * 255.0f);
                image.setColorArgb(x, y, (a << 24) | 0x00FFFFFF);
            }
        }
        NativeImageBackedTexture texture = new NativeImageBackedTexture(image);
        texture.upload();
        texture.setFilter(true, false);
        CIRCLE_TEXTURE_ID = Identifier.of("celestial", "dynamic/circle");
        MinecraftClient.getInstance().getTextureManager().registerTexture(CIRCLE_TEXTURE_ID, texture);
    }

    private static void fillLegacyPoint(
            DrawContext context, float centerX, float centerY, float diameter, int color
    ) {
        if (diameter <= 0.0f || (color >>> 24) == 0) return;
        ensureCircleTexture();
        context.draw();

        float quadSize = diameter * (128.0f / 125.0f);
        float offset = quadSize * 0.5f;
        float x = centerX - offset;
        float y = centerY - offset;
        Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(net.minecraft.client.gl.ShaderProgramKeys.POSITION_TEX_COLOR);
        RenderSystem.setShaderTexture(0, CIRCLE_TEXTURE_ID);

        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        buffer.vertex(matrix, x, y, 0.0f).texture(0.0f, 0.0f).color(color);
        buffer.vertex(matrix, x, y + quadSize, 0.0f).texture(0.0f, 1.0f).color(color);
        buffer.vertex(matrix, x + quadSize, y + quadSize, 0.0f).texture(1.0f, 1.0f).color(color);
        buffer.vertex(matrix, x + quadSize, y, 0.0f).texture(1.0f, 0.0f).color(color);
        BufferRenderer.drawWithGlobalProgram(buffer.end());

        RenderSystem.setShaderTexture(0, 0);
        RenderSystem.enableCull();
    }

    private int primaryColor() {
        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        if (theme != null) {
            return theme.getColorFirst();
        }
        return 0xFFBF68FF;
    }

    private int secondaryColor() {
        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        if (theme != null) {
            return theme.getColorSecond();
        }
        return 0xFF110122;
    }

    public boolean blurEnabled() {
        ClickGui clickGui = Instance.get(ClickGui.class);
        if (clickGui != null && clickGui.celestialBlur != null) {
            return clickGui.celestialBlur.getValue();
        }
        return true;
    }

    private boolean shadowsEnabled() {
        ClickGui clickGui = Instance.get(ClickGui.class);
        if (clickGui != null && clickGui.celestialShadows != null) {
            return clickGui.celestialShadows.getValue();
        }
        return true;
    }

    private static Identifier font(int legacySize) {
        return Identifier.of("ancient", "montserrat_" + legacySize);
    }

    private void drawFont(DrawContext context, String value, int x, int y, int color) {
        drawFont(context, value, x, y, color, FONT_14, false);
    }

    private Text fontText(String value, Identifier fontId) {
        return Text.literal(value).setStyle(Style.EMPTY.withFont(fontId));
    }

    private void drawFont(
            DrawContext context, String value, int x, int y, int color,
            Identifier fontId, boolean shadow
    ) {
        Text text = fontText(value, fontId);
        if (shadow) {
            context.getMatrices().push();
            context.getMatrices().translate(0.5f, 0.5f, 0.0f);
            context.drawText(textRenderer, text, x, y, legacyShadowColor(color), false);
            context.getMatrices().pop();
        }
        context.drawText(textRenderer, text, x, y, color, false);
    }

    private void drawFontFractional(
            DrawContext context, String value, float x, float y, int color,
            Identifier fontId, boolean shadow
    ) {
        int wholeX = (int) Math.floor(x);
        int wholeY = (int) Math.floor(y);
        context.getMatrices().push();
        context.getMatrices().translate(x - wholeX, y - wholeY, 0.0f);
        drawFont(context, value, wholeX, wholeY, color, fontId, shadow);
        context.getMatrices().pop();
    }

    private void drawCenteredFont(
            DrawContext context, String value, float centerX, float y, int color,
            Identifier fontId, boolean shadow
    ) {
        drawFontFractional(context, value, centerX - fontWidth(value, fontId) / 2.0f,
                y, color, fontId, shadow);
    }

    private static int legacyShadowColor(int color) {
        return (color & 0x00FCFCFC) >> 2 | color & 0xC8141414;
    }

    private int fontWidth(String value) {
        return fontWidth(value, FONT_14);
    }

    private int fontWidth(String value, Identifier fontId) {
        return textRenderer.getWidth(fontText(value, fontId));
    }

    private int legacyNameWidth(String value) {
        int measured = LEGACY_NAME_METRICS.width(value);
        int width = measured >= 0 ? measured : fontWidth(value, FONT_13);
        return width + LEGACY_SORT_WIDTH_CORRECTIONS.getOrDefault(value, 0);
    }

    private String trim(String value, int maximumWidth) {
        return trim(value, maximumWidth, FONT_14);
    }

    private String trim(String value, int maximumWidth, Identifier fontId) {
        if (maximumWidth <= 0 || fontWidth(value, fontId) <= maximumWidth) return value;
        String ellipsis = "...";
        int low = 0;
        int high = value.codePointCount(0, value.length());
        while (low < high) {
            int middle = (low + high + 1) >>> 1;
            int end = value.offsetByCodePoints(0, middle);
            if (fontWidth(value.substring(0, end) + ellipsis, fontId) <= maximumWidth) low = middle;
            else high = middle - 1;
        }
        return value.substring(0, value.offsetByCodePoints(0, low)) + ellipsis;
    }

    private static String settingKey(Module module, Setting setting) {
        return module.getName() + "/" + setting.getName();
    }

    private static void toggleSet(Set<String> values, String key) {
        if (!values.remove(key)) values.add(key);
    }

    private static boolean clearKey(int key) {
        return key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_DELETE;
    }

    private static String removeLastCodePoint(String value) {
        int end = value.offsetByCodePoints(value.length(), -1);
        return value.substring(0, end);
    }

    private static String keyName(int key) {
        if (key == -1 || key == 0) return "NONE";
        return KeyUtil.getKeyName(key);
    }

    private static String formatNumber(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.000_001) {
            return String.format(Locale.ROOT, "%.0f", value);
        }
        String formatted = String.format(Locale.ROOT, "%.2f", value);
        while (formatted.endsWith("0")) formatted = formatted.substring(0, formatted.length() - 1);
        return formatted;
    }

    private static String legacyFloat(double value) {
        return Float.toString((float) value);
    }

    private static String legacySliderValue(double value, double step) {
        float legacyValue = (float) value;
        float legacyStep = (float) step;
        if (!(legacyStep > 0.0f)) return Float.toString(legacyValue);
        double snapped = Math.round(legacyValue / legacyStep) * (double) legacyStep;
        float rounded = new BigDecimal(snapped)
                .setScale(3, RoundingMode.HALF_UP)
                .floatValue();
        return Float.toString(rounded);
    }

    private static String concise(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    private static String legacyTitle(ModuleCategory category) {
        if (category == ModuleCategory.MISC) return "Util";
        return switch (category) {
            case COMBAT -> "Combat";
            case MOVEMENT -> "Movement";
            case RENDER -> "Render";
            case PLAYER -> "Player";
            case MISC -> "Util";
        };
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static Rect settingRow(int x, int y, int width, int height, Rect viewport) {
        return new Rect(x, y, width, height);
    }

    private enum Action {
        PANEL_HEADER,
        MODULE,
        BOOLEAN,
        NUMBER,
        MODE_HEADER,
        MODE_OPTION,
        MULTI_HEADER,
        MULTI_OPTION,
        COLOR_HEADER,
        COLOR_SV,
        COLOR_HUE,
        COLOR_ALPHA,
        BIND,
        CONFIG_PROFILE,
        CONFIG_INPUT,
        CONFIG_SAVE,
        CONFIG_DELETE,
        CONFIG_FILES,
        THEME_SELECT,
        THEME_INPUT,
        THEME_CREATE,
        THEME_DELETE,
        THEME_COLOR1_HEADER,
        THEME_COLOR2_HEADER
    }

    private enum PanelKind {
        CATEGORY,
        CONFIGS,
        THEMES
    }

    private enum ColorDragPart {
        SV,
        HUE,
        ALPHA
    }

    private static final class Panel {
        private final PanelKind kind;
        private final ModuleCategory category;
        private final String title;
        private final int height;
        private int x;
        private int y;
        private double scroll;
        private double targetScroll;
        private double maxScroll;

        private Panel(ModuleCategory category, String title, int x, int y, int height) {
            this(PanelKind.CATEGORY, category, title, x, y, height);
        }

        private Panel(PanelKind kind, ModuleCategory category, String title, int x, int y, int height) {
            this.kind = kind;
            this.category = category;
            this.title = title;
            this.x = x;
            this.y = y;
            this.height = height;
        }

        private String layoutId() {
            if (kind == PanelKind.THEMES) return "themes";
            if (kind == PanelKind.CONFIGS || category == null) return "configs";
            return category.name().toLowerCase(Locale.ROOT);
        }
    }

    private static final class LegacyNameMetrics {
        private static final int LEGACY_GLYPH_COUNT = 1110;
        private final int[] glyphWidths;

        private LegacyNameMetrics(int[] glyphWidths) {
            this.glyphWidths = glyphWidths;
        }

        private static LegacyNameMetrics load() {
            try (InputStream stream = CelestialScreen.class.getResourceAsStream(
                    "/assets/ancient/font/montserrat.ttf")) {
                if (stream == null) return new LegacyNameMetrics(null);
                Font font = Font.createFont(Font.TRUETYPE_FONT, stream).deriveFont(13.0f);
                BufferedImage image = new BufferedImage(512, 512, BufferedImage.TYPE_INT_ARGB);
                Graphics2D graphics = image.createGraphics();
                try {
                    graphics.setFont(font);
                    graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
                            RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
                    graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);
                    FontMetrics metrics = graphics.getFontMetrics();
                    int[] widths = new int[LEGACY_GLYPH_COUNT];
                    for (int index = 0; index < widths.length; index++) {
                        char glyph = (char) index;
                        if (glyph >= '\u0100' && glyph <= '\u040E') {
                            widths[index] = -1;
                        } else {
                            widths[index] = metrics.getStringBounds(
                                    String.valueOf(glyph), graphics).getBounds().width;
                        }
                    }
                    return new LegacyNameMetrics(widths);
                } finally {
                    graphics.dispose();
                }
            } catch (Exception ignored) {
                return new LegacyNameMetrics(null);
            }
        }

        private int width(String value) {
            if (glyphWidths == null) return -1;
            int width = 0;
            for (int index = 0; index < value.length(); index++) {
                char glyph = value.charAt(index);
                if (glyph < glyphWidths.length && glyphWidths[glyph] >= 0) {
                    width += glyphWidths[glyph];
                }
            }
            return width / 2;
        }
    }

    private record HitTarget(
            Rect bounds,
            Action action,
            Panel panel,
            Module module,
            Setting setting,
            String value,
            Rect valueBounds
    ) {
        private HitTarget(
                Rect bounds, Action action, Panel panel, Module module,
                Setting setting, String value
        ) {
            this(bounds, action, panel, module, setting, value, bounds);
        }
    }

    private record Tooltip(int x, float y, String text, float alpha) {
    }

    private record Rect(int x, int y, int width, int height) {
        private int right() { return x + width; }
        private int bottom() { return y + height; }

        private boolean contains(double pointX, double pointY) {
            return pointX >= x && pointX < right() && pointY >= y && pointY < bottom();
        }

        private boolean intersects(Rect other) {
            return right() > other.x && x < other.right() && bottom() > other.y && y < other.bottom();
        }

        private Rect intersection(Rect other) {
            int left = Math.max(x, other.x);
            int top = Math.max(y, other.y);
            int right = Math.min(right(), other.right());
            int bottom = Math.min(bottom(), other.bottom());
            return new Rect(left, top, Math.max(0, right - left), Math.max(0, bottom - top));
        }
    }
}
