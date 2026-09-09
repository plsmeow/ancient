package tech.onetap.ui.delta;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Vector4f;
import tech.onetap.module.settings.ModeSetting;
import tech.onetap.util.render.ColorUtil;
import tech.onetap.util.render.Delta2DHolder;
import tech.onetap.util.render.DeltaAnimation;
import tech.onetap.util.render.DeltaEasing;
import tech.onetap.util.render.DeltaThemeInfo;
import tech.onetap.util.render.font.DeltaFonts;

/**
 * Delta-styled mode chips widget ported from DeltaClient (aethereal.ui.element.ModeElement):
 * flowing 9px chips with 2px radius, per-mode activation animations, wrap layout.
 */
public class DeltaModeElement extends DeltaElement<ModeSetting> {
    private final DeltaAnimation[] modeAnimations;

    public DeltaModeElement(ModeSetting setting) {
        super(setting);
        this.modeAnimations = new DeltaAnimation[setting.getModes().size()];
        for (int i = 0; i < this.modeAnimations.length; i++) {
            this.modeAnimations[i] = new DeltaAnimation();
        }
    }

    @Override
    public boolean onMouseClick(double mouseX, double mouseY, int button) {
        Vector4f b = this.bounds;
        if (button != 0) {
            if (button != 2 || !DeltaMath.isHovered(mouseX, mouseY, b.x, b.y, b.z, b.w)) {
                return false;
            }
            this.setting.setKey(-1);
            return true;
        }
        var fonts = DeltaFonts.SF_REGULAR.get();
        float x = b.x;
        float y = b.y + fonts.getHeight(6.5f) + 5.0f;
        for (String mode : this.setting.getModes()) {
            float chipWidth = fonts.getWidth(mode, 6.25f) + 6.0f;
            if (x + chipWidth > b.x + b.z) {
                x = b.x;
                y += 12.0f;
            }
            if (DeltaMath.isHovered(mouseX, mouseY, x, y, chipWidth, 9.0f)) {
                this.setting.setValue(mode);
                return true;
            }
            x += chipWidth + 3.0f;
        }
        return false;
    }

    @Override
    public void render(DrawContext context, double mouseX, double mouseY, float delta, float extend) {
        MatrixStack matrices = context.getMatrices();
        var draw = Delta2DHolder.get();
        var fonts = DeltaFonts.SF_REGULAR.get();
        fonts.drawText(matrices, this.setting.getName(), this.bounds.x, this.bounds.y, 6.5f,
                ColorUtil.applyAlphaToColor(DeltaThemeInfo.TEXT.resolve(), extend));
        float x = this.bounds.x;
        float y = this.bounds.y + fonts.getHeight(6.5f) + 5.0f;
        int i = 0;
        for (String mode : this.setting.getModes()) {
            float width = fonts.getWidth(mode, 6.25f) + 6.0f;
            if (x + width > this.bounds.x + this.bounds.z) {
                x = this.bounds.x;
                y += 12.0f;
            }
            this.modeAnimations[i].tick(this.setting.is(mode));
            this.modeAnimations[i].update(0.0f, 1.0f, 0.3f, DeltaEasing.SINE_IN_OUT, delta);
            float value = this.modeAnimations[i].getAnimationValue();
            draw.drawRounded(matrices, x, y, width, 9.0f, 2.0f,
                    ColorUtil.applyAlphaToColor(DeltaThemeInfo.PRIMARY.resolve(),
                            ((5.0f + (65.0f * value)) / 255.0f) * extend));
            draw.drawOutline(matrices, x, y, width, 9.0f, 2.0f, 0.5f,
                    ColorUtil.applyAlphaToColor(DeltaThemeInfo.OUTLINE_SMALL.resolve(),
                            DeltaThemeInfo.OUTLINE_SMALL.alphaFloat() * extend));
            int color = ColorUtil.lerpColor(DeltaThemeInfo.TEXT_DISABLED.resolve(),
                    DeltaThemeInfo.TEXT.resolve(), value);
            fonts.drawCenteredText(matrices, mode, x + (width / 2.0f),
                    (y + ((9.0f - fonts.getHeight(6.25f)) / 2.0f)) - 0.75f, 6.25f,
                    ColorUtil.applyAlphaToColor(color, extend));
            x += width + 3.0f;
            i++;
        }
        this.bounds.w = (y + 9.0f) - this.bounds.y;
    }
}
