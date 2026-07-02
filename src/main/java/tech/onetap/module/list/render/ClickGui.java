package tech.onetap.module.list.render;

import org.lwjgl.glfw.GLFW;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.ui.ClickGuiFrame;
import tech.onetap.ui.Panel;

@ModuleInformation(moduleName = "Click Gui", moduleCategory = ModuleCategory.RENDER, moduleKeybind = GLFW.GLFW_KEY_RIGHT_SHIFT)
public class ClickGui extends Module {

    private ClickGuiFrame clickGuiFrame;

    @Override
    public void onEnable() {
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
