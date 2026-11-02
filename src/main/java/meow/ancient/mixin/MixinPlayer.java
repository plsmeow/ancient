package meow.ancient.mixin;

import meow.ancient.client.util.rotation.MoveFixMode;
import meow.ancient.client.util.rotation.Rotation;
import meow.ancient.client.util.rotation.RotationManager;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Player.class)
public class MixinPlayer {
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