package cc.aerial.client.event.impl.game.player.interaction;

import net.minecraft.world.entity.Entity;

public final class AttackEvent {
    private final Entity target;

    public AttackEvent(Entity target) {
        this.target = target;
    }

    public Entity getTarget() {
        return target;
    }
}
