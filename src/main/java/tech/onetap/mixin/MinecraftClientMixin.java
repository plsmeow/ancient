package tech.onetap.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.RunArgs;
import net.minecraft.entity.Entity;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import tech.onetap.event.EventGameUpdate;
import tech.onetap.event.list.EventMinecraftInit;
import tech.onetap.event.list.EventTick;
import tech.onetap.event.list.EventTickEnd;
import tech.onetap.module.list.combat.AutoExplosion;
import tech.onetap.module.list.render.Hide;
import tech.onetap.util.base.Instance;

@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {
    @Unique
    private long lastHookTime = Util.getMeasuringTimeNano();
    @Unique
    private int accumulatedCalls = 0;

    @Inject(method = "getWindowTitle", at = @At(value = "HEAD"), cancellable = true)
    private void getWindowTitle(CallbackInfoReturnable<String> cir) {
        Hide hide = Instance.get(Hide.class);
        if (hide != null && hide.isEnabled()) return;
        cir.setReturnValue("Ancient");
    }

    @Inject(method = "<init>", at = @At(value = "NEW", target = "(Lnet/minecraft/client/MinecraftClient;Ljava/io/File;)Lnet/minecraft/client/option/GameOptions;"))
    private void initOptions(RunArgs args, CallbackInfo ci) {
        new EventMinecraftInit().post();
    }

    @Inject(method = "tick", at = @At(value = "HEAD"))
    private void tick(CallbackInfo ci) {
        new EventTick().post();
    }

    @Inject(method = "tick", at = @At(value = "RETURN"))
    private void tickEnd(CallbackInfo ci) {
        new EventTickEnd().post();
    }

    @Inject(method = "doItemUse", at = @At(value = "HEAD"), cancellable = true)
    private void fixAutoExplosion(CallbackInfo ci) {
        if (Instance.get(AutoExplosion.class).getTicksToDisableRightClicks() > 0) ci.cancel();
    }


    @Inject(method = "render", at = @At(value = "HEAD"))
    private void render(boolean tick, CallbackInfo ci) {
        var timeNano = Util.getMeasuringTimeNano();
        var deltaTime = timeNano - lastHookTime;
        accumulatedCalls += (int) (deltaTime / 4_166_666L);
        lastHookTime += (long) accumulatedCalls * 4_166_666L;

        for (accumulatedCalls = Math.min(accumulatedCalls, 240); accumulatedCalls > 0; --accumulatedCalls) new EventGameUpdate().post();
    }
}