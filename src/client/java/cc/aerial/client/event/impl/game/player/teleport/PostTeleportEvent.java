package cc.aerial.client.event.impl.game.player.teleport;

import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;

import java.util.Set;

public record PostTeleportEvent(int teleportId, PositionMoveRotation change, Set<Relative> relatives) {
}
