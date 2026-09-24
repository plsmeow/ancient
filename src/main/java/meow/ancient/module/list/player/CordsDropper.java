package meow.ancient.module.list.player;

import meteordevelopment.orbit.EventHandler;
import meow.ancient.event.list.EventKeyInput;
import meow.ancient.module.Module;
import meow.ancient.module.ModuleCategory;
import meow.ancient.module.ModuleInformation;
import meow.ancient.module.settings.BindSetting;

@ModuleInformation(moduleName = "Cords Dropper", moduleDesc = "Отправляет свои координаты в чат по бинду", moduleCategory = ModuleCategory.PLAYER)
public class CordsDropper extends Module {

    private final BindSetting bind = new BindSetting("Key",-1);

    @EventHandler
    private void onKey(EventKeyInput e) {
        if (e.getAction() == 0) return;
        if (e.getKey() == bind.getValue()) {
            if (mc.player != null) {
                String message = String.format("! %.0f %.0f !!!", mc.player.getX(), mc.player.getZ());
                mc.player.networkHandler.sendChatMessage(message);
            }
        }
    }
}