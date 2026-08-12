package tech.onetap.module.list.combat;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.LivingEntity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntityS2CPacket;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket;
import tech.onetap.event.list.EventPacket;
import tech.onetap.event.list.EventTick;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.settings.SliderSetting;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

@ModuleInformation(moduleName = "Backtrack", moduleDesc = "Delays target movement packets", moduleCategory = ModuleCategory.COMBAT)
public class Backtrack extends Module {
    private static final long DELAY_MS = 100L;

    private final SliderSetting minDistance = new SliderSetting("Минимальная дистанция", 2.5, 0.0, 6.0, 0.1);
    private final Queue<DelayedPacket> packets = new ConcurrentLinkedQueue<>();

    @Override
    public void onEnable() {
        packets.clear();
        super.onEnable();
    }

    @Override
    public void onDisable() {
        flush();
        packets.clear();
        super.onDisable();
    }

    @EventHandler
    private void onPacket(EventPacket event) {
        if (event.getType() != EventPacket.Type.RECEIVE || mc.player == null || mc.world == null) return;

        Packet<?> packet = event.getPacket();
        if (packet instanceof GameJoinS2CPacket || packet instanceof PlayerRespawnS2CPacket) {
            flush();
            return;
        }
        if (!(packet instanceof EntityS2CPacket entityPacket) || !shouldDelay(entityPacket)) return;

        packets.add(new DelayedPacket(entityPacket, System.currentTimeMillis() + DELAY_MS));
        event.cancelEvent();
    }

    @EventHandler
    private void onTick(EventTick event) {
        if (mc.player == null || mc.world == null) {
            packets.clear();
            return;
        }
        flushExpired();
    }

    private boolean shouldDelay(EntityS2CPacket packet) {
        KillAura aura = tech.onetap.Onetap.getInstance().getModuleStorage().get(KillAura.class);
        if (aura == null || !aura.isEnabled() || !(aura.getTarget() instanceof LivingEntity target)) return false;
        if (packet.getEntity(mc.world) != target) return false;
        return mc.player.distanceTo(target) >= minDistance.getValue();
    }

    private void flushExpired() {
        long now = System.currentTimeMillis();
        DelayedPacket delayed;
        while ((delayed = packets.peek()) != null && delayed.releaseAt <= now) {
            packets.poll();
            apply(delayed.packet);
        }
    }

    private void flush() {
        DelayedPacket delayed;
        while ((delayed = packets.poll()) != null) {
            apply(delayed.packet);
        }
    }
    @SuppressWarnings("unchecked")
    private void apply(EntityS2CPacket packet) {
        packet.apply((net.minecraft.network.listener.ClientPlayPacketListener) mc.getNetworkHandler());
    }

    private record DelayedPacket(EntityS2CPacket packet, long releaseAt) {
    }
}
