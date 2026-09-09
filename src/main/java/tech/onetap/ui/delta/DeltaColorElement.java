package tech.onetap.ui.delta;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Vector4f;
import tech.onetap.module.settings.ColorSetting;
import tech.onetap.util.render.ColorUtil;
import tech.onetap.util.render.Delta2DHolder;
import tech.onetap.util.render.DeltaEasing;
import tech.onetap.util.render.DeltaThemeInfo;
import tech.onetap.util.render.font.DeltaFonts;

import java.awt.Color;

/**
 * Delta-styled color widget ported from DeltaClient (aethereal.ui.element.ColorElement):
 * 11x11 swatch chip with icon + current color dot, expandable HSV picker rendered
 * above everything (saturation square, hue bar, alpha bar from pictures/*.png).
 */
public class DeltaColorElement extends DeltaElement<ColorSetting> {
    private final Vector4f pickerBackground = new Vector4f();
    private final Vector4f satArea = new Vector4f();
    private final Vector4f hueBar = new Vector4f();
    private final Vector4f alphaBar = new Vector4f();
    private float hue;
    private float saturation;
    private float brightness;
    private float alphaFraction;
    private DragMode dragMode;
    private boolean expanded;

    public DeltaColorElement(ColorSetting setting) {
        super(setting);
        this.dragMode = DragMode.NONE;
        this.bounds.w = 11.0f;
        initFromSetting();
    }

    @Override
    public boolean onMouseClick(double mouseX, double mouseY, int button) {
        Vector4f b = this.bounds;
        Vector4f sat = this.satArea;
        Vector4f hue = this.hueBar;
        Vector4f alpha = this.alphaBar;
        if (DeltaMath.isHovered(mouseX, mouseY, (b.x + b.z) - 11.0f, (b.y + (b.w / 2.0f)) - 5.0f, 11.0f, 11.0f)) {
            this.expanded = !this.expanded;
            return true;
        }
        if (!this.expanded || button != 0) {
            return false;
        }
        if (DeltaMath.isHovered(mouseX, mouseY, sat.x, sat.y, sat.z, sat.w)) {
            this.dragMode = DragMode.AREA;
            updateColorFromMouse(mouseX, mouseY);
            return true;
        }
        if (DeltaMath.isHovered(mouseX, mouseY, hue.x, hue.y, hue.z, hue.w)) {
            this.dragMode = DragMode.HUE;
            updateColorFromMouse(mouseX, mouseY);
            return true;
        }
        if (!DeltaMath.isHovered(mouseX, mouseY, alpha.x, alpha.y, alpha.z, alpha.w)) {
            return false;
        }
        this.dragMode = DragMode.ALPHA;
        updateColorFromMouse(mouseX, mouseY);
        return true;
    }

    @Override
    public boolean onMouseRelease(double mouseX, double mouseY, int button) {
        this.dragMode = DragMode.NONE;
        return false;
    }

