package tech.onetap.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Matrix4f;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tech.onetap.event.list.EventWorldRender;
import tech.onetap.module.list.player.FreeCamera;
import tech.onetap.util.base.Instance;
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

    @Inject(method = "renderHand", at = @At("HEAD"), cancellable = true)
    private void freecamRenderHand(Camera camera, float tickDelta, Matrix4f matrix4f, CallbackInfo ci) {
        FreeCamera freeCamera = Instance.get(FreeCamera.class);
        if (freeCamera != null && freeCamera.isEnabled()) ci.cancel();
    }

    @WrapOperation(
            method = "findCrosshairTarget",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;getCameraPosVec(F)Lnet/minecraft/util/math/Vec3d;")
    )
    private Vec3d freecamInteractionEye(Entity camera, float tickDelta, Operation<Vec3d> original) {
        FreeCamera freeCamera = Instance.get(FreeCamera.class);
        if (freeCamera != null && freeCamera.isEnabled()) {
            return freeCamera.getCameraPos(tickDelta);
        }

        return original.call(camera, tickDelta);
    }

    @WrapOperation(
            method = "findCrosshairTarget",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;raycast(DFZ)Lnet/minecraft/util/hit/HitResult;")
    )
    private HitResult interactionBlockRaycast(Entity camera, double maxDistance, float tickDelta, boolean includeFluids, Operation<HitResult> original) {
        FreeCamera freeCamera = Instance.get(FreeCamera.class);
        if (freeCamera != null && freeCamera.isEnabled()) {
            return onetap$blockRaycast(camera, freeCamera.getCameraPos(tickDelta), freeCamera.getCameraDirection(tickDelta), maxDistance, includeFluids);
        }

        Vec3d direction = FreeLookComponent.interactionDirection();
        if (direction == null) {
            return original.call(camera, maxDistance, tickDelta, includeFluids);
        }

        return onetap$blockRaycast(camera, camera.getCameraPosVec(tickDelta), direction, maxDistance, includeFluids);
    }

    @WrapOperation(
            method = "findCrosshairTarget",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;getRotationVec(F)Lnet/minecraft/util/math/Vec3d;")
    )
    private Vec3d interactionDirection(Entity camera, float tickDelta, Operation<Vec3d> original) {
        FreeCamera freeCamera = Instance.get(FreeCamera.class);
        if (freeCamera != null && freeCamera.isEnabled()) {
            return freeCamera.getCameraDirection(tickDelta);
        }

        Vec3d direction = FreeLookComponent.interactionDirection();
        return direction != null ? direction : original.call(camera, tickDelta);
    }

    @WrapOperation(
            method = "findCrosshairTarget",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;getBoundingBox()Lnet/minecraft/util/math/Box;")
    )
    private Box freecamEntitySearchBox(Entity camera, Operation<Box> original, @Local(argsOnly = true, ordinal = 0) float tickDelta) {
        FreeCamera freeCamera = Instance.get(FreeCamera.class);
        if (freeCamera != null && freeCamera.isEnabled()) {
            Vec3d pos = freeCamera.getCameraPos(tickDelta);
            return new Box(pos, pos);
        }

        return original.call(camera);
    }

    @Unique
    private HitResult onetap$blockRaycast(Entity camera, Vec3d eye, Vec3d direction, double maxDistance, boolean includeFluids) {
        Vec3d end = eye.add(direction.x * maxDistance, direction.y * maxDistance, direction.z * maxDistance);
        return camera.getWorld().raycast(new RaycastContext(
                eye,
                end,
                RaycastContext.ShapeType.OUTLINE,
                includeFluids ? RaycastContext.FluidHandling.ANY : RaycastContext.FluidHandling.NONE,
                camera));
    }
}