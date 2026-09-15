package tech.onetap.ui.delta;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Vector4f;
import tech.onetap.module.settings.impl.ThemeManager;
import tech.onetap.util.render.ColorUtil;
import tech.onetap.util.render.Draw2D;
import tech.onetap.util.render.Delta2DHolder;
import tech.onetap.util.render.DeltaAnimation;
import tech.onetap.util.render.DeltaEasing;
import tech.onetap.util.render.DeltaThemeInfo;
import tech.onetap.util.render.ScissorUtil;
import tech.onetap.util.render.font.DeltaFonts;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Отдельная панель тем Delta-гуй (аналог ThemeManagerWindow в dropdown-режиме):
 * список стандартных и кастомных тем, клик — применить, «+» — добавить свою,
 * ПКМ по кастомной — два HSV-пикера. Открывается кнопкой в настройках модуля
 * Interface и иконкой у поля поиска.
 */
public class DeltaThemePanel {
    private static final float WIDTH = 125.0f;
    private static final float HEIGHT = 270.0f;
    private static final float HEADER = 24.0f;
    private static final float ROW_HEIGHT = 16.0f;
    private static final float EDIT_HEIGHT = 46.0f;
    private static final float PICKER_SIZE = 40.0f;
    private static final float HUE_WIDTH = 4.0f;
    private static final Identifier HUE_TEXTURE = Identifier.of("mre", "pictures/color.png");

    private final Vector4f bounds = new Vector4f(0.0f, 0.0f, WIDTH, HEIGHT);
    private final Vector4f addRect = new Vector4f();
    private final DeltaAnimation open = new DeltaAnimation();
    private final DeltaAnimation scroll = new DeltaAnimation();
    private final DeltaAnimation addHoverAnim = new DeltaAnimation();
    private final List<ThemeRow> rows = new ArrayList<>();
    private boolean openState;
    private ThemeRow hoveredRow;

    public boolean isOpen() {
        return this.openState;
    }

    public void setOpen(boolean open) {
        this.openState = open;
    }

    public void render(DrawContext context, double mouseX, double mouseY, float delta, float panelLeft, float panelTop) {
        this.open.tick(this.openState);
        this.open.update(0.0f, 1.0f, 0.3f, DeltaEasing.SINE_IN_OUT, delta);
        float anim = DeltaEasing.EXPO_OUT.ease(this.open.getAnimationValue());
        if (anim <= 0.01f) {
            return;
        }
        MatrixStack matrices = context.getMatrices();
        Draw2D draw = Delta2DHolder.get();
        this.bounds.set(panelLeft - WIDTH - 8.0f, panelTop, WIDTH, HEIGHT);
        matrices.push();
        matrices.translate((WIDTH + 8.0f) * (1.0f - anim), 0.0f, 0.0f);
        int background = ColorUtil.combineColorWithAlpha(
                ColorUtil.lerpColor(DeltaThemeInfo.BACKGROUND_GUI.resolve(), DeltaThemeInfo.PRIMARY.resolve(),
                        DeltaThemeInfo.PRIMARY.alphaFloat() / 4.0f), (int) (200 * anim));
        draw.drawGlowBlur(matrices, this.bounds.x, this.bounds.y, WIDTH, HEIGHT, 8.0f, background, anim, background, 16.0f);
        draw.drawOutline(matrices, this.bounds.x, this.bounds.y, WIDTH, HEIGHT, 8.0f, 0.5f,
                ColorUtil.applyAlphaToColor(DeltaThemeInfo.OUTLINE_MEDIUM.resolve(),
                        DeltaThemeInfo.OUTLINE_MEDIUM.alphaFloat() * anim));
        renderHeader(matrices, draw, mouseX, mouseY, delta, anim);
        renderContent(context, draw, mouseX, mouseY, delta, anim);
        matrices.pop();
    }

