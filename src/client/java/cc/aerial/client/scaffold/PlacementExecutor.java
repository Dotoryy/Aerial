package cc.aerial.client.scaffold;

import cc.aerial.client.features.impl.world.ScaffoldModule;
import cc.aerial.client.mixin.MinecraftInvoker;
import cc.aerial.client.rotation.RaycastUtility;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class PlacementExecutor {
    private PlacementExecutor() {
    }

    public static boolean place(BlockPos blockFace, Direction facing, Vec3 hitVec) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.gameMode == null || blockFace == null || facing == null) {
            return false;
        }

        BlockHitResult hit = new BlockHitResult(hitVec, facing, blockFace, false);
        InteractionResult result = minecraft.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);
        if (result instanceof InteractionResult.Success) {
            ScaffoldModule.INSTANCE.performSwing(player);
            return true;
        }
        return false;
    }

    public static void placeStrict() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        ((MinecraftInvoker) minecraft).aerial$startUseItem();
    }

    public static boolean placeGrim(BlockPos blockFace, Direction facing, Vec3 hitVec,
                                    float targetYaw, float targetPitch) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        ClientPacketListener connection = minecraft.getConnection();
        if (player == null || connection == null) {
            return false;
        }

        float yaw = player.getYRot() + Mth.wrapDegrees(targetYaw - player.getYRot());
        connection.send(new ServerboundMovePlayerPacket.PosRot(player.getX(), player.getY(), player.getZ(),
                yaw, targetPitch, player.onGround(), player.horizontalCollision));

        boolean placed = place(blockFace, facing, hitVec);

        connection.send(new ServerboundMovePlayerPacket.PosRot(player.getX(), player.getY(), player.getZ(),
                (float) (player.getYRot() + Math.random() * 0.03),
                (float) Mth.clamp(player.getXRot() - Math.random(), -90.0, 90.0),
                player.onGround(), player.horizontalCollision));
        return placed;
    }

    public static Vec3 hitVec(BlockPos blockFace, Direction facing, float yaw, float pitch) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (blockFace == null || facing == null || player == null) {
            return Vec3.ZERO;
        }

        double x = blockFace.getX() + Math.random();
        double y = blockFace.getY() + Math.random();
        double z = blockFace.getZ() + Math.random();

        switch (facing) {
            case NORTH -> z = blockFace.getZ();
            case SOUTH -> z = blockFace.getZ() + 1;
            case WEST -> x = blockFace.getX();
            case EAST -> x = blockFace.getX() + 1;
            case DOWN -> y = blockFace.getY();
            case UP -> y = blockFace.getY() + 1;
        }

        BlockHitResult real = RaycastUtility.rayTraceBlock(yaw, pitch, player.blockInteractionRange());
        if (real != null && real.getType() == HitResult.Type.BLOCK
                && real.getBlockPos().equals(blockFace) && real.getDirection() == facing) {
            return real.getLocation();
        }
        return new Vec3(x, y, z);
    }
}
