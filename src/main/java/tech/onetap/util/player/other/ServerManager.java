package tech.onetap.util.player.other;

import com.google.common.eventbus.Subscribe;
import lombok.Getter;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import tech.onetap.Onetap;
import tech.onetap.event.list.EventPacket;
import tech.onetap.event.list.EventPlayerUpdate;
import tech.onetap.event.list.EventPopTotem;
import tech.onetap.event.list.EventTick;
import tech.onetap.util.IMinecraft;

@Getter
public class ServerManager implements IMinecraft {

    private int serverSlot;
    private float serverYaw, serverPitch, fallDistance;
    private double serverX, serverY, serverZ;
    private boolean serverOnGround, serverSprinting, serverSneaking, serverHorizontalCollision;

    private float sprintingChangeTicks;

    public ServerManager() {
        Onetap.getInstance().getEventBus().register(this);
    }

    @Subscribe
    private void onTick(final EventTick ignored) {
        if (mc.player == null) return;
        double y = mc.player.prevY - mc.player.getY();
        if (mc.player.isOnGround()) fallDistance = 0;
        else if (y > 0) fallDistance += (float) y;
    }

    @Subscribe
    private void onPlayerUpdate(final EventPlayerUpdate ignored) {
        sprintingChangeTicks++;
    }

    @Subscribe
    public void onPacketSend(final EventPacket e) {
        if (e.getPacket() instanceof PlayerMoveC2SPacket packet) {
            if (packet.changesPosition()) {
                serverX = packet.getX(mc.player.getX());
                serverY = packet.getY(mc.player.getY());
                serverZ = packet.getZ(mc.player.getZ());
            }

            if (packet.changesLook()) {
                serverYaw = packet.getYaw(mc.player.getYaw());
                serverPitch = packet.getPitch(mc.player.getPitch());
            }

            serverOnGround = packet.isOnGround();
            serverHorizontalCollision = packet.horizontalCollision();
        }

        if (e.getPacket() instanceof UpdateSelectedSlotC2SPacket packet) serverSlot = packet.getSelectedSlot();

        if (e.getPacket() instanceof ClientCommandC2SPacket packet) {
            switch (packet.getMode()) {
                case PRESS_SHIFT_KEY -> serverSneaking = true;
                case RELEASE_SHIFT_KEY -> serverSneaking = false;
            }
        }

        if (e.getPacket() instanceof ClientCommandC2SPacket command) {
            if (command.getMode().equals(ClientCommandC2SPacket.Mode.START_SPRINTING)) {
                e.setCancelled(serverSprinting);
                if (!e.isCancelled()) sprintingChangeTicks = 0;
                serverSprinting = true;
            } else if (command.getMode().equals(ClientCommandC2SPacket.Mode.STOP_SPRINTING)) {
                e.setCancelled(!serverSprinting);
                if (!e.isCancelled()) sprintingChangeTicks = 0;
                serverSprinting = false;
            }
        }
    }

    @Subscribe
    public void onPacketReceive(EventPacket e) {
        if (e.getPacket() instanceof EntityStatusS2CPacket packet && packet.getStatus() == 35) {
            if (!(packet.getEntity(mc.world) instanceof PlayerEntity player)) return;
            new EventPopTotem(player).post();
        }
    }
}