    private void renderHeader(MatrixStack matrices, Draw2D draw, double mouseX, double mouseY,
                              float delta, float anim) {
        var fonts = DeltaFonts.ONEST_REGULAR.get();
        var sf = DeltaFonts.SF_REGULAR.get();
        float headerCenter = this.bounds.y + (HEADER / 2.0f);
        int titleColor = ColorUtil.lerpColor(ColorUtil.convertToARGB(255, 255, 255, 255),
                DeltaThemeInfo.PRIMARY.resolve(), 0.25f);
        fonts.drawText(matrices, "Темы", this.bounds.x + 10.0f,
                fonts.centerInkY("Темы", 9.0f, headerCenter), 9.0f,
                ColorUtil.applyAlphaToColor(titleColor, anim));
        float plusW = sf.getWidth("+", 10.0f);
        float plusX = ((this.bounds.x + WIDTH) - 6.0f) - 4.0f - plusW;
        this.addRect.set(plusX - 4.0f, headerCenter - 7.0f, plusW + 8.0f, 14.0f);
        boolean hoverAdd = DeltaMath.isHovered(mouseX, mouseY, this.addRect.x, this.addRect.y, this.addRect.z, this.addRect.w);
        this.addHoverAnim.tick(hoverAdd);
        this.addHoverAnim.update(0.0f, 1.0f, 0.25f, DeltaEasing.SINE_IN_OUT, delta);
        int plusColor = ColorUtil.lerpColor(DeltaThemeInfo.TEXT_DISABLED.resolve(), DeltaThemeInfo.TEXT.resolve(),
                this.addHoverAnim.getAnimationValue());
        sf.drawText(matrices, "+", plusX, sf.centerInkY("+", 10.0f, headerCenter), 10.0f,
                ColorUtil.applyAlphaToColor(plusColor, anim));
    }

    private void renderContent(DrawContext context, Draw2D draw, double mouseX, double mouseY,
                               float delta, float anim) {
        MatrixStack matrices = context.getMatrices();
        var fonts = DeltaFonts.SF_REGULAR.get();
        float y0 = this.bounds.y + HEADER + 4.0f;
        float view = (((this.bounds.y + HEIGHT) - 6.0f) - y0) + 4.0f;
        syncRows();
        float content = 0.0f;
        for (ThemeRow row : this.rows) {
            content += rowHeight(row) + 4.0f;
        }
        float y = y0 + this.scroll.smooth(Math.min(0.0f, view - content), 0.0f, 1.0f);
        this.hoveredRow = null;
        float bottom = y0 + view;
        ScissorUtil.push(matrices, this.bounds.x, y0, WIDTH, view);
        for (ThemeRow row : this.rows) {
            float total = rowHeight(row);
            boolean hover = (float) mouseY >= y0 && (float) mouseY <= bottom
                    && DeltaMath.isHovered(mouseX, mouseY, this.bounds.x + 6.0f, y, WIDTH - 12.0f, ROW_HEIGHT);
            if (hover) {
                this.hoveredRow = row;
            }
            row.hoverAnim.tick(hover);
            row.hoverAnim.update(0.0f, 1.0f, 0.25f, DeltaEasing.SINE_IN_OUT, delta);
            row.activeAnim.tick(isActive(row));
            row.activeAnim.update(0.0f, 1.0f, 0.3f, DeltaEasing.SINE_IN_OUT, delta);
            row.editAnim.tick(row.expanded);
            row.editAnim.update(0.0f, 1.0f, 0.4f, DeltaEasing.SINE_IN_OUT, delta);
            float active = row.activeAnim.getAnimationValue();
            if (y + total > y0 && y < bottom) {
                float center = y + 8.0f;
                float fade = DeltaMath.clamp01((bottom - y) / 16.0f);
                draw.drawRounded(matrices, this.bounds.x + 6.0f, y, WIDTH - 12.0f, total, 4.0f,
                        ColorUtil.applyAlphaToColor(DeltaThemeInfo.PRIMARY.resolve(), 0.039215688f * active * fade * anim));
                draw.drawRounded(matrices, this.bounds.x + 6.0f, y, WIDTH - 12.0f, total, 4.0f,
                        ColorUtil.applyAlphaToColor(ColorUtil.convertToARGB(255, 255, 255, 255),
                                0.023529412f * row.hoverAnim.getAnimationValue() * fade * anim));
                draw.drawOutline(matrices, this.bounds.x + 6.0f, y, WIDTH - 12.0f, total, 4.0f, 0.5f,
                        ColorUtil.applyAlphaToColor(DeltaThemeInfo.OUTLINE_SMALL.resolve(),
                                DeltaThemeInfo.OUTLINE_SMALL.alphaFloat() * active * fade * anim));
                int nameColor = ColorUtil.lerpColor(DeltaThemeInfo.TEXT_DISABLED.resolve(),
                        DeltaThemeInfo.TEXT.resolve(), active);
                fonts.drawText(matrices, row.name, this.bounds.x + 10.0f,
                        (center - (fonts.getHeight(7.25f) / 2.0f)) - 0.5f, 7.25f,
                        ColorUtil.applyAlphaToColor(nameColor, fade * anim));
                float dot2X = (((this.bounds.x + WIDTH) - 6.0f) - 4.0f) - 5.5f;
                float dot1X = dot2X - 5.5f - 1.5f;
                float dotY = center - 2.75f;
                draw.drawRounded(matrices, dot1X, dotY, 5.5f, 5.5f, 1.75f,
                        ColorUtil.applyAlphaToColor(row.color1, fade * anim));
                draw.drawRounded(matrices, dot2X, dotY, 5.5f, 5.5f, 1.75f,
                        ColorUtil.applyAlphaToColor(row.color2, fade * anim));
                if (row.custom) {
                    float dotsX = dot1X - 3.0f - fonts.getWidth("...", 9.0f);
                    fonts.drawText(matrices, "...", dotsX, fonts.centerInkY("...", 9.0f, center), 9.0f,
                            ColorUtil.applyAlphaToColor(DeltaThemeInfo.TEXT_DISABLED.resolve(), fade * anim));
                }
                float edit = row.editAnim.getAnimationValue();
                if (edit > 0.0f) {
                    ScissorUtil.push(matrices, this.bounds.x + 6.0f, y, WIDTH - 12.0f, total);
                    float editorY = (y + ROW_HEIGHT + 2.0f) - (2.0f * (1.0f - edit));
                    layoutPickers(row, this.bounds.x + 10.5f, editorY);
                    if (row.dragMode != DragMode.NONE) {
                        updateColorFromMouse(row, mouseX, mouseY);
                    }
                    renderPicker(matrices, draw, row, 1, edit * anim);
                    renderPicker(matrices, draw, row, 2, edit * anim);
                    ScissorUtil.pop(matrices);
                }
            }
            y += total + 4.0f;
        }
        ScissorUtil.pop(matrices);
    }

