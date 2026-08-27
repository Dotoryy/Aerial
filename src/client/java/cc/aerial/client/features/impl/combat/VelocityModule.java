package cc.aerial.client.features.impl.combat;

import cc.aerial.client.event.impl.game.PreGameTickEvent;
import cc.aerial.client.event.impl.game.input.MoveInputEvent;
import cc.aerial.client.event.impl.game.packet.ReceivePacketEvent;
import cc.aerial.client.event.impl.game.player.movement.knockback.KnockbackEvent;
import cc.aerial.client.event.impl.game.player.movement.HitSlowdownEvent;
import cc.aerial.client.event.impl.game.player.interaction.AttackEvent;
import cc.aerial.client.utility.PacketUtility;
import cc.aerial.client.utility.GroundTickTracker;
import cc.aerial.client.features.impl.movement.SpeedModule;
import cc.aerial.client.event.impl.game.player.movement.PreMoveEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.world.InteractionHand;
import cc.aerial.client.event.impl.game.JoinWorldEvent;
import cc.aerial.client.event.impl.game.packet.SendPacketEvent;
import cc.aerial.client.event.impl.game.player.movement.PostMovementPacketEvent;
import cc.aerial.client.event.impl.game.server.ServerDisconnectEvent;
import cc.aerial.client.features.impl.combat.killaura.KillauraModule;
import cc.aerial.client.features.impl.combat.killaura.target.CurrentTarget;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ClientboundTransferPacket;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundDebugSamplePacket;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatEndPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatEnterPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerLookAtPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerRotationPacket;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSetCameraPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityLinkPacket;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.network.protocol.game.ClientboundSetSimulationDistancePacket;
import net.minecraft.network.protocol.game.ClientboundTickingStatePacket;
import net.minecraft.network.protocol.game.ClientboundTickingStepPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.ping.ClientboundPongResponsePacket;
import net.minecraft.network.protocol.status.ClientboundStatusResponsePacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.EntityHitResult;
import cc.aerial.client.event.subscriber.Subscribe;
import cc.aerial.client.features.Module;
import cc.aerial.client.features.ModuleCategory;
import cc.aerial.client.features.impl.movement.FlightModule;
import cc.aerial.client.features.impl.movement.LongJumpModule;
import cc.aerial.client.mixin.LivingEntityAccessor;
import cc.aerial.client.packet.blockage.BlockHolder;
import cc.aerial.client.packet.blockage.NetworkDirection;
import cc.aerial.client.packet.blockage.PacketValidator;
import cc.aerial.client.property.BooleanProperty;
import cc.aerial.client.property.ModeProperty;
import cc.aerial.client.property.NumberProperty;
import cc.aerial.client.utility.HypixelServer;
import cc.aerial.client.utility.MoveUtility;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundPingPacket;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.protocol.game.ClientboundClearTitlesPacket;
import net.minecraft.network.protocol.game.ClientboundCustomChatCompletionsPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerChatPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class VelocityModule extends Module {
    public static final VelocityModule INSTANCE = new VelocityModule();

    private static final long HOLD_TIMEOUT_MS = 1000L;

    private static final int SPRINT_RESET_TICKS = 10;

    private static final float SPRINT_RESET_ANGLE = 70.0f;

    private final ModeProperty<Mode> mode = new ModeProperty<>("Mode", Mode.NORMAL);

    private final NumberProperty horizontal = new NumberProperty("Horizontal", 0, 0, 100, 1)
            .hideIf(() -> mode.getValue() != Mode.NORMAL);
    private final NumberProperty vertical = new NumberProperty("Vertical", 100, 0, 100, 1)
            .hideIf(() -> mode.getValue() != Mode.NORMAL);

    private final BooleanProperty intaveOnSwing = new BooleanProperty("On Swing", false)
            .hideIf(() -> mode.getValue() != Mode.INTAVE);

    private final BooleanProperty onlyWhileTargeting = new BooleanProperty("Only While Targeting", false)
            .hideIf(() -> mode.getValue() != Mode.NORMAL);
    private final BooleanProperty delayUntilGround = new BooleanProperty("Delay Until Ground", false)
            .hideIf(() -> mode.getValue() != Mode.NORMAL);

    private final NumberProperty airHoldMs = new NumberProperty("Delay (ms)", 150, 0, 500, 10)
            .hideIf(() -> mode.getValue() != Mode.NORMAL || delayUntilGround.getValue());
    private final BooleanProperty jumpOnGround = new BooleanProperty("Jump On Ground", false)
            .hideIf(() -> mode.getValue() != Mode.NORMAL);

    private final BooleanProperty holdUntilGround = new BooleanProperty("Hold Until Ground", true)
            .hideIf(() -> mode.getValue() != Mode.WATCHDOG);

    private final NumberProperty watchdogHoldMs = new NumberProperty("Hold (ms)", 150, 0, 500, 10)
            .hideIf(() -> mode.getValue() != Mode.WATCHDOG || holdUntilGround.getValue());

    private final BooleanProperty onlyWhileTargetingWatchdog =
            new BooleanProperty("Only While Targeting", false)
                    .hideIf(() -> mode.getValue() != Mode.WATCHDOG);

    private final NumberProperty watchdog2DelayTicks = new NumberProperty("Delay Ticks", 1, 0, 100, 1)
            .hideIf(() -> mode.getValue() != Mode.WATCHDOG_2);
    private final NumberProperty watchdog2WorldChangeTimeout =
            new NumberProperty("World Change Timeout", 5000, 0, 10000, 1)
                    .hideIf(() -> mode.getValue() != Mode.WATCHDOG_2);
    private final BooleanProperty watchdog2DelayAllPackets = new BooleanProperty("Delay All Packets", true)
            .hideIf(() -> mode.getValue() != Mode.WATCHDOG_2);
    private final BooleanProperty watchdog2CancelExplosion = new BooleanProperty("Cancel Explosion", false)
            .hideIf(() -> mode.getValue() != Mode.WATCHDOG_2);
    private final BooleanProperty watchdog2IgnoreExplosion = new BooleanProperty("Ignore Explosion", false)
            .hideIf(() -> mode.getValue() != Mode.WATCHDOG_2);
    private final BooleanProperty watchdog2JumpReset = new BooleanProperty("Jump Reset", false)
            .hideIf(() -> mode.getValue() != Mode.WATCHDOG_2);

    private final BooleanProperty watchdog2Blink = new BooleanProperty("Blink", false)
            .hideIf(() -> mode.getValue() != Mode.WATCHDOG_2);
    private final BooleanProperty watchdog2AttackReduce =
            new BooleanProperty("Attack Reduce (experimental)", false)
                    .hideIf(() -> mode.getValue() != Mode.WATCHDOG_2);
    private final BooleanProperty watchdog2SwitchAttack =
            new BooleanProperty("Switch Attack (experimental)", false)
                    .hideIf(() -> mode.getValue() != Mode.WATCHDOG_2);
    private final BooleanProperty watchdog2DisableOnFlag =
            new BooleanProperty("Disable Velocity on Flag", false)
                    .hideIf(() -> mode.getValue() != Mode.WATCHDOG_2);

    private final java.util.Queue<Packet<?>> watchdog2Packets =
            new java.util.concurrent.LinkedBlockingQueue<>();
    private final BlockHolder watchdog2BlinkHolder = new BlockHolder(NetworkDirection.OUTBOUND);
    private final cc.aerial.client.utility.Stopwatch watchdog2WorldChangeTime =
            new cc.aerial.client.utility.Stopwatch();

    private volatile int watchdog2Count;
    private volatile int watchdog2AttackCount;

    private volatile int watchdog2StuckTicks;
    private volatile boolean watchdog2Strict;
    private volatile boolean watchdog2Delaying;
    private volatile boolean watchdog2Explosion;
    private volatile boolean watchdog2Jump;
    private volatile boolean watchdog2ServerSprint;

    private volatile boolean watchdog2ActuallyAttacked;

    private static final PacketValidator VISUAL_VALIDATOR = packet -> {
        if (packet instanceof ClientboundEntityEventPacket status) {
            return status.getEventId() != 2 && status.getEventId() != 3;
        }
        if (packet instanceof ClientboundSetEntityDataPacket tracker) {
            LocalPlayer player = Minecraft.getInstance().player;
            return player == null || tracker.id() == player.getId();
        }
        return !(packet instanceof ClientboundAnimatePacket
                || packet instanceof ClientboundSetTitleTextPacket
                || packet instanceof ClientboundSetTitlesAnimationPacket
                || packet instanceof ClientboundClearTitlesPacket
                || packet instanceof ClientboundSoundPacket
                || packet instanceof ClientboundStopSoundPacket
                || packet instanceof ClientboundPlayerChatPacket
                || packet instanceof ClientboundCustomChatCompletionsPacket
                || packet instanceof ClientboundSetEquipmentPacket
                || packet instanceof ClientboundSetSubtitleTextPacket);
    };

    private final BlockHolder normalHolder = new BlockHolder(NetworkDirection.INBOUND);
    private final BlockHolder watchdogHolder = new BlockHolder(NetworkDirection.INBOUND);
    private long normalHoldStartedAt;
    private long watchdogHoldStartedAt;

    private Packet<?> holdTrigger;

    private boolean normalJump;
    private boolean watchdogJump;
    private int sprintResetTicks;

    private boolean mushCancel;

    private VelocityModule() {
        super("Velocity", "Reduces or nullifies your players velocity when being hit.", ModuleCategory.COMBAT);
        addProperties(mode, horizontal, vertical, onlyWhileTargeting, delayUntilGround, airHoldMs,
                jumpOnGround, holdUntilGround, watchdogHoldMs, onlyWhileTargetingWatchdog,
                watchdog2DelayTicks, watchdog2WorldChangeTimeout, watchdog2DelayAllPackets,
                watchdog2CancelExplosion, watchdog2IgnoreExplosion, watchdog2JumpReset,
                watchdog2Blink, watchdog2AttackReduce, watchdog2SwitchAttack,
                watchdog2DisableOnFlag);
    }

    @Override
    public String getSuffix() {
        if (mode.getValue() == Mode.NORMAL) {
            return horizontal.getValue().intValue() + " " + vertical.getValue().intValue();
        }
        return mode.getValue().toString();
    }

    @Override
    protected void onDisable() {
        normalHolder.release();
        watchdogHolder.release();
        holdTrigger = null;
        normalJump = false;
        watchdogJump = false;
        sprintResetTicks = 0;
        mushCancel = false;
        watchdog2Reset();
    }

    public boolean isInvalid() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return true;
        }
        if (LongJumpModule.INSTANCE.isEnabled() || FlightModule.INSTANCE.isEnabled()) {
            return true;
        }
        HypixelServer.ModAPI.Location location = HypixelServer.ModAPI.get().getCurrentLocation();
        return HypixelServer.isCurrent() && location != null && location.isLobby();
    }

    public boolean isSprintReset() {
        return isEnabled() && mode.getValue() == Mode.WATCHDOG && sprintResetTicks > 0
                && Mth.degreesDifferenceAbs(MoveUtility.getMoveYaw(), MoveUtility.getDirectionDegrees())
                >= SPRINT_RESET_ANGLE;
    }

    @Subscribe
    public void onKnockback(KnockbackEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || isInvalid()) {
            return;
        }
        switch (mode.getValue()) {
            case NORMAL -> onKnockbackNormal(player, event);
            case WATCHDOG -> onKnockbackWatchdog(player, event);
            case MUSHMC, INTAVE, MATRIX, GRIM, WATCHDOG_2 -> {
            }
        }
    }

    @Subscribe
    public void onIntaveTick(PreGameTickEvent event) {
        if (mode.getValue() != Mode.INTAVE || isInvalid()) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        if (intaveOnSwing.getValue() && !player.swinging) {
            return;
        }
        if (intaveAttacked && !intaveSlowedDown && player.isSprinting()) {
            Vec3 motion = player.getDeltaMovement();
            player.setDeltaMovement(motion.x * 0.6, motion.y, motion.z * 0.6);
            player.setSprinting(false);
        }
        intaveAttacked = false;
        intaveSlowedDown = false;
    }

    @Subscribe
    public void onIntaveHitSlowdown(HitSlowdownEvent event) {
        intaveSlowedDown = true;
    }

    @Subscribe
    public void onIntaveAttack(AttackEvent event) {
        intaveAttacked = true;
    }

    @Subscribe
    public void onMatrixTick(PreGameTickEvent event) {
        if (mode.getValue() != Mode.MATRIX || isInvalid()) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        int sinceKnockback = GroundTickTracker.getTicksSinceKnockback();
        boolean speedOn = SpeedModule.INSTANCE.isEnabled();
        Vec3 motion = player.getDeltaMovement();

        if (sinceKnockback == 1 && !isMoving(player)) {
            player.setDeltaMovement(motion.x * -0.1, motion.y, motion.z * -0.1);
        } else if (sinceKnockback == 1 && !speedOn) {
            reaimHorizontal(player);
        }

        if (sinceKnockback < 6 && speedOn) {
            reaimHorizontal(player);
        } else if (sinceKnockback > 1 && sinceKnockback < 15 && !speedOn) {
            Vec3 current = player.getDeltaMovement();
            player.setDeltaMovement(current.x, current.y - 0.00348, current.z);
        }
    }

    private static void reaimHorizontal(LocalPlayer player) {
        if (!isMoving(player)) {
            return;
        }
        Vec3 motion = player.getDeltaMovement();
        double speed = Math.hypot(motion.x, motion.z);
        double direction = movementDirection(player);
        player.setDeltaMovement(-Math.sin(direction) * speed, motion.y, Math.cos(direction) * speed);
    }

    private static double movementDirection(LocalPlayer player) {
        net.minecraft.world.phys.Vec2 move = player.input.getMoveVector();
        float forward = move.y;
        float strafe = move.x;
        float yaw = player.getYRot();
        if (forward < 0.0f) {
            yaw += 180.0f;
        }
        float factor = 1.0f;
        if (forward < 0.0f) {
            factor = -0.5f;
        } else if (forward > 0.0f) {
            factor = 0.5f;
        }
        if (strafe > 0.0f) {
            yaw -= 90.0f * factor;
        } else if (strafe < 0.0f) {
            yaw += 90.0f * factor;
        }
        return Math.toRadians(yaw);
    }

    private void onGrimPacket(LocalPlayer player, ReceivePacketEvent event, Packet<?> packet) {
        if (!isGrimEligible(player)) {
            return;
        }
        if (packet instanceof ClientboundSetEntityMotionPacket motion && motion.id() == player.getId()) {
            if (player.onGround()) {
                grimFrozen = true;
            } else {
                grimHolding = true;
                grimHolder.block(null, held -> grimHolding && isGrimHeldPacket(held));
            }
            event.setCancelled();
        }
    }

    private static boolean isGrimHeldPacket(Packet<?> packet) {
        return packet instanceof ClientboundSetEntityMotionPacket
                || packet instanceof ClientboundMoveEntityPacket
                || packet instanceof ClientboundTeleportEntityPacket
                || packet instanceof ClientboundPlayerPositionPacket;
    }

    private boolean isGrimEligible(LocalPlayer player) {
        return GroundTickTracker.getTicksSinceSetback() >= 7
                && ((cc.aerial.client.mixin.EntityAccessor) player)
                        .aerial$getStuckSpeedMultiplier().lengthSqr() <= 1.0E-7;
    }

    @Subscribe
    public void onGrimTick(PreGameTickEvent event) {
        if (mode.getValue() != Mode.GRIM || isInvalid()) {
            grimRelease(false);
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            grimRelease(false);
            return;
        }
        if (player.onGround() && (grimHolding || grimFrozen)) {
            grimRelease(true);
            return;
        }

        if (grimHolding && GroundTickTracker.getAirTicks() > 25) {
            grimRelease(true);
            return;
        }
        if (grimFrozen) {
            grimClaimGroundBelow(player);
        }
    }

    private void grimClaimGroundBelow(LocalPlayer player) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) {
            return;
        }
        BlockPos below = player.blockPosition().below();
        Vec3 hit = new Vec3(player.getX(), below.getY() + 1.0, player.getZ());
        connection.send(new ServerboundUseItemOnPacket(InteractionHand.MAIN_HAND,
                new BlockHitResult(hit, Direction.UP, below, false), 0));
    }

    private void grimRelease(boolean restoreMotion) {
        if (!grimHolding && !grimFrozen) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        Vec3 saved = player == null ? null : player.getDeltaMovement();
        grimHolding = false;
        grimFrozen = false;
        grimHolder.release();
        if (restoreMotion && player != null && saved != null) {
            Vec3 now = player.getDeltaMovement();
            player.setDeltaMovement(saved.x, now.y, saved.z);
        }
    }

    @Subscribe
    public void onGrimMove(PreMoveEvent event) {
        if (mode.getValue() == Mode.GRIM && grimFrozen && isEnabled()) {
            event.setCancelled();
        }
    }

    private final BlockHolder grimHolder = new BlockHolder(NetworkDirection.INBOUND);
    private boolean grimHolding;
    private boolean grimFrozen;

    private static boolean isMoving(LocalPlayer player) {
        net.minecraft.world.phys.Vec2 move = player.input.getMoveVector();
        return move.x != 0.0f || move.y != 0.0f;
    }

    private boolean intaveAttacked;
    private boolean intaveSlowedDown;

    private void onKnockbackNormal(LocalPlayer player, KnockbackEvent event) {
        double horizontalFactor = horizontal.getValue().doubleValue() / 100.0;
        double verticalFactor = vertical.getValue().doubleValue() / 100.0;

        Vec3 current = player.getDeltaMovement();
        double velocityX = event.getX() * horizontalFactor;
        double velocityY = event.getY() * verticalFactor;
        double velocityZ = event.getZ() * horizontalFactor;

        event.setOverridden();

        if (!event.isExplosion() && horizontalFactor == 0.0 && verticalFactor == 0.0) {
            event.setX(current.x);
            event.setY(current.y);
            event.setZ(current.z);
            return;
        }

        if (player.onGround() && jumpOnGround.getValue()) {
            normalJump = true;
        }

        event.setX(horizontalFactor != 0.0 ? velocityX : current.x);
        event.setY(verticalFactor != 0.0 ? velocityY : current.y);
        event.setZ(horizontalFactor != 0.0 ? velocityZ : current.z);
    }

    private static boolean hasKillauraTarget() {
        return cc.aerial.client.features.impl.combat.killaura.KillauraModule.INSTANCE.hasTarget();
    }

    private void onKnockbackWatchdog(LocalPlayer player, KnockbackEvent event) {
        if (event.isExplosion()) {
            releaseWatchdogHold();
            return;
        }
        if (onlyWhileTargetingWatchdog.getValue() && !hasKillauraTarget()) {
            return;
        }
        if (player.onGround() && holdUntilGround.getValue() && HypixelServer.isCurrent()) {
            boolean jumpHeld = player.input != null && player.input.keyPresses.jump();
            if (jumpHeld || ((LivingEntityAccessor) player).aerial$getJumpPower() < event.getY()) {
                watchdogJump = true;
            }
        }
        sprintResetTicks = SPRINT_RESET_TICKS;
    }

    @Subscribe
    public void onReceivePacket(ReceivePacketEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        Packet<?> packet = event.getPacket();

        switch (mode.getValue()) {
            case NORMAL -> {
                if (player != null
                        && packet instanceof ClientboundSetEntityMotionPacket motion
                        && motion.id() == player.getId()
                        && !player.onGround()
                        && (delayUntilGround.getValue() || airHoldMs.getValue().intValue() > 0)) {
                    holdTrigger = packet;
                    normalHolder.block(null, this::isHeldByHold);
                    normalHoldStartedAt = System.currentTimeMillis();
                }
            }
            case WATCHDOG -> {
                if (player != null
                        && packet instanceof ClientboundSetEntityMotionPacket motion
                        && motion.id() == player.getId()
                        && (holdUntilGround.getValue() || watchdogHoldMs.getValue().intValue() > 0)
                        && !isInvalid()
                        && (!onlyWhileTargetingWatchdog.getValue() || hasKillauraTarget())) {
                    holdTrigger = packet;
                    watchdogHolder.block(null, held -> isHeldByHold(held) && VISUAL_VALIDATOR.isValid(held));
                    watchdogHoldStartedAt = System.currentTimeMillis();
                } else if (packet instanceof ClientboundPlayerPositionPacket) {
                    releaseWatchdogHold();
                }
            }
            case MUSHMC -> onReceivePacketMushMc(player, event, packet);

            case MATRIX -> {
                if (player != null
                        && packet instanceof ClientboundSetEntityMotionPacket motion
                        && motion.id() == player.getId()) {
                    player.setDeltaMovement(player.getDeltaMovement().x,
                            motion.movement().y, player.getDeltaMovement().z);
                    if (isMoving(player)) {
                        event.setCancelled();
                    }
                }
            }
            case INTAVE -> {
            }
            case GRIM -> {
                if (player != null) {
                    onGrimPacket(player, event, packet);
                }
            }
            case WATCHDOG_2 -> {
                if (player != null) {
                    onWatchdog2Packet(player, event, packet);
                }
            }
        }
    }

    private void onReceivePacketMushMc(LocalPlayer player, ReceivePacketEvent event, Packet<?> packet) {
        if (packet instanceof ClientboundSetEntityMotionPacket motion) {
            if (player != null && motion.id() == player.getId()) {
                event.setCancelled();
                mushCancel = true;
            }
            return;
        }
        if (packet instanceof ClientboundPingPacket) {
            if (mushCancel) {
                event.setCancelled();
                mushCancel = false;
            }
            return;
        }
        if (packet instanceof ClientboundLoginPacket) {
            mushCancel = false;
        }
    }

    @Subscribe
    public void onMoveInput(MoveInputEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }

        boolean wants = switch (mode.getValue()) {
            case NORMAL -> normalJump;
            case WATCHDOG -> watchdogJump;

            case MUSHMC, INTAVE, MATRIX, GRIM, WATCHDOG_2 -> false;
        };
        if (!wants) {
            return;
        }
        normalJump = false;
        watchdogJump = false;

        ((LivingEntityAccessor) player).aerial$setNoJumpDelay(0);
        event.setJump(true);
    }

    @Subscribe
    public void onPreGameTick(PreGameTickEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;

        if (normalHolder.isBlocking() && shouldReleaseNormalHold(player)) {
            normalHolder.release();
            holdTrigger = null;
        }
        if (watchdogHolder.isBlocking() && shouldReleaseWatchdogHold(player)) {
            watchdogHolder.release();
            holdTrigger = null;
        }
        if (sprintResetTicks > 0) {
            sprintResetTicks--;
        }
    }

    private boolean shouldReleaseNormalHold(LocalPlayer player) {
        if (delayUntilGround.getValue()) {
            return shouldReleaseHold(player, normalHoldStartedAt);
        }
        return player == null
                || player.onGround()
                || player.isInWater()
                || player.isInLava()
                || player.onClimbable()
                || System.currentTimeMillis() - normalHoldStartedAt >= airHoldMs.getValue().longValue();
    }

    private boolean shouldReleaseWatchdogHold(LocalPlayer player) {
        if (holdUntilGround.getValue()) {
            return shouldReleaseHold(player, watchdogHoldStartedAt);
        }
        return player == null
                || player.onGround()
                || player.isInWater()
                || player.isInLava()
                || player.onClimbable()
                || System.currentTimeMillis() - watchdogHoldStartedAt >= watchdogHoldMs.getValue().longValue();
    }

    private static boolean shouldReleaseHold(LocalPlayer player, long startedAt) {
        return player == null
                || player.onGround()

                || player.isInWater()
                || player.isInLava()
                || player.onClimbable()
                || System.currentTimeMillis() - startedAt >= HOLD_TIMEOUT_MS;
    }

    private void releaseWatchdogHold() {
        if (watchdogHolder.isBlocking()) {
            watchdogHolder.release();
            holdTrigger = null;
        }
    }

    private boolean isHeldByHold(Packet<?> packet) {
        return packet != holdTrigger;
    }

    private boolean watchdog2Inert() {
        return !watchdog2WorldChangeTime.hasTimeElapsed(
                watchdog2WorldChangeTimeout.getValue().longValue());
    }

    @Subscribe(priority = 1)
    public void onWatchdog2MoveInput(MoveInputEvent event) {
        if (mode.getValue() != Mode.WATCHDOG_2 || isInvalid() || watchdog2Inert()) {
            return;
        }
        if (watchdog2Jump && watchdog2StuckTicks == 0) {
            event.setJump(true);
            watchdog2Jump = false;
        }
        if (watchdog2Strict) {
            event.setForward(1.0f);
            event.setSideways(0.0f);
        }
    }

    @Subscribe
    public void onWatchdog2PostMovementPacket(PostMovementPacketEvent event) {
        if (mode.getValue() != Mode.WATCHDOG_2 || isInvalid() || watchdog2Inert()) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !watchdog2Delaying) {
            return;
        }
        watchdog2Count++;
        if (watchdog2JumpReset.getValue() && player.onGround() && watchdog2Count > 1) {
            watchdog2Jump = true;
            watchdog2Delaying = false;
            watchdog2Count = 0;
            watchdog2Release();
            return;
        }
        if (watchdog2Count > watchdog2DelayTicks.getValue().intValue()) {
            watchdog2Delaying = false;
            watchdog2Count = 0;
            watchdog2Release();
        }
    }

    @Subscribe
    public void onWatchdog2JoinWorld(JoinWorldEvent event) {
        watchdog2WorldChangeTime.reset();
    }

    @Subscribe
    public void onWatchdog2Disconnect(ServerDisconnectEvent event) {
        watchdog2Reset();
    }

    @Subscribe
    public void onWatchdog2Tick(PreGameTickEvent event) {
        if (mode.getValue() != Mode.WATCHDOG_2 || isInvalid() || watchdog2Inert()) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (player == null || connection == null) {
            return;
        }
        if (watchdog2AttackCount <= 0) {
            watchdog2Strict = false;
            return;
        }
        if (!watchdog2ActuallyAttacked && watchdog2ServerSprint) {
            Entity target = watchdog2RayCastEntity(player, 3.0);
            if (target == null) {
                CurrentTarget current = KillauraModule.INSTANCE.getTargeting().getTarget();
                Entity candidate = current == null ? null : current.getEntity();
                if (candidate != null && watchdog2LooksAt(player, candidate, 3.0)) {
                    target = candidate;
                }
            }
            if (target instanceof AbstractClientPlayer) {
                if (watchdog2SwitchAttack.getValue()) {
                    int slot = player.getInventory().getSelectedSlot();
                    connection.send(new ServerboundSetCarriedItemPacket((slot + 1) % 9));
                    connection.send(new ServerboundSetCarriedItemPacket(slot));
                }
                player.setSprinting(false);
                player.setDeltaMovement(player.getDeltaMovement().multiply(0.6, 1.0, 0.6));

                connection.send(new ServerboundAttackPacket(target.getId()));
                player.swing(InteractionHand.MAIN_HAND);
            }
        }
        watchdog2AttackCount--;
    }

    private static Entity watchdog2RayCastEntity(LocalPlayer player, double reach) {
        Vec3 eye = player.getEyePosition(1.0f);
        Vec3 end = eye.add(player.getViewVector(1.0f).scale(reach));
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player.level(), player, eye, end,
                player.getBoundingBox().expandTowards(player.getViewVector(1.0f).scale(reach)).inflate(1.0),
                candidate -> candidate != player && candidate.isPickable(), 0.0f);
        return hit == null ? null : hit.getEntity();
    }

    private static boolean watchdog2LooksAt(LocalPlayer player, Entity target, double reach) {
        Vec3 eye = player.getEyePosition(1.0f);
        Vec3 end = eye.add(player.getViewVector(1.0f).scale(reach));
        return target.getBoundingBox().clip(eye, end).isPresent();
    }

    @Subscribe
    public void onWatchdog2SendPacket(SendPacketEvent event) {
        if (mode.getValue() != Mode.WATCHDOG_2 || isInvalid() || watchdog2Inert()) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        Packet<?> packet = event.getPacket();
        if (packet instanceof ServerboundAttackPacket) {
            watchdog2ActuallyAttacked = true;
        }
        if (packet instanceof ServerboundPlayerCommandPacket command && command.getId() == player.getId()) {
            if (command.getAction() == ServerboundPlayerCommandPacket.Action.START_SPRINTING) {
                watchdog2ServerSprint = true;
            } else if (command.getAction() == ServerboundPlayerCommandPacket.Action.STOP_SPRINTING) {
                watchdog2ServerSprint = false;
            }
        }
    }

    private void onWatchdog2Packet(LocalPlayer player, ReceivePacketEvent event, Packet<?> packet) {
        if (isInvalid() || watchdog2Inert()) {
            return;
        }

        if (packet instanceof ClientboundPlayerPositionPacket && watchdog2DisableOnFlag.getValue()) {
            watchdog2WorldChangeTime.reset();
        }

        boolean delayAll = watchdog2DelayAllPackets.getValue();
        if (watchdog2Delaying) {
            if (delayAll || !watchdog2IsPassthrough(player, packet)) {
                if (delayAll
                        || packet instanceof ClientboundPingPacket
                        || packet instanceof ClientboundExplodePacket
                        || packet instanceof ClientboundSetEntityMotionPacket motion
                                && motion.id() == player.getId()) {
                    event.setCancelled();
                    watchdog2Packets.add(packet);
                }

                if (packet instanceof ClientboundPlayerPositionPacket) {
                    watchdog2Release();
                    watchdog2StuckTicks = 0;
                    watchdog2Count = 0;
                    watchdog2Delaying = false;
                    watchdog2Jump = false;
                }
            }
        } else if (packet instanceof ClientboundSetEntityMotionPacket motion
                && motion.id() == player.getId()) {
            if (watchdog2Explosion) {
                watchdog2Explosion = false;
                if (watchdog2CancelExplosion.getValue()) {
                    event.setCancelled();
                    return;
                }
                if (watchdog2IgnoreExplosion.getValue()) {
                    return;
                }
            }
            if (watchdog2AttackReduce.getValue()) {
                watchdog2Strict = true;
                watchdog2AttackCount = 2;

                watchdog2ActuallyAttacked = false;
            }
            if (watchdog2DelayTicks.getValue().intValue() == 0) {
                if (player.onGround()) {
                    watchdog2Jump = true;
                }
            } else if (watchdog2StuckTicks <= 0) {
                if (watchdog2Blink.getValue()) {
                    watchdog2BlinkHolder.block();
                }
                watchdog2Count = 0;
                watchdog2Delaying = true;
                watchdog2Packets.add(packet);
                event.setCancelled();
            }
        }

        if (packet instanceof ClientboundExplodePacket) {
            if (watchdog2CancelExplosion.getValue()) {
                event.setCancelled();
            }
            watchdog2Explosion = true;
        }
    }

    private static boolean watchdog2IsPassthrough(LocalPlayer player, Packet<?> packet) {
        return packet instanceof ClientboundMoveEntityPacket
                || packet instanceof ClientboundEntityPositionSyncPacket

                || packet instanceof ClientboundSetEntityMotionPacket motion && motion.id() != player.getId()
                || packet instanceof ClientboundEntityEventPacket
                || packet instanceof ClientboundPongResponsePacket
                || packet instanceof ClientboundSetEquipmentPacket
                || packet instanceof ClientboundUpdateMobEffectPacket
                || packet instanceof ClientboundSetEntityDataPacket
                || packet instanceof ClientboundDamageEventPacket
                || packet instanceof ClientboundSetEntityLinkPacket
                || packet instanceof ClientboundSetPassengersPacket
                || packet instanceof ClientboundUpdateAttributesPacket
                || packet instanceof ClientboundAnimatePacket
                || packet instanceof ClientboundKeepAlivePacket
                || packet instanceof ClientboundTransferPacket
                || packet instanceof ClientboundBlockUpdatePacket
                || packet instanceof ClientboundBlockEntityDataPacket
                || packet instanceof ClientboundTeleportEntityPacket
                || packet instanceof ClientboundStatusResponsePacket
                || packet instanceof ClientboundTickingStatePacket
                || packet instanceof ClientboundSetSimulationDistancePacket
                || packet instanceof ClientboundTickingStepPacket
                || packet instanceof ClientboundLevelEventPacket
                || packet instanceof ClientboundMoveVehiclePacket
                || packet instanceof ClientboundUpdateRecipesPacket
                || packet instanceof ClientboundSetCameraPacket
                || packet instanceof ClientboundPlayerRotationPacket
                || packet instanceof ClientboundPlayerLookAtPacket
                || packet instanceof ClientboundSetHealthPacket
                || packet instanceof ClientboundExplodePacket
                || packet instanceof ClientboundRotateHeadPacket
                || packet instanceof ClientboundDebugSamplePacket
                || packet instanceof ClientboundPlayerCombatEnterPacket
                || packet instanceof ClientboundPlayerCombatEndPacket
                || packet instanceof ClientboundSectionBlocksUpdatePacket;
    }

    private void watchdog2Release() {
        Packet<?> packet;
        while ((packet = watchdog2Packets.poll()) != null) {
            watchdog2Dispatch(packet);
        }

        watchdog2BlinkHolder.release();
        watchdog2Delaying = false;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void watchdog2Dispatch(Packet<?> packet) {
        Minecraft mc = Minecraft.getInstance();
        Runnable replay = () -> {
            ClientPacketListener listener = mc.getConnection();
            if (listener == null) {
                return;
            }

            if (packet.type().flow() == PacketFlow.SERVERBOUND) {
                PacketUtility.sendNoEvent(packet);
                return;
            }
            ((Packet) packet).handle(listener);
        };
        if (mc.isSameThread()) {
            replay.run();
        } else {
            mc.execute(replay);
        }
    }

    private void watchdog2Reset() {
        watchdog2Packets.clear();
        watchdog2BlinkHolder.drop();
        watchdog2Count = 0;
        watchdog2AttackCount = 0;
        watchdog2StuckTicks = 0;
        watchdog2Strict = false;
        watchdog2Delaying = false;
        watchdog2Explosion = false;
        watchdog2Jump = false;
    }

    public enum Mode {
        NORMAL("Normal"),
        WATCHDOG("Hypixel"),
        MUSHMC("MushMC"),
        INTAVE("Intave"),
        MATRIX("Matrix"),
        GRIM("Grim"),
        WATCHDOG_2("Hypixel 2");

        private final String name;

        Mode(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
