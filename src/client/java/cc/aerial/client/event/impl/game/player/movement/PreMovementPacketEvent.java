package cc.aerial.client.event.impl.game.player.movement;

import cc.aerial.client.event.EventCancellable;

public final class PreMovementPacketEvent extends EventCancellable {
    private double x, y, z;
    private float yaw, pitch;
    private boolean onGround, sprinting, horizontalCollision;

    public PreMovementPacketEvent(double x, double y, double z, float yaw, float pitch,
                                   boolean onGround, boolean sprinting, boolean horizontalCollision) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.onGround = onGround;
        this.sprinting = sprinting;
        this.horizontalCollision = horizontalCollision;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public void setX(double x) {
        this.x = x;
    }

    public void setY(double y) {
        this.y = y;
    }

    public void setZ(double z) {
        this.z = z;
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public void setYaw(float yaw) {
        this.yaw = yaw;
    }

    public void setPitch(float pitch) {
        this.pitch = pitch;
    }

    public boolean isOnGround() {
        return onGround;
    }

    public void setOnGround(boolean onGround) {
        this.onGround = onGround;
    }

    public boolean isSprinting() {
        return sprinting;
    }

    public void setSprinting(boolean sprinting) {
        this.sprinting = sprinting;
    }

    public boolean isHorizontalCollision() {
        return horizontalCollision;
    }

    public void setHorizontalCollision(boolean horizontalCollision) {
        this.horizontalCollision = horizontalCollision;
    }
}
