package meow.ancient.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import meow.ancient.module.modules.movement.ScaffoldModule;
import meow.ancient.client.util.rotation.MoveFixMode;
import meow.ancient.client.util.rotation.Rotation;
import meow.ancient.client.util.rotation.RotationManager;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Player.class)
public class MixinPlayer {
    @Inject(method = "isStayingOnGroundSurface()Z", at = @At("HEAD"), cancellable = true)
    private void ancient$scaffoldLedge(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this == Minecraft.getInstance().player && ScaffoldModule.INSTANCE.shouldClipAtLedge()) {
            cir.setReturnValue(true);
        }
    }

    @ModifyExpressionValue(
        method = "maybeBackOffFromEdge(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/entity/MoverType;)Lnet/minecraft/world/phys/Vec3;",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;maxUpStep()F")
    )
    private float ancient$scaffoldEdgeDepth(float depth) {
        return (Object) this == Minecraft.getInstance().player ? ScaffoldModule.INSTANCE.edgeDepth(depth) : depth;
    }

    @Redirect(
        method = "travel(Lnet/minecraft/world/phys/Vec3;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;getLookAngle()Lnet/minecraft/world/phys/Vec3;")
    )
    private Vec3 redirectSwimmingLookAngle(Player instance) {
        if (instance == Minecraft.getInstance().player
            && RotationManager.INSTANCE.isActive()
            && RotationManager.INSTANCE.getActiveMoveFix() != MoveFixMode.OFF) {
            Rotation rot = RotationManager.INSTANCE.getCurrentRotation();
            if (rot != null) {
                return Entity.calculateViewVector(rot.getPitch(), rot.getYaw());
            }
        }
        return instance.getLookAngle();
    }
}