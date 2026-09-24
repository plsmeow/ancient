package meow.ancient.mixin;

import net.minecraft.client.render.chunk.ChunkOcclusionDataBuilder;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import meow.ancient.module.list.player.FreeCamera;
import meow.ancient.util.base.Instance;

@Mixin(ChunkOcclusionDataBuilder.class)
public abstract class ChunkOcclusionDataBuilderMixin {

    @Inject(method = "markClosed", at = @At("HEAD"), cancellable = true)
    private void onMarkClosed(BlockPos pos, CallbackInfo ci) {
        FreeCamera freeCamera = Instance.get(FreeCamera.class);
        if (freeCamera != null && freeCamera.isEnabled() && freeCamera.reloadChunks()) ci.cancel();
    }
}
