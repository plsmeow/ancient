package tech.onetap.ui.wonderful;

import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.settings.*;
import tech.onetap.util.render.msdf.Fonts;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class ClickGuiLayout {

    // ── Общие размеры ──
    public static final float HEIGHT = 250f;
    public static final float SIDEBAR_WIDTH = 96f;
    public static final float MAIN_WIDTH = 250f;
    public static final float SETTINGS_WIDTH = 112f;
    public static final float PANEL_GAP = 6f;
    public static final float RADIUS = 6f;

    // ── Сайдбар ──
    public static final float SIDEBAR_PADDING = 6f;
    public static final float PROFILE_HEIGHT = 30f;
    public static final float SIDEBAR_ITEM_HEIGHT = 16f;
    public static final float SIDEBAR_ITEM_GAP = 2f;
    public static final float SIDEBAR_SECTION_GAP = 8f;

    // ── Центральная панель ──
    public static final float MAIN_HEADER_HEIGHT = 26f;
    public static final float MAIN_PADDING = 8f;
    public static final float CARD_GAP = 6f;
    public static final int GRID_COLUMNS = 2;

    // ── Карточки (динамическая высота) ──
    public static final float CARD_HEADER_HEIGHT = 16f;
    public static final float CARD_DESC_SIZE = 5.8f;
    public static final float CARD_DESC_LINE_HEIGHT = 7.5f;
    public static final int CARD_DESC_MAX_LINES = 4;
    public static final float CARD_BOTTOM_ZONE = 12f;
    public static final float CARD_MIN_HEIGHT = 30f;

    public static final float SEARCH_WIDTH = 88f;
    public static final float SEARCH_HEIGHT = 14f;
    public static final int SEARCH_MAX_CHARS = 24;

    // ── Панель настроек ──
    public static final float SETTINGS_HEADER_HEIGHT = 20f;
    public static final float SETTINGS_PADDING = 7f;
    public static final float SETTING_GAP = 3f;
    public static final float CHIP_GAP_X = 3f;
    public static final float CHIP_GAP_Y = 3f;
    public static final float CHIP_PADDING_X = 4f;

    private ClickGuiLayout() {
    }

    public static ModuleCategory[] getVisibleCategories() {
        return ModuleCategory.values();
    }

    public static float getTotalWidth() {
        return SIDEBAR_WIDTH + PANEL_GAP + MAIN_WIDTH + PANEL_GAP + SETTINGS_WIDTH;
    }

    public static float getSidebarX(float x) {
        return x;
    }

    public static float getMainX(float x) {
        return x + SIDEBAR_WIDTH + PANEL_GAP;
    }

    public static float getSettingsX(float x) {
        return getMainX(x) + MAIN_WIDTH + PANEL_GAP;
    }

    public static float getCardWidth() {
        return (MAIN_WIDTH - MAIN_PADDING * 2f - CARD_GAP * (GRID_COLUMNS - 1)) / GRID_COLUMNS;
    }

    public static float getGridViewHeight() {
        return HEIGHT - MAIN_HEADER_HEIGHT - MAIN_PADDING;
    }

    // ────────────────────────── Динамическая сетка ──────────────────────────

    /** Высота карточки модуля в зависимости от длины описания. */
    public static float getCardHeight(Module module, float cardWidth) {
        int lines = wrapText(module.getDesc(), CARD_DESC_SIZE, cardWidth - 12f, CARD_DESC_MAX_LINES).size();
        float height = CARD_HEADER_HEIGHT + lines * CARD_DESC_LINE_HEIGHT + CARD_BOTTOM_ZONE;
        return Math.max(height, CARD_MIN_HEIGHT);
    }

    /** Позиция одной карточки в сетке-кладке. */
    public record CardPos(Module module, int column, float offsetY, float height) {
    }

    /** Раскладка карточек: каждая встаёт в самую короткую колонку. */
    public static List<CardPos> layoutCards(List<Module> modules, float cardWidth) {
        List<CardPos> result = new ArrayList<>();
        float[] columnY = new float[GRID_COLUMNS];
        for (Module module : modules) {
            int column = 0;
            for (int i = 1; i < GRID_COLUMNS; i++) {
                if (columnY[i] < columnY[column]) column = i;
            }
            float height = getCardHeight(module, cardWidth);
            result.add(new CardPos(module, column, columnY[column], height));
            columnY[column] += height + CARD_GAP;
        }
        return result;
    }

    /** Полная высота контента сетки (для ограничения скролла). */
    public static float getGridContentHeight(List<Module> modules, float cardWidth) {
        float max = 0f;
        for (CardPos pos : layoutCards(modules, cardWidth)) {
            max = Math.max(max, pos.offsetY() + pos.height());
        }
        return max;
    }

    // ────────────────────────── Прочее ──────────────────────────

    public static float getSettingsInnerWidth() {
        return SETTINGS_WIDTH - SETTINGS_PADDING * 2f;
    }

    public static float getSettingsViewHeight() {
        return HEIGHT - SETTINGS_HEADER_HEIGHT - SETTINGS_PADDING;
    }

    /** Перенос текста по словам, максимум maxLines строк. */
    public static List<String> wrapText(String text, float size, float maxWidth, int maxLines) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isBlank() || maxLines <= 0) return lines;
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (Fonts.SFREGULAR.get().getWidth(candidate, size) > maxWidth && !line.isEmpty()) {
                lines.add(line.toString());
                line = new StringBuilder(word);
                if (lines.size() == maxLines) return lines;
            } else {
                line = new StringBuilder(candidate);
            }
        }
        if (!line.isEmpty() && lines.size() < maxLines) lines.add(line.toString());
        return lines;
    }

    /** Обрезка строки с ".." под максимальную ширину. */
    public static String truncate(String text, float size, float maxWidth) {
        if (text == null) return "";
        if (Fonts.SFREGULAR.get().getWidth(text, size) <= maxWidth) return text;
        String dots = "..";
        float dotsW = Fonts.SFREGULAR.get().getWidth(dots, size);
        StringBuilder out = new StringBuilder();
        float w = 0f;
        for (char c : text.toCharArray()) {
            float cw = Fonts.SFREGULAR.get().getWidth(String.valueOf(c), size);
            if (w + cw + dotsW > maxWidth) break;
            out.append(c);
            w += cw;
        }
        return out + dots;
    }

    public static boolean hasVisibleSettings(Module module) {
        List<Setting> settings = module.getSettings();
        if (settings == null || settings.isEmpty()) return false;
        for (Setting setting : settings) {
            if (setting != null && setting.visible.get()) return true;
        }
        return false;
    }

    public static float calculateChipsHeight(List<String> names, float innerWidth) {
        float x = 0f;
        int rows = 1;
        float rowHeight = 11f;
        for (String name : names) {
            float chipW = Fonts.SFREGULAR.get().getWidth(name, 6.0f) + CHIP_PADDING_X * 2f;
            if (x + chipW > innerWidth && x > 0f) {
                x = 0f;
                rows++;
            }
            x += chipW + CHIP_GAP_X;
        }
        return 9.0f + rows * rowHeight + (rows - 1) * CHIP_GAP_Y + 2.0f;
    }

    public static float calculateModeSettingHeight(ModeSetting modeSetting, float innerWidth) {
        return calculateChipsHeight(modeSetting.getModes(), innerWidth);
    }

    public static float calculateMultiBooleanHeight(ModeListSetting modeListSetting, float innerWidth) {
        return calculateChipsHeight(modeListSetting.getSettings().stream().map(Setting::getName).toList(), innerWidth);
    }

    public static float getSettingHeight(Setting setting, float innerWidth) {
        if (setting instanceof BooleanSetting || setting instanceof BindSetting) return 12f;
        if (setting instanceof SliderSetting) return 22f;
        if (setting instanceof ModeSetting modeSetting) return calculateModeSettingHeight(modeSetting, innerWidth);
        if (setting instanceof ModeListSetting multi) return calculateMultiBooleanHeight(multi, innerWidth);
        return 12f;
    }

    public static float calculateSettingsHeight(Module module, float innerWidth) {
        List<Setting> settings = module.getSettings();
        if (settings == null || settings.isEmpty()) return 0f;
        List<Setting> visible = settings.stream().filter(s -> s.visible.get()).toList();
        float height = 0f;
        for (int i = 0; i < visible.size(); i++) {
            height += getSettingHeight(visible.get(i), innerWidth);
            if (i < visible.size() - 1) height += SETTING_GAP;
        }
        return height;
    }
}
