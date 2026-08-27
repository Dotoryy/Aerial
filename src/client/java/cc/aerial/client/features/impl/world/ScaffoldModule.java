package cc.aerial.client.features.impl.world;

import cc.aerial.client.event.impl.game.PreGameTickEvent;
import cc.aerial.client.event.impl.game.player.interaction.ItemUseEvent;
import cc.aerial.client.event.impl.game.input.MoveInputEvent;
import cc.aerial.client.event.impl.game.player.movement.PostMovementPacketEvent;
import cc.aerial.client.event.impl.game.player.movement.PreMovementPacketEvent;
import cc.aerial.client.event.subscriber.Subscribe;
import cc.aerial.client.features.Module;
import cc.aerial.client.features.impl.movement.MovementFixModule;
import cc.aerial.client.features.ModuleCategory;
import cc.aerial.client.property.BooleanProperty;
import cc.aerial.client.property.ModeProperty;
import cc.aerial.client.property.NumberProperty;
import cc.aerial.client.rotation.RaycastUtility;
import cc.aerial.client.rotation.RotationUtility;
import cc.aerial.client.rotation.ServerRotation;
import cc.aerial.client.scaffold.PlacementExecutor;
import cc.aerial.client.scaffold.ScaffoldDebug;
import cc.aerial.client.scaffold.PlacementSearch;
import cc.aerial.client.scaffold.ScaffoldRotations;
import cc.aerial.client.utility.ScaffoldBlockFilter;
import cc.aerial.client.utility.TeleportTickTracker;
import cc.aerial.client.utility.GroundTickTracker;
import cc.aerial.client.utility.MoveUtility;
import com.mojang.blaze3d.platform.InputConstants;
import cc.aerial.client.mixin.KeyMappingAccessor;
import cc.aerial.client.property.ActionProperty;
import cc.aerial.client.property.GroupProperty;
import cc.aerial.client.property.Property;
import cc.aerial.client.screen.ScaffoldTellyScreen;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.client.KeyMapping;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import cc.aerial.client.event.impl.game.player.movement.StrafeEvent;
import cc.aerial.client.event.impl.game.packet.ReceivePacketEvent;
import cc.aerial.client.event.impl.game.player.movement.JumpEvent;
import cc.aerial.client.scaffold.WatchdogJumpSprintLogic;
import cc.aerial.client.scaffold.WatchdogTowerLogic;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import cc.aerial.client.features.impl.movement.SpeedModule;
import cc.aerial.client.utility.KeyMappingUtility;
import cc.aerial.client.event.impl.game.packet.SendPacketEvent;
import cc.aerial.client.mixin.LivingEntityAccessor;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

public final class ScaffoldModule extends Module {
    public static final ScaffoldModule INSTANCE = new ScaffoldModule();

    private final ModeProperty<Mode> mode = new ModeProperty<>("Mode", Mode.NORMAL);
    private final ModeProperty<RotationMode> rotationMode = new ModeProperty<>("Rotation Mode", RotationMode.NORMAL);
    private final ModeProperty<RayCast> rayCast = new ModeProperty<>("Ray Cast", RayCast.STRICT);

    private final BooleanProperty multiPlace = new BooleanProperty("Multi Place", true);

    private final ModeProperty<YawOffset> yawOffset = new ModeProperty<>("Yaw Offset", YawOffset.ZERO)
            .hideIf(() -> !this.advanced.getValue());
    private final ModeProperty<Sprint> sprint = new ModeProperty<>("Sprint", Sprint.NORMAL);
    private final ModeProperty<Tower> tower = new ModeProperty<>("Tower", Tower.OFF);
    private final ModeProperty<Downwards> downwards = new ModeProperty<>("Downwards", Downwards.OFF);
    private final ModeProperty<SameY> sameY = new ModeProperty<>("Same Y", SameY.OFF);
    private final NumberProperty expand = new NumberProperty("Expand", 0, 0, 4, 1);
    private final BooleanProperty movementCorrection = new BooleanProperty("Movement Correction", false);

    private final BooleanProperty advanced = new BooleanProperty("Advanced", false);

    private final BooleanProperty debug = new BooleanProperty("Debug", false)
            .hideIf(() -> !advanced.getValue());
    private final BooleanProperty upSideDown = new BooleanProperty("Up Side Down", false)
            .hideIf(() -> !advanced.getValue());

    private final BooleanProperty spoof = new BooleanProperty("Spoof", false);

    private final ModeProperty<SwingMode> swing = new ModeProperty<>("Swing", SwingMode.SWING);

    private final BooleanProperty newWatchdogRots = new BooleanProperty("New Watchdog Rots", false);
    private final BooleanProperty sneak = new BooleanProperty("Sneak", false);
    private final NumberProperty startSneaking = new NumberProperty("Start Sneaking", 0, 0, 5, 1);
    private final NumberProperty stopSneaking = new NumberProperty("Stop Sneaking", 0, 0, 5, 1);
    private final NumberProperty sneakEvery = new NumberProperty("Sneak every x blocks", 1, 1, 10, 1);
    private final NumberProperty sneakingSpeed = new NumberProperty("Sneaking Speed", 0.2, 0.2, 1, 0.05);

    private static final float NEW_WATCHDOG_STEP = 12.0f;

    private static final double NEW_WATCHDOG_SIDE_OFFSET = 0.49999999;
    private final BooleanProperty watchdogPrediction = new BooleanProperty("Watchdog Prediction", false);
    private final BooleanProperty ignoreSpeedEffect = new BooleanProperty("Ignore Speed Effect", false)
            .hideIf(() -> !advanced.getValue());
    private final BooleanProperty safeWalk = new BooleanProperty("Safe Walk", true);
    private final BooleanProperty bypassRaycastWhenFalling = new BooleanProperty("Bypass Ray Cast When Falling", false)
            .hideIf(() -> !advanced.getValue());
    private final NumberProperty timer = new NumberProperty("Timer", 1.0, 0.1, 10.0, 0.1);
    private final NumberProperty placeDelay = new NumberProperty("Place Delay", 0, 0, 5, 1);
    private final NumberProperty rotationSpeed = new NumberProperty("Rotation Speed", 5, 0, 10, 1);
    private final NumberProperty rotationSpeedMax = new NumberProperty("Rotation Speed Max", 10, 0, 10, 1);

    private final BooleanProperty tellyOnlyOnRightClick = new BooleanProperty("Telly Only on Right Click", false)
            .hideIf(() -> mode.getValue() != Mode.TELLY);

    private final BooleanProperty placeExtraBlock = new BooleanProperty("Place Extra Block", false)
            .hideIf(() -> mode.getValue() != Mode.TELLY);

    private final BooleanProperty randomExtraBlocks = new BooleanProperty("Random Extra Blocks", false)
            .hideIf(() -> mode.getValue() != Mode.TELLY);

    private final NumberProperty randomExtraChance = new NumberProperty("Random Extra Chance", 35, 0, 100, 5)
            .hideIf(() -> mode.getValue() != Mode.TELLY || !randomExtraBlocks.getValue());

    private final BooleanProperty extraBlockRotationPackets =
            new BooleanProperty("Extra Block Rotation Packets", false)
                    .hideIf(() -> mode.getValue() != Mode.TELLY
                            || (!placeExtraBlock.getValue() && !randomExtraBlocks.getValue()));

    private final BooleanProperty watchdogTelly = new BooleanProperty("Watchdog Telly", false);
    private final BooleanProperty visualBackRotations = new BooleanProperty("Visual Back Rotations", true)
            .hideIf(() -> !watchdogTelly.getValue());
    private final BooleanProperty blockDiagonalAscend = new BooleanProperty("Block Diagonal Ascend", true)
            .hideIf(() -> !watchdogTelly.getValue());
    private final BooleanProperty dontForceRaycastOnWatchdogTelly =
            new BooleanProperty("Don't force raycast on Watchdog Telly", false)
                    .hideIf(() -> !watchdogTelly.getValue());
    private final BooleanProperty extendBlockReachOnWatchdogTelly =
            new BooleanProperty("Extend Block Reach on Watchdog Telly", true)
                    .hideIf(() -> !watchdogTelly.getValue());
    private final NumberProperty watchdogTellyJumpDelay =
            new NumberProperty("Watchdog Telly Jump Delay", 0, 0, 5, 1)
                    .hideIf(() -> !watchdogTelly.getValue());

