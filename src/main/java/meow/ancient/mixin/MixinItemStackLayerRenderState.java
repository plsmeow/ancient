package meow.ancient.mixin;

import meow.ancient.module.modules.render.NoRenderModule;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ItemStackRenderState.LayerRenderState.class)
public class MixinItemStackLayerRenderState {
    @Shadow private ItemStackRenderState.FoilType foilType;

    // Read at submission time: cached GUI/hand item states stay reversible.
    @Redirect(method = "submit", at = @At(value = "FIELD",
        target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState$LayerRenderState;foilType:Lnet/minecraft/client/renderer/item/ItemStackRenderState$FoilType;"))
    private ItemStackRenderState.FoilType ancient$hideGlint(ItemStackRenderState.LayerRenderState state) {
        return NoRenderModule.INSTANCE.noEnchantGlint() ? ItemStackRenderState.FoilType.NONE : foilType;
    }
}
