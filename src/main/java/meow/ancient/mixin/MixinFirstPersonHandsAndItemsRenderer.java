package meow.ancient.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import meow.ancient.module.modules.render.ViewModelModule;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FirstPersonHandsAndItemsRenderer.class)
public class MixinFirstPersonHandsAndItemsRenderer {

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
