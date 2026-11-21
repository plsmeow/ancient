package meow.ancient.mixin;

import meow.ancient.module.modules.combat.FreezeModule;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class MixinFreezePacketListener {
    @Inject(method = "handleMoveEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V", shift = At.Shift.AFTER), cancellable = true)
    private void onMoveEntity(ClientboundMoveEntityPacket packet, CallbackInfo ci) {
        ClientPacketListener listener = (ClientPacketListener) (Object) this;
        if (FreezeModule.INSTANCE.delayPacket(listener, packet.getEntity(listener.getLevel()), packet)) ci.cancel();
    }

    @Inject(method = "handleEntityPositionSync", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V", shift = At.Shift.AFTER), cancellable = true)
    private void onEntityPositionSync(ClientboundEntityPositionSyncPacket packet, CallbackInfo ci) {
        ClientPacketListener listener = (ClientPacketListener) (Object) this;
        if (FreezeModule.INSTANCE.delayPacket(listener, listener.getLevel().getEntity(packet.id()), packet)) ci.cancel();
    }

    @Inject(method = "handleTeleportEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V", shift = At.Shift.AFTER), cancellable = true)
    private void onTeleportEntity(ClientboundTeleportEntityPacket packet, CallbackInfo ci) {
        ClientPacketListener listener = (ClientPacketListener) (Object) this;
        if (FreezeModule.INSTANCE.delayPacket(listener, listener.getLevel().getEntity(packet.id()), packet)) ci.cancel();
    }

    @Inject(method = "handleRotateMob", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V", shift = At.Shift.AFTER), cancellable = true)
    private void onRotateHead(ClientboundRotateHeadPacket packet, CallbackInfo ci) {
        ClientPacketListener listener = (ClientPacketListener) (Object) this;
        if (FreezeModule.INSTANCE.delayPacket(listener, packet.getEntity(listener.getLevel()), packet)) ci.cancel();
    }

    @Inject(method = "handleSetEntityMotion", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V", shift = At.Shift.AFTER), cancellable = true)
    private void onEntityMotion(ClientboundSetEntityMotionPacket packet, CallbackInfo ci) {
        ClientPacketListener listener = (ClientPacketListener) (Object) this;
        if (FreezeModule.INSTANCE.delayPacket(listener, listener.getLevel().getEntity(packet.id()), packet)) ci.cancel();
    }

    @Inject(method = "handleRemoveEntities", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V", shift = At.Shift.AFTER))
    private void onRemoveEntities(ClientboundRemoveEntitiesPacket packet, CallbackInfo ci) {
        FreezeModule.INSTANCE.onRemoveEntities((ClientPacketListener) (Object) this, packet.entityIds());
    }

    @Inject(method = "handleMovePlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V", shift = At.Shift.AFTER))
    private void onCorrection(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
        FreezeModule.INSTANCE.onCorrection((ClientPacketListener) (Object) this);
    }

    @Inject(method = "handleRespawn", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V", shift = At.Shift.AFTER))
    private void onRespawn(ClientboundRespawnPacket packet, CallbackInfo ci) {
        FreezeModule.INSTANCE.onRespawn((ClientPacketListener) (Object) this);
    }
}
