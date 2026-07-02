package tech.onetap.util.math;

import com.google.common.eventbus.Subscribe;
import lombok.Getter;
import net.minecraft.network.packet.s2c.play.WorldTimeUpdateS2CPacket;
import net.minecraft.util.math.MathHelper;
import tech.onetap.Onetap;
import tech.onetap.event.list.EventPacket;

import java.util.LinkedList;
import java.util.Queue;

@Getter
public class TPSGetter {
    private final Queue<Float> tpsHistory = new LinkedList<>();
    private float TPS = 20;
    private float averageTPS = 20;
    private float adjustTicks = 0;
    private long timestamp;

    public TPSGetter() {
        Onetap.getInstance().getEventBus().register(this);
    }

    @Subscribe
    private void onPacket(EventPacket e) {
        if (e.getPacket() != null && e.getPacket() instanceof WorldTimeUpdateS2CPacket) {
            updateTPS();
        }
    }

    private void updateTPS() {
        long delay = System.nanoTime() - timestamp;

        float maxTPS = 20;
        float rawTPS = maxTPS * (1e9f / delay);
        float boundedTPS = MathHelper.clamp(rawTPS, 0, maxTPS);

        TPS = (float) round(boundedTPS);
        adjustTicks = boundedTPS - maxTPS;
        timestamp = System.nanoTime();

        updateAverageTPS();
    }

    private void updateAverageTPS() {
        if (tpsHistory.size() >= 10) {
            tpsHistory.poll();
        }
        tpsHistory.add(TPS);

        float sum = 0;
        for (float tps : tpsHistory) {
            sum += tps;
        }
        averageTPS = (float) round(sum / tpsHistory.size());
    }

    public double round(final double input) {
        return Math.round(input * 100.0) / 100.0;
    }
}