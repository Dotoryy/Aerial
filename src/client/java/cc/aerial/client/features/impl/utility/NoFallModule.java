package cc.aerial.client.features.impl.utility;

import cc.aerial.client.event.impl.game.input.MoveInputEvent;
import cc.aerial.client.event.impl.game.packet.ReceivePacketEvent;
import cc.aerial.client.event.impl.game.player.movement.PostMoveEvent;
import cc.aerial.client.event.impl.game.player.movement.PostMovementPacketEvent;
import cc.aerial.client.event.impl.game.player.movement.PreMoveEvent;
import cc.aerial.client.event.impl.game.player.movement.PreMovementPacketEvent;
import cc.aerial.client.event.subscriber.Subscribe;
import cc.aerial.client.features.Module;
import cc.aerial.client.features.ModuleCategory;
import cc.aerial.client.mixin.LocalPlayerInvoker;
import cc.aerial.client.packet.blockage.BlockHolder;
import cc.aerial.client.packet.blockage.NetworkDirection;
import cc.aerial.client.property.BooleanProperty;
import cc.aerial.client.property.ModeProperty;
import cc.aerial.client.simulation.PlayerSimulation;
import cc.aerial.client.utility.GroundTickTracker;
import cc.aerial.client.utility.HypixelServer;
import cc.aerial.client.utility.KeyMappingUtility;
import cc.aerial.client.utility.PlayerUtility;
import net.hypixel.data.type.GameType;
import cc.aerial.client.event.impl.game.PreGameTickEvent;
import cc.aerial.client.rotation.RaycastUtility;
import cc.aerial.client.rotation.RotationUtility;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class NoFallModule extends Module {
    public static final NoFallModule INSTANCE = new NoFallModule();

    public enum Mode {
        SPOOF("Spoof"),
        WATCHDOG("Hypixel"),
        LEGIT("Legit"),
        GRIM_SERVER_19("Grim (Server 1.9+)"),
        MATRIX("Matrix"),
        ELYTRA("Elytra");

        private final String label;

        Mode(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private final ModeProperty<Mode> mode = new ModeProperty<>("Mode", Mode.SPOOF);
    private final BooleanProperty noGround = new BooleanProperty("No Ground", false).hideIf(() -> mode.getValue() != Mode.SPOOF);

    private final BooleanProperty pickupWater = new BooleanProperty("Pickup Water", true)
            .hideIf(() -> mode.getValue() != Mode.LEGIT);
    private final BooleanProperty silentAim = new BooleanProperty("Silent Aim", true)
            .hideIf(() -> mode.getValue() != Mode.LEGIT);
    private final BooleanProperty switchToItem = new BooleanProperty("Switch To Item", true)
            .hideIf(() -> mode.getValue() != Mode.LEGIT);

    private final BooleanProperty grim19MayFlagAnticheat = new BooleanProperty("Newest Grim, may flag the anticheat", false)
            .hideIf(() -> mode.getValue() != Mode.GRIM_SERVER_19);

    private static final long PLACE_DELAY_MS = 500L;

    private static final long PICKUP_WAIT_MS = 150L;

    private static final float FALL_THRESHOLD = 3.3f;

    private long lastPlaceTime;
    private boolean shouldPickup;
    private int slotBeforeSwitch = -1;

    private double fallDistance;

    private final BlockHolder inboundHolder = new BlockHolder(NetworkDirection.INBOUND);
    private final BlockHolder outboundHolder = new BlockHolder(NetworkDirection.OUTBOUND);
    private Vec3 prevMotion;
    private Vec3 nextPos;
    private boolean blocked;

    private boolean grim19ShouldNoFall;
    private boolean grim19ShouldJump;

    private boolean matrixPendingGroundSpoof;

    private NoFallModule() {
        super("No Fall", "Removes your player's fall damage", ModuleCategory.UTILITY);
        addProperties(mode, noGround, pickupWater, silentAim, switchToItem, grim19MayFlagAnticheat);
    }

    @Override
    public String getSuffix() {
        return mode.getValue().toString();
    }

    @Override
    protected void onEnable() {
        fallDistance = 0;
    }

    @Override
    protected void onDisable() {
        inboundHolder.release();
        outboundHolder.release();
        blocked = false;
        nextPos = null;
        prevMotion = null;
        grim19ShouldNoFall = false;
        grim19ShouldJump = false;
        matrixPendingGroundSpoof = false;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            KeyMappingUtility.release(Minecraft.getInstance().options.keyJump);
        }
    }

    public void syncFallDifference() {
        LocalPlayer player = Minecraft.getInstance().player;
        fallDistance = player.fallDistance;
    }

    public double getFallDifference() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player.getAbilities().flying) {
            return 0;
        }
        return player.fallDistance - fallDistance;
    }

    @Subscribe
    public void onPreMovementPacket(PreMovementPacketEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player.fallDistance == 0) {
            syncFallDifference();
        }

        switch (mode.getValue()) {
            case SPOOF -> {
                if (noGround.getValue()) {
                    event.setOnGround(false);
                } else if (getFallDifference() >= PlayerUtility.getMaxFallDistance(player)) {
                    syncFallDifference();
                    event.setOnGround(true);
                }
            }
            case WATCHDOG -> {
                if (prevMotion == null) {
                    return;
                }
                LocalPlayerInvoker accessor = (LocalPlayerInvoker) player;
                double diffX = event.getX() - accessor.aerial$getLastX();
                double diffY = event.getY() - accessor.aerial$getLastY();
                double diffZ = event.getZ() - accessor.aerial$getLastZ();
                boolean moved = diffX * diffX + diffY * diffY + diffZ * diffZ > 2.0E-4 * 2.0E-4;
                if (!moved) {
                    if (accessor.aerial$getPositionReminder() >= 20) {
                        accessor.aerial$setPositionReminder(18);
                    }
                    event.setOnGround(true);
                    syncFallDifference();
                    block();
                }
                player.setDeltaMovement(prevMotion);
                prevMotion = null;
            }
        }
    }

    @Subscribe
    public void onPostMovementPacket(PostMovementPacketEvent event) {
        if (mode.getValue() != Mode.WATCHDOG) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (nextPos != null) {
            Vec3 saved = nextPos;
            nextPos = player.position();
            player.setPos(saved.x, saved.y, saved.z);
        }

        if (blocked) {
            blocked = false;
        } else {
            release();
        }
    }

    @Subscribe
    public void onPreMove(PreMoveEvent event) {
        if (mode.getValue() != Mode.WATCHDOG) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;

        if (nextPos != null) {
            player.setPos(nextPos.x, nextPos.y, nextPos.z);
            nextPos = null;
            return;
        }

        HypixelServer.ModAPI.Location location = HypixelServer.ModAPI.get().getCurrentLocation();
        if (HypixelServer.isCurrent() && location != null
                && (location.isLobby() || location.serverType() == GameType.PIT
                        || location.serverType() == GameType.WOOL_GAMES
                        || location.serverType() == GameType.MURDER_MYSTERY)) {
            return;
        }

        if (isGoingToFall(player)) {
            return;
        }

        double predictedFallDistance = getFallDifference() - (player.getDeltaMovement().y - 0.08) * 0.98;
        if (predictedFallDistance >= PlayerUtility.getMaxFallDistance(player)) {
            prevMotion = player.getDeltaMovement();
        }
    }

    @Subscribe
    public void onPostMove(PostMoveEvent event) {
        if (mode.getValue() != Mode.WATCHDOG || prevMotion == null) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        Vec3 velocity = player.getDeltaMovement().scale(0.5);
        if (PlayerUtility.isBoxEmpty(player.level(), player.getBoundingBox().move(velocity.x, velocity.y, velocity.z))) {
            nextPos = player.position().add(velocity);
        }
        player.setDeltaMovement(Vec3.ZERO);
    }

    private boolean isGoingToFall(LocalPlayer player) {
        if (player.onGround()) {
            return true;
        }
        if (!PlayerUtility.isOverVoid(player.level(), player.getBoundingBox())) {
            return false;
        }
        PlayerSimulation simulation = new PlayerSimulation(player);
        for (int i = 0; i < 14; i++) {
            simulation.simulateTick();
            AABB simulatedBox = simulation.getSimulatedEntity().getBoundingBox();
            if (!PlayerUtility.isOverVoid(player.level(), simulatedBox)) {
                return false;
            }
        }
        return true;
    }

    private void block() {
        inboundHolder.block();
        outboundHolder.block();
        blocked = true;
    }

    private void release() {
        inboundHolder.release();
        outboundHolder.release();
    }

    private static boolean isFalling(LocalPlayer player) {
        return !player.onGround() && player.fallDistance >= FALL_THRESHOLD;
    }

    private static int findWaterBucketSlot(LocalPlayer player) {
        for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
            if (player.getInventory().getItem(slot).is(Items.WATER_BUCKET)) {
                return slot;
            }
        }
        return -1;
    }

    private void useHeldItem(Minecraft mc, LocalPlayer player) {
        if (mc.gameMode == null) {
            return;
        }
        if (!silentAim.getValue()) {
            mc.gameMode.useItem(player, InteractionHand.MAIN_HAND);
            return;
        }
        float realPitch = player.getXRot();
        RotationUtility.setRotationSilently(player, player.getYRot(), 90.0f);
        try {
            mc.gameMode.useItem(player, InteractionHand.MAIN_HAND);
        } finally {
            RotationUtility.setRotationSilently(player, player.getYRot(), realPitch);
        }
    }

    @Subscribe
    public void onLegitTick(PreGameTickEvent event) {
        if (mode.getValue() != Mode.LEGIT) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || player.getAbilities().flying
                || player.getAbilities().instabuild) {
            return;
        }
        long now = System.currentTimeMillis();

        if (shouldPickup && now - lastPlaceTime > PICKUP_WAIT_MS
                && player.getMainHandItem().is(Items.BUCKET)) {
            shouldPickup = false;
            useHeldItem(mc, player);
            if (slotBeforeSwitch != -1) {
                player.getInventory().setSelectedSlot(slotBeforeSwitch);
                slotBeforeSwitch = -1;
            }
            return;
        }

        if (!isFalling(player) || now - lastPlaceTime < PLACE_DELAY_MS) {
            return;
        }

        float pitch = silentAim.getValue() ? 90.0f : player.getXRot();
        BlockHitResult hit = RaycastUtility.rayTraceBlock(player.getYRot(), pitch, player.blockInteractionRange());
        if (hit == null || hit.getType() != HitResult.Type.BLOCK || hit.getDirection() != Direction.UP) {
            return;
        }
        if (!silentAim.getValue() && player.getXRot() < 80.0f) {
            return;
        }
        if (!player.getMainHandItem().is(Items.WATER_BUCKET)) {
            if (!switchToItem.getValue()) {
                return;
            }
            int slot = findWaterBucketSlot(player);
            if (slot == -1) {
                return;
            }
            slotBeforeSwitch = player.getInventory().getSelectedSlot();
            player.getInventory().setSelectedSlot(slot);
        }

        lastPlaceTime = now;
        useHeldItem(mc, player);
        shouldPickup = pickupWater.getValue();
        if (!shouldPickup) {
            slotBeforeSwitch = -1;
        }
    }

    @Subscribe(priority = -5)
    public void onLegitMovementPacket(PreMovementPacketEvent event) {
        if (mode.getValue() != Mode.LEGIT || !silentAim.getValue()) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || findWaterBucketSlot(player) == -1) {
            return;
        }
        boolean placing = System.currentTimeMillis() - lastPlaceTime < PLACE_DELAY_MS;
        if (isFalling(player) || placing) {
            event.setPitch(90.0f);
        }
    }

    @Subscribe
    public void onGrim19Tick(PreGameTickEvent event) {
        if (mode.getValue() != Mode.GRIM_SERVER_19) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || player.horizontalCollision) {
            return;
        }
        if (player.getDeltaMovement().y > 0.1) {
            grim19ShouldNoFall = false;
        }
        if (player.fallDistance > 3.0) {
            grim19ShouldNoFall = true;
        }
        if (grim19ShouldNoFall) {
            KeyMappingUtility.release(Minecraft.getInstance().options.keyJump);
            if (player.onGround()) {
                player.resetFallDistance();
            }
        }
    }

    @Subscribe
    public void onGrim19MovementPacket(PreMovementPacketEvent event) {
        if (mode.getValue() != Mode.GRIM_SERVER_19 || !grim19ShouldNoFall) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !player.onGround()) {
            return;
        }
        event.setOnGround(true);
        if (grim19MayFlagAnticheat.getValue()) {
            event.setY(event.getY() + 0.01);
        }
        grim19ShouldNoFall = false;
    }

    @Subscribe
    public void onGrim19ReceivePacket(ReceivePacketEvent event) {
        if (mode.getValue() != Mode.GRIM_SERVER_19) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || player.tickCount < 10 || player.horizontalCollision) {
            return;
        }
        if (!(event.getPacket() instanceof ClientboundSetEntityMotionPacket velocity) || event.isCancelled()) {
            return;
        }
        if (velocity.id() != player.getId()) {
            return;
        }
        if (velocity.movement().y > 0 || player.invulnerableTime <= 14 || GroundTickTracker.getGroundTicks() <= 1) {
            grim19ShouldJump = true;
        }
    }

    @Subscribe
    public void onGrim19MoveInput(MoveInputEvent event) {
        if (mode.getValue() != Mode.GRIM_SERVER_19) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || player.horizontalCollision) {
            return;
        }
        if (grim19ShouldJump && grim19ShouldNoFall) {
            event.setJump(true);
            grim19ShouldJump = false;
        }
    }

    @Subscribe
    public void onMatrixTick(PreGameTickEvent event) {
        if (mode.getValue() != Mode.MATRIX) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        Vec3 motion = player.getDeltaMovement();
        if (Math.round(player.fallDistance) - motion.y > 3.0) {
            player.setDeltaMovement(motion.x * 0.1, 0.0, motion.z * 0.1);
            player.resetFallDistance();
            matrixPendingGroundSpoof = true;
        }
    }

    @Subscribe
    public void onMatrixMovementPacket(PreMovementPacketEvent event) {
        if (mode.getValue() != Mode.MATRIX || !matrixPendingGroundSpoof) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientPacketListener connection = mc.getConnection();
        if (connection != null) {
            connection.send(new ServerboundMovePlayerPacket.StatusOnly(true, event.isHorizontalCollision()));
        }
        event.setOnGround(false);
        matrixPendingGroundSpoof = false;
    }

    @Subscribe
    public void onElytraPostMove(PostMoveEvent event) {
        if (mode.getValue() != Mode.ELYTRA) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || player.fallDistance <= 2.5) {
            return;
        }
        BlockPos below = BlockPos.containing(player.getX(), player.getY() - 1.0, player.getZ());
        if (mc.level.getBlockState(below).canBeReplaced()) {
            return;
        }
        boolean jumpWasDown = mc.options.keyJump.isDown();
        KeyMappingUtility.press(mc.options.keyJump);
        ClientPacketListener connection = mc.getConnection();
        if (connection != null) {
            connection.send(new ServerboundPlayerCommandPacket(player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
        }
        player.jumpFromGround();
        if (jumpWasDown) {
            KeyMappingUtility.press(mc.options.keyJump);
        } else {
            KeyMappingUtility.release(mc.options.keyJump);
        }
    }
}
