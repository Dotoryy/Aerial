package cc.aerial.client.features.impl.movement;

import cc.aerial.client.event.impl.game.JoinWorldEvent;
import cc.aerial.client.event.impl.game.PreGameTickEvent;
import cc.aerial.client.event.impl.game.input.MoveInputEvent;
import cc.aerial.client.event.impl.game.packet.SendPacketEvent;
import cc.aerial.client.event.impl.game.player.interaction.ItemUseEvent;
import cc.aerial.client.event.impl.game.player.movement.PreMovementPacketEvent;
import cc.aerial.client.event.impl.game.server.ServerDisconnectEvent;
import cc.aerial.client.event.subscriber.Subscribe;
import cc.aerial.client.features.Module;
import cc.aerial.client.features.ModuleCategory;
import cc.aerial.client.property.BooleanProperty;
import cc.aerial.client.property.NumberProperty;
import cc.aerial.client.rotation.RaycastUtility;
import cc.aerial.client.rotation.RotationUtility;
import cc.aerial.client.scaffold.ScaffoldRotations;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

public final class LadderClutchModule extends Module {
    public static final LadderClutchModule INSTANCE = new LadderClutchModule();

    private final NumberProperty minimumFallDistance =
            new NumberProperty("Minimum Fall Distance", 3.0, 0.0, 10.0, 0.1);
    private final NumberProperty minimumPitch =
            new NumberProperty("Minimum Pitch", 45.0, 0.0, 90.0, 1.0);
    private final BooleanProperty autoSteer =
            new BooleanProperty("Auto Steer", true);

    private enum Phase { IDLE, AIM_WOOL, PLACE_LADDER, CATCH }
    private Phase phase = Phase.IDLE;

    private long waitStartedAt;

    private float clutchPitch;

    private long rotationHoldUntil;

    private BlockPos woolPos;

    private Direction ladderSide;
    private long ladderPlacedAt;

    private boolean fallConsumed;

    private boolean scriptPlacing;

    private int previousSlot = -1;

    private int ladderSlot = -1;

    private LadderClutchModule() {
        super("Ladder Clutch", "Drops a block and pastes a ladder on the way down",
                ModuleCategory.MOVEMENT);
        addProperties(minimumFallDistance, minimumPitch, autoSteer);
    }

    @Override
    protected void onDisable() {
        reset();
    }

    @Subscribe
    public void onJoinWorld(JoinWorldEvent event) {
        reset();
    }

    @Subscribe
    public void onServerDisconnect(ServerDisconnectEvent event) {
        reset();
    }

    @Subscribe
    public void onPreGameTick(PreGameTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null) {
            reset();
            return;
        }

        if (player.onGround() || player.getDeltaMovement().y > -0.0784f) {
            fallConsumed = false;
        }

        if (phase != Phase.IDLE
                && (player.onGround() || !isRightClickDown() || player.onClimbable())) {
            reset();
            return;
        }

