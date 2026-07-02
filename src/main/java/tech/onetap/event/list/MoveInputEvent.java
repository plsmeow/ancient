package tech.onetap.event.list;

import tech.onetap.event.Event;

public class MoveInputEvent extends Event {
    public float forward, strafe;
    public boolean jump, sneak;
    public double sneakSlow;
    public boolean cancelled = false;

    public MoveInputEvent(float forward, float strafe, boolean jump, boolean sneak, double sneakSlow) {
        this.forward = forward;
        this.strafe = strafe;
        this.jump = jump;
        this.sneak = sneak;
        this.sneakSlow = sneakSlow;
    }

    public void cancel() {
        this.cancelled = true;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public double getSneakSlow() {
        return sneakSlow;
    }

    public float getForward() {
        return forward;
    }

    public float getStrafe() {
        return strafe;
    }

    public boolean isJump() {
        return jump;
    }

    public boolean isSneaking() {
        return sneak;
    }
}