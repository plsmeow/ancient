package tech.onetap.event.list;

import lombok.AllArgsConstructor;
import lombok.Getter;
import net.minecraft.network.packet.Packet;
import tech.onetap.event.Event;

@Getter
@AllArgsConstructor
public class EventPacket extends Event {
    private final Packet<?> packet;
    private Type type;

    private boolean isSent() {
        return type == Type.SEND;
    }

    private boolean isReceive() {
        return type == Type.RECEIVE;
    }

    public enum Type {
        SEND,
        RECEIVE
    }
}