package tech.onetap.ui.delta;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Vector4f;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.settings.BindSetting;
import tech.onetap.module.settings.BooleanSetting;
import tech.onetap.module.settings.ColorSetting;
import tech.onetap.module.settings.ModeListSetting;
import tech.onetap.module.settings.ModeSetting;
import tech.onetap.module.settings.Setting;
import tech.onetap.module.settings.SliderSetting;
import tech.onetap.util.KeyUtil;
import tech.onetap.util.render.ColorUtil;
import tech.onetap.util.render.Delta2DHolder;
import tech.onetap.util.render.DeltaAnimation;
import tech.onetap.util.render.DeltaEasing;
import tech.onetap.util.render.DeltaThemeInfo;
import tech.onetap.util.render.ScissorUtil;
import tech.onetap.util.render.font.Font;
import tech.onetap.util.render.font.DeltaFonts;

import java.util.ArrayList;
import java.util.List;

/**
 * Category panel of the Delta-styled ClickGUI, ported from DeltaClient
 * (aethereal.ui.screen.GUIPanel): 125x270 panel, blur+glow background, 24px header
 * with icon, 16px module rows with bind badges, "..." markers, toggle switches and
 * animated expanding setting widgets.
 */
public class DeltaGuiPanel {
    static final float PANEL_WIDTH = 125.0f;
    static final float PANEL_HEIGHT = 270.0f;

    private final Vector4f bounds = new Vector4f(0.0f, 0.0f, PANEL_WIDTH, PANEL_HEIGHT);
    private final DeltaAnimation scroll = new DeltaAnimation();
    private final DeltaAnimation open = new DeltaAnimation();
    private final ModuleCategory category;
    private List<ModuleEntry> entries = List.of();
    private ModuleEntry hoveredEntry;

    public DeltaGuiPanel(ModuleCategory category) {
        this.category = category;
    }

    public boolean onMouseClick(double mouseX, double mouseY, int button) {
        for (ModuleEntry entry : this.entries) {
            if (entry.bindListening) {
                entry.module.setKey(-100 + button);
                entry.bindListening = false;
                return true;
            }
        }
        if (this.hoveredEntry != null) {
            if (button == 0) {
                this.hoveredEntry.module.toggle();
                return true;
            }
            if (button == 1) {
                this.hoveredEntry.extended = !this.hoveredEntry.extended;
                return true;
            }
            if (button == 2) {
                for (ModuleEntry entry : this.entries) {
                    entry.extended = entry == this.hoveredEntry && !entry.bindListening;
                }
                return true;
            }
        }
        return this.entries.stream()
                .filter(entry -> entry.extended)
                .flatMap(entry -> entry.elements.stream())
                .filter(DeltaElement::isEnabled)
                .anyMatch(element -> element.onMouseClick(mouseX, mouseY, button));
    }

    public boolean onMouseRelease(double mouseX, double mouseY, int button) {
        return this.entries.stream()
                .filter(entry -> entry.extended)
                .flatMap(entry -> entry.elements.stream())
                .filter(DeltaElement::isEnabled)
                .anyMatch(element -> element.onMouseRelease(mouseX, mouseY, button));
    }

    public boolean onMouseDrag(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        return this.entries.stream()
                .filter(entry -> entry.extended)
                .flatMap(entry -> entry.elements.stream())
                .filter(DeltaElement::isEnabled)
                .anyMatch(element -> element.onMouseDrag(mouseX, mouseY, button, deltaX, deltaY));
    }

    public boolean onKeyPress(int keyCode, int scanCode, int modifiers) {
        for (ModuleEntry entry : this.entries) {
            if (entry.bindListening) {
                entry.module.setKey(keyCode);
                entry.bindListening = false;
                return true;
            }
        }
        return this.entries.stream()
                .filter(entry -> entry.extended)
                .flatMap(entry -> entry.elements.stream())
                .filter(DeltaElement::isEnabled)
                .anyMatch(element -> element.onKeyPress(keyCode, scanCode, modifiers));
    }

