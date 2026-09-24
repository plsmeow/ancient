package meow.ancient.module.list.player;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.Items;
import meow.ancient.event.list.EventPlayerUpdate;
import meow.ancient.module.Module;
import meow.ancient.module.ModuleCategory;
import meow.ancient.module.ModuleInformation;

@ModuleInformation(moduleName = "Fast Exp", moduleDesc = "Кидает бутылки опыта без задержки", moduleCategory = ModuleCategory.PLAYER)
public class FastExp extends Module {

    @EventHandler
    private void onPlayerUpdate(EventPlayerUpdate e) {
        if (!(mc.player.getMainHandStack().getItem() == Items.EXPERIENCE_BOTTLE || mc.player.getOffHandStack().getItem() == Items.EXPERIENCE_BOTTLE)) return;

        mc.itemUseCooldown = 0;
    }
}