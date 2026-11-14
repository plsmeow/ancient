package meow.ancient.mixin;

import meow.ancient.module.modules.render.NoRenderModule;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockEntityRenderDispatcher.class)
public class MixinBlockEntityRenderDispatcher {
    @Inject(method = "tryExtractRenderState", at = @At("HEAD"), cancellable = true)
    private void ancient$hideBlockEntity(BlockEntity entity, float partialTick,
        ModelFeatureRenderer.CrumblingOverlay overlay, boolean offscreen,
        CallbackInfoReturnable<BlockEntityRenderState> cir) {
        if (NoRenderModule.INSTANCE.noBlockEntity(entity.getType())) cir.setReturnValue(null);
    }
}
