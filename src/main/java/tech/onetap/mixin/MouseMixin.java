package tech.onetap.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.util.math.Smoother;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tech.onetap.Onetap;
import tech.onetap.event.list.EventKeyInput;
import tech.onetap.event.list.LookEvent;

@Mixin(Mouse.class)
public abstract class MouseMixin {

    @Shadow @Final private MinecraftClient client;
    @Shadow private double cursorDeltaX;
    @Shadow private double cursorDeltaY;
    @Final @Shadow private Smoother cursorXSmoother;
    @Final @Shadow private Smoother cursorYSmoother;

    @Inject(method = "updateMouse", at = @At("HEAD"), cancellable = true)
    private void onUpdateMouse(double timeDelta, CallbackInfo ci) {
        if (client.player == null) return;

        var sensitivity = client.options.getMouseSensitivity().getValue() * 0.6 + 0.2;
        var scaled = sensitivity * sensitivity * sensitivity * 8.0;
        double i, j;

        if (client.options.smoothCameraEnabled) {
            i = cursorXSmoother.smooth(cursorDeltaX * scaled, timeDelta * scaled);
            j = cursorYSmoother.smooth(cursorDeltaY * scaled, timeDelta * scaled);
        } else if (client.options.getPerspective().isFirstPerson() && client.player.isUsingSpyglass()) {
            cursorXSmoother.clear();
            cursorYSmoother.clear();
            i = cursorDeltaX * sensitivity * sensitivity * sensitivity;
            j = cursorDeltaY * sensitivity * sensitivity * sensitivity;
        } else {
            cursorXSmoother.clear();
            cursorYSmoother.clear();
            i = cursorDeltaX * scaled;
            j = cursorDeltaY * scaled;
        }

        var invert = this.client.options.getInvertYMouse().getValue() ? -1 : 1;

        var event = new LookEvent(i, j * invert);
        event.post();



        if (!event.isCancelled()) {
            client.getTutorialManager().onUpdateMouse(event.getYaw(), event.getPitch());
            client.player.changeLookDirection(event.getYaw(), event.getPitch());
        }

        cursorDeltaX = 0.0;
        cursorDeltaY = 0.0;

        ci.cancel();
    }

    @Inject(method = "onMouseButton", at = @At("HEAD"))
    private void onMouseButton(long window, int button, int action, int mods, CallbackInfo ci) {
        if (MinecraftClient.getInstance().currentScreen != null || action == 2) return;

        Onetap.getInstance().getEventBus().post(new EventKeyInput(button, action));
    }
}