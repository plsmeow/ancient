package tech.onetap.ui.wonderful;

import net.minecraft.client.util.Window;
import tech.onetap.Onetap;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.settings.BooleanSetting;
import tech.onetap.module.settings.Setting;
import tech.onetap.module.settings.SliderSetting;
import tech.onetap.util.render.math.Animation;
import tech.onetap.util.render.math.Easing;

import java.util.*;

public class ClickGuiState {

    private static final Map<Character, Character> RU_TO_EN = new HashMap<>();

    static {
        String ru = "йцукенгшщзхъфывапролджэячсмитьбюЙЦУКЕНГШЩЗХЪФЫВАПРОЛДЖЭЯЧСМИТЬБЮ";
        String en = "qwertyuiop[]asdfghjkl;'zxcvbnm,.QWERTYUIOP[]ASDFGHJKL;'ZXCVBNM,.";
        int length = Math.min(ru.length(), en.length());
        for (int i = 0; i < length; i++) {
            RU_TO_EN.put(ru.charAt(i), en.charAt(i));
        }
    }

    private final Map<Module, Animation> toggleAnimation = new IdentityHashMap<>();
    private final Map<Module, Animation> cardHoverAnimation = new IdentityHashMap<>();
    private final Map<BooleanSetting, Animation> booleanBackgroundAnimation = new IdentityHashMap<>();
    private final Map<SliderSetting, Animation> sliderAnimation = new IdentityHashMap<>();
    private final Map<String, Animation> chipAnimation = new HashMap<>();
    private final Map<ModuleCategory, Float> gridScrollTarget = new EnumMap<>(ModuleCategory.class);
    private final Map<ModuleCategory, Animation> gridScrollAnimation = new EnumMap<>(ModuleCategory.class);
    private final Map<ModuleCategory, List<Module>> modulesByCategory = new EnumMap<>(ModuleCategory.class);
    private final List<Module> allModules = new ArrayList<>();

    private final Animation settingsPanelAnimation = new Animation(Easing.CUBIC_OUT, 220);
    private final Animation settingsScrollAnimation = new Animation(Easing.CUBIC_OUT, 250);

    private float x;
    private float y;
    private float renderOffsetY;

    private ModuleCategory selectedCategory = ModuleCategory.COMBAT;
    private Module selectedModule;
    private float settingsScrollTarget;

    private Setting bindingSetting;
    private Module bindingModule;

    private boolean searchActive;
    private String searchText = "";
    private int searchCursor;

    private SliderSetting draggingSlider;

    public ClickGuiState() {
        refreshModules();
    }

    public void refreshModules() {
        allModules.clear();
        allModules.addAll(Onetap.getInstance().getModuleStorage().getModules());
        for (ModuleCategory category : ModuleCategory.values()) {
            modulesByCategory.put(category, allModules.stream()
                    .filter(module -> module.getCategory() == category)
                    .sorted(Comparator.comparing(m -> m.getName().toLowerCase(Locale.ROOT)))
                    .toList());
            gridScrollTarget.putIfAbsent(category, 0f);
            gridScrollAnimation.putIfAbsent(category, new Animation(Easing.CUBIC_OUT, 300));
        }
    }

    public void updatePosition(Window window) {
        this.x = (window.getScaledWidth() / 2F) - (ClickGuiLayout.getTotalWidth() / 2F);
        this.y = (window.getScaledHeight() / 2F) - (ClickGuiLayout.HEIGHT / 2F);
    }

    public float getX() { return x; }
    public float getY() { return y; }
    public void setX(float x) { this.x = x; }
    public void setY(float y) { this.y = y; }
    public float getRenderOffsetY() { return renderOffsetY; }
    public void setRenderOffsetY(float renderOffsetY) { this.renderOffsetY = renderOffsetY; }

    // ── Категория / модуль ──

    public ModuleCategory getSelectedCategory() { return selectedCategory; }

    public void setSelectedCategory(ModuleCategory category) {
        if (this.selectedCategory != category) {
            this.selectedCategory = category;
            this.selectedModule = null;
            this.settingsScrollTarget = 0f;
            this.settingsScrollAnimation.setValue(0f);
        }
    }

    public Module getSelectedModule() { return selectedModule; }

    public void setSelectedModule(Module module) {
        this.selectedModule = this.selectedModule == module ? null : module;
        this.settingsScrollTarget = 0f;
        this.settingsScrollAnimation.setValue(0f);
    }

    public float getSettingsPanelProgress() {
        settingsPanelAnimation.run(selectedModule != null);
        return settingsPanelAnimation.getValue();
    }