    private void layoutPickers(ThemeRow row, float x, float y) {
        float pickerW = PICKER_SIZE + 5.0f + HUE_WIDTH;
        float gap = 6.0f;
        row.sv1.set(x, y, PICKER_SIZE, PICKER_SIZE);
        row.hue1.set(x + PICKER_SIZE + 5.0f, y, HUE_WIDTH, PICKER_SIZE);
        row.sv2.set(x + pickerW + gap, y, PICKER_SIZE, PICKER_SIZE);
        row.hue2.set(x + pickerW + gap + PICKER_SIZE + 5.0f, y, HUE_WIDTH, PICKER_SIZE);
    }

    private void renderPicker(MatrixStack matrices, Draw2D draw, ThemeRow row, int index, float alpha) {
        Vector4f sv = index == 1 ? row.sv1 : row.sv2;
        Vector4f hue = index == 1 ? row.hue1 : row.hue2;
        float[] hsv = index == 1 ? row.hsv1 : row.hsv2;
        int hueColor = Color.HSBtoRGB(hsv[0], 1.0f, 1.0f);
        draw.drawGradient(matrices, sv.x, sv.y, sv.z, sv.w, 2.0f,
                ColorUtil.applyAlphaToColor(0xFFFFFF, alpha), ColorUtil.applyAlphaToColor(hueColor, alpha),
                ColorUtil.applyAlphaToColor(0, alpha), ColorUtil.applyAlphaToColor(0, alpha));
        draw.drawOutline(matrices, sv.x, sv.y, sv.z, sv.w, 2.0f, 0.5f,
                ColorUtil.applyAlphaToColor(DeltaThemeInfo.OUTLINE_SMALL.resolve(),
                        DeltaThemeInfo.OUTLINE_SMALL.alphaFloat() * alpha));
        float cursorX = DeltaMath.clamp(sv.x + (hsv[1] * sv.z), sv.x + 2.0f, (sv.x + sv.z) - 2.0f);
        float cursorY = DeltaMath.clamp(sv.y + ((1.0f - hsv[2]) * sv.w), sv.y + 2.0f, (sv.y + sv.w) - 2.0f);
        draw.drawOutline(matrices, cursorX - 2.0f, cursorY - 2.0f, 4.0f, 4.0f, 1.0f, 0.5f,
                ColorUtil.applyAlphaToColor(0xFFFFFF, alpha));
        draw.drawTexture(matrices, HUE_TEXTURE, hue.x, hue.y, hue.z, hue.w, hue.z / 4.0f,
                ColorUtil.applyAlphaToColor(0xFFFFFF, alpha));
        draw.drawRounded(matrices, hue.x - 1.0f, (hue.y + (hsv[0] * hue.w)) - 0.5f, hue.z + 2.0f, 1.0f, 0.0f,
                ColorUtil.applyAlphaToColor(0xFFFFFF, alpha));
    }

