package meow.ancient.module.list.movement;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import meow.ancient.event.list.EventPlayerUpdate;
import meow.ancient.event.list.RotationEvent;
import meow.ancient.module.Module;
import meow.ancient.module.ModuleCategory;
import meow.ancient.module.ModuleInformation;
import meow.ancient.module.list.combat.KillAura;
import meow.ancient.util.base.Instance;
import meow.ancient.util.packet.NetworkUtils;

@ModuleInformation(moduleName = "Elytra Jump", moduleDesc = "Полет вверх на элитре", moduleCategory = ModuleCategory.MOVEMENT)
public class ElytraJump extends Module {

    @EventHandler
    private void onPlayerUpdate(EventPlayerUpdate e) {
        if (mc.player == null) return;

        if (!mc.player.getAbilities().flying
                && mc.player.isOnGround()
                && !mc.player.isTouchingWater()
                && !mc.player.isInLava()
                && mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() == Items.ELYTRA
                && !mc.options.jumpKey.isPressed()) {
            mc.player.jump();
        }

        if (!mc.player.getAbilities().flying
                && !mc.player.isOnGround()
                && !mc.player.isTouchingWater()
                && !mc.player.isGliding()
                && mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() == Items.ELYTRA) {

            NetworkUtils.sendSilentPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
            mc.player.startGliding();
        }

        if (mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() == Items.ELYTRA) {
            mc.player.setVelocity(0, mc.player.getVelocity().y, 0);
            mc.options.jumpKey.setPressed(true);

            if (mc.player.isGliding()) {
                mc.player.setVelocity(
                        mc.player.getVelocity().x,
                        mc.player.getVelocity().y + 0.06,
                        mc.player.getVelocity().z
                );
            }
        }
    }

    @EventHandler
    private void onRotation(RotationEvent event) {
        if (mc.player == null || !mc.player.isGliding()) return;

        KillAura killAura = Instance.get(KillAura.class);
        LivingEntity target = killAura != null ? killAura.getTarget() : null;

        if (target != null) {
            float targetYaw = target.getYaw();
            float targetPitch = 0.0f;

            event.setYaw(targetYaw);
            event.setPitch(targetPitch);

            mc.player.setYaw(targetYaw);
            mc.player.setPitch(targetPitch);
            mc.player.headYaw = targetYaw;
            mc.player.bodyYaw = targetYaw;
        }
    }
}
