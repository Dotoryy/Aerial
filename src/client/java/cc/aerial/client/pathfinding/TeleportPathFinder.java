package cc.aerial.client.pathfinding;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class TeleportPathFinder {
    private static final Vec3[] HORIZONTAL = {
            new Vec3(1.0, 0.0, 0.0), new Vec3(-1.0, 0.0, 0.0),
            new Vec3(0.0, 0.0, 1.0), new Vec3(0.0, 0.0, -1.0)
    };

    private static final double ARRIVAL_DISTANCE_SQ = 9.5;

    private TeleportPathFinder() {
    }

    private static final class Node {
        final Vec3 pos;
        final List<Vec3> path;
        double distanceToGoal;
        double total;

        Node(Vec3 pos, List<Vec3> path, double distanceToGoal, double total) {
            this.pos = pos;
            this.path = path;
            this.distanceToGoal = distanceToGoal;
            this.total = total;
        }
    }

    public static List<Vec3> find(Vec3 from, Vec3 to, boolean appendGoal) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return null;
        }
        Vec3 start = from;
        if (!isPassable(mc.level, BlockPos.containing(start))) {
            start = start.add(0.0, 1.0, 0.0);
        }
        List<Vec3> raw = compute(floor(start), floor(to));
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        return simplify(raw, to, appendGoal);
    }

    private static List<Vec3> compute(Vec3 start, Vec3 goal) {
        List<Node> open = new ArrayList<>();
        List<Node> closed = new ArrayList<>();
        List<Vec3> firstPath = new ArrayList<>();
        firstPath.add(start);
        open.add(new Node(start, firstPath, distanceSq(start, goal), 0.0));

        outer:
        for (int iteration = 0; iteration < 1000 && !open.isEmpty(); iteration++) {
            open.sort(Comparator.comparingDouble(n -> n.distanceToGoal + n.total));
            int expanded = 0;
            for (Node node : new ArrayList<>(open)) {
                if (++expanded > 4) {
                    break;
                }
                open.remove(node);
                closed.add(node);
                for (Vec3 offset : HORIZONTAL) {
                    List<Vec3> done = step(open, closed, node, floor(node.pos.add(offset)), goal);
                    if (done != null) {
                        return done;
                    }
                }
                List<Vec3> up = step(open, closed, node, floor(node.pos.add(0.0, 1.0, 0.0)), goal);
                if (up != null) {
                    return up;
                }
                List<Vec3> down = step(open, closed, node, floor(node.pos.add(0.0, -1.0, 0.0)), goal);
                if (down != null) {
                    return down;
                }
            }
        }
        if (closed.isEmpty()) {
            return null;
        }

        closed.sort(Comparator.comparingDouble(n -> n.distanceToGoal + n.total));
        return closed.get(0).path;
    }

    private static List<Vec3> step(List<Node> open, List<Node> closed, Node from, Vec3 pos, Vec3 goal) {
        if (!isWalkable(pos) || findNode(open, closed, pos) != null) {
            return null;
        }
        List<Vec3> path = new ArrayList<>(from.path);
        path.add(pos);
        if (samePosition(pos, goal) || distanceSq(pos, goal) <= ARRIVAL_DISTANCE_SQ) {
            return path;
        }
        open.add(new Node(pos, path, distanceSq(pos, goal), from.total + 1.0));
        return null;
    }

    private static Node findNode(List<Node> open, List<Node> closed, Vec3 pos) {
        for (Node node : closed) {
            if (samePosition(node.pos, pos)) {
                return node;
            }
        }
        for (Node node : open) {
            if (samePosition(node.pos, pos)) {
                return node;
            }
        }
        return null;
    }

    private static List<Vec3> simplify(List<Vec3> raw, Vec3 goal, boolean appendGoal) {
        List<Vec3> out = new ArrayList<>();
        Vec3 previous = null;
        Vec3 anchor = null;
        for (int i = 0; i < raw.size(); i++) {
            Vec3 stepPos = raw.get(i);
            if (i == 0 || i == raw.size() - 1) {
                out.add(stepPos.add(0.5, 0.0, 0.5));
                anchor = stepPos;
            } else if (anchor != null && previous != null && !hasClearRun(anchor, stepPos)) {
                out.add(previous.add(0.5, 0.0, 0.5));
                anchor = previous;
            }
            previous = stepPos;
        }
        if (appendGoal) {
            out.add(goal);
        }
        return out;
    }

    private static boolean hasClearRun(Vec3 anchor, Vec3 target) {
        if (distanceSq(anchor, target) > ARRIVAL_DISTANCE_SQ * ARRIVAL_DISTANCE_SQ) {
            return false;
        }
        int minX = (int) Math.min(anchor.x, target.x);
        int minY = (int) Math.min(anchor.y, target.y);
        int minZ = (int) Math.min(anchor.z, target.z);
        int maxX = (int) Math.max(anchor.x, target.x);
        int maxY = (int) Math.max(anchor.y, target.y);
        int maxZ = (int) Math.max(anchor.z, target.z);
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (!isWalkable(new Vec3(x, y, z))) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static boolean isWalkable(Vec3 pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return false;
        }
        int x = (int) pos.x;
        int y = (int) pos.y;
        int z = (int) pos.z;
        return isPassable(mc.level, new BlockPos(x, y, z))
                && isPassable(mc.level, new BlockPos(x, y + 1, z))
                && !isPassable(mc.level, new BlockPos(x, y - 1, z));
    }

    private static boolean isPassable(Level level, BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    private static Vec3 floor(Vec3 v) {
        return new Vec3(Math.floor(v.x), Math.floor(v.y), Math.floor(v.z));
    }

    private static boolean samePosition(Vec3 a, Vec3 b) {
        return a.x == b.x && a.y == b.y && a.z == b.z;
    }

    private static double distanceSq(Vec3 a, Vec3 b) {
        double dx = a.x - b.x;
        double dy = a.y - b.y;
        double dz = a.z - b.z;
        return dx * dx + dy * dy + dz * dz;
    }
}
