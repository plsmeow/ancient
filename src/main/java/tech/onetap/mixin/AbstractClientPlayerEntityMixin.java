package tech.onetap.mixin;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayerEntity.class)
public class AbstractClientPlayerEntityMixin {
//    private static final Identifier CUSTOM_CAPE = Identifier.of("mre", "textures/entity/cape.png");
//
//    @Inject(method = "getSkinTextures", at = @At("RETURN"), cancellable = true)
//    private void onGetSkinTextures(CallbackInfoReturnable<net.minecraft.client.util.SkinTextures> cir) {
//        net.minecraft.client.util.SkinTextures original = cir.getReturnValue();
//        cir.setReturnValue(new net.minecraft.client.util.SkinTextures(
//            original.texture(),
//            original.textureUrl(),
//            CUSTOM_CAPE,
//            CUSTOM_CAPE,
//            original.model(),
//            original.secure()
//        ));
//    }
}
