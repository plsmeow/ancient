package tech.onetap.ui.wonderful;

import tech.onetap.module.Module;
import tech.onetap.module.settings.*;
import tech.onetap.util.keyboard.KeyStorage;
import tech.onetap.util.render.helper.HoverUtil;
import tech.onetap.util.render.math.Animation;
import tech.onetap.util.render.msdf.Fonts;
import tech.onetap.util.render.providers.ColorProvider;
import tech.onetap.util.render.renderers.DrawUtil;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

public class ClickGuiSettingRenderer {

    public void render(Module module, float x, float startY, float width, int accent,
                       double mouseX, double mouseY, ClickGuiState state, float alphaMul) {
        List<Setting> settings = module.getSettings();
        if (settings == null || settings.isEmpty() || alphaMul <= 0.001f) return;

        int alpha = (int) (255 * alphaMul);
        float y = startY;
        List<Setting> visible = settings.stream().filter(s -> s.visible.get()).toList();

        for (int i = 0; i < visible.size(); i++) {
            Setting setting = visible.get(i);
            if (setting instanceof BooleanSetting booleanSetting) {
                renderBoolean(x, y, width, alpha, alphaMul, accent, booleanSetting, state);
            } else if (setting instanceof SliderSetting sliderSetting) {
                renderSlider(x, y, width, alpha, accent, sliderSetting, state);
            } else if (setting instanceof ModeSetting modeSetting) {
                List<Chip> chips = new ArrayList<>();
                for (String mode : modeSetting.getModes()) {
                    chips.add(new Chip(mode, modeSetting.getValue().equals(mode), state.getChipAnimation(modeSetting, mode)));
                }
                renderChips(x, y, width, alpha, alphaMul, accent, modeSetting.getName(), null, mouseX, mouseY, chips);
            } else if (setting instanceof ModeListSetting multi) {
                List<Chip> chips = new ArrayList<>();
                int enabled = 0;
                for (BooleanSetting val : multi.getSettings()) {
                    if (val.getValue()) enabled++;
                    chips.add(new Chip(val.getName(), val.getValue(), state.getChipAnimation(multi, val.getName())));
                }
                String counter = enabled + "/" + multi.getSettings().size();
                renderChips(x, y, width, alpha, alphaMul, accent, multi.getName(), counter, mouseX, mouseY, chips);
            } else if (setting instanceof BindSetting bindSetting) {
                renderBind(x, y, width, alpha, alphaMul, bindSetting, state);
            }
            y += ClickGuiLayout.getSettingHeight(setting, width);
            if (i < visible.size() - 1) y += ClickGuiLayout.SETTING_GAP;
        }
    }

    private void renderBoolean(float x, float y, float width, int alpha, float alphaMul,
                               int accent, BooleanSetting setting, ClickGuiState state) {
        float trackW = 14f, trackH = 7f;
        float trackX = x + width - trackW;
        float trackY = y + 2f;

        boolean binding = state.getBindingSetting() == setting;
        String bindText = binding ? "[...]" : (setting.getKey() != -1 ? "[" + KeyStorage.getKey(setting.getKey()) + "]" : "");
        float bindW = bindText.isEmpty() ? 0f : Fonts.SFREGULAR.get().getWidth(bindText, 6f) + 3f;
        String name = ClickGuiLayout.truncate(setting.getName(), 6.5f, width - trackW - 4f - bindW);

        DrawUtil.drawText(Fonts.SFREGULAR.get(), name, x + 1f, y + 2.5f, ColorProvider.rgba(255, 255, 255, alpha), 6.5f);
        if (!bindText.isEmpty()) {
            DrawUtil.drawText(Fonts.SFREGULAR.get(), bindText,
                    x + 1f + Fonts.SFREGULAR.get().getWidth(name, 6.5f) + 4f, y + 3f,
                    ColorProvider.rgba(170, 170, 185, alpha), 6f);
        }

        Animation anim = state.getBooleanBackgroundAnimation(setting);
        anim.run(setting.getValue());
        float progress = anim.getValue();

        int off = ColorProvider.rgba(45, 45, 58, alpha);
        int trackColor = ColorProvider.setAlpha(ColorProvider.interpolateColor(off, accent, progress), alpha);
        DrawUtil.drawRound(trackX, trackY, trackW, trackH, trackH / 2f, trackColor);

        float knobSize = trackH - 2f;
        float knobX = trackX + 1f + (trackW - 2f - knobSize) * progress;
        DrawUtil.drawRound(knobX, trackY + 1f, knobSize, knobSize, knobSize / 2f,
                ColorProvider.rgba(255, 255, 255, alpha));
    }

