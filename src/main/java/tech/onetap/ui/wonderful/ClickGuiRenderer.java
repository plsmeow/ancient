package tech.onetap.ui.wonderful;

import net.minecraft.client.util.Window;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.util.keyboard.KeyStorage;
import tech.onetap.util.render.helper.HoverUtil;
import tech.onetap.util.render.math.Animation;
import tech.onetap.util.render.math.Scissor;
import tech.onetap.util.render.msdf.Fonts;
import tech.onetap.util.render.providers.ColorProvider;
import tech.onetap.util.render.renderers.DrawUtil;

import java.util.List;

public class ClickGuiRenderer {

    private final ClickGuiState state;
    private final ClickGuiSettingRenderer settingRenderer;

    public ClickGuiRenderer(ClickGuiState state, ClickGuiSettingRenderer settingRenderer) {
        this.state = state;
        this.settingRenderer = settingRenderer;
    }

    public void render(int mouseX, int mouseY, Window window, float progress) {
        if (window == null) return;
        float alpha = progress;
        int accent = ColorProvider.getThemeColor();
        float guiY = state.getY() + state.getRenderOffsetY();

        renderSidebar(mouseX, mouseY, ClickGuiLayout.getSidebarX(state.getX()), guiY, accent, alpha);
        renderMain(mouseX, mouseY, ClickGuiLayout.getMainX(state.getX()), guiY, accent, alpha);
        renderSettingsPanel(mouseX, mouseY, ClickGuiLayout.getSettingsX(state.getX()), guiY, accent, alpha);
    }

    // ────────────────────────── Сайдбар ──────────────────────────

    private void renderSidebar(int mouseX, int mouseY, float x, float y, int accent, float alpha) {
        drawPanelBase(x, y, ClickGuiLayout.SIDEBAR_WIDTH, ClickGuiLayout.HEIGHT, alpha);

        float pad = ClickGuiLayout.SIDEBAR_PADDING;
        float innerW = ClickGuiLayout.SIDEBAR_WIDTH - pad * 2f;

        // Профиль
        float profileY = y + pad;
        DrawUtil.drawRound(x + pad, profileY, innerW, ClickGuiLayout.PROFILE_HEIGHT, 4f,
                ColorProvider.rgba(255, 255, 255, (int) (10 * alpha)));
        String selfName = ClickGuiLayout.truncate(
                tech.onetap.util.IMinecraft.mc.getSession().getUsername(), 7f, innerW - 12f);
        DrawUtil.drawText(Fonts.SFMEDIUM.get(), selfName, x + pad + 6f, profileY + 7f,
                ColorProvider.rgba(255, 255, 255, (int) (255 * alpha)), 7f);
        DrawUtil.drawText(Fonts.SFREGULAR.get(), "Premium", x + pad + 6f, profileY + 17f,
                ColorProvider.setAlpha(accent, (int) (220 * alpha)), 5.5f);

        // MODULES
        float itemY = profileY + ClickGuiLayout.PROFILE_HEIGHT + ClickGuiLayout.SIDEBAR_SECTION_GAP;
        DrawUtil.drawText(Fonts.SFREGULAR.get(), "MODULES", x + pad + 2f, itemY,
                ColorProvider.rgba(140, 140, 155, (int) (255 * alpha)), 5.5f);
        itemY += 9f;

        for (ModuleCategory category : ClickGuiLayout.getVisibleCategories()) {
            boolean selected = state.getSelectedCategory() == category;
            boolean hovered = HoverUtil.isHovered(mouseX, mouseY, x + pad, itemY, innerW, ClickGuiLayout.SIDEBAR_ITEM_HEIGHT);

            if (selected) {
                DrawUtil.drawRound(x + pad, itemY, innerW, ClickGuiLayout.SIDEBAR_ITEM_HEIGHT, 4f,
                        ColorProvider.setAlpha(accent, (int) (55 * alpha)));
                DrawUtil.drawRound(x + pad + 1f, itemY + 3.5f, 1.5f, ClickGuiLayout.SIDEBAR_ITEM_HEIGHT - 7f, 0.75f,
                        ColorProvider.setAlpha(accent, (int) (255 * alpha)));
            } else if (hovered) {
                DrawUtil.drawRound(x + pad, itemY, innerW, ClickGuiLayout.SIDEBAR_ITEM_HEIGHT, 4f,
                        ColorProvider.rgba(255, 255, 255, (int) (12 * alpha)));
            }

            int textColor = selected
                    ? ColorProvider.rgba(255, 255, 255, (int) (255 * alpha))
                    : ColorProvider.rgba(185, 185, 200, (int) (255 * alpha));
            DrawUtil.drawText(Fonts.SFREGULAR.get(), capitalize(category.name()), x + pad + 8f, itemY + 5f, textColor, 7f);

            itemY += ClickGuiLayout.SIDEBAR_ITEM_HEIGHT + ClickGuiLayout.SIDEBAR_ITEM_GAP;
        }

        // Версия
        float versionY = y + ClickGuiLayout.HEIGHT - 12f;
        DrawUtil.drawText(Fonts.SFREGULAR.get(), "v1.0.0", x + pad + 2f, versionY,
                ColorProvider.rgba(140, 140, 155, (int) (255 * alpha)), 5.5f);
        DrawUtil.drawRound(x + ClickGuiLayout.SIDEBAR_WIDTH - pad - 4f, versionY + 0.5f, 3f, 3f, 1.5f,
                ColorProvider.setAlpha(accent, (int) (255 * alpha)));
    }

