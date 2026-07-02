package tech.onetap.mixin;

import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.util.PlayerInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tech.onetap.event.list.MoveInputEvent;

@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends Input {

    @Inject(method = "tick", at = @At("TAIL"))
    private void onTick(CallbackInfo ci) {
        var forward = this.movementForward;
        var strafe = this.movementSideways;
        var jump = ((KeyboardInput) (Object) this).playerInput.jump();
        var sneak = ((KeyboardInput) (Object) this).playerInput.sneak();

        var event = new MoveInputEvent(forward, strafe, jump, sneak, 0.3);
        event.post();

        if (event.isCancelled()) {
            this.movementForward = 0;
            this.movementSideways = 0;
            return;
        }

        this.movementForward = event.getForward();
        this.movementSideways = event.getStrafe();

        ((KeyboardInput) (Object) this).playerInput = new PlayerInput(
                event.getForward() > 0,
                event.getForward() < 0,
                event.getStrafe() > 0,
                event.getStrafe() < 0,
                event.isJump(),
                event.isSneaking(),
                ((KeyboardInput) (Object) this).playerInput.sprint()
        );
    }
}