package meow.ancient.mixin;

import meow.ancient.client.util.rotation.MoveFixMode;
import meow.ancient.client.util.rotation.Rotation;
import meow.ancient.client.util.rotation.RotationManager;
import net.minecraft.client.Minecraft;
import meow.ancient.module.modules.player.NoPushModule;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public class MixinLivingEntity {
    @Shadow
    public float yHeadRot;

    @Redirect(
        method = "jumpFromGround()V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getYRot()F")
    )
    private float redirectJumpYaw(LivingEntity instance) {
        if (instance == Minecraft.getInstance().player && RotationManager.INSTANCE.isActive() && RotationManager.INSTANCE.getActiveMoveFix() != MoveFixMode.OFF) {
            return RotationManager.INSTANCE.getMovementYaw(instance.getYRot());
        }
        return instance.getYRot();
    }

    @Redirect(
        method = "tickHeadTurn(F)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getYRot()F")
    )
    private float redirectHeadTurnYaw(LivingEntity instance) {
        if (instance == Minecraft.getInstance().player && RotationManager.INSTANCE.isActive()) {
            return RotationManager.INSTANCE.getServerRotation().getYaw();
        }
        return instance.getYRot();
    }

    @Inject(
        method = "tick()V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;tickHeadTurn(F)V", shift = At.Shift.AFTER)
    )
    private void onAfterTickHeadTurn(CallbackInfo ci) {
        if ((Object) this == Minecraft.getInstance().player && RotationManager.INSTANCE.isActive()) {
            Rotation rot = RotationManager.INSTANCE.getServerRotation();
            this.yHeadRot = rot.getYaw();
        }
    }

    @Redirect(
        method = "updateFallFlyingMovement(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getXRot()F")
    )
    private float redirectFallFlyingPitch(LivingEntity instance) {
        if (instance == Minecraft.getInstance().player && RotationManager.INSTANCE.isActive() && RotationManager.INSTANCE.getActiveMoveFix() != MoveFixMode.OFF) {
            Rotation rot = RotationManager.INSTANCE.getCurrentRotation();
            if (rot != null) {
                return rot.getPitch();
            }
        }
        return instance.getXRot();
    }

    @Redirect(
        method = "updateFallFlyingMovement(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getLookAngle()Lnet/minecraft/world/phys/Vec3;")
    )
    private Vec3 redirectFallFlyingLookAngle(LivingEntity instance) {
        if (instance == Minecraft.getInstance().player && RotationManager.INSTANCE.isActive() && RotationManager.INSTANCE.getActiveMoveFix() != MoveFixMode.OFF) {
            Rotation rot = RotationManager.INSTANCE.getCurrentRotation();
            if (rot != null) {
                return Entity.calculateViewVector(rot.getPitch(), rot.getYaw());
            }
        }
        return instance.getLookAngle();
    }


    @Inject(method = "push(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
    private void onPush(Entity entity, CallbackInfo ci) {
        if (NoPushModule.INSTANCE.shouldCancelEntityPush() && (Object) this == Minecraft.getInstance().player) {
            ci.cancel();
        }
    }

    @Inject(method = "doPush", at = @At("HEAD"), cancellable = true)
    private void onDoPush(Entity entity, CallbackInfo ci) {
        if (NoPushModule.INSTANCE.shouldCancelEntityPush() && ((Object) this == Minecraft.getInstance().player || entity == Minecraft.getInstance().player)) {
            ci.cancel();
        }
    }
}
