package tech.onetap.ui.delta;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Vector4f;
import tech.onetap.module.settings.BooleanSetting;
import tech.onetap.util.render.ColorUtil;
import tech.onetap.util.render.DeltaThemeInfo;
import tech.onetap.util.render.Delta2DHolder;
import tech.onetap.util.render.DeltaEasing;
import tech.onetap.util.render.font.DeltaFonts;

/**
 * Delta-styled boolean widget ported from DeltaClient (aethereal.ui.element.BooleanElement):
 * label with scrolling fade, 11x11 rounded checkbox with check/close icons and activation crossfade.
 */
public class DeltaBooleanElement extends DeltaElement<BooleanSetting> {
    public DeltaBooleanElement(BooleanSetting setting) {
        super(setting);
        this.bounds.w = 11.0f;
    }

    @Override
    public boolean onMouseClick(double mouseX, double mouseY, int button) {
        Vector4f b = this.bounds;
        if (!DeltaMath.isHovered(mouseX, mouseY, b.x, b.y, b.z, b.w)) {
            return false;
        }
        if (button == 0) {
            this.setting.toggle();
            return true;
        }
        if (button == 2) {
            this.setting.setKey(-1);
            return true;
        }
        return false;
    }

    @Override
    public void render(DrawContext context, double mouseX, double mouseY, float delta, float extend) {
        MatrixStack matrices = context.getMatrices();
        var draw = Delta2DHolder.get();
        getActivationAnimation().tick(this.setting.getValue());
        getActivationAnimation().update(0.0f, 1.0f, 0.5f, DeltaEasing.SINE_IN_OUT, delta);
        float enabled = getActivationAnimation().getAnimationValue();
        float disabled = 1.0f - enabled;
        float centerY = this.bounds.y + (this.bounds.w / 2.0f) + 0.5f;
        boolean hovered = DeltaMath.isHovered(mouseX, mouseY, this.bounds.x, this.bounds.y, this.bounds.z, this.bounds.w)
                && extend >= 1.0f;
        drawLabel(matrices, DeltaFonts.SF_REGULAR.get(), this.setting.getName(), this.bounds.x, this.bounds.y,
                this.bounds.w, 6.5f, DeltaThemeInfo.TEXT.resolve(), (this.bounds.z - 11.0f) - 4.0f, hovered, extend, delta);
        float boxX = (this.bounds.x + this.bounds.z) - 11.0f;
        float boxY = centerY - 5.5f;
        draw.drawRounded(matrices, boxX, boxY, 11.0f, 11.0f, 3.0f,
                ColorUtil.applyAlphaToColor(DeltaThemeInfo.PRIMARY.resolve(), 0.039215688f * extend));
        draw.drawOutline(matrices, boxX, boxY, 11.0f, 11.0f, 3.0f, 0.5f,
                ColorUtil.applyAlphaToColor(DeltaThemeInfo.OUTLINE_SMALL.resolve(),
                        DeltaThemeInfo.OUTLINE_SMALL.alphaFloat() * extend));
        if (disabled > 0.0f) {
            var icons = DeltaFonts.ICONS.get();
            icons.drawText(matrices, "u", boxX + ((11.0f - icons.getWidth("u", 6.0f)) / 2.0f) + 0.25f,
                    icons.centerInkY("u", 6.0f, centerY), 6.0f,
                    ColorUtil.applyAlphaToColor(ColorUtil.convertToARGB(200, 25, 25, 255), extend * disabled));
        }
        if (enabled > 0.0f) {
            var icons = DeltaFonts.ICONS.get();
            icons.drawText(matrices, "m", boxX + ((11.0f - icons.getWidth("m", 9.0f)) / 2.0f),
                    icons.centerInkY("m", 9.0f, centerY), 9.0f,
                    ColorUtil.applyAlphaToColor(ColorUtil.convertToARGB(130, 220, 150, 255), extend * enabled));
        }
    }
}
