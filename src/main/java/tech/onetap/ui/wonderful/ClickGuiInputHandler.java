package tech.onetap.ui.wonderful;

import net.minecraft.client.util.Window;
import org.lwjgl.glfw.GLFW;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.settings.*;
import tech.onetap.util.render.helper.HoverUtil;
import tech.onetap.util.render.msdf.Fonts;

import java.util.List;

public class ClickGuiInputHandler {

    private final ClickGuiState state;

    private SliderSetting draggingSlider;
    private float draggingSliderX;
    private float draggingSliderW;

    private boolean draggingGui;
    private double dragOffsetX;
    private double dragOffsetY;

    public ClickGuiInputHandler(ClickGuiState state) {
        this.state = state;
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button, Window window) {
        if (window == null) return false;
        float guiX = state.getX();
        float guiY = state.getY() + state.getRenderOffsetY();

        // Активные бинды мышью
        if (state.getBindingModule() != null && button >= 2) {
            state.getBindingModule().setKey(button);
            state.setBindingModule(null);
            return true;
        }
        if (state.getBindingSetting() != null && button >= 2) {
            state.getBindingSetting().setKey(button);
            state.setBindingSetting(null);
            return true;
        }

        // Поиск
        float mainX = ClickGuiLayout.getMainX(guiX);
        float searchX = mainX + ClickGuiLayout.MAIN_WIDTH - ClickGuiLayout.SEARCH_WIDTH - 8f;
        float searchY = guiY + (ClickGuiLayout.MAIN_HEADER_HEIGHT - ClickGuiLayout.SEARCH_HEIGHT) / 2f;
        if (button == 0 && HoverUtil.isHovered(mouseX, mouseY, searchX, searchY, ClickGuiLayout.SEARCH_WIDTH, ClickGuiLayout.SEARCH_HEIGHT)) {
            state.setSearchActive(true);
            state.setSearchCursor(state.getSearchText().length());
            return true;
        }
        if (button == 0 && state.isSearchActive()) {
            state.setSearchActive(false);
        }

        // Сайдбар: категории
        float sidebarX = ClickGuiLayout.getSidebarX(guiX);
        float pad = ClickGuiLayout.SIDEBAR_PADDING;
        float innerW = ClickGuiLayout.SIDEBAR_WIDTH - pad * 2f;
        float itemY = guiY + pad + ClickGuiLayout.PROFILE_HEIGHT + ClickGuiLayout.SIDEBAR_SECTION_GAP + 9f;
        for (ModuleCategory category : ClickGuiLayout.getVisibleCategories()) {
            if (button == 0 && HoverUtil.isHovered(mouseX, mouseY, sidebarX + pad, itemY, innerW, ClickGuiLayout.SIDEBAR_ITEM_HEIGHT)) {
                state.setSelectedCategory(category);
                return true;
            }
            itemY += ClickGuiLayout.SIDEBAR_ITEM_HEIGHT + ClickGuiLayout.SIDEBAR_ITEM_GAP;
        }

        // Сетка модулей
        float contentY = guiY + ClickGuiLayout.MAIN_HEADER_HEIGHT;
        if (HoverUtil.isHovered(mouseX, mouseY, mainX, contentY, ClickGuiLayout.MAIN_WIDTH, ClickGuiLayout.getGridViewHeight())) {
            ModuleCategory category = state.getSelectedCategory();
            List<Module> modules = state.getModules(category);
            float cardW = ClickGuiLayout.getCardWidth();
            float scroll = state.getGridScroll(category);
            float gridTop = contentY + ClickGuiLayout.MAIN_PADDING / 2f;

            for (ClickGuiLayout.CardPos pos : ClickGuiLayout.layoutCards(modules, cardW)) {
                Module module = pos.module();
                float cardX = mainX + ClickGuiLayout.MAIN_PADDING + pos.column() * (cardW + ClickGuiLayout.CARD_GAP);
                float cardY = gridTop + pos.offsetY() + scroll;
                float cardH = pos.height();
                if (!HoverUtil.isHovered(mouseX, mouseY, cardX, cardY, cardW, cardH)) continue;

                // Тумблер
                float toggleW = 16f;
                if (button == 0 && HoverUtil.isHovered(mouseX, mouseY, cardX + cardW - toggleW - 5f, cardY + 4f, toggleW + 3f, 11f)) {
                    module.toggle();
                    return true;
                }

                // Бинд средней кнопкой
                if (button == 2) {
                    state.setBindingModule(module);
                    return true;
                }

                // Открыть настройки
                if ((button == 0 || button == 1) && ClickGuiLayout.hasVisibleSettings(module)) {
                    state.setSelectedModule(module);
                    return true;
                }
                if (button == 0) {
                    module.toggle();
                }
                return true;
            }
            return tryStartDrag(mouseX, mouseY, button);
        }

        // Панель настроек
        Module selected = state.getSelectedModule();
        if (selected != null) {
            float settingsX = ClickGuiLayout.getSettingsX(guiX);
            float settingsContentY = guiY + ClickGuiLayout.SETTINGS_HEADER_HEIGHT;
            if (HoverUtil.isHovered(mouseX, mouseY, settingsX, guiY, ClickGuiLayout.SETTINGS_WIDTH, ClickGuiLayout.HEIGHT)) {
                if (mouseY < settingsContentY) {
                    return tryStartDrag(mouseX, mouseY, button);
                }
                return handleSettingClick(mouseX, mouseY, button,
                        settingsX + ClickGuiLayout.SETTINGS_PADDING,
                        settingsContentY + state.getSettingsScroll(),
                        ClickGuiLayout.getSettingsInnerWidth(),
                        selected.getSettings());
            }
        }

        if (isOverAnyPanel(mouseX, mouseY, guiX, guiY)) {
            return tryStartDrag(mouseX, mouseY, button);
        }

        return false;
    }

