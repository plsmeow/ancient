package tech.onetap.module.list.movement;

import com.google.common.eventbus.Subscribe;
import net.minecraft.block.BlockState;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import tech.onetap.event.list.EventAttack;
import tech.onetap.event.list.EventTick;
import tech.onetap.module.Module;
import tech.onetap.module.ModuleCategory;
import tech.onetap.module.ModuleInformation;
import tech.onetap.module.settings.BooleanSetting;
import tech.onetap.module.settings.SliderSetting;

@ModuleInformation(moduleName = "BoatNoClip", moduleDesc = "Позволяет двигаться на лодке сквозь блоки", moduleCategory = ModuleCategory.MOVEMENT)
public final class BoatNoClip extends Module {

    private static final int BYPASS_IDLE_REMOUNT_TICKS = 15;

    private final SliderSetting boatUpSpeed = new SliderSetting("Вверх", 0.4, 0.05, 5.0, 0.05);
    private final SliderSetting boatDownSpeed = new SliderSetting("Вниз", 0.4, 0.05, 5.0, 0.05);
    private final SliderSetting boatHorizontalSpeed = new SliderSetting("В стороны", 0.6, 0.05, 5.0, 0.05);
    private final BooleanSetting bypass = new BooleanSetting("Обход(тест, удары вроде идут)", false);
    private final SliderSetting bypassTicks = new SliderSetting("Тики обхода", 3.0, 1.0, 5.0, 1.0).setVisible(() -> bypass.getValue());

    private boolean wasInsideBlock;
    private BoatEntity bypassBoat;
    private int bypassWaitRemaining;
    private boolean bypassDismounted;
    private boolean bypassPendingRemount;
    private int bypassRemountDelay;
    private int bypassIdleTicks;

    public BoatNoClip() {
    }

    @Subscribe
    private void onTick(EventTick event) {
        if (mc.player == null || mc.world == null) {
            return;
        }

        BoatEntity boat = this.getActiveBoat();
        if (boat == null) {
            return;
        }

        boat.noClip = true;
        boat.setNoGravity(true);
        mc.player.noClip = true;

        boolean sneakDown = mc.options.sneakKey.isPressed();
        if (!this.isBypassWaiting()) {
            mc.options.sneakKey.setPressed(false);
        }
        mc.options.useKey.setPressed(false);

        boolean insideBlock = this.isInsideSolidBlock();
        double horizontalSpeed = insideBlock ? 0.25D : this.boatHorizontalSpeed.getValue();

        boat.setYaw(mc.player.getYaw());
        boat.prevYaw = boat.getYaw();
        mc.player.setYaw(mc.player.getYaw());

        double motionX = 0.0D;
        double motionY = 0.0D;
        double motionZ = 0.0D;
        float yaw = boat.getYaw();
        double rad = Math.toRadians(yaw);

        if (mc.options.jumpKey.isPressed()) {
            motionY += this.boatUpSpeed.getValue();
        }
        if (sneakDown) {
            motionY -= this.boatDownSpeed.getValue();
        }
        if (mc.options.forwardKey.isPressed()) {
            motionX -= MathHelper.sin((float) rad) * horizontalSpeed;
            motionZ += MathHelper.cos((float) rad) * horizontalSpeed;
        }
        if (mc.options.backKey.isPressed()) {
            motionX += MathHelper.sin((float) rad) * horizontalSpeed;
            motionZ -= MathHelper.cos((float) rad) * horizontalSpeed;
        }
        if (mc.options.rightKey.isPressed()) {
            motionX -= MathHelper.cos((float) rad) * horizontalSpeed;
            motionZ -= MathHelper.sin((float) rad) * horizontalSpeed;
        }
        if (mc.options.leftKey.isPressed()) {
            motionX += MathHelper.cos((float) rad) * horizontalSpeed;
            motionZ += MathHelper.sin((float) rad) * horizontalSpeed;
        }

        boat.setVelocity(new Vec3d(motionX, motionY, motionZ));

        if (this.bypass.getValue()) {
            this.handleBypassTick(boat);
        } else {
            this.resetBypassState();
        }

        if (!this.isBypassWaiting() && !mc.player.hasVehicle()) {
            mc.player.startRiding(boat, true);
        }

        this.wasInsideBlock = insideBlock;
    }

