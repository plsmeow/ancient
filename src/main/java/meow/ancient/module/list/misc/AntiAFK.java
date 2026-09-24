package meow.ancient.module.list.misc;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.Hand;
import meow.ancient.event.list.EventPlayerUpdate;
import meow.ancient.module.Module;
import meow.ancient.module.ModuleCategory;
import meow.ancient.module.ModuleInformation;
import meow.ancient.util.math.StopWatch;

@ModuleInformation(moduleName = "Anti AFK", moduleDesc = "Предотвращает кик за AFK", moduleCategory = ModuleCategory.MISC)
public class AntiAFK extends Module {

    private final StopWatch stopWatch = new StopWatch();

    @EventHandler
    private void onUpdate(EventPlayerUpdate e) {
        if (mc.player == null || mc.world == null) return;

        if (stopWatch.isReached(5000)) {
            mc.player.swingHand(Hand.MAIN_HAND);
            mc.player.jump();
            stopWatch.reset();
        }
    }

    @Override
    public void onEnable() {
        super.onEnable();
        stopWatch.reset();
    }
}
