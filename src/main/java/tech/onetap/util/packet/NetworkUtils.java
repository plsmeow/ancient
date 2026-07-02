package tech.onetap.util.packet;

import lombok.Getter;
import lombok.experimental.UtilityClass;
import net.minecraft.network.packet.Packet;
import tech.onetap.util.IMinecraft;

import java.util.ArrayList;
import java.util.List;

@UtilityClass
public class NetworkUtils implements IMinecraft {

    @Getter
    private final List<Packet<?>> silentPackets = new ArrayList<>();

    public void sendSilentPacket(Packet<?> packet) {
        silentPackets.add(packet);
        NetworkUtils.sendPacket(packet);
    }

    public void sendPacket(Packet<?> packet) {
        mc.getNetworkHandler().sendPacket(packet);
    }
}