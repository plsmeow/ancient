package meow.ancient.mixin;

import meow.ancient.client.util.rotation.RotationManager;
import meow.ancient.event.EventManager;
import meow.ancient.event.events.MovementInputEvent;
import meow.ancient.client.util.inventory.SlownessManager;
import meow.ancient.module.modules.movement.InventoryMoveModule;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public class MixinKeyboardInput {
    @Unique
    private MovementInputEvent ancientInputEvent;

    @Inject(method = "tick", at = @At("TAIL"))
    private void onTickInput(CallbackInfo ci) {
        RotationManager.INSTANCE.onInputUpdate((ClientInput) (Object) this);
        if (ancientInputEvent == null) {
            ancientInputEvent = new MovementInputEvent((ClientInput) (Object) this);
        }
        EventManager.INSTANCE.post(ancientInputEvent);
        InventoryMoveModule.INSTANCE.applySlownessInput((ClientInput) (Object) this);
        SlownessManager.INSTANCE.applyToInput((ClientInput) (Object) this);
    }
}
