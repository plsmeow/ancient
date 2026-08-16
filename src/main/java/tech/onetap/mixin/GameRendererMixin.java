package tech.onetap.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Matrix4f;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tech.onetap.event.list.EventWorldRender;
import tech.onetap.util.rotation.FreeLookComponent;
import tech.onetap.util.render.renderers.DrawUtil;

@Mixin(GameRenderer.class)
public class GameRendererMixin {

    @Inject(method = "renderWorld", at = @At(value = "FIELD", target = "Lnet/minecraft/client/render/GameRenderer;renderHand:Z", opcode = Opcodes.GETFIELD, ordinal = 0))
    public void hookWorldRender(RenderTickCounter tickCounter, CallbackInfo ci, @Local(ordinal = 2) Matrix4f matrix4f) {
        var matrixStack = new MatrixStack();
        matrixStack.multiplyPositionMatrix(matrix4f);

        var event = new EventWorldRender(matrixStack, tickCounter.getTickDelta(false));
        event.post();
        DrawUtil.onRender3D(event.getMatrixStack());
    }

    @Redirect(
            method = "findCrosshairTarget",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;raycast(DFZ)Lnet/minecraft/util/hit/HitResult;")
    )
    private HitResult freelookBlockRaycast(Entity camera, double maxDistance, float tickDelta, boolean includeFluids) {
        Vec3d direction = FreeLookComponent.interactionDirection();
        if (direction == null) {
            return camera.raycast(maxDistance, tickDelta, includeFluids);
        }

        Vec3d eye = camera.getCameraPosVec(tickDelta);
        Vec3d end = eye.add(direction.x * maxDistance, direction.y * maxDistance, direction.z * maxDistance);
        return camera.getWorld().raycast(new RaycastContext(
                eye,
                end,
                RaycastContext.ShapeType.OUTLINE,
                includeFluids ? RaycastContext.FluidHandling.ANY : RaycastContext.FluidHandling.NONE,
                camera));
    }

    @Redirect(
            method = "findCrosshairTarget",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;getRotationVec(F)Lnet/minecraft/util/math/Vec3d;")
    )
    private Vec3d freelookEntityDirection(Entity camera, float tickDelta) {
        Vec3d direction = FreeLookComponent.interactionDirection();
        return direction != null ? direction : camera.getRotationVec(tickDelta);
    }
}