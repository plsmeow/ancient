package meow.ancient.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import meow.ancient.client.util.render.ChamsRenderer;
import meow.ancient.module.modules.render.ChamsModule;
import meow.ancient.module.modules.render.SwingAnimationsModule;
import meow.ancient.module.modules.render.ViewModelModule;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class MixinFirstPersonHandsAndItemsRenderer {
    @Shadow
    protected abstract void swingArm(float swingProgress, PoseStack poseStack, int armX, HumanoidArm arm);

    @ModifyVariable(
        method = "submitHandsWithItems(FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/PlayerRenderState;Lnet/minecraft/client/renderer/state/level/FirstPersonHandsAndItemsRenderState;)V",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 0
    )
    private SubmitNodeCollector ancient$wrapHandsCollector(SubmitNodeCollector collector) {
        return ChamsModule.INSTANCE.shouldRenderHands() ? ChamsRenderer.INSTANCE.wrapHands(collector) : collector;
    }

    @ModifyVariable(
        method = "submitArmWithItem",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 3
    )
    private float ancient$disableArmHeightAnimation(float armHeight) {
        return SwingAnimationsModule.INSTANCE.isEnabled() ? 0.0F : armHeight;
    }

    @Redirect(
        method = "submitArmWithItem",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/FirstPersonHandsAndItemsRenderer;swingArm(FLcom/mojang/blaze3d/vertex/PoseStack;ILnet/minecraft/world/entity/HumanoidArm;)V",
            ordinal = 2
        )
    )
    private void ancient$redirectSwingArm(
        FirstPersonHandsAndItemsRenderer instance,
        float swingProgress,
        PoseStack poseStack,
        int armX,
        HumanoidArm arm,
        PlayerRenderState playerRenderState,
        FirstPersonHandsAndItemsRenderState handState
    ) {
        if (SwingAnimationsModule.INSTANCE.isEnabled()) {
            SwingAnimationsModule.INSTANCE.renderSwordAnimation(poseStack, swingProgress, arm, handState);
        } else {
            swingArm(swingProgress, poseStack, armX, arm);
        }
    }

    @Inject(
        method = "submitArmWithItem",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V",
            shift = At.Shift.AFTER
        )
    )
    private void onPushPoseInSubmitArm(
        PlayerRenderState playerRenderState,
        FirstPersonHandsAndItemsRenderState firstPersonHandsAndItemsRenderState,
        float partialTicks,
        float xRot,
        InteractionHand interactionHand,
        float attackValue,
        ItemStack itemStack,
        float armHeight,
        PoseStack poseStack,
        SubmitNodeCollector submitNodeCollector,
        int lightCoords,
        CallbackInfo ci
    ) {
        if (playerRenderState.avatarRenderState == null) return;
        boolean isMainHand = (interactionHand == InteractionHand.MAIN_HAND);
        HumanoidArm arm = isMainHand
            ? playerRenderState.avatarRenderState.mainArm
            : playerRenderState.avatarRenderState.mainArm.getOpposite();
        ViewModelModule.INSTANCE.applyHandPosition(poseStack, arm);
    }

}
