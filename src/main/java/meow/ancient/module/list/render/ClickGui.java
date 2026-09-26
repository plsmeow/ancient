package meow.ancient.module.list.render;

import org.lwjgl.glfw.GLFW;
import meow.ancient.module.Module;
import meow.ancient.module.ModuleCategory;
import meow.ancient.module.ModuleInformation;
import meow.ancient.module.settings.BooleanSetting;
import meow.ancient.module.settings.ColorSetting;
import meow.ancient.module.settings.ModeSetting;
import meow.ancient.ui.ClickGuiFrame;
import meow.ancient.ui.Panel;
import meow.ancient.ui.celestial.CelestialScreen;

@ModuleInformation(moduleName = "Click Gui", moduleDesc = "Открывает клик-гуй клиента", moduleCategory = ModuleCategory.RENDER, moduleKeybind = GLFW.GLFW_KEY_RIGHT_SHIFT)
public class ClickGui extends Module {

    public final ModeSetting mode = new ModeSetting("Режим", "Dropdown", "Dropdown", "Panel", "New", "Delta", "Celestial old");

    public final ColorSetting celestialPrimary = (ColorSetting) new ColorSetting("Первый цвет", 0xFFBF68FF).setVisible(() -> false);
    public final ColorSetting celestialSecondary = (ColorSetting) new ColorSetting("Второй цвет", 0xFF110122).setVisible(() -> false);
    public final BooleanSetting celestialBlur = (BooleanSetting) new BooleanSetting("Размытие", true).setVisible(() -> mode.is("Celestial old"));
    public final BooleanSetting celestialShadows = (BooleanSetting) new BooleanSetting("Тени", true).setVisible(() -> mode.is("Celestial old"));

    private ClickGuiFrame clickGuiFrame;

    public boolean isPanelMode() {
        return mode.is("Panel");
    }

    public boolean isNewMode() {
        return mode.is("New");
    }

    public boolean isCelestialOldMode() {
        return mode.is("Celestial old");
    }

    @Override
    public void onEnable() {
        if (mode.is("Celestial old")) {
            if (mc.currentScreen == null) {
                mc.setScreen(new CelestialScreen());
            }
            toggle();
            return;
        }
        if (mode.is("Delta")) {
            if (mc.currentScreen == null) {
                mc.setScreen(meow.ancient.ui.delta.DeltaGuiScreen.getInstance());
            }
            toggle();
            return;
        }
        if (isNewMode()) {
            if (mc.currentScreen == null) {
                mc.setScreen(new meow.ancient.ui.NewClickGuiFrame());
            }
            toggle();
            return;
        }
        if (clickGuiFrame == null) clickGuiFrame = new ClickGuiFrame();
        clickGuiFrame.getThemeManager().syncFromManager();
        mc.setScreen(clickGuiFrame);
        for (Panel panel : clickGuiFrame.getPanels()) {
            panel.getAnimationAlpha().setValue(0);
            panel.getAnimationAlpha().setStartValue(0);
            panel.getAnimationAlpha().reset();
        }
        toggle();
    }
}
