package meow.ancient.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(EntityCollisionContext.class)
public interface EntityCollisionContextAccessor {
    @Invoker("<init>")
    static EntityCollisionContext ancient$create(boolean descending, boolean placement, double entityBottom,
                                                 ItemStack heldItem, boolean alwaysCollideWithFluid, Entity entity) {
        throw new AssertionError();
    }
}
