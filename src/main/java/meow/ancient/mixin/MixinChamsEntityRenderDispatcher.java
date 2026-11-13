package meow.ancient.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import meow.ancient.client.util.render.ChamsEntityRenderState;
import meow.ancient.client.util.render.ChamsRenderer;
import meow.ancient.module.modules.render.ChamsModule;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(EntityRenderDispatcher.class)
public class MixinChamsEntityRenderDispatcher {
    @ModifyVariable(
        method = "submit(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lnet/minecraft/client/renderer/state/level/CameraRenderState;DDDLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 0
    )
    private SubmitNodeCollector ancient$wrapChamsCollector(
        SubmitNodeCollector collector,
        EntityRenderState state,
        CameraRenderState camera,
        double x,
        double y,
        double z,
        PoseStack poseStack,
        SubmitNodeCollector originalCollector
    ) {
        if (ChamsModule.INSTANCE.isEnabled() && ((ChamsEntityRenderState) state).ancient$isChamsTarget()) {
            return ChamsRenderer.INSTANCE.wrapWorld(collector);
        }
        return collector;
    }
}
