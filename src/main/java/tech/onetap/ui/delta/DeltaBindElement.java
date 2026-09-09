package tech.onetap.ui.delta;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Vector4f;
import tech.onetap.module.settings.BindSetting;
import tech.onetap.util.KeyUtil;
import tech.onetap.util.render.ColorUtil;
import tech.onetap.util.render.Delta2DHolder;
import tech.onetap.util.render.DeltaEasing;
import tech.onetap.util.render.DeltaThemeInfo;
import tech.onetap.util.render.ScissorUtil;
import tech.onetap.util.render.font.DeltaFonts;

/**
 * Delta-styled bind widget ported from DeltaClient (aethereal.ui.element.BindElement):
 * label + chip that crossfades to "..." while listening, accepts mouse buttons as binds.
 */
public class DeltaBindElement extends DeltaElement<BindSetting> {
    private boolean isListening;

    public DeltaBindElement(BindSetting setting) {
        super(setting);
        this.bounds.w = 11.0f;
    }

    @Override
    public boolean onMouseClick(double mouseX, double mouseY, int button) {
        Vector4f b = this.bounds;
        if (this.isListening) {
            this.setting.setValue(-100 + button);
            this.isListening = false;
            return true;
        }
        if (!DeltaMath.isHovered(mouseX, mouseY, b.x, b.y, b.z, b.w)) {
            return false;
        }
        if (button == 0) {
            this.isListening = true;
            return true;
        }
        if (button != 2) {
            return false;
        }
        this.setting.setKey(-1);
        return true;
    }

    @Override
    public boolean onKeyPress(int keyCode, int scanCode, int modifiers) {
        if (!this.isListening) {
            return false;
        }
        this.setting.setValue(keyCode);
        this.isListening = false;
        return true;
    }

    @Override
    public void render(DrawContext context, double mouseX, double mouseY, float delta, float extend) {
        MatrixStack matrices = context.getMatrices();
        var draw = Delta2DHolder.get();
        var fonts = DeltaFonts.SF_REGULAR.get();
        getActivationAnimation().tick(this.isListening);
        getActivationAnimation().update(0.0f, 1.0f, 0.4f, DeltaEasing.EXPO_OUT, delta);
        float centerY = this.bounds.y + (this.bounds.w / 2.0f) + 0.5f;
        boolean hovered = DeltaMath.isHovered(mouseX, mouseY, this.bounds.x, this.bounds.y, this.bounds.z, this.bounds.w)
                && extend >= 1.0f;
        float anim = getActivationAnimation().getAnimationValue();
        float reverse = 1.0f - anim;
        String value = this.setting.getValue() == -1 ? "None" : KeyUtil.getKeyName(this.setting.getValue());
        float total = (fonts.getWidth(value, 6.5f) * reverse) + (fonts.getWidth("...", 6.5f) * anim);
        float boxWidth = total + 8.0f;
        float boxHeight = fonts.getHeight(6.5f) + 3.0f;
        float boxX = (this.bounds.x + this.bounds.z) - boxWidth;
        float boxY = centerY - (boxHeight / 2.0f);
        float textY = (boxY + ((boxHeight - fonts.getHeight(6.5f)) / 2.0f)) - 0.75f;
        drawLabel(matrices, fonts, this.setting.getName(), this.bounds.x, this.bounds.y, this.bounds.w, 6.5f,
                DeltaThemeInfo.TEXT.resolve(), (boxX - this.bounds.x) - 4.0f, hovered, extend, delta);
        draw.drawRounded(matrices, boxX, boxY, boxWidth, boxHeight, 2.0f,
                ColorUtil.applyAlphaToColor(DeltaThemeInfo.PRIMARY.resolve(), 0.039215688f * extend));
        draw.drawOutline(matrices, boxX, boxY, boxWidth, boxHeight, 2.0f, 0.5f,
                ColorUtil.applyAlphaToColor(DeltaThemeInfo.OUTLINE_MEDIUM.resolve(),
                        DeltaThemeInfo.OUTLINE_MEDIUM.alphaFloat() * extend));
        ScissorUtil.push(matrices, boxX, boxY, boxWidth, boxHeight);
        if (reverse > 0.0f) {
            fonts.drawText(matrices, value, boxX + 4.0f, textY, 6.5f,
                    ColorUtil.applyAlphaToColor(DeltaThemeInfo.TEXT.resolve(), extend * reverse));
        }
        if (anim > 0.0f) {
            fonts.drawText(matrices, "...", boxX + 4.0f, textY, 6.5f,
                    ColorUtil.applyAlphaToColor(DeltaThemeInfo.TEXT.resolve(), extend * anim));
        }
        ScissorUtil.pop(matrices);
    }
}
