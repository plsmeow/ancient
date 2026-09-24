package meow.ancient.module.list.misc;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import meow.ancient.event.list.EventAttack;
import meow.ancient.module.Module;
import meow.ancient.module.ModuleCategory;
import meow.ancient.module.ModuleInformation;

@ModuleInformation(moduleName = "Crystal Optimizer", moduleDesc = "Оптимизирует взрывы кристаллов", moduleCategory = ModuleCategory.MISC)
public class CrystalOptimizer extends Module {
    @EventHandler
    private void onAttack(EventAttack e) {
        if (e.getEntity() instanceof EndCrystalEntity entity) {
            entity.remove(Entity.RemovalReason.DISCARDED);
        }
    }
}