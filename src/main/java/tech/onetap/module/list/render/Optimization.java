package tech.onetap.module.list.render;

import net.minecraft.client.MinecraftClient;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.ui.ClickGuiFrame;
import tech.onetap.util.base.Instance;

@ModuleInformation(moduleName = "Optimization", moduleCategory = ModuleCategory.RENDER)
public class Optimization extends Module {
    public static boolean isActive() {
        try {
            Optimization optimization = Instance.get(Optimization.class);
            return optimization != null && optimization.isEnabled();
        } catch (Exception ignored) {
            return false;
        }
    }

    public static boolean shouldDisableClickGuiBlur() {
        return isActive() && MinecraftClient.getInstance().currentScreen instanceof ClickGuiFrame;
    }

    public static boolean shouldDisableInterfaceBlur() {
        return isActive();
    }
}