    private final NumberProperty watchdogTellyJumpDelayMax =
            new NumberProperty("Watchdog Telly Jump Delay Max", 0, 0, 5, 1)
                    .hideIf(() -> !watchdogTelly.getValue());
    private final BooleanProperty disableOnFlag = new BooleanProperty("Disable On Flag", true)
            .hideIf(() -> !watchdogTelly.getValue());
    private final NumberProperty watchdogTellyRotationSpeed =
            new NumberProperty("Watchdog Telly Rotation Speed", 35, 0, 180, 1)
                    .hideIf(() -> !watchdogTelly.getValue());
    private final NumberProperty watchdogTellyRotationSpeedMax =
            new NumberProperty("Watchdog Telly Rotation Speed Max", 38, 0, 180, 1)
                    .hideIf(() -> !watchdogTelly.getValue());
    private final BooleanProperty rotationBlockBoost = new BooleanProperty("Rotation Block Boost", false)
            .hideIf(() -> !watchdogTelly.getValue());
    private final NumberProperty rotationBlockRotationSpeed =
            new NumberProperty("Rotation Block Rotation Speed", 122, 0, 180, 1)
                    .hideIf(() -> !watchdogTelly.getValue() || !rotationBlockBoost.getValue());
    private final NumberProperty rotationBlockRotationSpeedMax =
            new NumberProperty("Rotation Block Rotation Speed Max", 128, 0, 180, 1)
                    .hideIf(() -> !watchdogTelly.getValue() || !rotationBlockBoost.getValue());
    private final BooleanProperty boostOnlyWhileHoldingJump =
            new BooleanProperty("Boost Only While Holding Jump", false)
                    .hideIf(() -> !watchdogTelly.getValue() || !rotationBlockBoost.getValue());

    private final Property<?>[] tellyProperties = {
            tellyOnlyOnRightClick, placeExtraBlock, randomExtraBlocks, randomExtraChance,
            extraBlockRotationPackets,
            watchdogTelly, visualBackRotations, blockDiagonalAscend,
            dontForceRaycastOnWatchdogTelly, extendBlockReachOnWatchdogTelly, watchdogTellyJumpDelay,
            watchdogTellyJumpDelayMax,
            disableOnFlag, watchdogTellyRotationSpeed, watchdogTellyRotationSpeedMax,
            rotationBlockBoost, rotationBlockRotationSpeed, rotationBlockRotationSpeedMax,
            boostOnlyWhileHoldingJump
    };

    private final GroupProperty tellyGroup =
            new GroupProperty("Telly", tellyProperties).hideIf(() -> true);

    private final ActionProperty tellySettings = new ActionProperty("Telly Settings",
            () -> Minecraft.getInstance().setScreenAndShow(
                    new ScaffoldTellyScreen(Minecraft.getInstance().gui.screen(), tellyProperties)))
            .hideIf(() -> mode.getValue() != Mode.TELLY);

    private float targetYaw;
    private float targetPitch;

    private float currentYaw;
    private float currentPitch;

    private Vec3 targetBlock;
    private BlockPos blockFace;
    private Direction facing;

    private boolean reusedLastFacing;
    private Direction lastFacing;
    private Vec3 lastOffset;

    private Vec3 placeOffset = Vec3.ZERO;

    private boolean strictPlacing;

    private float prevSentYaw;
    private float prevSentPitch;
    private boolean sentRotationPrimed;

    private float visualYaw;
    private float visualPitch;
    private float prevVisualYaw;
    private float prevVisualPitch;
    private boolean visualRotationsActive;

    private boolean rotationBoostPending;

    private Float rotationSpeedOverride;

    private boolean rotationHeld;

    private int tellyTicks;
    private int tellyJumpDelay;
    private int flagDisableTicks;
    private int lastFlagTick = -1;

    private boolean readyToPlace;

    private int previousSlot = -1;

    private ItemStack spoofDisplayStack = ItemStack.EMPTY;
    private double startY;

    private boolean watchdogSprintSuppressSafeWalk;

    private Vec3 towerRequestedOffset;
    private boolean towerRequestedAim;
    private float towerRequestedYaw;
    private float towerRequestedPitch;

    private int ticksOnAir;

    private int sneakingTicks = -1;

    private int blocksUntilSneak;

    private ScaffoldModule() {
        super("Scaffold", "Places blocks beneath you", ModuleCategory.WORLD);
        addProperties(mode, tellySettings, tellyGroup, rotationMode, rayCast, multiPlace, yawOffset, sprint, tower, downwards, sameY, expand, movementCorrection, newWatchdogRots, watchdogPrediction, sneak, startSneaking, stopSneaking, sneakEvery, sneakingSpeed, advanced, debug, ignoreSpeedEffect, upSideDown, spoof, swing, safeWalk, bypassRaycastWhenFalling, timer, placeDelay, rotationSpeed, rotationSpeedMax);
    }

    @Override
    public String getSuffix() {
        if (watchdogTelly.getValue()) {
            return "Watchdog Keep-Y";
        }
        return getDisplayName();
    }

    public String getDisplayName() {
        updateFlagState();
        Mode selected = mode.getValue();
        if (selected == Mode.TELLY) {
            if (isDisabledByFlag()) {
                return Mode.NORMAL.toString();
            }
            if (tellyOnlyOnRightClick.getValue()) {
                return Minecraft.getInstance().options.keyUse.isDown()
                        ? Mode.TELLY.toString() : Mode.NORMAL.toString();
            }
        }
        return selected.toString();
    }

    @Subscribe
    public void onItemUse(ItemUseEvent event) {
        if (isEnabled() && tellyOnlyOnRightClick.getValue() && rayCast.getValue() != RayCast.STRICT) {
            event.setCancelled();
        }
    }

    public boolean wantsVanillaJumpDelay() {
        return isEnabled()
                && getDisplayName().equals(Mode.TELLY.toString())
                && Minecraft.getInstance().options.keyJump.isDown()
                && watchdogTelly.getValue()
                && watchdogTellyJumpDelayMax.getValue().intValue() > 0;
    }

    private void applyTimer() {
        float multiplier = (float) timer.getValue().doubleValue();
        if (multiplier != 1.0f
                && this.targetBlock != null && this.facing != null && this.blockFace != null) {
            TimerModule.request(multiplier);
        } else {
            TimerModule.clearRequest();
        }
    }

    private boolean isDisabledByFlag() {
        return watchdogTelly.getValue() && disableOnFlag.getValue() && this.flagDisableTicks > 0;
    }