    private void renderSlider(float x, float y, float width, int alpha, int accent,
                              SliderSetting setting, ClickGuiState state) {
        Animation anim = state.getSliderAnimation(setting);
        float target = state.getSliderPos(setting);
        if (state.isDraggingSlider(setting)) {
            anim.setValue(target);
        } else {
            anim.run(target);
        }
        float pos = Math.max(0f, Math.min(1f, anim.getValue()));

        DrawUtil.drawText(Fonts.SFREGULAR.get(), setting.getName(), x + 1f, y + 2f,
                ColorProvider.rgba(255, 255, 255, alpha), 6f);
        String valStr = BigDecimal.valueOf(setting.getValue())
                .setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
        float valW = Fonts.SFREGULAR.get().getWidth(valStr, 6f);
        DrawUtil.drawText(Fonts.SFREGULAR.get(), valStr, x + width - valW, y + 2f,
                ColorProvider.rgba(180, 180, 195, alpha), 6f);

        float slY = y + 12f, slH = 4f;
        DrawUtil.drawRound(x, slY, width, slH, 2f, ColorProvider.rgba(38, 38, 50, alpha));
        if (pos > 0f) {
            DrawUtil.drawRound(x, slY, width * pos, slH, 2f, ColorProvider.setAlpha(accent, alpha));
        }
        float knob = 6f;
        DrawUtil.drawRound(x + width * pos - knob / 2f, slY + slH / 2f - knob / 2f, knob, knob, knob / 2f,
                ColorProvider.rgba(255, 255, 255, alpha));
    }

    private record Chip(String name, boolean selected, Animation animation) {
    }

    private void renderChips(float x, float y, float width, int alpha, float alphaMul, int accent,
                             String title, String counter, double mouseX, double mouseY, List<Chip> chips) {
        DrawUtil.drawText(Fonts.SFREGULAR.get(), title, x + 1f, y + 1.5f, ColorProvider.rgba(255, 255, 255, alpha), 6.5f);
        if (counter != null) {
            float counterW = Fonts.SFREGULAR.get().getWidth(counter, 6f);
            DrawUtil.drawText(Fonts.SFREGULAR.get(), counter, x + width - counterW, y + 1.5f,
                    ColorProvider.rgba(170, 170, 185, alpha), 6f);
        }

        float cx = x, cy = y + 9f, rowH = 11f;
        for (Chip chip : chips) {
            float chipW = Fonts.SFREGULAR.get().getWidth(chip.name(), 6f) + ClickGuiLayout.CHIP_PADDING_X * 2f;
            if (cx + chipW > x + width && cx > x) {
                cx = x;
                cy += rowH + ClickGuiLayout.CHIP_GAP_Y;
            }
            boolean hovered = HoverUtil.isHovered(mouseX, mouseY, cx, cy, chipW, rowH);
            chip.animation().run(chip.selected() || hovered);
            float anim = chip.animation().getValue();

            int bg = chip.selected()
                    ? ColorProvider.setAlpha(accent, (int) (60 * alphaMul))
                    : ColorProvider.rgba(255, 255, 255, (int) ((10 + 8 * anim) * alphaMul));
            DrawUtil.drawRound(cx, cy, chipW, rowH, 3f, bg);
            if (chip.selected()) {
                DrawUtil.drawRound(cx - 0.5f, cy - 0.5f, chipW + 1f, rowH + 1f, 3.5f,
                        ColorProvider.setAlpha(accent, (int) (200 * alphaMul)));
                DrawUtil.drawRound(cx, cy, chipW, rowH, 3f, bg);
            }
            int textColor = chip.selected()
                    ? ColorProvider.rgba(255, 255, 255, alpha)
                    : ColorProvider.rgba(180, 180, 195, alpha);
            DrawUtil.drawText(Fonts.SFREGULAR.get(), chip.name(), cx + ClickGuiLayout.CHIP_PADDING_X, cy + 3.5f, textColor, 6f);
            cx += chipW + ClickGuiLayout.CHIP_GAP_X;
        }
    }

    private void renderBind(float x, float y, float width, int alpha, float alphaMul,
                            BindSetting setting, ClickGuiState state) {
        boolean binding = state.getBindingSetting() == setting;
        String bindString = binding ? "..." : (setting.getValue() == -1 ? "N/A" : KeyStorage.getKey(setting.getValue()));
        float bindW = Fonts.SFREGULAR.get().getWidth(bindString, 6f) + 6f;
        float bindX = x + width - bindW;

        DrawUtil.drawText(Fonts.SFREGULAR.get(), setting.getName(), x + 1f, y + 2.5f,
                ColorProvider.rgba(255, 255, 255, alpha), 6.5f);
        DrawUtil.drawRound(bindX, y, bindW, 11f, 3f, ColorProvider.rgba(255, 255, 255, (int) (12 * alphaMul)));
        DrawUtil.drawText(Fonts.SFREGULAR.get(), bindString, bindX + 3f, y + 3f,
                ColorProvider.rgba(255, 255, 255, alpha), 6f);
    }
}