    private boolean isOverAnyPanel(double mouseX, double mouseY, float guiX, float guiY) {
        if (HoverUtil.isHovered(mouseX, mouseY, ClickGuiLayout.getSidebarX(guiX), guiY,
                ClickGuiLayout.SIDEBAR_WIDTH, ClickGuiLayout.HEIGHT)) return true;
        if (HoverUtil.isHovered(mouseX, mouseY, ClickGuiLayout.getMainX(guiX), guiY,
                ClickGuiLayout.MAIN_WIDTH, ClickGuiLayout.HEIGHT)) return true;
        return state.getSelectedModule() != null
                && HoverUtil.isHovered(mouseX, mouseY, ClickGuiLayout.getSettingsX(guiX), guiY,
                ClickGuiLayout.SETTINGS_WIDTH, ClickGuiLayout.HEIGHT);
    }

    private boolean tryStartDrag(double mouseX, double mouseY, int button) {
        if (button != 0) return true;
        draggingGui = true;
        dragOffsetX = mouseX - state.getX();
        dragOffsetY = mouseY - state.getY();
        return true;
    }

    private boolean handleSettingClick(double mouseX, double mouseY, int button, float x, float startY, float width,
                                       List<Setting> settings) {
        if (settings == null) return true;
        float y = startY;
        List<Setting> visible = settings.stream().filter(s -> s.visible.get()).toList();
        for (int i = 0; i < visible.size(); i++) {
            Setting setting = visible.get(i);

            if (setting instanceof BooleanSetting booleanSetting) {
                if (HoverUtil.isHovered(mouseX, mouseY, x, y - 1, width, 12)) {
                    if (button == 0) {
                        booleanSetting.toggle();
                        return true;
                    }
                    if (button == 2) {
                        state.setBindingSetting(booleanSetting);
                        return true;
                    }
                }
            } else if (setting instanceof SliderSetting sliderSetting) {
                if (button == 0 && HoverUtil.isHovered(mouseX, mouseY, x, y + 9, width, 8)) {
                    sliderSetting.setValue(state.getSliderValue(sliderSetting, x, mouseX, width));
                    draggingSlider = sliderSetting;
                    draggingSliderX = x;
                    draggingSliderW = width;
                    state.setDraggingSlider(sliderSetting);
                    return true;
                }
            } else if (setting instanceof ModeSetting modeSetting) {
                float cx = x, cy = y + 9f, rowH = 11f;
                for (String mode : modeSetting.getModes()) {
                    float chipW = Fonts.SFREGULAR.get().getWidth(mode, 6f) + ClickGuiLayout.CHIP_PADDING_X * 2f;
                    if (cx + chipW > x + width && cx > x) {
                        cx = x;
                        cy += rowH + ClickGuiLayout.CHIP_GAP_Y;
                    }
                    if (button == 0 && HoverUtil.isHovered(mouseX, mouseY, cx, cy, chipW, rowH)) {
                        modeSetting.setValue(mode);
                        return true;
                    }
                    cx += chipW + ClickGuiLayout.CHIP_GAP_X;
                }
            } else if (setting instanceof ModeListSetting multi) {
                float cx = x, cy = y + 9f, rowH = 11f;
                for (BooleanSetting val : multi.getSettings()) {
                    float chipW = Fonts.SFREGULAR.get().getWidth(val.getName(), 6f) + ClickGuiLayout.CHIP_PADDING_X * 2f;
                    if (cx + chipW > x + width && cx > x) {
                        cx = x;
                        cy += rowH + ClickGuiLayout.CHIP_GAP_Y;
                    }
                    if (button == 0 && HoverUtil.isHovered(mouseX, mouseY, cx, cy, chipW, rowH)) {
                        val.toggle();
                        return true;
                    }
                    cx += chipW + ClickGuiLayout.CHIP_GAP_X;
                }
            } else if (setting instanceof BindSetting bindSetting) {
                if (button == 0 && HoverUtil.isHovered(mouseX, mouseY, x, y - 1, width, 12)) {
                    state.setBindingSetting(bindSetting);
                    return true;
                }
            }

            y += ClickGuiLayout.getSettingHeight(setting, width);
            if (i < visible.size() - 1) y += ClickGuiLayout.SETTING_GAP;
        }
        return true;
    }

