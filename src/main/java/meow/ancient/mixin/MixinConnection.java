package meow.ancient.mixin;

import meow.ancient.event.EventManager;
import meow.ancient.network.SilentPacketQueue;
import meow.ancient.event.events.PacketReceiveEvent;
import meow.ancient.event.events.PacketSendEvent;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public class MixinConnection {
    private static int lastServerSlot = -1;

    public static void resetServerSlot() {
        lastServerSlot = -1;
    }
    @Inject(
        method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onSendPacket(Packet<?> packet, ChannelFutureListener listener, boolean flush, CallbackInfo ci) {
        if (packet instanceof ServerboundSetCarriedItemPacket heldItemPacket) {
            int slot = heldItemPacket.getSlot();
            if (slot == lastServerSlot) {
                ci.cancel();
                return;
            }
            lastServerSlot = slot;
        }
        if (SilentPacketQueue.consume(packet)) return;
        PacketSendEvent event = new PacketSendEvent(packet);
        EventManager.INSTANCE.post(event);
        if (event.getCancel()) {
            ci.cancel();
        }
    }

    @Inject(
        method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onReceivePacket(ChannelHandlerContext context, Packet<?> packet, CallbackInfo ci) {
        PacketReceiveEvent event = new PacketReceiveEvent(packet);
        EventManager.INSTANCE.post(event);
        if (event.getCancel()) {
            ci.cancel();
        }
    }

    @Inject(method = "channelInactive", at = @At("HEAD"))
    private void onChannelInactive(ChannelHandlerContext context, CallbackInfo ci) {
        lastServerSlot = -1;
    }
}
