package tech.onetap.event;

import lombok.Getter;
import lombok.Setter;
import meteordevelopment.orbit.ICancellable;
import tech.onetap.Onetap;

@Getter
@Setter
public class Event implements ICancellable {
    private boolean cancelled;

    public void post() {
        Onetap.getInstance().getEventBus().post(this);
    }

    public void cancelEvent() {
        setCancelled(true);
    }
}
