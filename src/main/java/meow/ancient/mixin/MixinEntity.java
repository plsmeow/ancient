package meow.ancient.mixin;

import meow.ancient.client.util.rotation.RotationManager;
import meow.ancient.module.modules.player.FakePlayerEntity;
import meow.ancient.module.modules.render.FreeCameraModule;
import meow.ancient.module.modules.movement.NoWebModule;
import meow.ancient.client.util.friend.FriendRepository;
import meow.ancient.module.modules.combat.HitBoxesModule;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public class MixinEntity {
    @Inject(method = "hurtOrSimulate", at = @At("HEAD"))
    private void onHurtOrSimulate(DamageSource source, float damage, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof FakePlayerEntity fakePlayer) {
            fakePlayer.applyDamage(source, damage);
        }
    }

    @Redirect(
        method = "moveRelative(FLnet/minecraft/world/phys/Vec3;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getYRot()F")
    )
    private float redirectMoveRelativeYaw(Entity instance) {
        if (instance == Minecraft.getInstance().player) {
            return RotationManager.INSTANCE.getMovementYaw(instance.getYRot());
        }
        return instance.getYRot();
    }

    @Inject(method = "turn", at = @At("HEAD"), cancellable = true)
    private void onTurn(double xo, double yo, CallbackInfo ci) {
        if ((Object) this == Minecraft.getInstance().player && FreeCameraModule.INSTANCE.isEnabled()) {
            FreeCameraModule.INSTANCE.changeLookDirection(xo * 0.15, yo * 0.15);
            ci.cancel();
        }
    }

    @Inject(method = "makeStuckInBlock", at = @At("HEAD"), cancellable = true)
    private void onMakeStuckInBlock(BlockState state, Vec3 multiplier, CallbackInfo ci) {
        if ((Object) this == Minecraft.getInstance().player && NoWebModule.INSTANCE.shouldCancelSlowdown()) {
            ci.cancel();
        }
    }

    @Inject(method = "getPickRadius", at = @At("RETURN"), cancellable = true)
    private void onGetPickRadius(CallbackInfoReturnable<Float> cir) {
        if (!((Object) this instanceof Player player)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || player == mc.player) return;
        if (!FriendRepository.INSTANCE.isFriend(player) && HitBoxesModule.INSTANCE.isEnabled()) {
            cir.setReturnValue(cir.getReturnValue() + HitBoxesModule.size.getValue().floatValue());
        }
    }
}
