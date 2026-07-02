package tech.onetap.module.list.misc;

import com.google.common.eventbus.Subscribe;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import tech.onetap.event.list.EventAttack;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;

@ModuleInformation(moduleName = "Crystal Optimizer", moduleCategory = ModuleCategory.MISC)
public class CrystalOptimizer extends Module {
    @Subscribe
    private void onAttack(EventAttack e) {
        if (e.getEntity() instanceof EndCrystalEntity entity) {
            entity.remove(Entity.RemovalReason.DISCARDED);
        }
    }
}