    @Subscribe
    private void onAttack(EventAttack event) {
        if (!this.isEnabled() || !this.bypass.getValue() || mc.player == null) {
            return;
        }

        if (mc.player.getVehicle() instanceof BoatEntity boat) {
            event.cancelEvent();
            if (this.bypassDismounted) {
                return;
            }
            this.bypassBoat = boat;
            this.bypassDismounted = true;
            this.bypassPendingRemount = false;
            this.bypassRemountDelay = 0;
            this.bypassIdleTicks = 0;
            this.bypassWaitRemaining = MathHelper.clamp((int) this.bypassTicks.getValue(), 1, 5);
            this.dismountFromBoat();
            return;
        }

        if (this.bypassDismounted && this.bypassWaitRemaining > 0) {
            event.cancelEvent();
            return;
        }

        if (this.bypassDismounted && this.bypassWaitRemaining <= 0) {
            this.bypassPendingRemount = true;
            this.bypassRemountDelay = 2;
            this.bypassIdleTicks = 0;
        }
    }

    private void handleBypassTick(BoatEntity boat) {
        if (!this.bypassDismounted || mc.player.hasVehicle()) {
            if (mc.player.hasVehicle() && this.bypassDismounted) {
                this.finishBypassCycle();
            }
            return;
        }

        mc.player.setPosition(boat.getX(), boat.getY() + boat.getHeight() * 0.5D, boat.getZ());

        if (this.bypassWaitRemaining > 0) {
            this.bypassWaitRemaining--;
            return;
        }

        if (this.bypassPendingRemount) {
            if (this.bypassRemountDelay > 0) {
                this.bypassRemountDelay--;
                return;
            }
            this.remountBoat(boat);
            return;
        }

        this.bypassIdleTicks++;
        if (this.bypassIdleTicks >= BYPASS_IDLE_REMOUNT_TICKS) {
            this.remountBoat(boat);
        }
    }

    private void dismountFromBoat() {
        if (mc.player == null || mc.getNetworkHandler() == null) {
            return;
        }
        mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.PRESS_SHIFT_KEY));
        mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.RELEASE_SHIFT_KEY));
        mc.player.stopRiding();
    }

    private void remountBoat(BoatEntity boat) {
        if (mc.player == null || boat == null || boat.isRemoved()) {
            this.finishBypassCycle();
            return;
        }

        mc.player.setPosition(boat.getX(), boat.getY() + boat.getHeight() * 0.5D, boat.getZ());

        if (!mc.player.hasVehicle()) {
            mc.player.startRiding(boat, true);
        }

        this.finishBypassCycle();
    }

    private void finishBypassCycle() {
        this.bypassDismounted = false;
        this.bypassPendingRemount = false;
        this.bypassRemountDelay = 0;
        this.bypassWaitRemaining = 0;
        this.bypassIdleTicks = 0;
    }

    private BoatEntity getActiveBoat() {
        if (mc.player == null) {
            return null;
        }
        if (mc.player.getVehicle() instanceof BoatEntity boat) {
            return boat;
        }
        if (this.bypassBoat != null && !this.bypassBoat.isRemoved()) {
            return this.bypassBoat;
        }
        return null;
    }

    private boolean isBypassWaiting() {
        return this.bypass.getValue() && (this.bypassDismounted || this.bypassPendingRemount);
    }

    private void resetBypassState() {
        this.bypassBoat = null;
        this.finishBypassCycle();
    }

    private boolean isInsideSolidBlock() {
        if (mc.player == null || mc.world == null) {
            return false;
        }

        Box box = mc.player.getBoundingBox().expand(0.001D);
        int minX = MathHelper.floor(box.minX);
        int minY = MathHelper.floor(box.minY);
        int minZ = MathHelper.floor(box.minZ);
        int maxX = MathHelper.floor(box.maxX);
        int maxY = MathHelper.floor(box.maxY);
        int maxZ = MathHelper.floor(box.maxZ);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = BlockPos.ofFloored(x, y, z);
                    BlockState state = mc.world.getBlockState(pos);
                    if (state.isSolid()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public void onDisable() {
        super.onDisable();
        this.wasInsideBlock = false;
        this.resetBypassState();

        if (mc.player != null) {
            mc.player.noClip = false;

            if (mc.player.getVehicle() instanceof BoatEntity boat) {
                boat.noClip = false;
                boat.setNoGravity(false);
            }
        }
    }
}
