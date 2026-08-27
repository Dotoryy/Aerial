package cc.aerial.client.event.impl.game.player.movement.knockback;

public final class KnockbackEvent {
    private double x;
    private double y;
    private double z;

    private boolean overridden;

    private final boolean explosion;

    public KnockbackEvent(double x, double y, double z) {
        this(x, y, z, false);
    }

    public KnockbackEvent(double x, double y, double z, boolean explosion) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.explosion = explosion;
    }

    public boolean isExplosion() {
        return explosion;
    }

    public boolean isOverridden() {
        return overridden;
    }

    public void setOverridden() {
        this.overridden = true;
    }

    public double getX() {
        return this.x;
    }

    public void setX(double x) {
        this.x = x;
        this.overridden = true;
    }

    public double getY() {
        return this.y;
    }

    public void setY(double y) {
        this.y = y;
        this.overridden = true;
    }

    public double getZ() {
        return this.z;
    }

    public void setZ(double z) {
        this.z = z;
        this.overridden = true;
    }
}
