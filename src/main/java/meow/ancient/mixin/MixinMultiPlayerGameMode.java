package meow.ancient.mixin;

import meow.ancient.client.util.rotation.RotationManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(MultiPlayerGameMode.class)
public class MixinMultiPlayerGameMode {
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