        switch (phase) {
            case AIM_WOOL -> aimWoolTick(player);
            case PLACE_LADDER -> placeLadderTick(player, level);
            case CATCH -> catchTick();
            case IDLE -> idleTick(player);
        }
    }

    private void idleTick(LocalPlayer player) {
        if (!canStart(player)) {
            return;
        }
        int woolSlot = findWoolSlot(player.getInventory(), player.getInventory().getSelectedSlot());
        if (woolSlot == -1) {
            return;
        }

        fallConsumed = true;

        clutchPitch = player.getXRot();

        ladderSlot = player.getInventory().getSelectedSlot();
        previousSlot = ladderSlot;
        selectSlot(player, woolSlot);

        phase = Phase.AIM_WOOL;
        waitStartedAt = System.currentTimeMillis();
    }

    private void aimWoolTick(LocalPlayer player) {
        if (System.currentTimeMillis() - waitStartedAt > AIM_TIMEOUT_MS) {
            reset();
            return;
        }

        Direction travel = travelDirection(player);
        BlockPos landing = predictedLandingColumn(player);

        for (Direction step : stepsFrom(travel)) {
            BlockPos support = supportIn(player, landing.relative(step));
            if (support == null) {
                continue;
            }
            BlockHitResult hit = aimAtTopFace(player, support);
            if (hit == null) {
                continue;
            }

            useItemOn(hit);
            woolPos = support.above();
            phase = Phase.PLACE_LADDER;
            waitStartedAt = System.currentTimeMillis();
            return;
        }
    }

    private static BlockPos predictedLandingColumn(LocalPlayer player) {
        Vec3 motion = player.getDeltaMovement();
        Vec3 predicted = player.position()
                .add(motion.x * LANDING_PREDICT, 0.0, motion.z * LANDING_PREDICT);
        return BlockPos.containing(predicted.x, player.getY(), predicted.z);
    }

    private static Direction travelDirection(LocalPlayer player) {
        Vec3 motion = player.getDeltaMovement();
        if (motion.x * motion.x + motion.z * motion.z <= MOTION_EPSILON) {
            return player.getDirection();
        }
        return Math.abs(motion.x) > Math.abs(motion.z)
                ? (motion.x > 0.0 ? Direction.EAST : Direction.WEST)
                : (motion.z > 0.0 ? Direction.SOUTH : Direction.NORTH);
    }

    private static Direction[] stepsFrom(Direction first) {
        Direction[] ordered = new Direction[HORIZONTAL_SIDES.length];
        ordered[0] = first;
        int index = 1;
        for (Direction side : HORIZONTAL_SIDES) {
            if (side != first) {
                ordered[index++] = side;
            }
        }
        return ordered;
    }

    private static BlockPos supportIn(LocalPlayer player, BlockPos column) {
        BlockPos origin = new BlockPos(column.getX(), player.blockPosition().getY(), column.getZ());
        for (int dy = 0; dy >= -VERTICAL_SCAN; dy--) {
            BlockPos candidate = origin.offset(0, dy, 0);
            if (!player.level().getBlockState(candidate)
                    .isFaceSturdy(player.level(), candidate, Direction.UP)) {
                continue;
            }
            return player.level().getBlockState(candidate.above()).isAir() ? candidate : null;
        }
        return null;
    }

    private BlockHitResult aimAtTopFace(LocalPlayer player, BlockPos support) {
        Vec3 faceCentre = new Vec3(support.getX() + 0.5, support.getY() + 1.0, support.getZ() + 0.5);
        float yaw = player.getYRot();
        float direct = ScaffoldRotations.rotationsTo(faceCentre).y;

        for (float offset : PITCH_OFFSETS) {
            float pitch = quantizePitch(yaw, ScaffoldRotations.clampPitch(direct + offset));
            BlockHitResult hit = verifyFromBothPositions(yaw, pitch, support, Direction.UP);
            if (hit == null) {
                continue;
            }
            holdPitch(pitch);
            return hit;
        }
        return null;
    }

    private static float quantizePitch(float yaw, float pitch) {
        return RotationUtility.getQuantizedRotation(new Vec2(yaw, pitch)).y;
    }

    private static BlockHitResult verifyFromBothPositions(float yaw, float pitch,
                                                         BlockPos target, Direction face) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return null;
        }
        Vec3 eye = player.getEyePosition();
        BlockHitResult now = RaycastUtility.rayTraceBlockFrom(eye, yaw, pitch, PLACE_REACH);
        if (!hits(now, target, face)) {
            return null;
        }
        BlockHitResult next = RaycastUtility.rayTraceBlockFrom(
                eye.add(player.getDeltaMovement()), yaw, pitch, PLACE_REACH);
        return hits(next, target, face) ? now : null;
    }

    private void holdPitch(float pitch) {
        clutchPitch = pitch;
        rotationHoldUntil = System.currentTimeMillis() + ROTATION_HOLD_MS;
    }

    private void placeLadderTick(LocalPlayer player, ClientLevel level) {
        if (woolPos == null || ladderSlot == -1) {
            reset();
            return;
        }
        if (System.currentTimeMillis() - waitStartedAt > LADDER_TIMEOUT_MS) {
            reset();
            return;
        }

        if (level.getBlockState(woolPos).isAir()) {
            reset();
            return;
        }
        selectSlot(player, ladderSlot);

        BlockHitResult hit = findLadderFace(player, woolPos, player.getYRot());

        if (hit == null) {
            return;
        }

        useItemOn(hit);
        ladderSide = hit.getDirection();
        ladderPlacedAt = System.currentTimeMillis();
        phase = Phase.CATCH;
    }

    private BlockHitResult findLadderFace(LocalPlayer player, BlockPos wool, float yaw) {
        Direction preferred = closestHorizontalSide(player, wool);
        BlockHitResult hit = verifyFace(wool, preferred, yaw);
        if (hit != null) {
            return hit;
        }
        for (Direction side : HORIZONTAL_SIDES) {
            if (side == preferred) {
                continue;
            }
            hit = verifyFace(wool, side, yaw);
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }

    private BlockHitResult verifyFace(BlockPos wool, Direction side, float yaw) {
        Vec3 edge = new Vec3(
                wool.getX() + 0.5 + side.getStepX() * EDGE_INSET,
                wool.getY() + 1.0,
                wool.getZ() + 0.5 + side.getStepZ() * EDGE_INSET);
        float edgePitch = quantizePitch(yaw,
                ScaffoldRotations.clampPitch(ScaffoldRotations.rotationsTo(edge).y));
        BlockHitResult hit = verifyFromBothPositions(yaw, edgePitch, wool, side);
        if (hit != null) {
            holdPitch(edgePitch);
            return hit;
        }

        Vec3 faceCentre = new Vec3(
                wool.getX() + 0.5 + side.getStepX() * 0.5,
                wool.getY() + 0.5,
                wool.getZ() + 0.5 + side.getStepZ() * 0.5);

        float direct = ScaffoldRotations.rotationsTo(faceCentre).y;
        for (float offset : PITCH_OFFSETS) {
            float pitch = quantizePitch(yaw, ScaffoldRotations.clampPitch(direct + offset));
            hit = verifyFromBothPositions(yaw, pitch, wool, side);
            if (hit != null) {
                holdPitch(pitch);
                return hit;
            }
        }
        return null;
    }

    private static boolean hits(BlockHitResult hit, BlockPos pos, Direction side) {
        return hit != null && pos != null && hit.getBlockPos().equals(pos) && hit.getDirection() == side;
    }

    private static BlockHitResult crosshairTopFace(LocalPlayer player) {
        BlockHitResult hit = RaycastUtility.rayTraceBlock(
                player.getYRot(), player.getXRot(), CROSSHAIR_REACH);
        if (hit == null || hit.getDirection() != Direction.UP) {
            return null;
        }
        return player.level().getBlockState(hit.getBlockPos().above()).isAir() ? hit : null;
    }

    private void catchTick() {
        if (System.currentTimeMillis() - ladderPlacedAt > CATCH_TIMEOUT_MS) {
            reset();
        }
    }

    @Subscribe
    public void onMoveInput(MoveInputEvent event) {
        if (phase == Phase.IDLE) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        steer(event, player);
    }

    private void steer(MoveInputEvent event, LocalPlayer player) {
        if (!autoSteer.getValue() || woolPos == null) {
            return;
        }

        Direction side = ladderSide != null ? ladderSide : closestHorizontalSide(player, woolPos);

        double targetX = woolPos.getX() + 0.5 + side.getStepX();
        double targetZ = woolPos.getZ() + 0.5 + side.getStepZ();
        double dx = targetX - player.getX();
        double dz = targetZ - player.getZ();
        if (dx * dx + dz * dz < 0.04) {
            return;
        }

        double wanted = Math.atan2(dz, dx);
        float bestForward = 0.0f;
        float bestSideways = 0.0f;
        double bestDifference = Double.MAX_VALUE;
        for (float forward = -1.0f; forward <= 1.0f; forward += 1.0f) {
            for (float sideways = -1.0f; sideways <= 1.0f; sideways += 1.0f) {
                if (forward == 0.0f && sideways == 0.0f) {
                    continue;
                }
                double candidate = moveDirection(player.getYRot(), forward, sideways);
                double difference = Math.abs(Mth.wrapDegrees(
                        (float) Math.toDegrees(candidate - wanted)));
                if (difference < bestDifference) {
                    bestDifference = difference;
                    bestForward = forward;
                    bestSideways = sideways;
                }
            }
        }
        event.setForward(bestForward);
        event.setSideways(bestSideways);
    }

    private static double moveDirection(float rotationYaw, double moveForward, double moveStrafing) {
        if (moveForward < 0.0) {
            rotationYaw += 180.0f;
        }
        float forward = 1.0f;
        if (moveForward < 0.0) {
            forward = -0.5f;
        } else if (moveForward > 0.0) {
            forward = 0.5f;
        }
        if (moveStrafing > 0.0) {
            rotationYaw -= 90.0f * forward;
        } else if (moveStrafing < 0.0) {
            rotationYaw += 90.0f * forward;
        }
        return Math.toRadians(rotationYaw);
    }

    @Subscribe
    public void onPreMovementPacket(PreMovementPacketEvent event) {
        if (phase == Phase.IDLE) {
            return;
        }
        event.setPitch(clutchPitch);
    }

    @Subscribe
    public void onSendPacket(SendPacketEvent event) {
        if (scriptPlacing || phase == Phase.IDLE) {
            return;
        }

        if (event.getPacket() instanceof ServerboundUseItemPacket
                || event.getPacket() instanceof ServerboundUseItemOnPacket) {
            event.setCancelled();
        }
    }

    @Subscribe
    public void onItemUse(ItemUseEvent event) {
        if (phase != Phase.IDLE && !scriptPlacing) {
            event.setCancelled();
        }
    }

    private void useItemOn(BlockHitResult hit) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.gameMode == null) {
            return;
        }
        scriptPlacing = true;
        try {
            mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);
            player.swing(InteractionHand.MAIN_HAND);
        } finally {
            scriptPlacing = false;
        }
    }

    private static final double PLACE_REACH = 4.5;

    private static final double CROSSHAIR_REACH = 32.0;

    private static final double EDGE_INSET = 0.42;
    private static final Direction[] HORIZONTAL_SIDES =
            {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};

    private static final long ROTATION_HOLD_MS = 260L;

    private static final double LANDING_PREDICT = 0.72;

    private static final float[] PITCH_OFFSETS = {0.0f, 2.0f, -2.0f, 4.5f, -4.5f, 8.0f, -8.0f};

    private static final int VERTICAL_SCAN = 5;

    private static final double MOTION_EPSILON = 1.0E-4;

    private static final long AIM_TIMEOUT_MS = 1200L;

    private static final long LADDER_TIMEOUT_MS = 600L;
    private static final long CATCH_TIMEOUT_MS = 1500L;

    private static Direction closestHorizontalSide(LocalPlayer player, BlockPos block) {
        double dx = player.getX() - (block.getX() + 0.5);
        double dz = player.getZ() - (block.getZ() + 0.5);
        if (Math.abs(dx) > Math.abs(dz)) {
            return dx >= 0 ? Direction.EAST : Direction.WEST;
        }
        return dz >= 0 ? Direction.SOUTH : Direction.NORTH;
    }

    private boolean canStart(LocalPlayer player) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() != null) return false;
        if (!isRightClickDown()) return false;
        if (!isLadderStack(player.getMainHandItem())) return false;
        if (player.getAbilities().flying || player.getAbilities().instabuild) return false;
        if (player.onGround()) return false;
        if (player.getDeltaMovement().y > -0.0784) return false;
        if (player.fallDistance < minimumFallDistance.getValue().floatValue()) return false;
        if (fallConsumed) return false;
        if (player.getXRot() < minimumPitch.getValue().floatValue()) return false;
        return crosshairTopFace(player) != null;
    }

    private static boolean isRightClickDown() {
        return Minecraft.getInstance().options.keyUse.isDown();
    }

    private void reset() {
        restoreSlot();
        phase = Phase.IDLE;
        woolPos = null;
        ladderSide = null;
        ladderPlacedAt = 0L;
        ladderSlot = -1;
        waitStartedAt = 0L;
        rotationHoldUntil = 0L;
    }

    private static void selectSlot(LocalPlayer player, int slot) {
        Inventory inventory = player.getInventory();
        if (inventory.getSelectedSlot() != slot) {
            inventory.setSelectedSlot(slot);
        }
    }

    private void restoreSlot() {
        if (previousSlot == -1) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            selectSlot(player, previousSlot);
        }
        previousSlot = -1;
    }

    private static int findWoolSlot(Inventory inv, int exceptSlot) {
        for (int slot = 0; slot < 9; slot++) {
            if (slot == exceptSlot) continue;
            ItemStack stack = inv.getItem(slot);
            if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem)) continue;
            if (isLadderStack(stack)) continue;
            return slot;
        }
        return -1;
    }

    private static boolean isLadderStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return stack.getItem() instanceof BlockItem b && b.getBlock() instanceof LadderBlock;
    }
}
