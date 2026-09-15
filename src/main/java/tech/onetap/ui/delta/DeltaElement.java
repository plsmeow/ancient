package tech.onetap.ui.delta;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Vector4f;
import org.lwjgl.glfw.GLFW;
import tech.onetap.module.settings.Setting;
import tech.onetap.util.KeyUtil;
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
    private boolean bindListening;

    public DeltaElement(SettingType setting) {
        this.setting = setting;
    }

    /**
     * Запуск бинда настройки средней кнопкой мыши — как в dropdown-режиме
     * (ПКМ и ЛКМ остаются за обычными действиями виджета).
     */
    protected boolean handleBindClick(double mouseX, double mouseY, int button, float x, float y, float width, float height) {
        if (button != 2 || !this.setting.canBind()
                || !DeltaMath.isHovered(mouseX, mouseY, x, y, width, height)) {
            return false;
        }
        this.bindListening = true;
        return true;
    }

    public boolean isBindListening() {
        return this.bindListening;
    }

    /**
     * Завершение бинда кликом: средняя кнопка отменяет прослушивание,
     * любая другая привязывает кнопку мыши к настройке.
     */
    public void completeBindClick(int button) {
        if (!this.bindListening) {
            return;
        }
        this.bindListening = false;
        if (button != 2) {
            this.setting.bindTo(button);
        }
    }

    /**
     * Клавиша во время прослушивания: Esc — отмена, Del — сброс бинда,
     * любая другая — привязка клавиши.
     */
    public boolean handleBindKey(int keyCode) {
        if (!this.bindListening) {
            return false;
        }
        this.bindListening = false;
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DELETE) {
            this.setting.unbind();
            return true;
        }
        this.setting.bindTo(keyCode);
        return true;
    }

    /**
     * Подпись настройки: во время прослушивания — подсказка,
     * при привязанной клавише — имя с бейджем [клавиша].
     */
    protected String bindLabelText() {
        if (this.bindListening) {
            return "Нажмите клавишу...";
        }
        if (this.setting.canBind() && this.setting.isBound()) {
            return this.setting.getName() + " [" + bindKeyLabel(this.setting.getKey()) + "]";
        }
        return this.setting.getName();
    }

    private static String bindKeyLabel(int key) {
        return KeyUtil.getKeyName(key >= 0 && key <= 7 ? -100 + key : key);
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
