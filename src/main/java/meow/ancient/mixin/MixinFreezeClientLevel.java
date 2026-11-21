package meow.ancient.mixin;

import meow.ancient.module.modules.combat.FreezeModule;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ClientLevel.class)
public class MixinFreezeClientLevel {
    @Redirect(method = "tickNonPassenger", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;tick()V"))
    private void onEntityTick(Entity entity) {
        if (!FreezeModule.INSTANCE.shouldFreeze(entity)) entity.tick();
    }
}