    private static String capitalize(String text) {
        return text.substring(0, 1).toUpperCase() + text.substring(1).toLowerCase();
    }

    public static float getSidebarItemsY(float guiY) {
        return guiY + ClickGuiLayout.SIDEBAR_PADDING + ClickGuiLayout.PROFILE_HEIGHT + ClickGuiLayout.SIDEBAR_SECTION_GAP + 9f;
    }

    // ────────────────────────── Центральная панель ──────────────────────────

    private void renderMain(int mouseX, int mouseY, float x, float y, int accent, float alpha) {
        drawPanelBase(x, y, ClickGuiLayout.MAIN_WIDTH, ClickGuiLayout.HEIGHT, alpha);

        ModuleCategory category = state.getSelectedCategory();

        DrawUtil.drawText(Fonts.SFSEMIBOLD.get(), capitalize(category.name()), x + 10f, y + 9f,
                ColorProvider.rgba(255, 255, 255, (int) (255 * alpha)), 9.5f);
        DrawUtil.drawRound(x + 1f, y + ClickGuiLayout.MAIN_HEADER_HEIGHT - 1f,
                ClickGuiLayout.MAIN_WIDTH - 2f, 0.6f, 0.3f, ColorProvider.rgba(255, 255, 255, (int) (20 * alpha)));

        renderSearchField(x, y, alpha);

        float contentY = y + ClickGuiLayout.MAIN_HEADER_HEIGHT;
        Scissor.push();
        Scissor.setFromComponentCoordinates(x, contentY, ClickGuiLayout.MAIN_WIDTH, ClickGuiLayout.HEIGHT - ClickGuiLayout.MAIN_HEADER_HEIGHT);

        List<Module> modules = state.getModules(category);
        float cardW = ClickGuiLayout.getCardWidth();
        float scroll = state.getGridScroll(category);
        float gridTop = contentY + ClickGuiLayout.MAIN_PADDING / 2f;

        for (ClickGuiLayout.CardPos pos : ClickGuiLayout.layoutCards(modules, cardW)) {
            float cardX = x + ClickGuiLayout.MAIN_PADDING + pos.column() * (cardW + ClickGuiLayout.CARD_GAP);
            float cardY = gridTop + pos.offsetY() + scroll;
            if (cardY + pos.height() < contentY || cardY > y + ClickGuiLayout.HEIGHT) continue;
            renderModuleCard(mouseX, mouseY, cardX, cardY, cardW, pos.height(), pos.module(), accent, alpha);
        }

        Scissor.unset();
        Scissor.pop();
    }

