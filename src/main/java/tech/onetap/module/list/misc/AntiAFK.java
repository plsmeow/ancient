package tech.onetap.module.list.misc;

import com.google.common.eventbus.Subscribe;
import net.minecraft.util.Hand;
import tech.onetap.event.list.EventPlayerUpdate;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.util.math.StopWatch;

@ModuleInformation(moduleName = "Anti AFK", moduleDesc = "Предотвращает кик за AFK", moduleCategory = ModuleCategory.MISC)
public class AntiAFK extends Module {

    private final StopWatch stopWatch = new StopWatch();

    @Subscribe
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
