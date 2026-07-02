package tech.onetap.module.list.player;

import com.google.common.eventbus.Subscribe;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerPosition;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.TeleportConfirmC2SPacket;
import net.minecraft.network.packet.s2c.common.DisconnectS2CPacket;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.util.math.Vec3d;
import tech.onetap.event.list.EventPacket;
import tech.onetap.event.list.EventTick;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.settings.SliderSetting;
import tech.onetap.util.packet.NetworkUtils;
import tech.onetap.util.player.move.MoveUtil;

import java.util.Set;
import java.util.UUID;

@ModuleInformation(moduleName = "Free Camera", moduleCategory = ModuleCategory.PLAYER)
public class FreeCamera extends Module {
    public final SliderSetting xyi = new SliderSetting("Скорость по Y",0.5,0.1,1,0.1f);
    private Vec3d frozenPos;
    private float frozenYaw, frozenPitch;
    public OtherClientPlayerEntity fakePlayer;

    @Override
    public void onEnable() {
        if (mc.player == null || mc.world == null || mc.getNetworkHandler() == null) return;

        frozenPos = mc.player.getPos();
        frozenYaw = mc.player.getYaw();
        frozenPitch = mc.player.getPitch();

        GameProfile profile = new GameProfile(UUID.randomUUID(), mc.player.getName().getString());
        fakePlayer = new OtherClientPlayerEntity(mc.world, profile);

        fakePlayer.copyFrom(mc.player);
        fakePlayer.setPos(frozenPos.x, frozenPos.y, frozenPos.z);
        fakePlayer.setYaw(frozenYaw);
        fakePlayer.setPitch(frozenPitch);
        mc.world.addEntity(fakePlayer);

        mc.player.noClip = true;
        mc.player.setVelocity(Vec3d.ZERO);
        super.onEnable();
    }

    @Override
    public void onDisable() {
        if (mc.player == null || mc.world == null) return;

        mc.player.noClip = false;
        mc.player.setPos(frozenPos.x, frozenPos.y, frozenPos.z);
        mc.player.setYaw(frozenYaw);
        mc.player.setPitch(frozenPitch);
        mc.player.setVelocity(Vec3d.ZERO);

        if (fakePlayer != null) {
            mc.world.removeEntity(fakePlayer.getId(), Entity.RemovalReason.DISCARDED);
            fakePlayer = null;
        }

        super.onDisable();
    }

    @Subscribe
    private void onLivingUpdate(EventTick e) {
        if (mc.player == null) return;

        mc.player.noClip = true;
        mc.player.setVelocity(Vec3d.ZERO);

        float speed = (float) xyi.getValue();
        Vec3d motion = Vec3d.ZERO;

        MoveUtil.setMotion(1);
        if (mc.options.jumpKey.isPressed())
            motion = motion.add(0, speed, 0);
        if (mc.options.sneakKey.isPressed())
            motion = motion.subtract(0, speed, 0);

        mc.player.setVelocity(mc.player.getVelocity().x, motion.y, mc.player.getVelocity().z);
    }

    private void setPosition(PlayerPosition pos, Set<PositionFlag> flags) {
        PlayerPosition playerPosition = PlayerPosition.fromEntityLerpTarget(fakePlayer);
        PlayerPosition playerPosition2 = PlayerPosition.apply(playerPosition, pos, flags);
        frozenPos = new Vec3d(playerPosition2.position().getX(), playerPosition2.position().getY(), playerPosition2.position().getZ());
        frozenYaw = playerPosition2.yaw();
        frozenPitch = playerPosition2.pitch();
    }

    @Subscribe
    private void onPacket(EventPacket e) {
        if (e.getPacket() instanceof PlayerPositionLookS2CPacket packet) {
            if (mc.world == null || mc.player == null || !mc.player.isAlive()) return;
            NetworkUtils.sendPacket(new TeleportConfirmC2SPacket(packet.teleportId()));
            if (!mc.player.hasVehicle()) {
                setPosition(packet.change(), packet.relatives());
            }

            NetworkUtils.sendSilentPacket(new PlayerMoveC2SPacket.Full(frozenPos.getX(), frozenPos.getY(), frozenPos.getZ(), frozenYaw, frozenPitch, false, false));
            e.cancelEvent();
        }

        if (e.getPacket() instanceof PlayerMoveC2SPacket) {
            e.cancelEvent();
        }

        if (e.getPacket() instanceof PlayerInteractBlockC2SPacket p) {
            NetworkUtils.sendPacket(new PlayerInteractItemC2SPacket(p.getHand(), p.getSequence(), mc.player.getYaw(), mc.player.getPitch()));
            e.cancelEvent();
        }

        if (e.getPacket() instanceof PlayerRespawnS2CPacket) {
            setEnabled(false);
        }

        if (e.getPacket() instanceof GameJoinS2CPacket) {
            setEnabled(false);
        }

        if (e.getPacket() instanceof DisconnectS2CPacket) {
            setEnabled(false);
        }
    }
}