    private void renderSearchField(float mainX, float mainY, float alpha) {
        float searchX = mainX + ClickGuiLayout.MAIN_WIDTH - ClickGuiLayout.SEARCH_WIDTH - 8f;
        float searchY = mainY + (ClickGuiLayout.MAIN_HEADER_HEIGHT - ClickGuiLayout.SEARCH_HEIGHT) / 2f;
        float radius = ClickGuiLayout.SEARCH_HEIGHT / 2f;

        DrawUtil.drawRound(searchX - 0.5f, searchY - 0.5f, ClickGuiLayout.SEARCH_WIDTH + 1f, ClickGuiLayout.SEARCH_HEIGHT + 1f,
                radius + 0.5f, ColorProvider.rgba(255, 255, 255, (int) (25 * alpha)));
        DrawUtil.drawRound(searchX, searchY, ClickGuiLayout.SEARCH_WIDTH, ClickGuiLayout.SEARCH_HEIGHT, radius,
                ColorProvider.rgba(255, 255, 255, (int) (10 * alpha)));

        String text = state.getSearchText();
        boolean hasText = text != null && !text.isEmpty();
        String shown = (hasText || state.isSearchActive()) ? (text != null ? text : "") : "Search...";
        int textColor = hasText
                ? ColorProvider.rgba(255, 255, 255, (int) (230 * alpha))
                : ColorProvider.rgba(150, 150, 165, (int) (255 * alpha));
        DrawUtil.drawText(Fonts.SFREGULAR.get(), shown, searchX + 8f, searchY + 3.5f, textColor, 6.5f);

        if (state.isSearchActive() && (System.currentTimeMillis() / 500) % 2 == 0) {
            float cursorX = searchX + 8f + (hasText ? Fonts.SFREGULAR.get().getWidth(text, 6.5f) : 0f) + 1f;
            DrawUtil.drawRound(cursorX, searchY + 3.5f, 0.8f, 7f, 0.4f,
                    ColorProvider.rgba(255, 255, 255, (int) (220 * alpha)));
        }
    }

    private void renderModuleCard(int mouseX, int mouseY, float x, float y, float w, float h,
                                  Module module, int accent, float alpha) {
        boolean hovered = HoverUtil.isHovered(mouseX, mouseY, x, y, w, h);
        Animation hoverAnim = state.getCardHoverAnimation(module);
        hoverAnim.run(hovered);
        float hover = hoverAnim.getValue();

        boolean selected = state.getSelectedModule() == module;
        if (module.isEnabled() || selected) {
            int borderAlpha = (int) ((module.isEnabled() ? 160 : 90) * alpha);
            DrawUtil.drawRound(x - 1f, y - 1f, w + 2f, h + 2f, 6f, ColorProvider.setAlpha(accent, borderAlpha));
        }

        int bgAlpha = (int) ((12 + 10 * hover) * alpha);
        DrawUtil.drawRound(x, y, w, h, 5f, ColorProvider.rgba(20, 20, 24, (int) (235 * alpha)));
        DrawUtil.drawRound(x, y, w, h, 5f, ColorProvider.rgba(255, 255, 255, bgAlpha));

        // Имя
        float toggleW = 16f;
        String name = ClickGuiLayout.truncate(module.getName(), 7f, w - toggleW - 14f);
        DrawUtil.drawText(Fonts.SFMEDIUM.get(), name, x + 6f, y + 5f,
                ColorProvider.rgba(255, 255, 255, (int) (255 * alpha)), 7f);

        // Бинд
        int key = module.getKey();
        String keyText = state.getBindingModule() == module ? "[...]" : (key != -1 ? "[" + KeyStorage.getKey(key) + "]" : "");
        if (!keyText.isEmpty()) {
            DrawUtil.drawText(Fonts.SFREGULAR.get(), keyText, x + 6f, y + h - 10f,
                    ColorProvider.rgba(150, 150, 165, (int) (255 * alpha)), 5.5f);
        }

        // Описание
        List<String> descLines = ClickGuiLayout.wrapText(module.getDesc(), ClickGuiLayout.CARD_DESC_SIZE,
                w - 12f, ClickGuiLayout.CARD_DESC_MAX_LINES);
        float descY = y + ClickGuiLayout.CARD_HEADER_HEIGHT - 2f;
        for (String line : descLines) {
            DrawUtil.drawText(Fonts.SFREGULAR.get(), line, x + 6f, descY,
                    ColorProvider.rgba(160, 160, 175, (int) (255 * alpha)), ClickGuiLayout.CARD_DESC_SIZE);
            descY += ClickGuiLayout.CARD_DESC_LINE_HEIGHT;
        }

        // Тумблер
        renderToggle(x + w - toggleW - 5f, y + 5.5f, toggleW, 8f, module, accent, alpha);

        // Индикатор настроек
        if (ClickGuiLayout.hasVisibleSettings(module)) {
            int gearColor = selected
                    ? ColorProvider.setAlpha(accent, (int) (255 * alpha))
                    : ColorProvider.rgba(160, 160, 175, (int) (255 * alpha));
            DrawUtil.drawText(Fonts.SFMEDIUM.get(), "...", x + w - 14f, y + h - 13f, gearColor, 8f);
        }
    }

