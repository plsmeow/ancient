package meow.ancient.mixin;

import meow.ancient.module.modules.render.FreeCameraModule;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(BlockPlaceContext.class)
public abstract class MixinBlockPlaceContext {
    @Redirect(method = {"getNearestLookingDirection", "getNearestLookingVerticalDirection", "getNearestLookingDirections"},
        at = @At(value = "INVOKE", target = "Lnet/minecraft/core/Direction;orderedByNearest(Lnet/minecraft/world/entity/Entity;)[Lnet/minecraft/core/Direction;"))
    private Direction[] ancient$cameraLookingDirections(Entity entity) {
        if (((UseOnContext) (Object) this).getPlayer() == Minecraft.getInstance().player && FreeCameraModule.INSTANCE.isEnabled()) {
            return Direction.orderedByNearest(FreeCameraModule.INSTANCE.interactionEntity());
        }
        return Direction.orderedByNearest(entity);
    }
}
