package meow.ancient.mixin;

import meow.ancient.module.modules.render.FreeCameraModule;
import net.minecraft.client.renderer.chunk.VisGraph;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VisGraph.class)
public class MixinVisGraph {
    @Inject(method = "setOpaque", at = @At("HEAD"), cancellable = true)
    private void onSetOpaque(BlockPos pos, CallbackInfo ci) {
        if (FreeCameraModule.INSTANCE.isEnabled() && FreeCameraModule.INSTANCE.getReloadChunks().getValue()) {
            ci.cancel();
        }
    }
}
