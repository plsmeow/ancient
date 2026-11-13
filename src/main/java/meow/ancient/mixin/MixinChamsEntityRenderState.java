package meow.ancient.mixin;

import meow.ancient.client.util.render.ChamsEntityRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(EntityRenderState.class)
public class MixinChamsEntityRenderState implements ChamsEntityRenderState {
    @Unique
    private boolean ancient$chamsTarget;

    @Override
    public boolean ancient$isChamsTarget() {
        return ancient$chamsTarget;
    }

    @Override
    public void ancient$setChamsTarget(boolean target) {
        ancient$chamsTarget = target;
    }
}
