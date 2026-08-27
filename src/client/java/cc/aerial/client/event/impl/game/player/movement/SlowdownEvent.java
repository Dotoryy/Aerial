package cc.aerial.client.event.impl.game.player.movement;

import cc.aerial.client.event.EventCancellable;

public final class SlowdownEvent extends EventCancellable {
    private float slowdown;

    public SlowdownEvent(float slowdown) {
        this.slowdown = slowdown;
    }

    public float getSlowdown() {
        return slowdown;
    }

    public void setSlowdown(float slowdown) {
        this.slowdown = slowdown;
    }
}