    @Override
    public void render(DrawContext context, double mouseX, double mouseY, float delta, float extend) {
        MatrixStack matrices = context.getMatrices();
        var draw = Delta2DHolder.get();
        var fonts = DeltaFonts.SF_REGULAR.get();
        var icons = DeltaFonts.ICONS.get();
        float centerY = this.bounds.y + (this.bounds.w / 2.0f) + 0.5f;
        float boxX = (this.bounds.x + this.bounds.z) - 11.0f;
        float boxY = centerY - 5.5f;
        this.satArea.set(this.bounds.x + this.bounds.z + 6.0f + 5.0f, boxY, 56.0f, 56.0f);
        this.hueBar.set(this.satArea.x + 56.0f + 5.0f, this.satArea.y, 4.0f, 56.0f);
        this.alphaBar.set(this.hueBar.x + 4.0f + 5.0f, this.satArea.y, 4.0f, 56.0f);
        this.pickerBackground.set(this.satArea.x - 5.0f, this.satArea.y - 5.0f, 84.0f, 66.0f);
        boolean hovered = DeltaMath.isHovered(mouseX, mouseY, this.bounds.x, this.bounds.y, this.bounds.z, this.bounds.w)
                && extend >= 1.0f;
        if (extend < 1.0f) {
            this.expanded = false;
        }
        drawLabel(matrices, fonts, this.setting.getName(), this.bounds.x, this.bounds.y, this.bounds.w, 6.5f,
                DeltaThemeInfo.TEXT.resolve(), (boxX - this.bounds.x) - 4.0f, hovered, extend, delta);
        draw.drawRounded(matrices, boxX, boxY, 11.0f, 11.0f, 2.0f,
                ColorUtil.applyAlphaToColor(DeltaThemeInfo.PRIMARY.resolve(), 0.039215688f * extend));
        draw.drawOutline(matrices, boxX, boxY, 11.0f, 11.0f, 2.0f, 0.5f,
                ColorUtil.applyAlphaToColor(DeltaThemeInfo.OUTLINE_MEDIUM.resolve(),
                        DeltaThemeInfo.OUTLINE_MEDIUM.alphaFloat() * extend));
        icons.drawText(matrices, "J", boxX + ((11.0f - icons.getWidth("J", 6.5f)) / 2.0f),
                icons.centerInkY("J", 6.5f, centerY), 6.5f,
                ColorUtil.applyAlphaToColor(DeltaThemeInfo.PRIMARY.resolve(), extend));
        draw.drawRounded(matrices, ((boxX + 11.0f) - 3.0f) - 1.25f, ((boxY + 11.0f) - 3.0f) - 1.25f,
                3.0f, 3.0f, 0.5f, ColorUtil.applyAlphaToColor(this.setting.getValue(), extend));
    }

    @Override
    public void renderColorPicker(DrawContext context, double mouseX, double mouseY, float delta) {
        getActivationAnimation().tick(this.expanded);
        getActivationAnimation().update(0.0f, 1.0f, 0.25f, DeltaEasing.EXPO_OUT, delta);
        float anim = DeltaEasing.EXPO_OUT.ease(getActivationAnimation().getAnimationValue());
        if (anim > 0.0f) {
            MatrixStack matrices = context.getMatrices();
            var draw = Delta2DHolder.get();
            updateColorFromMouse(mouseX, mouseY);
            int hueColor = Color.HSBtoRGB(this.hue, 1.0f, 1.0f);
            int rgb = this.setting.getValue() & 0xFFFFFF;
            int handle = ColorUtil.applyAlphaToColor(0xFFFFFF, anim);
            int background = ColorUtil.applyAlphaToColor(ColorUtil.lerpColor(DeltaThemeInfo.BACKGROUND_GUI.resolve(),
                    DeltaThemeInfo.PRIMARY.resolve(), 0.05f), 0.8235294f * anim);
            float scale = 0.85f + (0.15f * DeltaEasing.BACK_OUT.ease(getActivationAnimation().getAnimationValue()));
            float centerX = this.pickerBackground.x + (this.pickerBackground.z / 2.0f);
            float centerY = this.pickerBackground.y + (this.pickerBackground.w / 2.0f);
            matrices.push();
            matrices.translate(centerX, centerY + ((1.0f - anim) * 6.0f), 0.0f);
            matrices.scale(scale, scale, 1.0f);
            matrices.translate(-centerX, -centerY, 0.0f);
            draw.drawShadowBlur(matrices, this.pickerBackground.x, this.pickerBackground.y,
                    this.pickerBackground.z, this.pickerBackground.w, 4.0f, background, anim);
            draw.drawOutline(matrices, this.pickerBackground.x, this.pickerBackground.y,
                    this.pickerBackground.z, this.pickerBackground.w, 4.0f, 0.5f,
                    ColorUtil.applyAlphaToColor(DeltaThemeInfo.OUTLINE_MEDIUM.resolve(),
                            DeltaThemeInfo.OUTLINE_MEDIUM.alphaFloat() * anim));
            draw.drawGradient(matrices, this.satArea.x, this.satArea.y, this.satArea.z, this.satArea.w, 2.0f,
                    ColorUtil.applyAlphaToColor(0xFFFFFF, anim), ColorUtil.applyAlphaToColor(hueColor, anim),
                    ColorUtil.applyAlphaToColor(0, anim), ColorUtil.applyAlphaToColor(0, anim));
            draw.drawOutline(matrices, this.satArea.x, this.satArea.y, this.satArea.z, this.satArea.w, 2.0f, 0.5f,
                    ColorUtil.applyAlphaToColor(DeltaThemeInfo.OUTLINE_SMALL.resolve(),
                            DeltaThemeInfo.OUTLINE_SMALL.alphaFloat() * anim));
            float cursorX = DeltaMath.clamp(this.satArea.x + (this.saturation * this.satArea.z),
                    this.satArea.x + 2.0f, (this.satArea.x + this.satArea.z) - 2.0f);
            float cursorY = DeltaMath.clamp(this.satArea.y + ((1.0f - this.brightness) * this.satArea.w),
                    this.satArea.y + 2.0f, (this.satArea.y + this.satArea.w) - 2.0f);
            draw.drawOutline(matrices, cursorX - 2.0f, cursorY - 2.0f, 4.0f, 4.0f, 1.0f, 0.5f, handle);
            float knob = this.hueBar.z + 2.0f;
            draw.drawTexture(matrices, Identifier.of("mre", "pictures/color.png"), this.hueBar.x, this.hueBar.y,
                    this.hueBar.z, this.hueBar.w, this.hueBar.z / 4.0f, ColorUtil.applyAlphaToColor(0xFFFFFF, anim));
            drawFlat(context, this.hueBar.x - 1.0f, (this.hueBar.y + (this.hue * this.hueBar.w)) - 0.5f, knob, 1.0f, handle);
            draw.drawTexture(matrices, Identifier.of("mre", "pictures/opacity.png"), this.alphaBar.x, this.alphaBar.y,
                    this.alphaBar.z, this.alphaBar.w, this.alphaBar.z / 4.0f, ColorUtil.applyAlphaToColor(0xFFFFFF, 0.019607844f * anim));
            draw.drawGradient(matrices, this.alphaBar.x, this.alphaBar.y, this.alphaBar.z, this.alphaBar.w,
                    this.alphaBar.z / 4.0f, ColorUtil.applyAlphaToColor(rgb, anim), ColorUtil.applyAlphaToColor(rgb, anim),
                    ColorUtil.applyAlphaToColor(rgb, 0.0f), ColorUtil.applyAlphaToColor(rgb, 0.0f));
            drawFlat(context, this.alphaBar.x - 1.0f, (this.alphaBar.y + ((1.0f - this.alphaFraction) * this.alphaBar.w)) - 0.5f,
                    knob, 1.0f, handle);
            matrices.pop();
        }
    }

