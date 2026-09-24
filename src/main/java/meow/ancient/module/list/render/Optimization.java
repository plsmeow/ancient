package meow.ancient.module.list.render;

import net.minecraft.client.MinecraftClient;
import meow.ancient.module.Module;
import meow.ancient.module.ModuleCategory;
import meow.ancient.module.ModuleInformation;
import meow.ancient.ui.ClickGuiFrame;
import meow.ancient.util.base.Instance;

@ModuleInformation(moduleName = "Optimization", moduleDesc = "Оптимизация рендера клиента", moduleCategory = ModuleCategory.RENDER)
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
        return isActive() && (MinecraftClient.getInstance().currentScreen instanceof ClickGuiFrame
                || MinecraftClient.getInstance().currentScreen instanceof meow.ancient.ui.NewClickGuiFrame);
    }

    public static boolean shouldDisableInterfaceBlur() {
        return isActive();
    }
}