    public List<Module> getModules(ModuleCategory category) {
        List<Module> modules = modulesByCategory.getOrDefault(category, List.of());
        if (searchText.isBlank()) return modules;
        String query = searchText.toLowerCase(Locale.ROOT);
        String queryEn = toEnglish(searchText).toLowerCase(Locale.ROOT);
        return modules.stream()
                .filter(module -> module.getName().toLowerCase(Locale.ROOT).contains(query)
                        || module.getName().toLowerCase(Locale.ROOT).contains(queryEn))
                .toList();
    }

    public String toEnglish(String text) {
        StringBuilder result = new StringBuilder();
        for (char c : text.toCharArray()) {
            result.append(RU_TO_EN.getOrDefault(c, c));
        }
        return result.toString();
    }

    // ── Скроллы ──

    public float getGridScroll(ModuleCategory category) {
        Animation animation = gridScrollAnimation.computeIfAbsent(category, key -> new Animation(Easing.CUBIC_OUT, 300));
        animation.run(gridScrollTarget.getOrDefault(category, 0f));
        return animation.getValue();
    }

    public void addGridScroll(ModuleCategory category, double amount) {
        float contentHeight = ClickGuiLayout.getGridContentHeight(getModules(category), ClickGuiLayout.getCardWidth());
        float maxScroll = Math.min(0f, ClickGuiLayout.getGridViewHeight() - contentHeight);
        float target = gridScrollTarget.getOrDefault(category, 0f) + (float) (amount * 20f);
        gridScrollTarget.put(category, Math.max(maxScroll, Math.min(0f, target)));
    }

    public float getSettingsScroll() {
        settingsScrollAnimation.run(settingsScrollTarget);
        return settingsScrollAnimation.getValue();
    }

    public void addSettingsScroll(double amount) {
        if (selectedModule == null) return;
        float contentHeight = ClickGuiLayout.calculateSettingsHeight(selectedModule, ClickGuiLayout.getSettingsInnerWidth());
        float maxScroll = Math.min(0f, ClickGuiLayout.getSettingsViewHeight() - contentHeight - 4f);
        settingsScrollTarget = Math.max(maxScroll, Math.min(0f, settingsScrollTarget + (float) (amount * 15f)));
    }

    // ── Анимации ──

    public Animation getToggleAnimation(Module module) {
        return toggleAnimation.computeIfAbsent(module, key -> new Animation(Easing.CUBIC_OUT, 200));
    }

    public Animation getCardHoverAnimation(Module module) {
        return cardHoverAnimation.computeIfAbsent(module, key -> new Animation(Easing.CUBIC_OUT, 150));
    }

    public Animation getBooleanBackgroundAnimation(BooleanSetting setting) {
        return booleanBackgroundAnimation.computeIfAbsent(setting, key -> new Animation(Easing.CUBIC_OUT, 200));
    }

    public Animation getSliderAnimation(SliderSetting setting) {
        return sliderAnimation.computeIfAbsent(setting, key -> new Animation(Easing.CUBIC_OUT, 300));
    }

    public Animation getChipAnimation(Setting setting, String value) {
        return chipAnimation.computeIfAbsent(System.identityHashCode(setting) + ":" + value,
                key -> new Animation(Easing.CUBIC_OUT, 200));
    }

    // ── Слайдеры ──

    public float getSliderPos(SliderSetting setting) {
        double delta = setting.getMax() - setting.getMin();
        if (delta == 0) return 0f;
        return (float) ((setting.getValue() - setting.getMin()) / delta);
    }

    public double getSliderValue(SliderSetting setting, float posX, double mouseX, float sliderWidth) {
        double delta = setting.getMax() - setting.getMin();
        float clickedX = (float) mouseX - posX;
        float value = Math.max(0f, Math.min(1f, clickedX / sliderWidth));
        double outValue = setting.getMin() + delta * value;
        double step = setting.getStep();
        if (step > 0) outValue = Math.round(outValue / step) * step;
        return Math.max(setting.getMin(), Math.min(setting.getMax(), outValue));
    }

    public boolean isDraggingSlider(SliderSetting setting) { return draggingSlider == setting; }
    public SliderSetting getDraggingSlider() { return draggingSlider; }
    public void setDraggingSlider(SliderSetting setting) { this.draggingSlider = setting; }

    // ── Бинды / поиск ──

    public Setting getBindingSetting() { return bindingSetting; }
    public void setBindingSetting(Setting bindingSetting) { this.bindingSetting = bindingSetting; }
    public Module getBindingModule() { return bindingModule; }
    public void setBindingModule(Module bindingModule) { this.bindingModule = bindingModule; }

    public boolean isSearchActive() { return searchActive; }
    public void setSearchActive(boolean searchActive) { this.searchActive = searchActive; }
    public String getSearchText() { return searchText; }
    public void setSearchText(String searchText) { this.searchText = searchText; }
    public int getSearchCursor() { return searchCursor; }
    public void setSearchCursor(int searchCursor) { this.searchCursor = searchCursor; }
}