    private void updateFlagState() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !watchdogTelly.getValue() || !disableOnFlag.getValue()) {
            this.flagDisableTicks = 0;
            this.lastFlagTick = -1;
            return;
        }
        if (TeleportTickTracker.getTicksSinceTeleport() == 1 && this.lastFlagTick != player.tickCount) {
            this.flagDisableTicks = 10;
            this.lastFlagTick = player.tickCount;
        }
    }

    @Override
    protected void onEnable() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }

        this.targetYaw = player.getYRot() - 180.0f + yawOffset.getValue().degrees();
        this.targetPitch = 90.0f;

        this.tellyTicks = 0;
        this.rotationHeld = false;

        this.tellyJumpDelay = drawTellyJumpDelay();
        this.currentYaw = player.getYRot();
        this.currentPitch = player.getXRot();
        this.startY = Math.floor(player.getY());
        this.targetBlock = null;
        this.blockFace = null;
        this.facing = null;
        this.lastFacing = null;
        this.lastOffset = null;
        this.readyToPlace = false;
        this.ticksOnAir = 0;
        this.sentRotationPrimed = false;
        this.sneakingTicks = -1;
        this.blocksUntilSneak = 0;
        ScaffoldDebug.enabled = debug.getValue();
        if (tower.getValue() == Tower.WATCHDOG) {
            WatchdogTowerLogic.onEnable();
        }
        if (sprint.getValue() == Sprint.WATCHDOG_JUMP) {
            WatchdogJumpSprintLogic.onEnable(sameY.getValue() == SameY.AUTO_JUMP);
        }
    }

    @Override
    protected void onDisable() {
        TimerModule.clearRequest();
        restoreSlot();

        KeyMappingUtility.release(Minecraft.getInstance().options.keyShift);
        this.sneakingTicks = -1;
        this.readyToPlace = false;
        this.blockFace = null;
        this.facing = null;
        WatchdogTowerLogic.onDisable();
        WatchdogJumpSprintLogic.onDisable();
    }

    @Subscribe(priority = 1)
    public void onWatchdogScaffoldPreGameTick(PreGameTickEvent event) {
        this.towerRequestedOffset = null;
        this.towerRequestedAim = false;
        if (tower.getValue() == Tower.WATCHDOG) {
            WatchdogTowerLogic.onPreGameTick(this::aimAtSideFaceForTower);
        }
        if (sprint.getValue() == Sprint.WATCHDOG_JUMP) {
            this.watchdogSprintSuppressSafeWalk = WatchdogJumpSprintLogic.onPreGameTick(
                    sameY.getValue() == SameY.AUTO_JUMP, this::reanchorAfterJump);
        } else {
            this.watchdogSprintSuppressSafeWalk = false;
        }
    }

    @Subscribe
    public void onPreGameTick(PreGameTickEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return;
        }

        this.readyToPlace = false;
        maintainStartY(player);
        updateTicksOnAir(player);
        updateTellyState(player);
        tickAutoJump(player);

        int slot = findBlockSlot(player);
        if (slot < 0) {
            restoreSlot();
            tickRotationWithoutTarget(player);
            return;
        }

        this.placeOffset = Vec3.ZERO;
        if (downwards.getValue() == Downwards.NORMAL && isSneakKeyRawlyDown()) {
            this.placeOffset = this.placeOffset.add(0.0, -1.0, 0.0);
        }

        if (upSideDown.getValue()) {
            this.placeOffset = new Vec3(this.placeOffset.x, 3.0, this.placeOffset.z);
        }
        applyExpand();

        if (this.towerRequestedOffset != null) {
            this.placeOffset = this.towerRequestedOffset;
        }

        this.targetBlock = PlacementSearch.findTarget(
                this.placeOffset.x, this.placeOffset.y, this.placeOffset.z, requiredY());
        if (this.targetBlock == null) {
            tickRotationWithoutTarget(player);
            return;
        }

        this.reusedLastFacing = false;
        boolean allowDown = this.placeOffset.y < 0.0;
        PlacementSearch.FacingOffset found =
                PlacementSearch.findFace(this.targetBlock, this.targetYaw, allowDown);

        if (found != null && found.facing() != Direction.UP && isFallingFast(player)) {
            PlacementSearch.FacingOffset upright =
                    PlacementSearch.findFace(this.targetBlock, this.targetYaw, false);
            if (upright != null && upright.facing() == Direction.UP) {
                found = upright;
            }
        }

        if (found == null) {
            found = PlacementSearch.findFace(this.targetBlock, this.targetYaw, !allowDown);
        }

        if (found == null) {
            if (this.lastFacing == null || this.lastOffset == null) {
                this.facing = null;
                return;
            }
            found = new PlacementSearch.FacingOffset(this.lastFacing, this.lastOffset);
            this.reusedLastFacing = true;
        }
        this.facing = found.facing();
        this.lastFacing = found.facing();
        this.lastOffset = found.offset();

        this.blockFace = BlockPos.containing(
                this.targetBlock.x + found.offset().x,
                this.targetBlock.y + found.offset().y,
                this.targetBlock.z + found.offset().z);

        int delay = watchdogPrediction.getValue()
                ? (GroundTickTracker.getAirTicks() >= 2 && !(Math.random() > 0.3) ? 1 : 0)
                : (isFallingFast(player) ? 0 : (int) placeDelay.getValue().doubleValue());
        this.readyToPlace = this.ticksOnAir > delay;

        calculateSneaking(player);

        boolean reaimed;
        if (getDisplayName().equals(Mode.TELLY.toString())) {
            reaimed = aimTelly(player);
        } else if (newWatchdogRots.getValue() && wantsNewWatchdogRotations()) {
            applyNewWatchdogRotations(player);
            reaimed = true;
        } else {
            reaimed = this.readyToPlace
                    && !Minecraft.getInstance().options.keyPickItem.isDown()
                    && !crosshairOnFace(true);
            if (reaimed) {
                Vec2 rotation = ScaffoldRotations.computeNormalRotation(this.blockFace, this.facing,
                        this.targetBlock, this.currentYaw, this.currentPitch,
                        yawOffset.getValue().degrees(), player.blockInteractionRange(),
                        this.targetYaw, this.targetPitch);
                this.targetYaw = rotation.x;
                this.targetPitch = rotation.y;
            }
        }
        if (this.towerRequestedAim) {
            this.targetYaw = this.towerRequestedYaw;
            this.targetPitch = this.towerRequestedPitch;
        }
        stepRotation();

        ScaffoldDebug.tick(this.targetBlock, this.blockFace, this.facing,
                this.currentYaw, this.currentPitch, this.ticksOnAir, this.readyToPlace,
                reaimed, crosshairOnFace(true), crosshairOnFace(false));

        boolean placedThisTick = false;
        if (shouldPlace()) {
            selectBlockSlot(player, slot);

            runRandomExtraPlace(player);
            Vec3 hitVec = PlacementExecutor.hitVec(this.blockFace, this.facing,
                    this.currentYaw, this.currentPitch);
            if (rotationMode.getValue() == RotationMode.GRIM) {
                boolean ok = PlacementExecutor.placeGrim(this.blockFace, this.facing, hitVec,
                        this.currentYaw, this.currentPitch);
                placedThisTick = ok;
                ScaffoldDebug.placed(this.blockFace, this.facing, ok, "grim");
            } else if (rayCast.getValue() == RayCast.STRICT) {
                BlockHitResult aimed = RaycastUtility.rayTraceBlock(this.currentYaw, this.currentPitch,
                        player.blockInteractionRange());
                if (aimed != null) {
                    Minecraft.getInstance().hitResult = aimed;

                    this.strictPlacing = true;
                    try {
                        PlacementExecutor.placeStrict();
                    } finally {
                        this.strictPlacing = false;
                    }
                    ScaffoldDebug.placed(this.blockFace, this.facing, true, "strict");
                    placedThisTick = true;
                    runMultiPlace(player);
                } else {
                    ScaffoldDebug.placed(this.blockFace, this.facing, false, "strict-miss");
                }
            } else {
                boolean ok = PlacementExecutor.place(this.blockFace, this.facing, hitVec);
                placedThisTick = ok;
                ScaffoldDebug.placed(this.blockFace, this.facing, ok, "normal");
                if (ok) {
                    runMultiPlace(player);
                }
            }
        }

        if (placedThisTick && isDisabledByFlag()) {
            this.flagDisableTicks--;
        }

        if (!placedThisTick) {
            runExtraTellyPlace(player);
        }

        runStrictTopFaceClick(player);
    }

    private void runStrictTopFaceClick(LocalPlayer player) {
        if (rayCast.getValue() != RayCast.STRICT || this.blockFace == null) {
            return;
        }
        if (!(Math.random() > 0.3)) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }

        BlockHitResult hit = RaycastUtility.rayTraceBlock(this.currentYaw, this.currentPitch,
                player.blockInteractionRange());
        if (hit == null
                || hit.getType() != HitResult.Type.BLOCK
                || !hit.getBlockPos().equals(this.blockFace)
                || hit.getDirection() != Direction.UP) {
            return;
        }
        if (level.getBlockState(player.blockPosition().below()).isAir()) {
            return;
        }

        minecraft.hitResult = hit;

        this.strictPlacing = true;
        try {
            PlacementExecutor.placeStrict();
        } finally {
            this.strictPlacing = false;
        }
    }

    private void runExtraTellyPlace(LocalPlayer player) {
        if (!placeExtraBlock.getValue() || mode.getValue() != Mode.TELLY || player.onGround()) {
            return;
        }

        int nextBlockY = (int) Math.floor(player.getY() + player.getDeltaMovement().y);
        if (nextBlockY > this.startY || player.getY() <= this.startY + 1.0) {
            return;
        }

        int extraY = (int) Math.floor(player.getY()) + (int) this.placeOffset.y;
        placeExtraLayer(player, extraY, player.blockInteractionRange());
    }

    private void runRandomExtraPlace(LocalPlayer player) {
        if (!randomExtraBlocks.getValue() || mode.getValue() != Mode.TELLY || player.onGround()) {
            return;
        }
        if (Math.random() * 100.0 >= randomExtraChance.getValue().doubleValue()) {
            return;
        }

        int base = (int) Math.floor(this.startY) + (int) this.placeOffset.y;
        double reach = player.blockInteractionRange();
        for (int layer = 1; layer <= MAX_RANDOM_EXTRA; layer++) {
            if (!placeExtraLayer(player, base + layer, reach)) {
                return;
            }
        }
    }

    private static final int MAX_RANDOM_EXTRA = 3;

    private boolean placeExtraLayer(LocalPlayer player, int layerY, double reach) {
        Vec3 target = PlacementSearch.findTarget(
                this.placeOffset.x, this.placeOffset.y, this.placeOffset.z, layerY);
        if (target == null) {
            return false;
        }
        if (player.getBoundingBox().intersects(new AABB(BlockPos.containing(target)))) {
            return false;
        }
        PlacementSearch.FacingOffset found =
                PlacementSearch.findFace(target, this.targetYaw, this.placeOffset.y < 0.0);
        if (found == null) {
            return false;
        }
        Direction face = found.facing();
        BlockPos pos = BlockPos.containing(target.x + found.offset().x,
                target.y + found.offset().y, target.z + found.offset().z);
        boolean ok = placeVerified(player, pos, face, reach,
                extraBlockRotationPackets.getValue());
        ScaffoldDebug.placed(pos, face, ok, "extra");
        return ok;
    }

    private void runMultiPlace(LocalPlayer player) {
        if (!multiPlace.getValue()) {
            return;
        }
        double reach = player.blockInteractionRange();
        for (int i = 0; i < 3; i++) {
            Vec3 target = PlacementSearch.findTarget(
                    this.placeOffset.x, this.placeOffset.y, this.placeOffset.z, requiredY());
            if (target == null) {
                return;
            }
            PlacementSearch.FacingOffset found =
                    PlacementSearch.findFace(target, this.targetYaw, this.placeOffset.y < 0.0);
            if (found == null) {
                return;
            }
            Direction face = found.facing();
            BlockPos pos = BlockPos.containing(target.x + found.offset().x,
                    target.y + found.offset().y, target.z + found.offset().z);

            if (!placeVerified(player, pos, face, reach, false)) {
                return;
            }
        }
    }

    private boolean placeVerified(LocalPlayer player, BlockPos pos, Direction face, double reach,
                                  boolean rotationPackets) {
        BlockHitResult direct = RaycastUtility.rayTraceBlock(this.currentYaw, this.currentPitch, reach);
        if (hits(direct, pos, face)) {
            return PlacementExecutor.place(pos, face, direct.getLocation());
        }
        Vec2 rotation = ScaffoldRotations.stepRotationToPoint(
                ScaffoldRotations.faceClickVec(pos, face), player.getYRot(), player.getXRot());
        if (Math.abs(rotation.x - this.currentYaw) >= 120.0f
                || Math.abs(rotation.y - this.currentPitch) >= 60.0f) {
            return false;
        }
        BlockHitResult verified = RaycastUtility.rayTraceBlock(rotation.x, rotation.y, reach);
        if (!hits(verified, pos, face)) {
            return false;
        }

        if (rotationPackets) {
            return PlacementExecutor.placeGrim(pos, face, verified.getLocation(),
                    rotation.x, rotation.y);
        }
        return PlacementExecutor.place(pos, face, verified.getLocation());
    }

    private static boolean hits(BlockHitResult hit, BlockPos pos, Direction facing) {
        return hit != null && hit.getType() == HitResult.Type.BLOCK
                && hit.getBlockPos().equals(pos) && hit.getDirection() == facing;
    }

    private void selectBlockSlot(LocalPlayer player, int slot) {
        Inventory inventory = player.getInventory();
        if (inventory.getSelectedSlot() == slot) {
            return;
        }
        if (this.previousSlot == -1) {
            this.previousSlot = inventory.getSelectedSlot();
        }

        if (this.spoof.getValue() && this.spoofDisplayStack.isEmpty()) {
            this.spoofDisplayStack = inventory.getItem(inventory.getSelectedSlot()).copy();
        }
        inventory.setSelectedSlot(slot);
    }

    public boolean shouldSilenceVanillaSwing() {
        return isEnabled() && this.strictPlacing && swing.getValue() == SwingMode.SILENT;
    }

    public void performSwing(LocalPlayer player) {
        if (swing.getValue() == SwingMode.SWING) {
            player.swing(InteractionHand.MAIN_HAND);
            return;
        }
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection != null) {
            connection.send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
        }
    }

    public ItemStack getSpoofDisplayStack() {
        return this.spoofDisplayStack;
    }

    private void restoreSlot() {
        this.spoofDisplayStack = ItemStack.EMPTY;
        if (this.previousSlot == -1) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            player.getInventory().setSelectedSlot(this.previousSlot);
        }
        this.previousSlot = -1;
    }

    @Subscribe
    public void onPreMovementPacket(PreMovementPacketEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;

        if (player == null) {
            TimerModule.clearRequest();
            return;
        }
        applyTimer();
        if (this.blockFace == null || this.facing == null) {
            if (!this.rotationHeld || rotationMode.getValue() == RotationMode.GRIM) {
                this.rotationHeld = false;
                return;
            }
            this.targetYaw = player.getYRot();
            this.targetPitch = player.getXRot();
            stepRotation();
            if (Math.abs(Mth.wrapDegrees(this.currentYaw - player.getYRot())) < 1.0f
                    && Math.abs(this.currentPitch - player.getXRot()) < 1.0f) {
                this.rotationHeld = false;
                return;
            }
            event.setYaw(this.currentYaw);
            event.setPitch(this.currentPitch);
            return;
        }
        this.rotationHeld = true;
        if (rotationMode.getValue() == RotationMode.GRIM) {
            return;
        }

        event.setYaw(this.currentYaw);
        event.setPitch(this.currentPitch);

        if (this.sentRotationPrimed) {
            player.yHeadRotO = this.prevSentYaw;
        } else {
            player.yHeadRotO = this.currentYaw;
            this.sentRotationPrimed = true;
        }
        player.yHeadRot = this.currentYaw;
        this.prevSentYaw = this.currentYaw;
        this.prevSentPitch = this.currentPitch;
    }

    public float getModelYaw(float fallback) {
        if (!isEnabled() || this.blockFace == null || this.facing == null
                || rotationMode.getValue() == RotationMode.GRIM) {
            return fallback;
        }
        return this.currentYaw;
    }

    private boolean shouldPlace() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return false;
        }

        if (!this.readyToPlace) {
            ScaffoldDebug.gate(false, false, false, false, false, this.currentYaw, this.currentPitch);
            return false;
        }
        boolean off = rayCast.getValue() == RayCast.OFF;

        boolean falling = bypassRaycastWhenFalling.getValue()
                && player.getDeltaMovement().y < -0.1
                && GroundTickTracker.getAirTicks() > 3;
        boolean hit = crosshairOnFace(rayCast.getValue() == RayCast.STRICT);

        boolean reusedFace = this.reusedLastFacing && isMovingForLayerLock() && player.onGround();

        boolean grimAim = rotationMode.getValue() == RotationMode.GRIM && crosshairOnFaceAt(
                this.targetYaw, this.targetPitch, false);
        boolean result = off || falling || hit || reusedFace || grimAim;
        ScaffoldDebug.gate(true, off, falling, hit, result, this.currentYaw, this.currentPitch);
        return result;
    }

    private boolean wantsNewWatchdogRotations() {
        Minecraft mc = Minecraft.getInstance();
        return sprint.getValue() == Sprint.WATCHDOG_JUMP
                && (MoveUtility.isMoving() || !mc.options.keyJump.isDown());
    }

    private void applyNewWatchdogRotations(LocalPlayer player) {
        Vec3 centre = Vec3.atCenterOf(this.blockFace);

        float yaw = player.getYRot();
        double radians = Math.toRadians(yaw);
        Vec3 look = new Vec3(-Math.sin(radians), 0.0, Math.cos(radians)).normalize();

        Vec3 right = new Vec3(look.z, 0.0, -look.x).normalize();

        Vec3 aim = centre.add(right.scale(NEW_WATCHDOG_SIDE_OFFSET))
                .add(0.0, (Math.random() - 0.5) * 0.05, 0.0);
        Vec2 between = ScaffoldRotations.rotationsBetween(centre, aim);

        this.targetYaw = stepTowards(this.targetYaw, between.x, NEW_WATCHDOG_STEP);
        this.targetPitch = stepTowards(this.targetPitch, between.y, NEW_WATCHDOG_STEP);

        this.rotationSpeedOverride = 10.0f;

        Minecraft mc = Minecraft.getInstance();
        boolean strafing = mc.options.keyLeft.isDown() || mc.options.keyRight.isDown();
        float wrapped = Mth.wrapDegrees(player.getYRot());
        boolean nearAxis = Math.abs(wrapped % 90.0f) <= 10.0f || Math.abs(wrapped % 90.0f) >= 80.0f;
        if (strafing || (MoveUtility.enoughMovementForSprinting() && MoveUtility.getSpeed() >= 0.09 && !nearAxis)) {
            return;
        }

        boolean jumpHeld = mc.options.keyJump.isDown();
        double speed = MoveUtility.getSpeed();
        if (jumpHeld) {
            this.targetYaw -= 10.0f;
        } else if (speed < 0.21 || !player.onGround()) {
            this.targetYaw -= 30.0f;
        } else if (speed < 0.3) {
            this.targetYaw -= 45.0f;
        }
    }

    private void calculateSneaking(LocalPlayer player) {
        KeyMapping sneakKey = Minecraft.getInstance().options.keyShift;
        if (downwards.getValue() != Downwards.WATCHDOG && this.ticksOnAir == 0) {
            KeyMappingUtility.release(sneakKey);
        }

        this.sneakingTicks--;
        if (!sneak.getValue()) {
            return;
        }

        int start = (int) startSneaking.getValue().doubleValue();
        int hold = (int) placeDelay.getValue().doubleValue();
        int stop = (int) stopSneaking.getValue().doubleValue();

        if (this.sneakingTicks >= 0) {
            KeyMappingUtility.press(sneakKey);
            return;
        }

        if (this.ticksOnAir > 0) {
            this.sneakingTicks = stop;
        }

        Vec3 motion = player.getDeltaMovement();
        boolean airAhead = player.level()
                .getBlockState(BlockPos.containing(
                        player.getX() + motion.x * start,
                        player.getY() - 0.0784000015258789,
                        player.getZ() + motion.z * start))
                .isAir();
        if ((this.ticksOnAir > 0 || airAhead) && this.blocksUntilSneak <= 0) {
            this.sneakingTicks = start + hold + stop;
            this.blocksUntilSneak = (int) sneakEvery.getValue().doubleValue();
        }
    }

    @Subscribe
    public void onSneakPacketSend(SendPacketEvent event) {
        if (sneak.getValue() && event.getPacket() instanceof ServerboundUseItemOnPacket) {
            this.blocksUntilSneak--;
        }
    }

    @Subscribe
    public void onSneakMoveInput(MoveInputEvent event) {
        if (!sneak.getValue() && sprint.getValue() != Sprint.MATRIX) {
            event.setSneak(false);
        }
    }

    public double getSneakSlowdownOverride() {
        if (!isEnabled() || !sneak.getValue()) {
            return -1.0;
        }
        double value = sneakingSpeed.getValue().doubleValue();
        return value > 0.2 ? value : -1.0;
    }

    private static float stepTowards(float from, float to, float limit) {
        return from + Mth.clamp(Mth.wrapDegrees(to - from), -limit, limit);
    }

    private boolean crosshairOnFace(boolean requireFace) {
        return crosshairOnFaceAt(this.currentYaw, this.currentPitch, requireFace);
    }

    private boolean crosshairOnFaceAt(float yaw, float pitch, boolean requireFace) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || this.blockFace == null) {
            return false;
        }
        BlockHitResult hit = RaycastUtility.rayTraceBlock(yaw, pitch,
                player.blockInteractionRange());
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) {
            return false;
        }
        if (!hit.getBlockPos().equals(this.blockFace)) {
            return false;
        }
        return !requireFace || hit.getDirection() == this.facing;
    }

    private int findBlockSlot(LocalPlayer player) {
        for (int i = 0; i < 9; i++) {
            if (ScaffoldBlockFilter.isPlaceable(player.getInventory().getItem(i))) {
                return i;
            }
        }
        return -1;
    }

    public boolean isCorrectingMovement() {
        return movementCorrection.getValue() && isEnabled()
                && this.blockFace != null && this.facing != null
                && rotationMode.getValue() != RotationMode.GRIM;
    }

    @Subscribe(priority = 1)
    public void onMovementCorrectionInput(MoveInputEvent event) {
        if (!isCorrectingMovement()) {
            return;
        }
        ServerRotation.submit(this.currentYaw, 0);
        if (!MovementFixModule.INSTANCE.isFixMovement()) {
            MovementFixModule.applyNormalFix(event);
        }
    }

    @Subscribe(priority = 1)
    public void onMovementCorrectionStrafe(StrafeEvent event) {
        if (isCorrectingMovement()) {
            event.setYaw(this.currentYaw);
        }
    }

    @Subscribe(priority = 1)
    public void onMovementCorrectionJump(JumpEvent event) {
        if (isCorrectingMovement()) {
            event.setYaw(this.currentYaw);
        }
    }

    @Subscribe
    public void onTellyMoveInput(MoveInputEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !getDisplayName().equals(Mode.TELLY.toString())) {
            return;
        }
        if (!player.onGround() || !MoveUtility.enoughMovementForSprinting()) {
            return;
        }
        if (watchdogTelly.getValue()) {
            if (GroundTickTracker.getGroundTicks() >= this.tellyJumpDelay) {
                event.setJump(true);
            }
        } else {
            event.setJump(true);
        }
    }

    private int drawTellyJumpDelay() {
        long min = watchdogTellyJumpDelay.getValue().longValue();
        long max = watchdogTellyJumpDelayMax.getValue().longValue();
        if (min == max) {
            return (int) min;
        }
        if (min > max) {
            long swap = min;
            min = max;
            max = swap;
        }
        return (int) (min + (max - min) * Math.random() * Math.random());
    }

    private void updateTellyState(LocalPlayer player) {
        if (!watchdogTelly.getValue() || !getDisplayName().equals(Mode.TELLY.toString())) {
            return;
        }
        this.tellyTicks++;
        if (player.onGround() && GroundTickTracker.getGroundTicks() == 1) {
            this.tellyJumpDelay = drawTellyJumpDelay();
        }
    }

    private boolean isMovingForLayerLock() {
        return (sameY.getValue() != SameY.OFF || SpeedModule.INSTANCE.isEnabled())
                && !Minecraft.getInstance().options.keyJump.isDown()
                && MoveUtility.isMoving();
    }

    private Integer requiredY() {
        if (!isMovingForLayerLock()) {
            return null;
        }
        return (int) Math.floor(this.startY) + (int) this.placeOffset.y;
    }

    private void tickAutoJump(LocalPlayer player) {
        if (sameY.getValue() == SameY.AUTO_JUMP && player.onGround()
                && MoveUtility.isMoving() && player.getY() == this.startY) {
            ((LivingEntityAccessor) player).aerial$jumpFromGround();
        }
    }

    public boolean isIgnoreSpeedEffect() {
        return isEnabled() && ignoreSpeedEffect.getValue();
    }

    public enum SameY {
        OFF("Off"),
        ON("On"),
        AUTO_JUMP("Auto Jump");

        private final String name;

        SameY(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private void tickRotationWithoutTarget(LocalPlayer player) {
        if (this.blockFace == null || this.facing == null
                || !getDisplayName().equals(Mode.TELLY.toString())) {
            return;
        }
        aimTelly(player);
        stepRotation();
    }

    @Subscribe
    public void onVisualBackRotations(PostMovementPacketEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }

        float sentYaw = getModelYaw(player.getYRot());
        float sentPitch = sentYaw == player.getYRot() ? player.getXRot() : this.currentPitch;

        if (!watchdogTelly.getValue() || !visualBackRotations.getValue()
                || !getDisplayName().equals(Mode.TELLY.toString())
                || this.blockFace == null || this.facing == null) {
            resetVisual(sentYaw, sentPitch);
            return;
        }

        boolean jumpHeld = minecraft.options.keyJump.isDown();
        int groundTicks = GroundTickTracker.getGroundTicks();

        boolean onGroundPhase = MoveUtility.enoughMovementForSprinting() && player.onGround()
                && !(!jumpHeld && groundTicks > 1 && this.tellyTicks > 3
                        && (this.tellyJumpDelay <= 0 || groundTicks != this.tellyJumpDelay));

        boolean missing = !crosshairOnFace(isStrictRayCast());
        Vec2 found = ScaffoldRotations.findPlacementRotation(this.blockFace, this.facing,
                missing && isStrictRayCast(),
                this.visualRotationsActive ? this.visualYaw : Mth.wrapDegrees(sentYaw - 135.0f),
                this.visualRotationsActive ? this.visualPitch : 84.0f,
                45.0f);
        Vec2 aim = new Vec2(found.x, Mth.clamp(found.y, 80.0f, 89.9f));

        boolean diverged = Math.abs(Mth.wrapDegrees(sentYaw - aim.x)) > 22.5f || onGroundPhase;
        Vec2 goal = diverged ? aim : new Vec2(sentYaw, sentPitch);

        if (!diverged && !this.visualRotationsActive) {
            resetVisual(sentYaw, sentPitch);
        } else {
            if (!this.visualRotationsActive) {
                resetVisual(sentYaw, sentPitch);
            }

            boolean boost = this.rotationBoostPending && rotationBlockBoost.getValue() && !player.onGround();
            if (boostOnlyWhileHoldingJump.getValue()) {
                boost = boost && jumpHeld;
            }
            float speed = (float) (boost
                    ? rotationBlockRotationSpeed.getValue().doubleValue()
                    : watchdogTellyRotationSpeed.getValue().doubleValue())
                    * (boost ? 0.42f : 0.3f);

            this.prevVisualYaw = this.visualYaw;
            this.prevVisualPitch = this.visualPitch;
            this.visualRotationsActive = true;
            this.visualYaw = stepAngle(this.visualYaw, goal.x, Math.max(1.5f, speed));
            this.visualPitch = stepAngle(this.visualPitch, goal.y, Math.max(1.0f, speed * 0.65f));

            if (!diverged
                    && Math.abs(Mth.wrapDegrees(this.visualYaw - sentYaw)) <= 2.0f
                    && Math.abs(this.visualPitch - sentPitch) <= 2.0f) {
                resetVisual(sentYaw, sentPitch);
            }
        }

        if (this.visualRotationsActive && !minecraft.options.getCameraType().isFirstPerson()) {
            player.yHeadRotO = this.prevVisualYaw;
            player.yHeadRot = this.visualYaw;
            player.yBodyRotO = this.prevVisualYaw;
            player.yBodyRot = this.visualYaw;
        }
    }

    private void resetVisual(float yaw, float pitch) {
        this.visualRotationsActive = false;
        this.prevVisualYaw = yaw;
        this.prevVisualPitch = pitch;
        this.visualYaw = yaw;
        this.visualPitch = pitch;
    }

    private boolean aimPredictionTelly(LocalPlayer player) {
        Minecraft minecraft = Minecraft.getInstance();
        int airTicks = GroundTickTracker.getAirTicks();
        int groundTicks = GroundTickTracker.getGroundTicks();
        boolean jumpHeld = minecraft.options.keyJump.isDown();
        int windowEnd = sameY.getValue() == SameY.OFF ? 7 : 10;

        boolean reaimed = false;
        if (airTicks <= windowEnd && (!player.onGround() || !jumpHeld)) {
            if (this.facing != null && !crosshairOnFace(isStrictRayCast())) {
                reaimed = applyPredictionRotation(player, 45);
            }
        } else {
            reaimed = applyPredictionRotation(player, (int) yawOffset.getValue().degrees());

            this.targetYaw = player.getYRot();
        }

        if ((airTicks <= 0 && groundTicks < 2)
                || (player.onGround() && jumpHeld && groundTicks < 2)) {
            this.readyToPlace = false;
        }
        return reaimed;
    }

    private boolean applyPredictionRotation(LocalPlayer player, int yawOffsetDegrees) {
        if (this.blockFace == null || this.facing == null || this.targetBlock == null) {
            return false;
        }

        double drop = player.getEyeY() - this.targetBlock.y - 0.5 - (Math.random() - 0.5) * 0.1;
        double originalY = player.getY();
        List<Vec2> candidates = new ArrayList<>();
        try {
            player.setPos(player.getX(), originalY - drop, player.getZ());
            for (int i = -180 + yawOffsetDegrees; i <= 180; i += 45) {
                BlockHitResult hit = RaycastUtility.rayTraceBlock(player.getYRot() + i * 3, 0.0f, 6.0);
                if (hit == null || hit.getType() != HitResult.Type.BLOCK || hit.getLocation() == null) {
                    continue;
                }
                Vec2 rotation = ScaffoldRotations.rotationsTo(hit.getLocation());
                BlockHitResult verify = RaycastUtility.rayTraceBlock(rotation.x, rotation.y, 6.0);
                if (verify != null && verify.getType() == HitResult.Type.BLOCK
                        && verify.getBlockPos().equals(this.blockFace)
                        && verify.getDirection() == this.facing) {
                    candidates.add(rotation);
                }
            }
        } finally {
            player.setPos(player.getX(), originalY, player.getZ());
        }

        if (candidates.isEmpty()) {
            Vec2 geometric = ScaffoldRotations.rotationsTo(new Vec3(
                    this.blockFace.getX(), this.blockFace.getY(), this.blockFace.getZ()));
            this.targetYaw = geometric.x;
            this.targetPitch = geometric.y;
            return true;
        }

        Vec2 best = null;
        float bestScore = Float.MAX_VALUE;
        for (Vec2 candidate : candidates) {
            float yawDelta = Math.abs(Mth.wrapDegrees(candidate.x - this.currentYaw));
            float pitchDelta = Math.abs(candidate.y - this.currentPitch);
            float score = yawDelta * yawDelta + pitchDelta * pitchDelta;
            if (score < bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        this.targetYaw = best.x;
        this.targetPitch = best.y;
        return true;
    }

    public boolean isIgnoringSpeedEffect() {
        return isEnabled() && ignoreSpeedEffect.getValue();
    }

    private static float stepAngle(float from, float to, float max) {
        float delta = Mth.wrapDegrees(to - from);
        if (delta > max) {
            delta = max;
        }
        if (delta < -max) {
            delta = -max;
        }
        return from + delta;
    }

    private boolean isStrictRayCast() {
        return rayCast.getValue() == RayCast.STRICT;
    }

    private boolean aimWatchdogTelly(LocalPlayer player) {
        Minecraft minecraft = Minecraft.getInstance();

        float wrapped = Mth.wrapDegrees(player.getYRot());
        float offAxis = Math.abs(wrapped % 90.0f);
        boolean nearAxis = offAxis <= 10.0f || offAxis >= 80.0f;
        boolean jumpHeld = minecraft.options.keyJump.isDown();
        boolean moving = MoveUtility.enoughMovementForSprinting();
        int groundTicks = GroundTickTracker.getGroundTicks();

        boolean reaimed = false;
        boolean handled = false;

        if (player.onGround() && moving && this.tellyJumpDelay > 0 && groundTicks == this.tellyJumpDelay) {
            this.targetYaw = (float) Math.toDegrees(MoveUtility.getDirectionRadians());
            this.targetPitch = (float) (85.0 + Math.random() * 0.5);
            this.rotationSpeedOverride = 180.0f;
            this.rotationBoostPending = true;
            reaimed = true;
            handled = true;
        }

        if (!handled) {
            boolean backwardsHold = player.onGround() && moving
                    && !(blockDiagonalAscend.getValue() && jumpHeld && !nearAxis)
                    && !(!jumpHeld && groundTicks > 1 && this.tellyTicks > 3);
            if (backwardsHold) {
                this.targetYaw = wrapped;
                this.targetPitch = (float) (Mth.clamp((float) (85.0 + Math.random() * 0.1), 0.0f, 90.0f)
                        + Math.random() * 0.5);
                this.rotationSpeedOverride = 10.0f;
                this.rotationBoostPending = true;
                reaimed = true;
                handled = true;
            }
        }

        if (!handled && this.facing != null && this.blockFace != null) {
            boolean missing = !crosshairOnFace(isStrictRayCast());
            if (missing || (player.onGround() && !jumpHeld) || this.tellyTicks <= 5) {
                Vec2 found = ScaffoldRotations.findPlacementRotation(this.blockFace, this.facing,
                        missing && isStrictRayCast(),
                        (float) (this.targetYaw + Math.random() * 0.5),
                        (float) (this.targetPitch + Math.random() * 0.5),
                        45.0f);

                boolean boost = this.rotationBoostPending && rotationBlockBoost.getValue() && !player.onGround();
                if (boostOnlyWhileHoldingJump.getValue()) {
                    boost = boost && jumpHeld;
                }
                double low;
                double high;
                if (boost) {
                    low = rotationBlockRotationSpeed.getValue().doubleValue();
                    high = rotationBlockRotationSpeedMax.getValue().doubleValue();
                    this.rotationBoostPending = false;
                } else {
                    low = watchdogTellyRotationSpeed.getValue().doubleValue();
                    high = watchdogTellyRotationSpeedMax.getValue().doubleValue();
                }
                if (low > high) {
                    double swap = low;
                    low = high;
                    high = swap;
                }

                this.targetYaw = stepAngle(this.targetYaw, found.x,
                        (float) (low + (high - low) * Math.random()));
                this.targetPitch = stepAngle(this.targetPitch, found.y,
                        (float) (low + (high - low) * Math.random()));
                reaimed = true;
            }
        }

        if (!dontForceRaycastOnWatchdogTelly.getValue()) {
            forceRaycastMode(player, jumpHeld, moving);
        }
        return reaimed;
    }

    private void forceRaycastMode(LocalPlayer player, boolean jumpHeld, boolean moving) {
        int airTicks = GroundTickTracker.getAirTicks();
        double motionX = player.getDeltaMovement().x;
        double motionZ = player.getDeltaMovement().z;
        double speed = Math.hypot(motionX, motionZ);

        boolean on = (player.invulnerableTime >= 10 || airTicks <= 6
                        || !(speed > 0.2) || !(Math.random() > 0.5))
                && moving
                && (!(speed < 0.15) || jumpHeld)
                && !player.horizontalCollision
                && (this.tellyTicks >= 15 || airTicks <= 8 || jumpHeld)
                && (airTicks <= 11 || !(Math.random() > 0.5));
        rayCast.setValueOrdinal((on ? RayCast.NORMAL : RayCast.OFF).ordinal());
    }

    private boolean aimTelly(LocalPlayer player) {
        if (watchdogTelly.getValue()) {
            return aimWatchdogTelly(player);
        }
        if (watchdogPrediction.getValue()) {
            return aimPredictionTelly(player);
        }
        int airTicks = GroundTickTracker.getAirTicks();
        int windowEnd = sameY.getValue() == SameY.OFF ? 7 : 10;
        boolean outsideWindow = airTicks < 3 || airTicks > windowEnd;

        boolean reaimed = false;
        if (outsideWindow) {
            Vec2 rotation = ScaffoldRotations.computeNormalRotation(this.blockFace, this.facing,
                    this.targetBlock, this.currentYaw, this.currentPitch,
                    yawOffset.getValue().degrees(), player.blockInteractionRange(),
                    this.targetYaw, this.targetPitch);
            this.targetPitch = rotation.y;
            this.targetYaw = player.getYRot();
            reaimed = true;
        } else if (this.facing != null && !crosshairOnFace(isStrictRayCast())) {
            Vec2 rotation = ScaffoldRotations.computeNormalRotation(this.blockFace, this.facing,
                    this.targetBlock, this.currentYaw, this.currentPitch,
                    0.0f, player.blockInteractionRange(), this.targetYaw, this.targetPitch);
            this.targetYaw = rotation.x;
            this.targetPitch = rotation.y;
            reaimed = true;
        }

        if (airTicks <= 3) {
            this.readyToPlace = false;
        }
        return reaimed;
    }

    private void applyExpand() {
        int distance = (int) expand.getValue().doubleValue();
        if (distance == 0 || !MoveUtility.isMoving()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return;
        }

        double direction = MoveUtility.getDirectionRadians();
        for (double step = 0.0; step <= distance; step += 1.0) {
            double aheadX = player.getX() + -Math.sin(direction) * step;
            double aheadY = player.getY() + this.placeOffset.y - 0.5;
            double aheadZ = player.getZ() + Math.cos(direction) * step;
            if (!mc.level.getBlockState(BlockPos.containing(aheadX, aheadY, aheadZ)).isAir()) {
                continue;
            }

            this.placeOffset = new Vec3(
                    this.placeOffset.x + (int) (-Math.sin(direction) * (step + 1.0)),
                    this.placeOffset.y + this.placeOffset.y,
                    this.placeOffset.z + (int) (Math.cos(direction) * (step + 1.0)));

            break;
        }
    }

    private void maintainStartY(LocalPlayer player) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.keyJump.isDown() && player.getY() % 1.0 > 0.5) {
            this.startY = Math.floor(player.getY());
        }
        if ((player.getY() < this.startY || player.onGround()) && !MoveUtility.isMoving()) {
            this.startY = Math.floor(player.getY());
        }

        if (GroundTickTracker.getGroundTicks() > 2 && Math.floor(player.getY()) != this.startY
                && !isReplaceableBelow(player, 1)) {
            this.startY = Math.floor(player.getY());
        }
    }

    private void stepRotation() {
        Float override = this.rotationSpeedOverride;
        this.rotationSpeedOverride = null;
        if (override != null) {
            stepRotation(override * 36.0);
            return;
        }
        double low = rotationSpeed.getValue().doubleValue();
        double high = rotationSpeedMax.getValue().doubleValue();
        if (low > high) {
            double swap = low;
            low = high;
            high = swap;
        }

        stepRotation((low == high ? low : low + (high - low) * Math.random()) * 36.0);
    }

    private void stepRotation(double speed) {
        if (speed <= 0.0) {
            this.currentYaw = this.targetYaw;
            this.currentPitch = this.targetPitch;
            return;
        }

        float deltaYaw = Mth.wrapDegrees(this.targetYaw - this.currentYaw);
        float deltaPitch = this.targetPitch - this.currentPitch;
        double distance = Math.sqrt(deltaYaw * deltaYaw + deltaPitch * deltaPitch);
        if (distance < 1.0E-4) {
            return;
        }

        double maxYaw = speed * Math.abs(deltaYaw / distance);
        double maxPitch = speed * Math.abs(deltaPitch / distance);
        float stepYaw = (float) Math.max(Math.min(deltaYaw, maxYaw), -maxYaw);
        float stepPitch = (float) Math.max(Math.min(deltaPitch, maxPitch), -maxPitch);

        float yaw = Mth.wrapDegrees(this.currentYaw + stepYaw);
        float pitch = ScaffoldRotations.clampPitch(this.currentPitch + stepPitch);

        int passes = (int) (Minecraft.getInstance().getFps() / 20.0f + Math.random() * 10.0);
        boolean moved = Math.abs(stepYaw) + Math.abs(stepPitch) > 1.0E-4f;
        for (int i = 1; i <= passes; i++) {
            if (moved) {
                yaw += (float) ((Math.random() - 0.5) / 1000.0);
                pitch -= (float) (Math.random() / 200.0);
            }
            Vec2 snapped = RotationUtility.patchConstantRotation(new Vec2(yaw, pitch), previousRotation());
            yaw = snapped.x;
            pitch = ScaffoldRotations.clampPitch(snapped.y);
        }

        this.currentYaw = yaw;
        this.currentPitch = pitch;
    }

    private static Vec2 previousRotation() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return Vec2.ZERO;
        }
        return new Vec2(player.yRotO, player.xRotO);
    }

    private void updateTicksOnAir(LocalPlayer player) {
        Minecraft mc = Minecraft.getInstance();
        boolean jumpHeld = mc.options.keyJump.isDown();

        boolean moving = isMovingForLayerLock();

        boolean nothingBelow;
        if (jumpHeld && !MoveUtility.isMoving() && !SpeedModule.INSTANCE.isEnabled()) {
            nothingBelow = isOffsetReplaceableBelow(player, 1);
        } else {
            nothingBelow = moving
                    ? isReplaceableAtStartY(player)
                    : isReplaceableBelow(player, 1) && (!moving || isReplaceableAtStartY(player));
        }

        this.ticksOnAir = nothingBelow ? this.ticksOnAir + 1 : 0;
    }

    private boolean isOffsetReplaceableBelow(LocalPlayer player, int depth) {
        BlockPos pos = BlockPos.containing(
                player.getX() + this.placeOffset.x,
                player.getY() - depth + this.placeOffset.y,
                player.getZ() + this.placeOffset.z);
        return player.level().getBlockState(pos).canBeReplaced();
    }

    private boolean isReplaceableBelow(LocalPlayer player, int depth) {
        BlockPos pos = new BlockPos(Mth.floor(player.getX()), Mth.floor(player.getY()) - depth,
                Mth.floor(player.getZ()));
        return player.level().getBlockState(pos).canBeReplaced();
    }

    private boolean isReplaceableAtStartY(LocalPlayer player) {
        BlockPos pos = BlockPos.containing(player.getX(), this.startY - 1.0, player.getZ());
        return player.level().getBlockState(pos).canBeReplaced();
    }

    private boolean isFallingFast(LocalPlayer player) {
        return player.getDeltaMovement().y < -0.1 && GroundTickTracker.getAirTicks() > 3;
    }

    public boolean isRotating() {
        return isEnabled() && this.blockFace != null && this.facing != null
                && rotationMode.getValue() != RotationMode.GRIM;
    }

    public float getRenderPitch(float partialTick) {
        if (!this.sentRotationPrimed) {
            return this.currentPitch;
        }
        return Mth.lerp(partialTick, this.prevSentPitch, this.currentPitch);
    }

    public float getRenderYaw() {
        return this.currentYaw;
    }

    public float getRenderYaw(float partialTick) {
        if (!this.sentRotationPrimed) {
            return this.currentYaw;
        }
        return this.prevSentYaw + Mth.wrapDegrees(this.currentYaw - this.prevSentYaw) * partialTick;
    }

    public double getFallbackReach() {
        LocalPlayer player = Minecraft.getInstance().player;

        double reach = player != null ? player.blockInteractionRange() : 4.5;
        return extendBlockReachOnWatchdogTelly.getValue() ? reach : reach + 1.0;
    }

    public boolean isSafeWalk() {
        return isEnabled() && safeWalk.getValue() && !this.watchdogSprintSuppressSafeWalk;
    }

    public boolean wantsSafeWalk() {
        if (!isSafeWalk()) {
            return false;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return false;
        }
        return isOffsetReplaceableBelow(player, 2)
                && isOffsetReplaceableBelow(player, 3)
                && !SpeedModule.INSTANCE.isEnabled()
                && !isSneakKeyRawlyDown();
    }

    public double getStartY() {
        return startY;
    }

    @Subscribe
    public void onSprintPreMovementPacket(PreMovementPacketEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }

        if (!player.onGround() && watchdogTelly.getValue()
                && Minecraft.getInstance().options.keyJump.isDown()) {
            float offAxis = Math.abs(Mth.wrapDegrees(player.getYRot()) % 90.0f);
            if (offAxis <= 10.0f || offAxis >= 80.0f) {
                player.setSprinting(false);
            }
        }

        switch (sprint.getValue()) {
            case BYPASS -> player.setSprinting(false);
            case LEGIT -> {
                if (legitSprintWantsRelease(player)) {
                    KeyMappingUtility.release(Minecraft.getInstance().options.keySprint);
                    player.setSprinting(false);
                }
            }
            default -> {
            }
        }
    }

    @Subscribe
    public void onDiagonalSpeedStrafe(StrafeEvent event) {
        if (yawOffset.getValue() == YawOffset.ZERO || movementCorrection.getValue()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || !MoveUtility.isMoving()) {
            return;
        }
        int held = 0;
        for (net.minecraft.client.KeyMapping key : new net.minecraft.client.KeyMapping[]{
                mc.options.keyUp, mc.options.keyRight, mc.options.keyDown, mc.options.keyLeft}) {
            if (key.isDown()) {
                held++;
            }
        }
        if (held != 1) {
            return;
        }
        double amount = player.onGround() ? 0.0026000750109401644 : 5.199896488849598E-4;
        double radians = MoveUtility.getDirectionRadians();
        Vec3 motion = player.getDeltaMovement();
        player.setDeltaMovement(
                motion.x + -Math.sin(radians) * amount,
                motion.y,
                motion.z + Math.cos(radians) * amount);
    }

    @Subscribe
    public void onSprintStrafe(StrafeEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }
        switch (sprint.getValue()) {
            case DISABLED -> {
                KeyMappingUtility.release(mc.options.keySprint);
                player.setSprinting(false);
            }
            case MATRIX -> {
                SpeedModule.INSTANCE.setEnabled(false);

                float f = Mth.wrapDegrees(player.getYRot());
                boolean flag = Math.abs(f % 80.0f) <= 15.0f || Math.abs(f % 90.0f) >= 75.0f;
                if (!flag) {
                    Vec3 motion = player.getDeltaMovement();
                    player.setDeltaMovement(motion.x * 0.95, motion.y, motion.z * 0.95);
                }
            }
            default -> {
            }
        }
    }

    private boolean legitSprintWantsRelease(LocalPlayer player) {
        float real = Mth.wrapDegrees(player.getYRot());
        float aim = Mth.wrapDegrees(getModelYaw(player.getYRot()));
        return Math.abs(real - aim) > 90.0f;
    }

    public boolean isSuppressingSprint() {
        if (!isEnabled()) {
            return false;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return false;
        }
        return switch (sprint.getValue()) {
            case DISABLED -> true;
            case LEGIT -> legitSprintWantsRelease(player);
            default -> false;
        };
    }

    public enum Sprint {
        NORMAL("Normal"),
        DISABLED("Disabled"),
        BYPASS("Bypass"),
        LEGIT("Legit"),
        MATRIX("Matrix"),
        WATCHDOG_JUMP("Watchdog Jump");

        private final String name;

        Sprint(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private static final double TOWER_FALL_CLAMP = -0.0784000015258789;

    @Subscribe
    public void onTowerPreMovementPacket(PreMovementPacketEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || tower.getValue() == Tower.OFF || !mc.options.keyJump.isDown()) {
            return;
        }
        Vec3 motion = player.getDeltaMovement();
        switch (tower.getValue()) {
            case NORMAL -> {
                if (player.onGround()) {
                    player.setDeltaMovement(motion.x, 0.42, motion.z);
                }
            }
            case MATRIX -> {
                if (hasFullBlockWithin(2) && motion.y < 0.2 && !MoveUtility.isMoving()) {
                    player.setDeltaMovement(motion.x, 0.42, motion.z);
                    event.setOnGround(true);
                }
            }
            case AIR_JUMP -> {
                if (player.tickCount % 2 == 0 && hasFullBlockWithin(2)) {
                    player.setDeltaMovement(motion.x, 0.42, motion.z);
                    event.setOnGround(true);
                }
            }
            case NCP -> {
                double fraction = player.getY() % 1.0;
                if (fraction <= 0.00153598) {
                    player.setPos(player.getX(), Math.floor(player.getY()), player.getZ());
                    player.setDeltaMovement(motion.x, 0.42, motion.z);
                } else if (fraction < 0.1 && GroundTickTracker.getAirTicks() != 0) {
                    player.setPos(player.getX(), Math.floor(player.getY()), player.getZ());
                    player.setDeltaMovement(motion.x, 0.0, motion.z);
                }
            }
            case WATCHDOG_PREDICTION_18 -> {
                if (player.onGround() && !MoveUtility.enoughMovementForSprinting()) {
                    ((LivingEntityAccessor) player).aerial$jumpFromGround();
                }

                int airTicks = GroundTickTracker.getAirTicks();
                if (airTicks == 4 && MoveUtility.getSpeed() == 0.0) {
                    Vec3 v = player.getDeltaMovement();
                    player.setDeltaMovement(v.x, v.y - 0.03, v.z);
                }
                if (airTicks == 5 && MoveUtility.getSpeed() == 0.0) {
                    Vec3 v = player.getDeltaMovement();
                    player.setDeltaMovement(v.x, v.y - 0.5, v.z);
                }
            }
            default -> {
            }
        }
    }

    @Subscribe
    public void onTowerPacketSend(SendPacketEvent event) {
        if (tower.getValue() != Tower.NORMAL) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !(event.getPacket() instanceof ServerboundUseItemOnPacket packet)) {
            return;
        }
        Vec3 motion = player.getDeltaMovement();
        if (motion.y <= TOWER_FALL_CLAMP) {
            return;
        }
        BlockPos below = BlockPos.containing(player.getX(), Math.floor(player.getY()) - 2.0, player.getZ());
        if (packet.getHitResult().getBlockPos().equals(below)) {
            player.setDeltaMovement(motion.x, TOWER_FALL_CLAMP, motion.z);
        }
    }

    @Subscribe
    public void onTowerStrafe(StrafeEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || tower.getValue() != Tower.LEGIT) {
            return;
        }
        if (mc.options.keyJump.isDown() && player.onGround()) {
            ((LivingEntityAccessor) player).aerial$jumpFromGround();
        }
    }

    @Subscribe
    public void onWatchdogTowerPreMovementPacket(PreMovementPacketEvent event) {
        if (tower.getValue() != Tower.WATCHDOG) {
            return;
        }
        WatchdogTowerLogic.onPreMovementPacket();
    }

    @Subscribe
    public void onWatchdogTowerStrafe(StrafeEvent event) {
        if (tower.getValue() == Tower.WATCHDOG) {
            WatchdogTowerLogic.onStrafe();
        }
    }

    @Subscribe
    public void onWatchdogTowerMoveInput(MoveInputEvent event) {
        if (tower.getValue() == Tower.WATCHDOG) {
            event.setForward(WatchdogTowerLogic.modifyForwardInput(event.getForward()));
        }
    }

    @Subscribe
    public void onWatchdogTowerJump(JumpEvent event) {
        if (tower.getValue() == Tower.WATCHDOG) {
            WatchdogTowerLogic.onJump();
        }
    }

    private void aimAtSideFaceForTower() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        boolean alignX = Math.cos(Math.toRadians(player.getYRot())) < 0.0;
        this.towerRequestedOffset = alignX ? new Vec3(1.0, 0.0, 0.0) : new Vec3(0.0, 0.0, 1.0);
        double jitter = (Math.random() - 0.5) * 3.0;

        this.towerRequestedYaw = (float) (player.getYRot() + (alignX ? -164.0 : 164.0) + jitter);
        this.towerRequestedPitch = 86.0f;
        this.towerRequestedAim = true;
    }

    @Subscribe
    public void onWatchdogSprintPreMovementPacket(PreMovementPacketEvent event) {
        if (sprint.getValue() != Sprint.WATCHDOG_JUMP) {
            return;
        }
        WatchdogJumpSprintLogic.onPreMovementPacket();
    }

    @Subscribe
    public void onWatchdogSprintStrafe(StrafeEvent event) {
        if (sprint.getValue() == Sprint.WATCHDOG_JUMP) {
            WatchdogJumpSprintLogic.onStrafe(sameY.getValue() == SameY.AUTO_JUMP);
        }
    }

    @Subscribe
    public void onWatchdogSprintBlockChange(ReceivePacketEvent event) {
        if (sprint.getValue() == Sprint.WATCHDOG_JUMP
                && event.getPacket() instanceof ClientboundBlockUpdatePacket) {
            WatchdogJumpSprintLogic.onBlockChanged();
        }
    }

    private void reanchorAfterJump() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return;
        }
        this.towerRequestedYaw = (float) (player.getYRot() + (Math.random() - 0.5) * 3.0);
        this.towerRequestedPitch = 90.0f;
        this.towerRequestedAim = true;
        BlockPos below = BlockPos.containing(player.getX(), player.getY() - 2.0, player.getZ());
        if (!mc.level.getBlockState(below).isAir()) {
            this.startY = player.getY() - 1.0;
        }
    }

    private static boolean hasFullBlockWithin(int depth) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return false;
        }
        for (int i = 0; i < depth; i++) {
            BlockPos pos = BlockPos.containing(player.getX(), player.getY() - i, player.getZ());
            if (mc.level.getBlockState(pos).isCollisionShapeFullBlock(mc.level, pos)) {
                return true;
            }
        }
        return false;
    }

    public enum Tower {
        OFF("Off"),
        NORMAL("Normal"),
        LEGIT("Legit"),
        MATRIX("Matrix"),
        AIR_JUMP("Air Jump"),
        NCP("NCP"),
        WATCHDOG_PREDICTION_18("Watchdog Prediction 1.8"),
        WATCHDOG("Watchdog");

        private final String name;

        Tower(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private static boolean isSneakKeyRawlyDown() {
        Minecraft mc = Minecraft.getInstance();
        InputConstants.Key key = ((KeyMappingAccessor) mc.options.keyShift).aerial$getKey();
        return InputConstants.isKeyDown(mc.getWindow(), key.getValue());
    }

    public enum Downwards {
        OFF("Off"),
        NORMAL("Normal"),
        WATCHDOG("Watchdog");

        private final String name;

        Downwards(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public enum SwingMode {
        SWING("Swing"),
        SILENT("Silent");

        private final String label;

        SwingMode(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public enum Mode {
        NORMAL("Normal"),
        TELLY("Keep-Y");

        private final String name;

        Mode(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public enum RotationMode {
        NORMAL("Normal"),
        GRIM("Grim");

        private final String name;

        RotationMode(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public enum RayCast {
        OFF("Off"),
        NORMAL("Normal"),
        STRICT("Strict");

        private final String name;

        RayCast(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public enum YawOffset {
        ZERO("0", 0.0f),
        PLUS_45("45", 45.0f),
        MINUS_45("-45", -45.0f);

        private final String name;
        private final float degrees;

        YawOffset(String name, float degrees) {
            this.name = name;
            this.degrees = degrees;
        }

        public float degrees() {
            return degrees;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