    public boolean onCharTyped(char chr, int modifiers) {
        return this.entries.stream()
                .filter(entry -> entry.extended)
                .flatMap(entry -> entry.elements.stream())
                .filter(DeltaElement::isEnabled)
                .anyMatch(element -> element.onCharTyped(chr, modifiers));
    }

    public boolean onMouseScroll(double mouseX, double mouseY, double amount) {
        if (!DeltaMath.isHovered(mouseX, mouseY, this.bounds.x, this.bounds.y, this.bounds.z, this.bounds.w)) {
            return false;
        }
        if (this.entries.stream()
                .filter(entry -> entry.extended)
                .flatMap(entry -> entry.elements.stream())
                .filter(DeltaElement::isEnabled)
                .anyMatch(element -> element.onMouseScroll(mouseX, mouseY, amount))) {
            return true;
        }
        this.scroll.add((float) amount * 15.0f);
        return true;
    }

    public void setModules(List<Module> modules) {
        List<ModuleEntry> newEntries = new ArrayList<>();
        for (Module module : modules) {
            ModuleEntry existing = findEntry(module);
            if (existing != null) {
                newEntries.add(existing);
            } else {
                newEntries.add(new ModuleEntry(module));
            }
        }
        this.entries = newEntries;
    }

    private ModuleEntry findEntry(Module module) {
        for (ModuleEntry entry : this.entries) {
            if (entry.module == module) {
                return entry;
            }
        }
        return null;
    }

    public Vector4f getBounds() {
        return this.bounds;
    }

    public DeltaAnimation getScroll() {
        return this.scroll;
    }

    public DeltaAnimation getOpenAnimation() {
        return this.open;
    }

    public ModuleCategory getCategory() {
        return this.category;
    }

