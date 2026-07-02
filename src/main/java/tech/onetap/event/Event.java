package tech.onetap.event;

import lombok.Data;
import tech.onetap.Onetap;

@Data
public class Event {
    private boolean cancelled;

    public void post() {
        Onetap.getInstance().getEventBus().post(this);
    }

    public void cancelEvent() {
        setCancelled(true);
    }
}