    public boolean onMouseClick(double mouseX, double mouseY, int button) {
        if (!this.openState || this.open.getAnimationValue() < 0.9f) {
            return false;
        }
        if (!DeltaMath.isHovered(mouseX, mouseY, this.bounds.x, this.bounds.y, WIDTH, HEIGHT)) {
            return false;
        }
        if (button == 0 && DeltaMath.isHovered(mouseX, mouseY, this.addRect.x, this.addRect.y, this.addRect.z, this.addRect.w)) {
            addCustomTheme();
            return true;
        }
        if (this.hoveredRow != null) {
            if (button == 0) {
                applyTheme(this.hoveredRow);
                return true;
            }
            if (button == 1 && this.hoveredRow.custom) {
                this.hoveredRow.expanded = !this.hoveredRow.expanded;
                if (this.hoveredRow.expanded) {
                    ThemeRow.updateHSV(this.hoveredRow.hsv1, this.hoveredRow.color1);
                    ThemeRow.updateHSV(this.hoveredRow.hsv2, this.hoveredRow.color2);
                }
                return true;
            }
        }
        for (ThemeRow row : this.rows) {
            if (row.editAnim.getAnimationValue() > 0.5f && button == 0 && tryStartPickerDrag(row, mouseX, mouseY)) {
                return true;
            }
        }
        return true;
    }

    public void onMouseRelease(double mouseX, double mouseY, int button) {
        for (ThemeRow row : this.rows) {
            if (row.dragMode != DragMode.NONE) {
                row.dragMode = DragMode.NONE;
                saveThemes();
            }
        }
    }

    public boolean onMouseScroll(double mouseX, double mouseY, double amount) {
        if (!this.openState || this.open.getAnimationValue() < 0.9f) {
            return false;
        }
        if (!DeltaMath.isHovered(mouseX, mouseY, this.bounds.x, this.bounds.y + HEADER, WIDTH, HEIGHT - HEADER)) {
            return false;
        }
        this.scroll.add((float) amount * 15.0f);
        return true;
    }

    private boolean tryStartPickerDrag(ThemeRow row, double mouseX, double mouseY) {
        for (int index = 1; index <= 2; index++) {
            Vector4f sv = index == 1 ? row.sv1 : row.sv2;
            Vector4f hue = index == 1 ? row.hue1 : row.hue2;
            if (DeltaMath.isHovered(mouseX, mouseY, sv.x, sv.y, sv.z, sv.w)) {
                row.dragMode = DragMode.AREA;
                row.dragIndex = index;
                updateColorFromMouse(row, mouseX, mouseY);
                return true;
            }
            if (DeltaMath.isHovered(mouseX, mouseY, hue.x, hue.y, hue.z, hue.w)) {
                row.dragMode = DragMode.HUE;
                row.dragIndex = index;
                updateColorFromMouse(row, mouseX, mouseY);
                return true;
            }
        }
        return false;
    }

    private void updateColorFromMouse(ThemeRow row, double mouseX, double mouseY) {
        Vector4f sv = row.dragIndex == 1 ? row.sv1 : row.sv2;
        Vector4f hue = row.dragIndex == 1 ? row.hue1 : row.hue2;
        float[] hsv = row.dragIndex == 1 ? row.hsv1 : row.hsv2;
        switch (row.dragMode) {
            case AREA -> {
                hsv[1] = DeltaMath.clamp((float) ((mouseX - sv.x) / sv.z), 0.0f, 1.0f);
                hsv[2] = 1.0f - DeltaMath.clamp((float) ((mouseY - sv.y) / sv.w), 0.0f, 1.0f);
            }
            case HUE -> hsv[0] = DeltaMath.clamp((float) ((mouseY - hue.y) / hue.w), 0.0f, 1.0f);
            case NONE -> {
                return;
            }
        }
        int color = Color.HSBtoRGB(hsv[0], hsv[1], hsv[2]) | 0xFF000000;
        if (row.dragIndex == 1) {
            row.color1 = color;
        } else {
            row.color2 = color;
        }
        if (isActive(row)) {
            ThemeManager.getInstance().getCurrentTheme().setColors(row.color1, row.color2);
        }
    }