    public ModuleEntry getHovered() {
        return this.hoveredEntry;
    }

    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        MatrixStack matrices = context.getMatrices();
        var draw = Delta2DHolder.get();
        var fonts = DeltaFonts.ONEST_REGULAR.get();
        var icons = DeltaFonts.ICONS.get();
        float scale = 0.8f + (0.2f * DeltaEasing.BACK_OUT.ease(this.open.getAnimationValue()));
        matrices.push();
        matrices.translate(this.bounds.x + (this.bounds.z / 2.0f),
                this.bounds.y + (this.bounds.w / 2.0f) + ((1.0f - DeltaEasing.EXPO_OUT.ease(this.open.getAnimationValue())) * 14.0f), 0.0f);
        matrices.scale(scale, scale, 1.0f);
        matrices.translate(-(this.bounds.x + (this.bounds.z / 2.0f)), -(this.bounds.y + (this.bounds.w / 2.0f)), 0.0f);
        int background = ColorUtil.combineColorWithAlpha(
                ColorUtil.lerpColor(DeltaThemeInfo.BACKGROUND_GUI.resolve(), DeltaThemeInfo.PRIMARY.resolve(),
                        DeltaThemeInfo.PRIMARY.alphaFloat() / 4.0f), 200);
        draw.drawGlowBlur(matrices, this.bounds.x, this.bounds.y, this.bounds.z, this.bounds.w, 8.0f,
                background, 1.0f, background, 16.0f);
        draw.drawOutline(matrices, this.bounds.x, this.bounds.y, this.bounds.z, this.bounds.w, 8.0f, 0.5f,
                DeltaThemeInfo.OUTLINE_MEDIUM.resolve());
        renderHeader(matrices, fonts, icons, 24.0f);
        renderContent(context, mouseX, mouseY, this.bounds.y + 24.0f + 4.0f, delta);
        matrices.pop();
    }

    private void renderHeader(MatrixStack matrices, Font font, Font iconFont, float header) {
        float headerCenter = this.bounds.y + (header / 2.0f);
        float titleX = this.bounds.x + 6.0f + 4.0f;
        String title = categoryDisplayName();
        float iconWidth = iconFont.getWidth(categoryIcon(), 9.0f);
        float iconX = (((this.bounds.x + this.bounds.z) - 6.0f) - 4.0f) - iconWidth;
        int color = ColorUtil.lerpColor(ColorUtil.convertToARGB(255, 255, 255, 255),
                ColorUtil.applyAlphaToColor(DeltaThemeInfo.PRIMARY.resolve(), 1.0f), 0.25f);
        font.drawText(matrices, title, titleX, font.centerInkY(title, 9.0f, headerCenter), 9.0f, color);
        iconFont.drawText(matrices, categoryIcon(), iconX, iconFont.centerInkY(categoryIcon(), 9.0f, headerCenter), 9.0f, color);
    }

    private void renderContent(DrawContext context, int mouseX, int mouseY, float y, float delta) {
        MatrixStack matrices = context.getMatrices();
        var draw = Delta2DHolder.get();
        var fonts = DeltaFonts.ONEST_REGULAR.get();
        var icons = DeltaFonts.ICONS.get();
        float view = (((this.bounds.y + this.bounds.w) - 6.0f) - y) + 4.0f;
        float content = 0.0f;
        for (ModuleEntry entry : this.entries) {
            content += entryHeight(entry) + 4.0f;
        }
        float y2 = y + this.scroll.smooth(Math.min(0.0f, view - content), 0.0f, 1.0f);
        this.hoveredEntry = null;
        ScissorUtil.push(matrices, this.bounds.x, y, this.bounds.z, view);
        float bottom = y + view;
        for (ModuleEntry entry : this.entries) {
            entry.tickAnimations(delta);
            float center = y2 + 8.0f;
            float activation = entry.enableAnim.getAnimationValue();
            float fade = (float) Math.pow(DeltaMath.clamp01((bottom - y2) / 16.0f), 1.0);
            boolean hover = ((float) mouseY) >= y && ((float) mouseY) <= bottom
                    && DeltaMath.isHovered(mouseX, mouseY, this.bounds.x + 6.0f, y2, this.bounds.z - 12.0f, 16.0f);
            if (hover) {
                this.hoveredEntry = entry;
            }
            entry.extendAnim.tick(entry.extended);
            entry.extendAnim.update(0.0f, 1.0f, 0.5f, DeltaEasing.SINE_IN_OUT, delta);
            entry.hoverAnim.tick(entry == this.hoveredEntry);
            entry.hoverAnim.update(0.0f, 1.0f, 0.25f, DeltaEasing.SINE_IN_OUT, delta);
            float total = entryHeight(entry);
            if (y2 + total > y && y2 < bottom) {
                draw.drawRounded(matrices, this.bounds.x + 6.0f, y2, this.bounds.z - 12.0f, total, 4.0f,
                        ColorUtil.applyAlphaToColor(DeltaThemeInfo.PRIMARY.resolve(), 0.039215688f * activation * fade));
                draw.drawRounded(matrices, this.bounds.x + 6.0f, y2, this.bounds.z - 12.0f, total, 4.0f,
                        ColorUtil.applyAlphaToColor(ColorUtil.convertToARGB(255, 255, 255, 255),
                                0.023529412f * entry.hoverAnim.getAnimationValue() * fade));
                draw.drawOutline(matrices, this.bounds.x + 6.0f, y2, this.bounds.z - 12.0f, total, 4.0f, 0.5f,
                        ColorUtil.applyAlphaToColor(DeltaThemeInfo.OUTLINE_SMALL.resolve(),
                                DeltaThemeInfo.OUTLINE_SMALL.alphaFloat() * activation * fade));
                fonts.drawText(matrices, entry.module.getName(), this.bounds.x + 6.0f + 4.0f,
                        (center - (fonts.getHeight(7.25f) / 2.0f)) - 0.5f, 7.25f,
                        ColorUtil.applyAlphaToColor(DeltaThemeInfo.TEXT.resolve(), fade));
                entry.bindAnim.tick(entry.module.getKey() != -1 || entry.bindListening);
                entry.bindAnim.update(0.0f, 1.0f, 0.25f, DeltaEasing.SINE_IN_OUT, delta);
                if (entry.bindAnim.getAnimationValue() > 0.0f) {
                    float bind = entry.bindAnim.getAnimationValue();
                    String bindText = entry.bindListening ? "?" : KeyUtil.getKeyName(entry.module.getKey());
                    float iconWidth = icons.getWidth("C", 6.0f);
                    float boxWidth = 4.0f + iconWidth + 2.5f + fonts.getWidth(bindText, 6.0f) + 4.0f;
                    float boxX = this.bounds.x + 6.0f + 4.0f + fonts.getWidth(entry.module.getName(), 7.25f) + 4.0f;
                    float boxY = center - 4.5f;
                    draw.drawRounded(matrices, boxX, boxY, boxWidth, 9.0f, 2.0f,
                            ColorUtil.applyAlphaToColor(DeltaThemeInfo.PRIMARY.resolve(), 0.15686275f * bind));
                    draw.drawOutline(matrices, boxX, boxY, boxWidth, 9.0f, 2.0f, 0.5f,
                            ColorUtil.applyAlphaToColor(DeltaThemeInfo.OUTLINE_MEDIUM.resolve(),
                                    DeltaThemeInfo.OUTLINE_MEDIUM.alphaFloat() * bind));
                    icons.drawText(matrices, "C", boxX + 4.0f, icons.centerInkY("C", 6.0f, center), 6.0f,
                            ColorUtil.applyAlphaToColor(DeltaThemeInfo.TEXT.resolve(), bind));
                    fonts.drawText(matrices, bindText, boxX + 4.0f + iconWidth + 2.5f,
                            fonts.centerInkY(bindText, 6.0f, center), 6.0f,
                            ColorUtil.applyAlphaToColor(DeltaThemeInfo.TEXT.resolve(), bind));
                }
                if (!entry.elements.isEmpty() && entry.elements.stream().anyMatch(DeltaElement::isEnabled)) {
                    fonts.drawText(matrices, "...",
                            ((((this.bounds.x + this.bounds.z) - 6.0f) - 4.0f) - fonts.getWidth("...", 10.0f))
                                    - (activation > 0.0f ? 18.0f : 0.0f),
                            fonts.centerInkY("...", 10.0f, center), 10.0f,
                            ColorUtil.applyAlphaToColor(DeltaThemeInfo.TEXT_DISABLED.resolve(), fade));
                }
                if (activation > 0.0f) {
                    float toggleX = (((this.bounds.x + this.bounds.z) - 6.0f) - 4.0f) - 14.0f;
                    float toggleY = center - 4.25f;
                    draw.drawRounded(matrices, toggleX, toggleY, 14.0f, 8.5f, 3.25f,
                            ColorUtil.applyAlphaToColor(DeltaThemeInfo.PRIMARY.resolve(), 0.49019608f * activation * fade));
                    draw.drawOutline(matrices, toggleX, toggleY, 14.0f, 8.5f, 3.25f, 0.3f,
                            ColorUtil.applyAlphaToColor(DeltaThemeInfo.OUTLINE_SMALL.resolve(),
                                    DeltaThemeInfo.OUTLINE_SMALL.alphaFloat() * activation * fade));
                    draw.drawRounded(matrices, toggleX + 1.5f + (5.5f * activation), toggleY + 1.5f, 5.5f, 5.5f, 1.75f,
                            ColorUtil.applyAlphaToColor(
                                    ColorUtil.lerpColor(ColorUtil.convertToARGB(150, 150, 155, 255),
                                            ColorUtil.convertToARGB(255, 255, 255, 255), activation), activation * fade));
                }
                float extend = entry.extendAnim.getAnimationValue();
                if (extend > 0.0f) {
                    ScissorUtil.push(matrices, this.bounds.x + 6.0f, y2, this.bounds.z - 12.0f, total);
                    float baseY = (y2 + 16.0f) - (4.0f * (1.0f - extend));
                    float offset = 0.0f;
                    for (DeltaElement<?> element : entry.elements) {
                        element.getVisibilityAnimation().tick(element.isEnabled());
                        element.getVisibilityAnimation().update(0.0f, 1.0f, 0.4f, DeltaEasing.SINE_IN_OUT, delta);
                        float visible = element.getVisibilityAnimation().getAnimationValue();
                        if (visible > 0.0f) {
                            float targetY = (baseY + offset) - (4.0f * (1.0f - visible));
                            float currentY = baseY + ((targetY - baseY) * extend);
                            element.getBounds().set(this.bounds.x + 6.0f + 4.5f, currentY,
                                    (this.bounds.z - 12.0f) - 8.0f, element.getBounds().w());
                            element.render(context, mouseX, mouseY, delta, extend * visible);
                            offset += (element.getBounds().w() + 4.0f) * visible;
                        }
                    }
                    ScissorUtil.pop(matrices);
                }
            }
            y2 += total + 4.0f;
        }
        ScissorUtil.pop(matrices);
    }

    public void renderColorPickers(DrawContext context, double mouseX, double mouseY, float delta) {
        for (ModuleEntry entry : this.entries) {
            for (DeltaElement<?> element : entry.elements) {
                element.renderColorPicker(context, mouseX, mouseY, delta);
            }
        }
    }

    private float entryHeight(ModuleEntry entry) {
        return 16.0f + (entry.elements.isEmpty() ? 0.0f : ((float) entry.elements.stream()
                .mapToDouble(e -> (e.getBounds().w() + 4.0f) * e.getVisibilityAnimation().getAnimationValue()).sum())
                * entry.extendAnim.getAnimationValue());
    }

    private String categoryIcon() {
        return switch (this.category) {
            case COMBAT -> "V";
            case MOVEMENT -> "I";
            case RENDER -> "t";
            case PLAYER -> "L";
            case MISC -> "D";
        };
    }

    private String categoryDisplayName() {
        String name = this.category.name();
        return name.charAt(0) + name.substring(1).toLowerCase();
    }

    /**
     * Per-module GUI state (animations, expand flag, element widgets).
     */
    public static class ModuleEntry {
        public final Module module;
        public final List<DeltaElement<?>> elements = new ArrayList<>();
        final DeltaAnimation enableAnim = new DeltaAnimation();
        final DeltaAnimation extendAnim = new DeltaAnimation();
        final DeltaAnimation hoverAnim = new DeltaAnimation();
        final DeltaAnimation bindAnim = new DeltaAnimation();
        boolean extended;
        boolean bindListening;

        ModuleEntry(Module module) {
            this.module = module;
            for (Setting setting : module.getSettings()) {
                DeltaElement<?> element = createElement(setting);
                if (element != null) {
                    elements.add(element);
                }
            }
        }

        public void tickAnimations(float delta) {
            enableAnim.tick(module.isEnabled());
            enableAnim.update(0.0f, 1.0f, 0.3f, DeltaEasing.SINE_IN, delta);
        }
    }

    static DeltaElement<?> createElement(Setting setting) {
        if (setting instanceof BooleanSetting booleanSetting) {
            return new DeltaBooleanElement(booleanSetting);
        }
        if (setting instanceof SliderSetting sliderSetting) {
            return new DeltaSliderElement(sliderSetting);
        }
        if (setting instanceof ModeSetting modeSetting) {
            return new DeltaModeElement(modeSetting);
        }
        if (setting instanceof ModeListSetting modeListSetting) {
            return new DeltaModeListElement(modeListSetting);
        }
        if (setting instanceof BindSetting bindSetting) {
            return new DeltaBindElement(bindSetting);
        }
        if (setting instanceof ColorSetting colorSetting) {
            return new DeltaColorElement(colorSetting);
        }
        return null;
    }
}