    private void drawFlat(DrawContext context, float x, float y, float width, float height, int color) {
        context.getMatrices().push();
        context.getMatrices().translate(x, y, 0.0f);
        context.getMatrices().scale(width, height, 1.0f);
        context.fill(0, 0, 1, 1, color);
        context.getMatrices().pop();
    }

    private void updateColorFromMouse(double mouseX, double mouseY) {
        switch (this.dragMode) {
            case DragMode.NONE:
                return;
            case DragMode.AREA:
                this.saturation = DeltaMath.clamp(((float) (mouseX - this.satArea.x)) / this.satArea.z, 0.0f, 1.0f);
                this.brightness = 1.0f - DeltaMath.clamp(((float) (mouseY - this.satArea.y)) / this.satArea.w, 0.0f, 1.0f);
                break;
            case DragMode.HUE:
                this.hue = DeltaMath.clamp(((float) (mouseY - this.hueBar.y)) / this.hueBar.w, 0.0f, 1.0f);
                break;
            case DragMode.ALPHA:
                this.alphaFraction = 1.0f - DeltaMath.clamp(((float) (mouseY - this.alphaBar.y)) / this.alphaBar.w, 0.0f, 1.0f);
                break;
        }
        this.setting.setValue(ColorUtil.applyAlphaToColor(Color.HSBtoRGB(this.hue, this.saturation, this.brightness),
                this.alphaFraction));
    }

    private void initFromSetting() {
        int color = this.setting.getValue();
        float[] hsb = Color.RGBtoHSB((color >> 16) & 255, (color >> 8) & 255, color & 255, null);
        this.hue = hsb[0];
        this.saturation = hsb[1];
        this.brightness = hsb[2];
        this.alphaFraction = ((color >> 24) & 255) / 255.0f;
    }

    enum DragMode {
        NONE,
        AREA,
        HUE,
        ALPHA
    }
}
