package tech.onetap.ui.delta;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Vector4f;
import tech.onetap.module.settings.SliderSetting;
import tech.onetap.util.render.ColorUtil;
import tech.onetap.util.render.Delta2DHolder;
import tech.onetap.util.render.DeltaEasing;
import tech.onetap.util.render.DeltaThemeInfo;
import tech.onetap.util.render.font.DeltaFonts;

/**
 * Delta-styled slider widget ported from DeltaClient (aethereal.ui.element.SliderElement):
 * label + value chip, 3px track with white 6px knob, drag/scroll editing with step rounding.
 */
public class DeltaSliderElement extends DeltaElement<SliderSetting> {
    private boolean isDragging;

    public DeltaSliderElement(SliderSetting setting) {
        super(setting);
    }

    @Override
    public boolean onMouseClick(double mouseX, double mouseY, int button) {
        Vector4f b = this.bounds;
        float font = DeltaFonts.SF_REGULAR.get().getHeight(6.5f);
        if (!DeltaMath.isHovered(mouseX, mouseY, b.x, b.y + font, b.z, 11.0f)) {
            return false;
        }
        if (button == 0) {
            this.isDragging = true;
            updateSliderFromMouse(mouseX);
            return true;
        }
        if (button != 2) {
            return false;
        }
        this.setting.setKey(-1);
        return true;
    }

    @Override
    public boolean onMouseRelease(double mouseX, double mouseY, int button) {
        this.isDragging = false;
        return false;
    }

    @Override
    public void render(DrawContext context, double mouseX, double mouseY, float delta, float extend) {
        MatrixStack matrices = context.getMatrices();
        var draw = Delta2DHolder.get();
        var fonts = DeltaFonts.SF_REGULAR.get();
        this.bounds.w = 18.0f;
        if (this.isDragging) {
            updateSliderFromMouse(mouseX);
        }
        float min = (float) this.setting.getMin();
        float max = (float) this.setting.getMax();
        getActivationAnimation().setCurrentValue(DeltaMath.clamp(
                (getActivationAnimation().getCurrentValue() + ((this.setting.getFloatValue() - min) / (max - min))) * 0.5f, 0.0f, 1.0f));
        getActivationAnimation().update(0.0f, 1.0f, 0.0f, DeltaEasing.LINEAR, delta);
        getActivationAnimation().setCurrentValue((this.setting.getFloatValue() - min) / (max - min));
        float progress = getActivationAnimation().getCurrentValue();
        boolean hovered = DeltaMath.isHovered(mouseX, mouseY, this.bounds.x, this.bounds.y, this.bounds.z, this.bounds.w)
                && extend >= 1.0f;
        float current = min + ((max - min) * progress);
        String value = this.setting.getStep() % 1.0f == 0.0f
                ? String.valueOf(Math.round(current))
                : String.valueOf(Math.round(current * 100.0f) / 100.0f);
        float boxWidth = fonts.getWidth(value, 6.25f) + 6.0f;
        float boxHeight = fonts.getHeight(6.25f) + 2.0f;
        float boxX = (this.bounds.x + this.bounds.z) - boxWidth;
        drawLabel(matrices, fonts, this.setting.getName(), this.bounds.x, this.bounds.y + 0.5f,
                fonts.getHeight(6.5f), 6.5f, DeltaThemeInfo.TEXT.resolve(), (boxX - this.bounds.x) - 4.0f,
                hovered, extend, delta);
        draw.drawRounded(matrices, boxX, this.bounds.y, boxWidth, boxHeight, 2.0f,
                ColorUtil.applyAlphaToColor(DeltaThemeInfo.PRIMARY.resolve(), 0.03137255f * extend));
        draw.drawOutline(matrices, boxX, this.bounds.y, boxWidth, boxHeight, 2.0f, 0.5f,
                ColorUtil.applyAlphaToColor(DeltaThemeInfo.OUTLINE_SMALL.resolve(),
                        DeltaThemeInfo.OUTLINE_SMALL.alphaFloat() * extend));
        fonts.drawCenteredText(matrices, value, boxX + (boxWidth / 2.0f),
                (this.bounds.y + ((boxHeight - fonts.getHeight(6.25f)) / 2.0f)) - 0.5f, 6.25f,
                ColorUtil.applyAlphaToColor(DeltaThemeInfo.TEXT.resolve(), extend));
        float trackY = this.bounds.y + fonts.getHeight(6.5f) + 6.5f;
        draw.drawRounded(matrices, this.bounds.x, trackY, this.bounds.z, 3.0f, 0.75f,
                ColorUtil.applyAlphaToColor(ColorUtil.convertToARGB(50, 52, 60, 255), extend * 0.35f));
        draw.drawRounded(matrices, this.bounds.x, trackY, this.bounds.z * progress, 3.0f, 0.75f,
                ColorUtil.applyAlphaToColor(DeltaThemeInfo.PRIMARY.resolve(), extend));
        draw.drawRounded(matrices, this.bounds.x + ((this.bounds.z - 6.0f) * progress), (trackY + 1.5f) - 3.0f,
                6.0f, 6.0f, 2.0f, ColorUtil.applyAlphaToColor(ColorUtil.convertToARGB(255, 255, 255, 255), extend));
    }

    private void updateSliderFromMouse(double mouseX) {
        float min = (float) this.setting.getMin();
        float max = (float) this.setting.getMax();
        float progress = DeltaMath.clamp(((float) (mouseX - this.bounds.x)) / this.bounds.z, 0.0f, 1.0f);
        float value = min + ((max - min) * progress);
        this.setting.setValue(DeltaMath.clamp((float) (Math.round(value / this.setting.getStep()) * this.setting.getStep()),
                min, max));
    }
}
