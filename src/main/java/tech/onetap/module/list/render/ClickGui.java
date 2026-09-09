package tech.onetap.module.list.render;

import org.lwjgl.glfw.GLFW;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.settings.ModeSetting;
import tech.onetap.ui.ClickGuiFrame;
import tech.onetap.ui.Panel;

@ModuleInformation(moduleName = "Click Gui", moduleDesc = "Открывает клик-гуй клиента", moduleCategory = ModuleCategory.RENDER, moduleKeybind = GLFW.GLFW_KEY_RIGHT_SHIFT)
public class ClickGui extends Module {

    public final ModeSetting mode = new ModeSetting("Режим", "Dropdown", "Dropdown", "Panel", "New", "Delta");

    private ClickGuiFrame clickGuiFrame;

    public boolean isPanelMode() {
        return mode.is("Panel");
    }

    public boolean isNewMode() {
        return mode.is("New");
    }

    @Override
    public void onEnable() {
        if (mode.is("Delta")) {
            if (mc.currentScreen == null) {
                mc.setScreen(tech.onetap.ui.delta.DeltaGuiScreen.getInstance());
            }
            toggle();
            return;
        }
        if (isNewMode()) {
            if (mc.currentScreen == null) {
                mc.setScreen(new tech.onetap.ui.NewClickGuiFrame());
            }
            toggle();
            return;
        }
        if (clickGuiFrame == null) clickGuiFrame = new ClickGuiFrame();
        mc.setScreen(clickGuiFrame);
        for (Panel panel : clickGuiFrame.getPanels()) {
            panel.getAnimationAlpha().setValue(0);
            panel.getAnimationAlpha().setStartValue(0);
            panel.getAnimationAlpha().reset();
        }
        toggle();
    }
}
