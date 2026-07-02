package tech.onetap.util.math;

import lombok.Getter;

public class StopWatch {
    @Getter
    public long lastMS = System.currentTimeMillis();
    public void reset() {
        lastMS = System.currentTimeMillis();
    }
    public boolean isReached(long time) {
        return System.currentTimeMillis() - lastMS > time;
    }

    public boolean finished(final double delay) {
        return System.currentTimeMillis() - delay >= lastMS;
    }

    public boolean every(final double delay) {
        boolean finished = this.finished(delay);
        if (finished) reset();
        return finished;
    }

    public void setLastMS(long newValue) {
        lastMS = System.currentTimeMillis() + newValue;
    }
    public void setTime(long time) {
        lastMS = time;
    }

    public long getTime() {
        return System.currentTimeMillis() - lastMS;
    }
    public boolean isRunning() {
        return System.currentTimeMillis() - lastMS <= 0;
    }
    public boolean hasTimeElapsed() {
        return lastMS < System.currentTimeMillis();
    }
}