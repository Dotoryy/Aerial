package cc.aerial.client.event.impl.game.player.teleport;

import cc.aerial.client.event.EventCancellable;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;

import java.util.Set;

public final class PreTeleportEvent extends EventCancellable {
    private final int teleportId;
    private PositionMoveRotation change;
    private final Set<Relative> relatives;

    public PreTeleportEvent(int teleportId, PositionMoveRotation change, Set<Relative> relatives) {
        this.teleportId = teleportId;
        this.change = change;
        this.relatives = relatives;
    }

    public int getTeleportId() {
        return teleportId;
    }

    public PositionMoveRotation getChange() {
        return change;
    }

    public void setChange(PositionMoveRotation change) {
        this.change = change;
    }

    public Set<Relative> getRelatives() {
        return relatives;
    }
}
