package tech.onetap.mixin;

import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import tech.onetap.Onetap;
import tech.onetap.module.list.render.Ambience;

@Mixin(ClientWorld.Properties.class)
public class WorldMixin {

    @Inject(method = "getTimeOfDay", at = @At("HEAD"), cancellable = true)
    private void onGetTimeOfDay(CallbackInfoReturnable<Long> cir) {
        try {
            if (Onetap.getInstance() == null) return;
            if (Onetap.getInstance().getModuleStorage() == null) return;

            Ambience ambience = Onetap.getInstance().getModuleStorage().get(Ambience.class);
            if (ambience != null && ambience.isEnabled()) {
                cir.setReturnValue(ambience.getCustomTime());
            }
        } catch (Exception ignored) {}
    }
}
