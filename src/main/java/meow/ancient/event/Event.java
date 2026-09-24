package meow.ancient.event;

import lombok.Getter;
import lombok.Setter;
import meteordevelopment.orbit.ICancellable;
import meow.ancient.Ancient;

@Getter
@Setter
public class Event implements ICancellable {
    private boolean cancelled;

    public void post() {
        Ancient.getInstance().getEventBus().post(this);
    }

    public void cancelEvent() {
        setCancelled(true);
    }
}
