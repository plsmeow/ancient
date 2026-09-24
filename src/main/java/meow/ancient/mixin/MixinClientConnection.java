package meow.ancient.mixin;

import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import meow.ancient.Ancient;
import meow.ancient.event.list.EventPacket;
import meow.ancient.util.packet.NetworkUtils;

@Mixin(ClientConnection.class)
public class MixinClientConnection {
    @Inject(method = "send(Lnet/minecraft/network/packet/Packet;)V", at = @At("HEAD"), cancellable = true)
    public void send(Packet<?> packet, CallbackInfo ci) {
        if (NetworkUtils.getSilentPackets().contains(packet)) {
            NetworkUtils.getSilentPackets().remove(packet);
            return;
        }
        EventPacket event = new EventPacket(packet, EventPacket.Type.SEND);
        Ancient.getInstance().getEventBus().post(event);
        if (event.isCancelled()) {
            ci.cancel();
        }
    }

    @Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/packet/Packet;)V", at = @At("HEAD"), cancellable = true)
    private void onReceivePacket(ChannelHandlerContext ctx, Packet<?> packet, CallbackInfo ci) {
        EventPacket event = new EventPacket(packet, EventPacket.Type.RECEIVE);
        Ancient.getInstance().getEventBus().post(event);
        if (event.isCancelled()) {
            ci.cancel();
        }
    }
}