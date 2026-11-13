package meow.ancient.mixin;

import meow.ancient.client.util.rotation.RotationManager;
import meow.ancient.client.util.combat.AuraTerrainMiner;
import meow.ancient.module.modules.combat.CrystalAuraModule;
import meow.ancient.module.modules.player.PacketMineModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiPlayerGameMode.class)
public class MixinMultiPlayerGameMode {
    @Inject(method = "startDestroyBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)Z", at = @At("HEAD"), cancellable = true)
    private void ancient$startPacketMining(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (!AuraTerrainMiner.isVanillaCall() && PacketMineModule.INSTANCE.selectBlock(pos, direction)) cir.setReturnValue(true);
    }

    @Inject(method = "continueDestroyBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)Z", at = @At("HEAD"), cancellable = true)
    private void ancient$continuePacketMining(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (!AuraTerrainMiner.isVanillaCall() && PacketMineModule.INSTANCE.continueMining(pos, direction)) cir.setReturnValue(true);
    }

    @Inject(method = "stopDestroyBlock", at = @At("HEAD"), cancellable = true)
    private void ancient$keepTerrainMining(CallbackInfo ci) {
        if (CrystalAuraModule.INSTANCE.shouldKeepTerrainMining()) ci.cancel();
    }

    @Redirect(
        method = "lambda$useItem$0",
        at = @At(
            value = "NEW",
            target = "(Lnet/minecraft/world/InteractionHand;IFF)Lnet/minecraft/network/protocol/game/ServerboundUseItemPacket;"
        )
    )
    private ServerboundUseItemPacket ancient$useClientLook(InteractionHand hand, int sequence, float yaw, float pitch) {
        Player player = Minecraft.getInstance().player;
        if (player == null || player.getItemInHand(hand).is(Items.FIREWORK_ROCKET)) {
            return new ServerboundUseItemPacket(hand, sequence, yaw, pitch);
        }
        return new ServerboundUseItemPacket(
            hand,
            sequence,
            RotationManager.INSTANCE.getClientYaw(),
            RotationManager.INSTANCE.getClientPitch()
        );
    }
}
