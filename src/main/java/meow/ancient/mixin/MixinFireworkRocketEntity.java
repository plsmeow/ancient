package meow.ancient.mixin;

import meow.ancient.client.util.rotation.MoveFixMode;
import meow.ancient.client.util.rotation.Rotation;
import meow.ancient.client.util.rotation.RotationManager;
import meow.ancient.module.modules.movement.ElytraBoosterModule;
import meow.ancient.module.modules.movement.ElytraFlyModule;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(FireworkRocketEntity.class)
public abstract class MixinFireworkRocketEntity {
    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V"))
    private void redirectBoost(LivingEntity entity, Vec3 vanillaVelocity) {
        if (entity == Minecraft.getInstance().player) {
            if (entity instanceof LocalPlayer player) {
                Vec3 motion = ElytraFlyModule.INSTANCE.getMotion(player);
                if (motion != null) {
                    entity.setDeltaMovement(motion);
                    return;
                }
            }
            Vec3 look = entity.getLookAngle();
            if (RotationManager.INSTANCE.isActive() && RotationManager.INSTANCE.getActiveMoveFix() != MoveFixMode.OFF) {
                Rotation rot = RotationManager.INSTANCE.getCurrentRotation();
                if (rot != null) {
                    look = Entity.calculateViewVector(rot.getPitch(), rot.getYaw());
                }
            }
            if (entity instanceof LocalPlayer player && ElytraBoosterModule.INSTANCE.isGrimMode()) {
                entity.setDeltaMovement(ElytraBoosterModule.INSTANCE.getGrimBoost(player, (FireworkRocketEntity) (Object) this, look));
                return;
            }
            if (ElytraBoosterModule.INSTANCE.isEnabled()) {
                Vec3 old = entity.getDeltaMovement();
                double xz = ElytraBoosterModule.INSTANCE.getSpeedXZ();
                double y = ElytraBoosterModule.INSTANCE.getSpeedY();
                entity.setDeltaMovement(old.add(
                    look.x * 0.1 + (look.x * xz - old.x) * 0.5,
                    look.y * 0.1 + (look.y * y - old.y) * 0.5,
                    look.z * 0.1 + (look.z * xz - old.z) * 0.5
                ));
            } else if (RotationManager.INSTANCE.isActive() && RotationManager.INSTANCE.getActiveMoveFix() != MoveFixMode.OFF) {
                Vec3 old = entity.getDeltaMovement();
                entity.setDeltaMovement(old.add(
                    look.x * 0.1 + (look.x * 1.5 - old.x) * 0.5,
                    look.y * 0.1 + (look.y * 1.5 - old.y) * 0.5,
                    look.z * 0.1 + (look.z * 1.5 - old.z) * 0.5
                ));
            } else {
                entity.setDeltaMovement(vanillaVelocity);
            }
        } else {
            entity.setDeltaMovement(vanillaVelocity);
        }
    }
}