    private void applyTheme(ThemeRow row) {
        ThemeManager manager = ThemeManager.getInstance();
        manager.getCurrentTheme().setColors(row.color1, row.color2);
        saveThemes(row.name);
    }

    private void addCustomTheme() {
        int index = 1;
        for (ThemeRow row : this.rows) {
            if (row.custom) {
                index++;
            }
        }
        this.rows.add(new ThemeRow("Custom " + index, true,
                ColorUtil.convertToARGB(133, 156, 255, 255), ColorUtil.convertToARGB(25, 30, 70, 255)));
        ThemeManager.ThemePreset active = ThemeManager.getInstance().getActivePreset();
        saveThemes(active == null ? null : active.name());
        this.scroll.add(-4096.0f);
    }

    private void saveThemes() {
        ThemeManager.ThemePreset active = ThemeManager.getInstance().getActivePreset();
        saveThemes(active == null ? null : active.name());
    }

    private void saveThemes(String activeName) {
        if (activeName == null) {
            activeName = ThemeManager.DEFAULT_THEMES.getFirst().name();
        }
        List<ThemeManager.ThemePreset> customs = new ArrayList<>();
        for (ThemeRow row : this.rows) {
            if (row.custom) {
                customs.add(new ThemeManager.ThemePreset(row.name, row.color1, row.color2));
            }
        }
        ThemeManager.getInstance().saveThemes(customs, activeName);
    }

    private void syncRows() {
        ThemeManager manager = ThemeManager.getInstance();
        List<ThemeManager.ThemePreset> presets = new ArrayList<>(ThemeManager.DEFAULT_THEMES);
        presets.addAll(manager.getCustomThemes());
        Set<String> customNames = new HashSet<>();
        for (ThemeManager.ThemePreset preset : manager.getCustomThemes()) {
            customNames.add(preset.name());
        }
        this.rows.removeIf(row -> presets.stream().noneMatch(preset -> preset.name().equals(row.name)));
        for (ThemeManager.ThemePreset preset : presets) {
            ThemeRow existing = null;
            for (ThemeRow row : this.rows) {
                if (row.name.equals(preset.name())) {
                    existing = row;
                    break;
                }
            }
            if (existing == null) {
                this.rows.add(new ThemeRow(preset.name(), customNames.contains(preset.name()),
                        preset.color1(), preset.color2()));
            } else if (existing.dragMode == DragMode.NONE) {
                existing.color1 = preset.color1();
                existing.color2 = preset.color2();
            }
        }
    }

    private boolean isActive(ThemeRow row) {
        ThemeManager.ThemePreset active = ThemeManager.getInstance().getActivePreset();
        return active != null && active.name().equals(row.name);
    }

    private float rowHeight(ThemeRow row) {
        return ROW_HEIGHT + (row.custom ? EDIT_HEIGHT * row.editAnim.getAnimationValue() : 0.0f);
    }

    private enum DragMode {
        NONE,
        AREA,
        HUE
    }

    private static final class ThemeRow {
        final String name;
        final boolean custom;
        final DeltaAnimation activeAnim = new DeltaAnimation();
        final DeltaAnimation hoverAnim = new DeltaAnimation();
        final DeltaAnimation editAnim = new DeltaAnimation();
        final Vector4f sv1 = new Vector4f();
        final Vector4f hue1 = new Vector4f();
        final Vector4f sv2 = new Vector4f();
        final Vector4f hue2 = new Vector4f();
        final float[] hsv1 = new float[3];
        final float[] hsv2 = new float[3];
        int color1;
        int color2;
        boolean expanded;
        DragMode dragMode = DragMode.NONE;
        int dragIndex;

        ThemeRow(String name, boolean custom, int color1, int color2) {
            this.name = name;
            this.custom = custom;
            this.color1 = color1;
            this.color2 = color2;
            updateHSV(this.hsv1, color1);
            updateHSV(this.hsv2, color2);
        }

        static void updateHSV(float[] hsv, int rgb) {
            Color c = new Color(rgb, true);
            Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), hsv);
        }
    }
}
