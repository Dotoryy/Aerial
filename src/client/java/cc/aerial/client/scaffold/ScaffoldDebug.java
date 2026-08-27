package cc.aerial.client.scaffold;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ScaffoldDebug {
    private static final Logger LOGGER = LoggerFactory.getLogger("Aerial/Scaffold");

    public static boolean enabled = false;

    private ScaffoldDebug() {
    }

    public static void tick(Vec3 target, BlockPos blockFace, Direction facing,
                            float currentYaw, float currentPitch,
                            int ticksOnAir, boolean readyToPlace,
                            boolean reaimed, boolean hitsStrict, boolean hitsLoose) {
        if (!enabled) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        LOGGER.info("t={} pos=({}) onGround={} tR={} air={} ready={} | target={} face={} dir={} | rot={}/{} | rel={} | reaim={} hitStrict={} hitLoose={}",
                player.tickCount,
                String.format("%.2f,%.2f,%.2f", player.getX(), player.getY(), player.getZ()),
                player.onGround(),
                cc.aerial.client.utility.GroundTickTracker.getAirTicks(),
                ticksOnAir,
                readyToPlace,
                target == null ? "null" : String.format("%.0f,%.0f,%.0f", target.x, target.y, target.z),
                blockFace == null ? "null" : blockFace.toShortString(),
                facing,
                String.format("%.1f", currentYaw),
                String.format("%.1f", currentPitch),

                target == null ? "null"
                        : String.format("%+d,%+d,%+d",
                        (int) Math.floor(target.x) - player.getBlockX(),
                        (int) Math.floor(target.y) - player.getBlockY(),
                        (int) Math.floor(target.z) - player.getBlockZ()),
                reaimed, hitsStrict, hitsLoose);
    }

    public static void gate(boolean ready, boolean rayCastOff, boolean fallingBypass,
                            boolean hit, boolean result, float yaw, float pitch) {
        if (!enabled) {
            return;
        }
        LOGGER.info("    GATE ready={} off={} falling={} hit={} -> {} | rotAtPacket={}/{}",
                ready, rayCastOff, fallingBypass, hit, result,
                String.format("%.1f", yaw), String.format("%.1f", pitch));
    }

    public static void normalScan(int candidates, int from, int to) {
        if (!enabled) {
            return;
        }
        LOGGER.info("    NORMAL-SCAN candidates={} sweep={}..{}", candidates, from, to);
    }

    public static void placed(BlockPos blockFace, Direction facing, boolean success, String path) {
        if (!enabled) {
            return;
        }
        LOGGER.info("    PLACE via={} face={} dir={} -> {}",
                path, blockFace == null ? "null" : blockFace.toShortString(), facing, success);
    }
}
