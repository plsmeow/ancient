package tech.onetap.ui.component.impl;

import net.minecraft.client.util.math.MatrixStack;
import tech.onetap.module.settings.ColorSetting;
import tech.onetap.ui.component.Component;
import tech.onetap.util.cursor.CursorManager;
import tech.onetap.util.render.helper.HoverUtil;
import tech.onetap.util.render.math.Animation;
import tech.onetap.util.render.math.Easing;
import tech.onetap.util.render.math.Scissor;
import tech.onetap.util.render.msdf.Fonts;
import tech.onetap.util.render.providers.ColorProvider;
import tech.onetap.util.render.renderers.DrawUtil;

import java.awt.Color;

public class ColorPickerComponent extends Component {
    private final ColorSetting setting;
    private boolean opened;
    private final Animation openAnim = new Animation(Easing.QUINTIC_OUT, 300);

    private boolean draggingSV, draggingHue;
    private float hue, saturation, value;

    public ColorPickerComponent(ColorSetting setting) {
        this.setting = setting;
        updateHSV();
    }

    private void updateHSV() {
        Color c = new Color(setting.getValue(), true);
        float[] hsv = Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null);
        hue = hsv[0];
        saturation = hsv[1];
        value = hsv[2];
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float partialTicks) {
        float alpha = Math.min(getAlphaAnimSetting().getValue(), 1);
        int alphaInt = (int) (255 * alpha);

        openAnim.run(opened);
        float expandedHeight = 55f;

        DrawUtil.drawText(Fonts.SFREGULAR.get(), setting.getName(), x + 4.5f, y + 3, ColorProvider.rgba(255, 255, 255, alphaInt), 6.5f);

        float previewSize = 8f;
        float previewX = x + width - previewSize - 5;
        float previewY = y + 2.5f;

        if (HoverUtil.isHovered(mouseX, mouseY, previewX, previewY, previewSize, previewSize)) CursorManager.requestHand();

        DrawUtil.drawRound(previewX - 0.5f, previewY - 0.5f, previewSize + 1, previewSize + 1, 1.5f, ColorProvider.rgba(60, 60, 65, (int)(150 * alpha)));
        DrawUtil.drawRound(previewX, previewY, previewSize, previewSize, 1.5f, setting.getValue());

        if (openAnim.getValue() > 0.01f) {
            float pickerY = y + 13;
            float animH = openAnim.getValue() * expandedHeight;
            float animAlpha = (float) (alpha * openAnim.getValue());
            int animAlphaInt = (int)(255 * animAlpha);

            Scissor.push();
            Scissor.setFromComponentCoordinates(x, pickerY, width, animH);

            // Фон под пикером
            DrawUtil.drawRound(x + 4.5f, pickerY - 0.5f, width - 9, expandedHeight + 1, 2.5f, ColorProvider.rgba(60, 60, 65, (int)(150 * animAlpha)));
            DrawUtil.drawRound(x + 5, pickerY, width - 10, expandedHeight, 2.5f, ColorProvider.rgba(20, 20, 25, animAlphaInt));

            float svX = x + 9;
            float svY = pickerY + 4;
            float svSize = 40;

            // ==========================================
            // ИДЕАЛЬНЫЙ КВАДРАТ ВЫБОРА ЦВЕТА (В 2 СЛОЯ)
            // ==========================================
            int cHue        = ColorProvider.setAlpha(Color.HSBtoRGB(hue, 1F, 1F), animAlphaInt);
            int cWhite      = ColorProvider.rgba(255, 255, 255, animAlphaInt);
            int cClearWhite = ColorProvider.rgba(255, 255, 255, 0); // Прозрачный белый
            int cBlack      = ColorProvider.rgba(0, 0, 0, animAlphaInt);
            int cClearBlack = ColorProvider.rgba(0, 0, 0, 0);       // Прозрачный черный

            // СЛОЙ 1: Чистый яркий цвет (Заливка всего фона)
            DrawUtil.drawRound(svX, svY, svSize, svSize, 2f, cHue);

            // СЛОЙ 2: Накладываем Белый свет (Слева - направо)
            DrawUtil.drawRound(svX, svY, svSize, svSize, 2f, cWhite, cWhite, cClearWhite, cClearWhite);

            // СЛОЙ 3: Накладываем Черную тень (Сверху - вниз)
            DrawUtil.drawRound(svX, svY, svSize, svSize, 2f, cClearBlack, cBlack, cBlack, cClearBlack);

            // Ползунок SV
            float svCursorX = svX + saturation * svSize;
            float svCursorY = svY + (1 - value) * svSize;
            DrawUtil.drawRound(svCursorX - 2.5f, svCursorY - 2.5f, 5, 5, 2.5f, ColorProvider.rgba(0, 0, 0, (int)(180 * animAlpha))); // Тень
            DrawUtil.drawRound(svCursorX - 1.5f, svCursorY - 1.5f, 3, 3, 1.5f, ColorProvider.rgba(255, 255, 255, animAlphaInt)); // Центр

            // ==========================================
            // ИДЕАЛЬНАЯ РАДУЖНАЯ ПОЛОСКА (HUE)
            // ==========================================
            float hueX = svX + svSize + 6;
            float hueY = svY;
            float hueW = 6;

            // Рисуем по полпикселя для максимальной гладкости перехода
            for (float i = 0; i <= svSize; i += 0.5f) {
                int color = ColorProvider.setAlpha(Color.HSBtoRGB(i / svSize, 1F, 1F), animAlphaInt);
                DrawUtil.drawRound(hueX, hueY + i, hueW, 1f, 0f, color);
            }

            // Ползунок Hue
            float hueCursorY = hueY + hue * svSize;
            DrawUtil.drawRound(hueX - 1.5f, hueCursorY - 2.5f, hueW + 3f, 5f, 2f, ColorProvider.rgba(0, 0, 0, (int)(180 * animAlpha))); // Тень
            DrawUtil.drawRound(hueX - 0.5f, hueCursorY - 1.5f, hueW + 1f, 3f, 1f, ColorProvider.rgba(255, 255, 255, animAlphaInt)); // Центр

            // Логика перетаскивания
            if (draggingSV) {
                saturation = Math.max(0, Math.min(1, (mouseX - svX) / svSize));
                value = 1F - Math.max(0, Math.min(1, (mouseY - svY) / svSize));
                syncColor();
            } else if (draggingHue) {
                hue = Math.max(0, Math.min(1, (mouseY - hueY) / svSize));
                syncColor();
            }

            Scissor.unset();
            Scissor.pop();
        }

        setHeight(13 + (openAnim.getValue() * expandedHeight));
    }

    private void syncColor() {
        int rgb = Color.HSBtoRGB(hue, saturation, value);
        setting.setValue(rgb | 0xFF000000);
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        float previewSize = 8f;
        float previewX = x + width - previewSize - 5;
        float previewY = y + 2.5f;

        if (HoverUtil.isHovered(mouseX, mouseY, previewX, previewY, previewSize, previewSize) && button == 1) {
            opened = !opened;
            return;
        }

        if (opened && button == 0) {
            float svX = x + 9;
            float svY = y + 13 + 4;
            float svSize = 40;
            float hueX = svX + svSize + 6;

            if (HoverUtil.isHovered(mouseX, mouseY, svX, svY, svSize, svSize)) draggingSV = true;
            else if (HoverUtil.isHovered(mouseX, mouseY, hueX - 2, svY, 10, svSize)) draggingHue = true;
        }
    }

    @Override
    public void mouseReleased(double mouseX, double mouseY, int button) {
        draggingSV = false;
        draggingHue = false;
    }

    @Override
    public boolean isVisible() {
        return setting.visible.get();
    }
}