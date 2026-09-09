package tech.onetap.ui.delta;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Vector4f;
import tech.onetap.module.settings.Setting;
import tech.onetap.util.render.DeltaAnimation;
import tech.onetap.util.render.ColorUtil;
import tech.onetap.util.render.DeltaThemeInfo;
import tech.onetap.util.render.font.Font;

/**
 * Base class for Delta-styled setting widgets, ported from DeltaClient
 * (aethereal.ui.element.Element) and adapted to onetap settings.
 */
public abstract class DeltaElement<SettingType extends Setting> {
    protected final Vector4f bounds = new Vector4f();
    protected final SettingType setting;
    private final DeltaAnimation activationAnimation = new DeltaAnimation();
    private final DeltaAnimation visibilityAnimation = new DeltaAnimation();
    protected float scroll;

    public DeltaElement(SettingType setting) {
        this.setting = setting;
    }

    public boolean onMouseClick(double mouseX, double mouseY, int button) {
        return false;
    }

    public boolean onMouseRelease(double mouseX, double mouseY, int button) {
        return false;
    }

    public boolean onMouseDrag(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        return false;
    }

    public boolean onMouseScroll(double mouseX, double mouseY, double amount) {
        return false;
    }

    public boolean onCharTyped(char chr, int modifiers) {
        return false;
    }

    public boolean onKeyPress(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    public DeltaAnimation getActivationAnimation() {
        return this.activationAnimation;
    }

    public DeltaAnimation getVisibilityAnimation() {
        return this.visibilityAnimation;
    }

    public Vector4f getBounds() {
        return this.bounds;
    }

    public SettingType getSetting() {
        return this.setting;
    }

    public float getScroll() {
        return this.scroll;
    }

    public void setScroll(float scroll) {
        this.scroll = scroll;
    }

    public boolean isEnabled() {
        return this.setting.visible.get();
    }

    public void render(DrawContext context, double mouseX, double mouseY, float delta, float extend) {
    }

    /**
     * Scrolling label with marquee + fade (DeltaClient Element.drawLabel).
     */
    protected void drawLabel(MatrixStack matrixStack, Font font, String text, float x, float y, float height,
                             float size, int color, float maxWidth, boolean hovered, float extend, float delta) {
        this.scroll = font.drawScrollingText(matrixStack, text, x, (y + ((height - font.getHeight(size)) / 2.0f)) - 0.5f,
                size, ColorUtil.applyAlphaToColor(color, extend), maxWidth, hovered, this.scroll, delta);
    }

    public void renderColorPicker(DrawContext context, double mouseX, double mouseY, float delta) {
    }
}
