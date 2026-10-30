package meow.ancient.mixin;

import meow.ancient.module.modules.movement.ElytraBoosterModule;
import net.minecraft.client.Minecraft;
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
        if (ElytraBoosterModule.INSTANCE.isEnabled() && entity == Minecraft.getInstance().player) {
            Vec3 look = entity.getLookAngle();
            Vec3 old = entity.getDeltaMovement();
            double xz = ElytraBoosterModule.INSTANCE.getSpeedXZ();
            double y = ElytraBoosterModule.INSTANCE.getSpeedY();
            entity.setDeltaMovement(old.add(
                look.x * 0.1 + (look.x * xz - old.x) * 0.5,
                look.y * 0.1 + (look.y * y - old.y) * 0.5,
                look.z * 0.1 + (look.z * xz - old.z) * 0.5
            ));
        } else {
            entity.setDeltaMovement(vanillaVelocity);
        }
    }
}
