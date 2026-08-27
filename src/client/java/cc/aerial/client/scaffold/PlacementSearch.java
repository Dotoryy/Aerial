package cc.aerial.client.scaffold;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class PlacementSearch {
    private static final double MAX_DISTANCE = 5.0;

    private PlacementSearch() {
    }

    public static Vec3 findTarget(double offsetX, double offsetY, double offsetZ, Integer requiredY) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player == null || level == null) {
            return null;
        }

        int radius = (int) (5.0 + (Math.abs(offsetX) + Math.abs(offsetZ)));
        List<Vec3> candidates = new ArrayList<>();

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    double cellX = player.getX() + x;
                    double cellY = player.getY() + y;
                    double cellZ = player.getZ() + z;
                    BlockPos pos = blockPos(cellX, cellY, cellZ);
                    BlockState state = level.getBlockState(pos);
                    if (isInteractable(state)) {
                        continue;
                    }
                    if (state.canBeReplaced()) {
                        continue;
                    }

                    for (int sign = -1; sign <= 1; sign += 2) {
                        candidates.add(new Vec3(cellX + sign, cellY, cellZ));
                        candidates.add(new Vec3(cellX, cellY + sign, cellZ));
                        candidates.add(new Vec3(cellX, cellY, cellZ + sign));
                    }
                }
            }
        }

        candidates.removeIf(candidate -> player.distanceToSqr(candidate) > MAX_DISTANCE * MAX_DISTANCE
                || !level.getBlockState(blockPos(candidate)).canBeReplaced());

        if (requiredY != null) {
            candidates.removeIf(candidate -> Math.floor(candidate.y + 1.0) != requiredY.intValue());
        }
        if (candidates.isEmpty()) {
            return null;
        }

        Vec3 wanted = new Vec3(player.getX() + offsetX, player.getY() - 1.0 + offsetY, player.getZ() + offsetZ);
        candidates.sort(Comparator.comparingDouble(candidate -> candidate.distanceToSqr(wanted)));
        return candidates.getFirst();
    }

    public static FacingOffset findFace(Vec3 target, float yaw, boolean allowDown) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || target == null) {
            return null;
        }

        List<FacingOffset> horizontal = new ArrayList<>(4);
        for (int sign = -1; sign <= 1; sign += 2) {
            if (!isReplaceable(level, target.x, target.y, target.z + sign)) {
                horizontal.add(new FacingOffset(sign < 0 ? Direction.SOUTH : Direction.NORTH,
                        new Vec3(0.0, 0.0, sign)));
            }
        }
        for (int sign = -1; sign <= 1; sign += 2) {
            if (!isReplaceable(level, target.x + sign, target.y, target.z)) {
                horizontal.add(new FacingOffset(sign > 0 ? Direction.WEST : Direction.EAST,
                        new Vec3(sign, 0.0, 0.0)));
            }
        }

        if (!horizontal.isEmpty()) {
            double reference = yaw % 360.0f + 90.0f;
            horizontal.sort(Comparator.comparingDouble(face -> {
                double degrees = Math.toDegrees(Math.atan2(face.offset().z, face.offset().x)) % 360.0;
                return Math.abs(angleDifference(degrees, reference));
            }));
            return horizontal.getFirst();
        }

        for (int sign = -1; sign <= 1; sign += 2) {
            if (!isReplaceable(level, target.x, target.y + sign, target.z)) {
                if (sign < 0) {
                    return new FacingOffset(Direction.UP, new Vec3(0.0, sign, 0.0));
                }
                if (allowDown) {
                    return new FacingOffset(Direction.DOWN, new Vec3(0.0, sign, 0.0));
                }
            }
        }

        return null;
    }

    private static boolean isInteractable(BlockState state) {
        return state.is(Blocks.CHEST) || state.is(Blocks.TRAPPED_CHEST)
                || state.is(Blocks.ANVIL) || state.is(Blocks.CRAFTING_TABLE)
                || state.is(Blocks.FURNACE) || state.is(Blocks.HOPPER)
                || state.is(Blocks.DROPPER) || state.is(Blocks.DISPENSER)
                || state.is(Blocks.NOTE_BLOCK) || state.is(Blocks.JUKEBOX);
    }

    public static boolean isPartialBlock(BlockState state) {
        return state.getBlock() instanceof StairBlock || state.getBlock() instanceof SlabBlock;
    }

    private static boolean isReplaceable(ClientLevel level, double x, double y, double z) {
        return level.getBlockState(blockPos(x, y, z)).canBeReplaced();
    }

    private static BlockPos blockPos(double x, double y, double z) {
        return BlockPos.containing(x, y, z);
    }

    private static BlockPos blockPos(Vec3 vec) {
        return BlockPos.containing(vec.x, vec.y, vec.z);
    }

    private static double angleDifference(double a, double b) {
        return Math.min(Math.abs(a - b),
                Math.min(Math.abs(a - 360.0) - Math.abs(b), Math.abs(b - 360.0) - Math.abs(a)));
    }

    public record FacingOffset(Direction facing, Vec3 offset) {
    }
}
