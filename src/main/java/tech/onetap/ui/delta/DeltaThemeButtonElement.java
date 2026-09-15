package tech.onetap.ui.delta;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Vector4f;
import tech.onetap.module.settings.Setting;
import tech.onetap.util.render.ColorUtil;
import tech.onetap.util.render.Delta2DHolder;
import tech.onetap.util.render.DeltaAnimation;
import tech.onetap.util.render.DeltaEasing;
import tech.onetap.util.render.DeltaThemeInfo;
import tech.onetap.util.render.font.DeltaFonts;

/**
 * Кнопка «Открыть менеджер тем» в настройках модуля Interface — открывает
 * отдельную панель тем {@link DeltaThemePanel} (аналог ThemeActionComponent
 * в dropdown-режиме).
 */
public class DeltaThemeButtonElement extends DeltaElement<Setting> {
    private static final float HEIGHT = 13.0f;

    private final DeltaAnimation hoverAnim = new DeltaAnimation();

    public DeltaThemeButtonElement() {
        super(null);
        this.bounds.w = HEIGHT;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public boolean onMouseClick(double mouseX, double mouseY, int button) {
        Vector4f b = this.bounds;
        if (button == 0 && DeltaMath.isHovered(mouseX, mouseY, b.x, b.y, b.z, b.w)) {
            DeltaGuiScreen.getInstance().toggleThemePanel();
            return true;
        }
        return false;
    }

    @Override
    public void render(DrawContext context, double mouseX, double mouseY, float delta, float extend) {
        MatrixStack matrices = context.getMatrices();
        var draw = Delta2DHolder.get();
        var fonts = DeltaFonts.SF_REGULAR.get();
        this.bounds.w = HEIGHT;
        float centerY = this.bounds.y + (this.bounds.w / 2.0f) + 0.5f;
        boolean hovered = DeltaMath.isHovered(mouseX, mouseY, this.bounds.x, this.bounds.y, this.bounds.z, this.bounds.w)
                && extend >= 1.0f;
        this.hoverAnim.tick(hovered);
        this.hoverAnim.update(0.0f, 1.0f, 0.25f, DeltaEasing.SINE_IN_OUT, delta);
        draw.drawRounded(matrices, this.bounds.x, this.bounds.y, this.bounds.z, this.bounds.w, 4.0f,
                ColorUtil.applyAlphaToColor(DeltaThemeInfo.PRIMARY.resolve(), 0.039215688f * extend));
        draw.drawRounded(matrices, this.bounds.x, this.bounds.y, this.bounds.z, this.bounds.w, 4.0f,
                ColorUtil.applyAlphaToColor(ColorUtil.convertToARGB(255, 255, 255, 255),
                        0.03137255f * this.hoverAnim.getAnimationValue() * extend));
        draw.drawOutline(matrices, this.bounds.x, this.bounds.y, this.bounds.z, this.bounds.w, 4.0f, 0.5f,
                ColorUtil.applyAlphaToColor(DeltaThemeInfo.OUTLINE_SMALL.resolve(),
                        DeltaThemeInfo.OUTLINE_SMALL.alphaFloat() * extend));
        String text = DeltaGuiScreen.getInstance().isThemePanelOpen() ? "Закрыть менеджер тем" : "Открыть менеджер тем";
        fonts.drawText(matrices, text, this.bounds.x + 4.0f,
                fonts.centerInkY(text, 6.5f, centerY), 6.5f,
                ColorUtil.applyAlphaToColor(DeltaThemeInfo.TEXT.resolve(), extend));
    }
}
