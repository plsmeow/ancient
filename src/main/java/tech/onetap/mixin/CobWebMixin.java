package tech.onetap.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.CobwebBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tech.onetap.event.list.EventCobweb;

@Mixin(CobwebBlock.class)
public class CobWebMixin {

    @Inject(
            method = "onEntityCollision",
            at = @At("RETURN")
    )
    protected void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity, CallbackInfo ci) {
        if (entity != MinecraftClient.getInstance().player) return;
        var event = new EventCobweb();
        event.post();
    }
}