package tech.onetap.util.math;

import meteordevelopment.orbit.EventHandler;
import lombok.Getter;
import net.minecraft.network.packet.s2c.common.CommonPingS2CPacket;
import tech.onetap.Onetap;
import tech.onetap.event.list.EventPacket;
import tech.onetap.event.list.EventTick;
import tech.onetap.util.IMinecraft;

@Getter
public class PingGetter implements IMinecraft {
    public PingGetter() {
        Onetap.getInstance().getEventBus().subscribe(this);
    }

    private final StopWatch stopWatch = new StopWatch();
    private boolean lagged;
    private int ping;

    @EventHandler
    private void onUpdate(EventTick e) {
        ping = (int) stopWatch.getTime();
        if (stopWatch.getTime() > 1000) lagged = true;
    }

    @EventHandler
    private void onPacket(EventPacket e) {
        if (e.getPacket() instanceof CommonPingS2CPacket) {
            stopWatch.reset();
        }
    }
}