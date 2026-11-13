package meow.ancient.mixin;

import meow.ancient.event.EventManager;
import meow.ancient.client.util.inventory.SlownessManager;
import meow.ancient.client.util.inventory.SilentSwapUtil;
import meow.ancient.client.util.action.ActionCoordinator;
import meow.ancient.network.SilentPacketQueue;
import meow.ancient.event.events.PacketReceiveEvent;
import meow.ancient.event.events.PacketSendEvent;
import meow.ancient.event.events.PacketSentEvent;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public class MixinConnection {
    @Unique
    private int lastServerSlot = -1;
    @Inject(
        method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onSendPacket(Packet<?> packet, ChannelFutureListener listener, boolean flush, CallbackInfo ci) {
        Connection connection = (Connection) (Object) this;
        boolean silent = SilentPacketQueue.consume(packet);
        if (!ActionCoordinator.canSendPacket(connection, packet)) {
            ci.cancel();
            return;
        }
        if (packet instanceof ServerboundSetCarriedItemPacket heldItemPacket) {
            if (!SilentSwapUtil.canSendCarriedItem(connection, heldItemPacket.getSlot(), lastServerSlot)
                    || heldItemPacket.getSlot() == lastServerSlot) {
                ci.cancel();
                return;
            }
        }
        if (packet instanceof ServerboundContainerClosePacket) {
            SlownessManager.onContainerClose((Connection) (Object) this);
        }
        if (!silent) {
            PacketSendEvent event = new PacketSendEvent(packet);
            EventManager.INSTANCE.post(event);
            if (event.getCancel()) {
                ci.cancel();
                return;
            }
        }
        if (!ActionCoordinator.canSendPacket(connection, packet)) {
            ci.cancel();
            return;
        }
        if (packet instanceof ServerboundSetCarriedItemPacket heldItemPacket) {
            int slot = heldItemPacket.getSlot();
            if (!SilentSwapUtil.canSendCarriedItem(connection, slot, lastServerSlot) || slot == lastServerSlot) {
                ci.cancel();
                return;
            }
            lastServerSlot = slot;
        }
        SilentSwapUtil.onPacketSending(connection, packet, lastServerSlot);
        SilentSwapUtil.onPacketSent(connection, packet, lastServerSlot);
        ActionCoordinator.onPacketSent(connection, packet);
        EventManager.INSTANCE.post(new PacketSentEvent(packet));
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
            return;
        }
        SilentSwapUtil.onPacketReceived((Connection) (Object) this, packet);
    }

    @Inject(method = "channelInactive", at = @At("HEAD"))
    private void onChannelInactive(ChannelHandlerContext context, CallbackInfo ci) {
        lastServerSlot = -1;
        SilentSwapUtil.onConnectionClosed((Connection) (Object) this);
    }
}
