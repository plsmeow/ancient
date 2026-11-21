package meow.ancient.mixin;

import it.unimi.dsi.fastutil.floats.FloatUnaryOperator;
import meow.ancient.module.modules.movement.MioScaffoldTower;
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(DeltaTracker.Timer.class)
public class MixinScaffoldTimer {
    @Redirect(
        method = "advanceGameTime(J)I",
        at = @At(
            value = "INVOKE",
            target = "Lit/unimi/dsi/fastutil/floats/FloatUnaryOperator;apply(F)F"
        )
    )
    private float ancient$scaffoldTickMillis(FloatUnaryOperator provider, float millis) {
        return MioScaffoldTower.INSTANCE.timerMillis(provider.apply(millis));
    }
}