    public boolean mouseReleased(int button) {
        if (button == 0) {
            if (draggingGui) {
                draggingGui = false;
                return true;
            }
            if (draggingSlider != null) {
                state.setDraggingSlider(null);
                draggingSlider = null;
                return true;
            }
        }
        return false;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        if (draggingGui) {
            state.setX((float) (mouseX - dragOffsetX));
            state.setY((float) (mouseY - dragOffsetY));
            return true;
        }
        if (draggingSlider != null) {
            draggingSlider.setValue(state.getSliderValue(draggingSlider, draggingSliderX, mouseX, draggingSliderW));
            return true;
        }
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double verticalAmount) {
        float guiX = state.getX();
        float guiY = state.getY() + state.getRenderOffsetY();

        float mainX = ClickGuiLayout.getMainX(guiX);
        float contentY = guiY + ClickGuiLayout.MAIN_HEADER_HEIGHT;
        if (HoverUtil.isHovered(mouseX, mouseY, mainX, contentY, ClickGuiLayout.MAIN_WIDTH, ClickGuiLayout.getGridViewHeight())) {
            state.addGridScroll(state.getSelectedCategory(), verticalAmount);
            return true;
        }

        float settingsX = ClickGuiLayout.getSettingsX(guiX);
        if (state.getSelectedModule() != null
                && HoverUtil.isHovered(mouseX, mouseY, settingsX, guiY, ClickGuiLayout.SETTINGS_WIDTH, ClickGuiLayout.HEIGHT)) {
            state.addSettingsScroll(verticalAmount);
            return true;
        }
        return false;
    }

    public boolean charTyped(char chr, int modifiers) {
        if (!state.isSearchActive() || Character.isISOControl(chr)) return false;
        String text = state.getSearchText();
        if (text.length() >= ClickGuiLayout.SEARCH_MAX_CHARS) return true;
        int cursor = Math.max(0, Math.min(state.getSearchCursor(), text.length()));
        state.setSearchText(text.substring(0, cursor) + chr + text.substring(cursor));
        state.setSearchCursor(cursor + 1);
        return true;
    }

    public boolean keyPressed(int keyCode, int modifiers) {
        // Поиск
        if (state.isSearchActive()) {
            String text = state.getSearchText();
            int cursor = Math.max(0, Math.min(state.getSearchCursor(), text.length()));
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_ENTER) {
                state.setSearchActive(false);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (cursor > 0) {
                    state.setSearchText(text.substring(0, cursor - 1) + text.substring(cursor));
                    state.setSearchCursor(cursor - 1);
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_DELETE) {
                if (cursor < text.length()) state.setSearchText(text.substring(0, cursor) + text.substring(cursor + 1));
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_LEFT) {
                state.setSearchCursor(Math.max(0, cursor - 1));
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_RIGHT) {
                state.setSearchCursor(Math.min(text.length(), cursor + 1));
                return true;
            }
            return true;
        }

        // Бинд модуля
        if (state.getBindingModule() != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                state.setBindingModule(null);
            } else if (keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                state.getBindingModule().setKey(-1);
                state.setBindingModule(null);
            } else {
                state.getBindingModule().setKey(keyCode);
                state.setBindingModule(null);
            }
            return true;
        }

        // Бинд настройки
        if (state.getBindingSetting() != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                state.setBindingSetting(null);
            } else if (keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                state.getBindingSetting().setKey(-1);
                state.setBindingSetting(null);
            } else {
                state.getBindingSetting().setKey(keyCode);
                state.setBindingSetting(null);
            }
            return true;
        }

        // Esc закрывает панель настроек
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && state.getSelectedModule() != null) {
            state.setSelectedModule(state.getSelectedModule());
            return true;
        }

        return false;
    }
}
