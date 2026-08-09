package tech.onetap.event.list;

import lombok.Getter;
import tech.onetap.event.Event;

@Getter
public class MoveInputEvent extends Event {
    public float forward, strafe;
    public boolean jump, sneak;
    public double sneakSlow;

    public MoveInputEvent(float forward, float strafe, boolean jump, boolean sneak, double sneakSlow) {
        this.forward = forward;
        this.strafe = strafe;
        this.jump = jump;
        this.sneak = sneak;
        this.sneakSlow = sneakSlow;
    }

    public boolean isSneaking() {
        return sneak;
    }
}
