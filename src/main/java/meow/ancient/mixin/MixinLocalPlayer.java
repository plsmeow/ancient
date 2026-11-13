package meow.ancient.mixin;

import meow.ancient.client.util.rotation.RotationManager;
import meow.ancient.client.util.action.ActionCoordinator;
import meow.ancient.client.util.inventory.SilentSwapUtil;
import meow.ancient.event.EventManager;
import meow.ancient.event.events.PlayerTickEndEvent;
import meow.ancient.event.events.PlayerUpdateEvent;
import meow.ancient.event.events.PlayerPreMovementEvent;
import meow.ancient.event.events.PlayerMovementSentEvent;
import meow.ancient.module.modules.combat.HitAuraModule;
import meow.ancient.module.modules.combat.VelocityModule;
import meow.ancient.module.modules.combat.SpearKillModule;
import meow.ancient.module.modules.combat.TriggerBotModule;
import meow.ancient.module.modules.movement.InventoryMoveModule;
import meow.ancient.module.modules.movement.ElytraRecastModule;
import meow.ancient.module.modules.movement.PhaseModule;
import meow.ancient.module.modules.movement.NoSlowModule;
import net.minecraft.client.player.LocalPlayer;
import meow.ancient.module.modules.player.NoPushModule;
import meow.ancient.module.modules.player.PacketMineModule;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(LocalPlayer.class)
public class MixinLocalPlayer {
    @Inject(method = "aiStep()V", at = @At("RETURN"))
    private void ancient$recastElytra(CallbackInfo ci) {
        ElytraRecastModule.INSTANCE.onAfterMovement((LocalPlayer) (Object) this);
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void onTick(CallbackInfo ci) {
        ActionCoordinator.INSTANCE.beginTick();
        SilentSwapUtil.INSTANCE.onTickStart();
        RotationManager.INSTANCE.onPreUpdate();
        EventManager.INSTANCE.post(PlayerUpdateEvent.INSTANCE);
        RotationManager.INSTANCE.onPostUpdate();
        InventoryMoveModule.INSTANCE.applySlownessInput(((LocalPlayer) (Object) this).input);
        if (VelocityModule.INSTANCE.shouldFreezeTick()) ci.cancel();
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void onTickEnd(CallbackInfo ci) {
        EventManager.INSTANCE.post(PlayerTickEndEvent.INSTANCE);
    }

    @Inject(method = "sendChanges", at = @At("HEAD"))
    private void onPreSendChanges(CallbackInfo ci) {
        RotationManager.INSTANCE.onPreSendChanges((LocalPlayer) (Object) this);
        LocalPlayer player = (LocalPlayer) (Object) this;
        if (player == net.minecraft.client.Minecraft.getInstance().player && player.connection.hasClientLoaded()) {
            InventoryMoveModule.INSTANCE.processBeforeMovement();
            PacketMineModule.INSTANCE.flushAborts();
            EventManager.INSTANCE.post(PlayerPreMovementEvent.INSTANCE);
        }
    }

    @Inject(method = "sendChanges", at = @At("RETURN"))
    private void onPostSendChanges(CallbackInfo ci) {
        RotationManager.INSTANCE.onPostSendChanges((LocalPlayer) (Object) this);
        LocalPlayer player = (LocalPlayer) (Object) this;
        if (player == net.minecraft.client.Minecraft.getInstance().player && player.connection.hasClientLoaded()) {
            InventoryMoveModule.INSTANCE.onMovementSent();
            EventManager.INSTANCE.post(PlayerMovementSentEvent.INSTANCE);
        }
    }

    @Inject(method = "canStartSprinting", at = @At("HEAD"), cancellable = true)
    private void preventSprintBeforeCriticalMovement(CallbackInfoReturnable<Boolean> cir) {
        if (HitAuraModule.INSTANCE.shouldPreventSprint() || TriggerBotModule.INSTANCE.shouldPreventSprint()
            || InventoryMoveModule.INSTANCE.shouldPreventSprint()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "isSlowDueToUsingItem", at = @At("HEAD"), cancellable = true)
    private void onIsSlowDueToUsingItem(CallbackInfoReturnable<Boolean> cir) {
        if (NoSlowModule.INSTANCE.shouldCancelSlowdown()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "itemUseSpeedMultiplier", at = @At("HEAD"), cancellable = true)
    private void onItemUseSpeedMultiplier(CallbackInfoReturnable<Float> cir) {
        if (NoSlowModule.INSTANCE.shouldCancelSlowdown()) {
            cir.setReturnValue(1.0f);
        }
    }

    @ModifyVariable(method = "move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Vec3 onMove(Vec3 movement, MoverType type) {
        if (type == MoverType.SELF && SpearKillModule.INSTANCE.shouldOverrideMovement()) {
            Vec3 motion = SpearKillModule.INSTANCE.getMotion();
            if (motion != null) {
                ((LocalPlayer) (Object) this).setDeltaMovement(motion);
                return motion;
            }
        }
        return movement;
    }

    @Inject(method = "moveTowardsClosestSpace", at = @At("HEAD"), cancellable = true)
    private void onMoveTowardsClosestSpace(double x, double z, CallbackInfo ci) {
        if (NoPushModule.INSTANCE.shouldCancelBlockPush() || PhaseModule.INSTANCE.shouldCancelBlockPush()) {
            ci.cancel();
        }
    }
}
