package cc.aerial.client.utility;

public final class Stopwatch {
    private long lastMs;

    public Stopwatch(long lastMs) {
        this.lastMs = lastMs;
    }

    public Stopwatch() {
        this.reset();
    }

    public void reset() {
        lastMs = System.currentTimeMillis();
    }

    public boolean hasTimeElapsed(long time, boolean reset) {
        if (getTime() > time) {
            if (reset) {
                reset();
            }
            return true;
        }
        return false;
    }

    public boolean hasTimeElapsed(long time) {
        return hasTimeElapsed(time, false);
    }

    public long getTime() {
        return System.currentTimeMillis() - lastMs;
    }
}
