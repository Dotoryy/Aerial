package cc.aerial.client.event.impl.game.player.movement;

import net.minecraft.world.phys.Vec3;

public final class PostMoveEvent {
    private final float speed;
    private final Vec3 movementInput;

    public PostMoveEvent(float speed, Vec3 movementInput) {
        this.speed = speed;
        this.movementInput = movementInput;
    }

    public float getSpeed() {
        return speed;
    }

    public Vec3 getMovementInput() {
        return movementInput;
    }
}
