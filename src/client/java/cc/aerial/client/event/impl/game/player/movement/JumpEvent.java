package cc.aerial.client.event.impl.game.player.movement;

import cc.aerial.client.event.EventCancellable;

public final class JumpEvent extends EventCancellable {
    private float yaw;

    public JumpEvent(float yaw) {
        this.yaw = yaw;
    }

    public float getYaw() {
        return yaw;
    }

    public void setYaw(float yaw) {
        this.yaw = yaw;
    }
}