    private void renderToggle(float x, float y, float w, float h, Module module, int accent, float alpha) {
        Animation anim = state.getToggleAnimation(module);
        anim.run(module.isEnabled());
        float progress = anim.getValue();

        int a = (int) (255 * alpha);
        int off = ColorProvider.rgba(45, 45, 58, a);
        int trackColor = ColorProvider.setAlpha(ColorProvider.interpolateColor(off, accent, progress), a);
        DrawUtil.drawRound(x, y, w, h, h / 2f, trackColor);

        float knobSize = h - 2f;
        float knobX = x + 1f + (w - 2f - knobSize) * progress;
        DrawUtil.drawRound(knobX, y + 1f, knobSize, knobSize, knobSize / 2f, ColorProvider.rgba(255, 255, 255, a));
    }

    // ────────────────────────── Панель настроек ──────────────────────────

    private void renderSettingsPanel(int mouseX, int mouseY, float x, float y, int accent, float alpha) {
        float panelProgress = state.getSettingsPanelProgress();
        Module module = state.getSelectedModule();
        if (panelProgress <= 0.01f || module == null) return;
        float alphaMul = alpha * panelProgress;

        drawPanelBase(x, y, ClickGuiLayout.SETTINGS_WIDTH, ClickGuiLayout.HEIGHT, alphaMul);

        String header = ClickGuiLayout.truncate(module.getName().toUpperCase() + " SETTINGS", 6.5f,
                ClickGuiLayout.SETTINGS_WIDTH - 14f);
        DrawUtil.drawText(Fonts.SFSEMIBOLD.get(), header, x + ClickGuiLayout.SETTINGS_PADDING, y + 7f,
                ColorProvider.setAlpha(accent, (int) (255 * alphaMul)), 6.5f);

        float contentY = y + ClickGuiLayout.SETTINGS_HEADER_HEIGHT;
        Scissor.push();
        Scissor.setFromComponentCoordinates(x, contentY, ClickGuiLayout.SETTINGS_WIDTH,
                ClickGuiLayout.HEIGHT - ClickGuiLayout.SETTINGS_HEADER_HEIGHT - 3f);
        settingRenderer.render(module,
                x + ClickGuiLayout.SETTINGS_PADDING, contentY + state.getSettingsScroll(),
                ClickGuiLayout.getSettingsInnerWidth(),
                accent, mouseX, mouseY, state, alphaMul);
        Scissor.unset();
        Scissor.pop();
    }

    // ────────────────────────── Общее ──────────────────────────

    private void drawPanelBase(float x, float y, float w, float h, float alpha) {
        if (tech.onetap.module.list.render.Optimization.shouldDisableClickGuiBlur()) {
            DrawUtil.drawRound(x, y, w, h, ClickGuiLayout.RADIUS,
                    ColorProvider.rgba(15, 12, 21, (int) (245 * alpha)));
        } else {
            DrawUtil.drawRoundBlur(x, y, w, h, ClickGuiLayout.RADIUS,
                    ColorProvider.rgba(75, 75, 75, (int) (255 * alpha)), 20f);
            DrawUtil.drawRound(x, y, w, h, ClickGuiLayout.RADIUS,
                    ColorProvider.rgba(15, 12, 21, (int) (225 * alpha)));
        }
        DrawUtil.drawRound(x - 0.5f, y - 0.5f, w + 1f, h + 1f, ClickGuiLayout.RADIUS + 0.5f,
                ColorProvider.rgba(255, 255, 255, (int) (18 * alpha)));
        DrawUtil.drawRound(x, y, w, h, ClickGuiLayout.RADIUS,
                ColorProvider.rgba(15, 12, 21, (int) ((tech.onetap.module.list.render.Optimization.shouldDisableClickGuiBlur() ? 245 : 225) * alpha)));
    }
}
