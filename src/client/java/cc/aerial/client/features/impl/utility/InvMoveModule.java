package cc.aerial.client.features.impl.utility;

import cc.aerial.client.event.impl.game.PreGameTickEvent;
import cc.aerial.client.event.impl.game.input.MoveInputEvent;
import cc.aerial.client.event.impl.game.packet.InstantaneousSendPacketEvent;
import cc.aerial.client.event.impl.game.packet.ReceivePacketEvent;
import cc.aerial.client.features.impl.combat.AutoBlockModule;
import cc.aerial.client.features.impl.combat.killaura.KillauraModule;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ServerboundPongPacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.world.inventory.ContainerInput;
import cc.aerial.client.event.subscriber.Subscribe;
import cc.aerial.client.features.Module;
import cc.aerial.client.features.ModuleCategory;
import cc.aerial.client.property.BooleanProperty;
import cc.aerial.client.property.ModeProperty;
import cc.aerial.client.property.NumberProperty;
import cc.aerial.client.rotation.RotationHelper;
import cc.aerial.client.rotation.model.impl.LinearRotationModel;
import cc.aerial.client.utility.GroundTickTracker;
import cc.aerial.client.utility.KeyMappingUtility;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class InvMoveModule extends Module {
    public static final InvMoveModule INSTANCE = new InvMoveModule();

    private static final int WATCHDOG_DELAY_TICKS = 8;

    public enum Mode {
        VANILLA("Vanilla"),
        GRIM("Grim"),
        WATCHDOG_BYPASS("Old Hypixel"),
        WATCHDOG("Hypixel"),
        WATCHDOG_2("Hypixel 2");

        private final String label;

        Mode(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private final ModeProperty<Mode> mode = new ModeProperty<>("Mode", Mode.VANILLA);

    private final BooleanProperty vanillaInventory = new BooleanProperty("Inventory", true).hideIf(() -> !usesScreenScope());
    private final BooleanProperty vanillaContainer = new BooleanProperty("Container", true).hideIf(() -> !usesScreenScope());

    private final BooleanProperty allowJumping = new BooleanProperty("Allow Jumping", true)
            .hideIf(() -> !isAstralisMode());
    private final BooleanProperty allowSneaking = new BooleanProperty("Allow Sneaking", false)
            .hideIf(() -> !isAstralisMode());
    private final BooleanProperty allowSprinting = new BooleanProperty("Allow Sprinting", true)
            .hideIf(() -> !isAstralisMode());

    private final NumberProperty grimExtraSprintTicks = new NumberProperty("Manager Extra Sprint Ticks", 9, 0, 20, 1).hideIf(() -> mode.getValue() != Mode.GRIM);

    private final BooleanProperty bypassPredictionMode = new BooleanProperty("Prediction Mode", false).hideIf(() -> mode.getValue() != Mode.WATCHDOG_BYPASS);
    private final NumberProperty bypassTicks = new NumberProperty("Ticks", 1, 1, 20, 1).hideIf(() -> mode.getValue() != Mode.WATCHDOG_BYPASS);
    private final BooleanProperty bypassMeasureChestOpen = new BooleanProperty("Measure Chest Open", true).hideIf(() -> mode.getValue() != Mode.WATCHDOG_BYPASS);

    private final Queue<ServerboundContainerClickPacket> clickQueue = new ConcurrentLinkedQueue<>();
    private int delayTicks = 0;
    private boolean keysPressed = false;
    private boolean replaying = false;

    private final Queue<Packet<?>> watchdogQueue = new ConcurrentLinkedQueue<>();
    private volatile boolean watchdogShouldBlink;
    private boolean watchdogReplaying;

    private volatile boolean watchdog2StopMovement;
    private int watchdog2TicksSinceClick;

    private BlockPos bypassPendingChestPos;
    private boolean bypassOpenPending;
    private boolean bypassAwaitingGui;
    private int bypassOpenSentTick = -1;
    private int bypassOpenLatencyTicks = -1;
    private int bypassChestOpenTick = -1;
    private boolean bypassChestOpenConfirmed;
    private boolean bypassClicking;
    private long bypassInputBlockStart;
    private boolean bypassInputDelayPassed;

    private InvMoveModule() {
        super("Inventory Move", "Walk while a GUI is open", ModuleCategory.UTILITY);
        addProperties(mode, vanillaInventory, vanillaContainer, grimExtraSprintTicks,
                bypassPredictionMode, bypassTicks, bypassMeasureChestOpen,
                allowJumping, allowSneaking, allowSprinting);
    }

    @Override
    public String getSuffix() {
        return mode.getValue().toString();
    }

    @Override
    protected void onDisable() {
        clickQueue.clear();
        delayTicks = 0;
        keysPressed = false;
        resetBypassState();
        watchdogDequeue();
        watchdogShouldBlink = false;
        watchdog2StopMovement = false;
        watchdog2TicksSinceClick = 0;
    }

    private void resetBypassState() {
        bypassPendingChestPos = null;
        bypassOpenPending = false;
        bypassAwaitingGui = false;
        bypassOpenSentTick = -1;
        bypassOpenLatencyTicks = -1;
        bypassChestOpenTick = -1;
        bypassChestOpenConfirmed = false;
        bypassClicking = false;
        bypassInputBlockStart = 0L;
        bypassInputDelayPassed = false;
    }

    public boolean canWalk(Screen screen) {
        if (!(screen instanceof AbstractContainerScreen<?>)) {
            return false;
        }
        if (screen instanceof CreativeModeInventoryScreen) {
            return false;
        }
        return switch (mode.getValue()) {
            case VANILLA, WATCHDOG, WATCHDOG_2 -> {
                if (!(screen instanceof InventoryScreen ? vanillaInventory.getValue() : vanillaContainer.getValue())) {
                    yield false;
                }
                yield switch (mode.getValue()) {
                    case WATCHDOG -> !isWatchdogBusy();

                    case WATCHDOG_2 -> !watchdog2StopMovement;
                    default -> true;
                };
            }
            case GRIM, WATCHDOG_BYPASS -> true;
        };
    }

    private boolean usesScreenScope() {
        Mode current = mode.getValue();
        return current == Mode.VANILLA || current == Mode.WATCHDOG || current == Mode.WATCHDOG_2;
    }

    private boolean isAstralisMode() {
        return mode.getValue() == Mode.WATCHDOG || mode.getValue() == Mode.WATCHDOG_2;
    }

    private boolean isWatchdogBusy() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return true;
        }
        if (player.isUsingItem() || AutoBlockModule.INSTANCE.isBlocking()) {
            return true;
        }
        return KillauraModule.INSTANCE.getTargeting().getTarget() != null;
    }

    public void applyAllowFilters(Minecraft mc) {
        if (!isAstralisMode() || mc.player == null) {
            return;
        }
        if (!allowJumping.getValue()) {
            mc.options.keyJump.setDown(false);
        }
        if (!allowSneaking.getValue()) {
            mc.options.keyShift.setDown(false);
        }
        if (!allowSprinting.getValue()) {
            mc.options.keySprint.setDown(false);
            mc.player.setSprinting(false);
        }
    }

    @Subscribe
    public void onWatchdogSendPacket(InstantaneousSendPacketEvent event) {
        if (mode.getValue() != Mode.WATCHDOG || watchdogReplaying) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || isWatchdogBusy()) {
            return;
        }
        Packet<?> packet = event.getPacket();

        if (packet instanceof ServerboundContainerClickPacket click) {
            if (click.containerId() == mc.player.inventoryMenu.containerId
                    && isWatchdogCheapClick(click.containerInput())) {
                ClientPacketListener connection = mc.getConnection();
                if (connection != null) {
                    connection.send(new ServerboundContainerClosePacket(click.containerId()));
                }
            } else {
                watchdogShouldBlink = true;
            }
        } else if (packet instanceof ServerboundContainerClosePacket) {
            watchdogShouldBlink = false;
        } else if (watchdogShouldBlink
                && !(packet instanceof ServerboundKeepAlivePacket)
                && !(packet instanceof ServerboundPongPacket)) {
            watchdogQueue.add(packet);
            event.setCancelled();
        }

        if (mc.gui.screen() == null) {
            watchdogShouldBlink = false;
            watchdogDequeue();
        }
    }

    private static boolean isWatchdogCheapClick(ContainerInput input) {
        return input == ContainerInput.QUICK_MOVE
                || input == ContainerInput.SWAP
                || input == ContainerInput.THROW;
    }

    @Subscribe
    public void onWatchdogReceivePacket(ReceivePacketEvent event) {
        if (mode.getValue() != Mode.WATCHDOG) {
            return;
        }
        if (event.getPacket() instanceof ClientboundLoginPacket
                || event.getPacket() instanceof ClientboundPlayerPositionPacket) {
            watchdogQueue.clear();
            watchdogShouldBlink = false;
        }
    }

    private void watchdogDequeue() {
        if (watchdogShouldBlink || watchdogQueue.isEmpty()) {
            return;
        }
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) {
            watchdogQueue.clear();
            return;
        }
        watchdogReplaying = true;
        try {
            Packet<?> packet;
            while ((packet = watchdogQueue.poll()) != null) {
                connection.send(packet);
            }
        } finally {
            watchdogReplaying = false;
        }
    }

    @Subscribe
    public void onWatchdog2SendPacket(InstantaneousSendPacketEvent event) {
        if (mode.getValue() != Mode.WATCHDOG_2 || Minecraft.getInstance().level == null) {
            return;
        }
        if (event.getPacket() instanceof ServerboundContainerClickPacket) {
            watchdog2StopMovement = true;
            watchdog2TicksSinceClick = 0;
        }
    }

    @Subscribe
    public void onWatchdog2Tick(PreGameTickEvent event) {
        if (mode.getValue() != Mode.WATCHDOG_2 || !watchdog2StopMovement) {
            return;
        }
        Screen screen = Minecraft.getInstance().gui.screen();
        if (screen == null || !(screen instanceof AbstractContainerScreen<?>)) {
            return;
        }
        watchdog2TicksSinceClick++;
        if (watchdog2TicksSinceClick >= 10) {
            watchdog2StopMovement = false;
            watchdog2TicksSinceClick = 0;
        }
    }

    public boolean isKeysPressed() {
        return keysPressed;
    }

    public void setKeysPressed(boolean value) {
        keysPressed = value;
    }

    public void tickDelay() {
        if (delayTicks > 0) {
            delayTicks--;
        }
    }

    public void drainClickQueue() {
        if (clickQueue.isEmpty()) {
            return;
        }
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) {
            clickQueue.clear();
            return;
        }
        replaying = true;
        try {
            ServerboundContainerClickPacket packet;
            while ((packet = clickQueue.poll()) != null) {
                connection.send(packet);
            }
        } finally {
            replaying = false;
        }
    }

    @Subscribe
    public void onSendPacket(InstantaneousSendPacketEvent event) {
        if (replaying || isAstralisMode()) {
            return;
        }
        if (!(event.getPacket() instanceof ServerboundContainerClickPacket packet)) {
            return;
        }
        event.setCancelled();
        clickQueue.offer(packet);
        delayTicks = WATCHDOG_DELAY_TICKS;
    }

    @Subscribe
    public void onGrimTick(PreGameTickEvent event) {
        if (mode.getValue() != Mode.GRIM) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        Screen screen = Minecraft.getInstance().gui.screen();
        if (player != null && screen instanceof AbstractContainerScreen<?>) {
            player.setSprinting(false);
        }
    }

    @Subscribe
    public void onBypassSendPacket(InstantaneousSendPacketEvent event) {
        if (mode.getValue() != Mode.WATCHDOG_BYPASS || event.isCancelled()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        if (bypassMeasureChestOpen.getValue() && event.getPacket() instanceof ServerboundUseItemOnPacket useOn) {
            BlockPos pos = useOn.getHitResult().getBlockPos();
            if (!(mc.gui.screen() instanceof AbstractContainerScreen<?>) && isChestBlock(mc, pos)) {
                bypassPendingChestPos = pos;
                bypassOpenPending = true;
            }
        }
        if (event.getPacket() instanceof ServerboundContainerClickPacket && mc.gui.screen() instanceof InventoryScreen) {
            bypassClicking = true;
        }
    }

    private static boolean isChestBlock(Minecraft mc, BlockPos pos) {
        if (pos == null || mc.level == null) {
            return false;
        }
        var block = mc.level.getBlockState(pos).getBlock();
        return block == Blocks.CHEST || block == Blocks.TRAPPED_CHEST || block == Blocks.ENDER_CHEST;
    }

    @Subscribe
    public void onBypassTick(PreGameTickEvent event) {
        if (mode.getValue() != Mode.WATCHDOG_BYPASS) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || player.tickCount < 50) {
            return;
        }
        boolean chestLike = isChestLike(mc.gui.screen());

        if (bypassOpenPending) {
            bypassOpenSentTick = player.tickCount;
            bypassOpenPending = false;
            bypassAwaitingGui = true;
        }

        if (bypassAwaitingGui) {
            if (chestLike) {
                bypassOpenLatencyTicks = player.tickCount - bypassOpenSentTick;
                if (bypassChestOpenTick == -1) {
                    bypassChestOpenTick = player.tickCount;
                }
                bypassAwaitingGui = false;
            } else if (player.tickCount - bypassOpenSentTick > 40) {
                bypassAwaitingGui = false;
            }
        }

        if (!chestLike) {
            bypassChestOpenTick = -1;
            bypassChestOpenConfirmed = false;
        } else {
            if (bypassChestOpenTick == -1) {
                bypassChestOpenTick = player.tickCount;
            }
            if (!bypassChestOpenConfirmed && bypassOpenLatencyTicks >= 0
                    && player.tickCount - bypassChestOpenTick >= bypassOpenLatencyTicks - bypassTicks.getValue().intValue()) {
                bypassChestOpenConfirmed = true;
            }
            player.setSprinting(false);
        }

        if (!(mc.gui.screen() instanceof InventoryScreen)) {
            bypassClicking = false;
        }

        int heldMovementKeys = countHeldMovementKeys(mc);
        if ((chestLike || bypassClicking) && heldMovementKeys > 1) {
            float facing = movementFacingYaw(mc, player);
            RotationHelper.getHandler().rotate(new Vec2(facing, player.getXRot()), new LinearRotationModel(4.5), this);
        }
    }

    private static boolean isChestLike(Screen screen) {
        return screen instanceof AbstractContainerScreen<?> && !(screen instanceof InventoryScreen)
                && !(screen instanceof CreativeModeInventoryScreen);
    }

    private static int countHeldMovementKeys(Minecraft mc) {
        int count = 0;
        if (mc.options.keyUp.isDown()) count++;
        if (mc.options.keyRight.isDown()) count++;
        if (mc.options.keyDown.isDown()) count++;
        if (mc.options.keyLeft.isDown()) count++;
        return count;
    }

    private static float movementFacingYaw(Minecraft mc, LocalPlayer player) {
        Vec3 movement = player.getDeltaMovement();
        double angle = Math.atan2(-movement.x, movement.z);
        return (float) Math.toDegrees(angle);
    }

    @Subscribe
    public void onBypassMoveInput(MoveInputEvent event) {
        if (mode.getValue() != Mode.WATCHDOG_BYPASS) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }
        boolean chestLike = isChestLike(mc.gui.screen());

        if (bypassChestOpenConfirmed && player.tickCount % 5 != 0 && chestLike) {
            event.setForward(0.0f);
            event.setSideways(0.0f);
        }
        if (!chestLike) {
            bypassChestOpenConfirmed = false;
        }

        boolean speedActive = player.hasEffect(MobEffects.SPEED);
        if ((chestLike || bypassClicking) && bypassPredictionMode.getValue() && (speedActive || !player.onGround())) {
            event.setForward(0.0f);
            event.setSideways(0.0f);
        } else if (bypassClicking && !bypassInputDelayPassed) {
            event.setForward(0.0f);
            event.setSideways(0.0f);
            if (bypassInputBlockStart == 0L) {
                bypassInputBlockStart = System.currentTimeMillis();
            }
        } else {
            bypassInputBlockStart = 0L;
        }

        if (bypassInputBlockStart != 0L && System.currentTimeMillis() - bypassInputBlockStart >= 60L) {
            bypassInputDelayPassed = true;
            bypassInputBlockStart = 0L;
        }
        if (!chestLike && !bypassClicking) {
            bypassInputDelayPassed = false;
        }
    }

    @Subscribe
    public void onBypassPreMove(PreGameTickEvent event) {
        if (mode.getValue() != Mode.WATCHDOG_BYPASS || bypassPredictionMode.getValue()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }
        boolean chestLike = isChestLike(mc.gui.screen());
        if (!chestLike && !bypassClicking) {
            return;
        }

        boolean ySettled = Math.abs(player.getY() - Math.round(player.getY())) <= 0.03;
        if (GroundTickTracker.getGroundTicks() < 10 && ySettled && !chestLike) {
            strafe(player, 0.0365);
            return;
        }
        if (!player.onGround()) {
            Vec3 motion = player.getDeltaMovement();
            player.setDeltaMovement(0.0, motion.y, 0.0);
            return;
        }
        MobEffectInstance speed = player.getEffect(MobEffects.SPEED);
        if (speed != null && speed.getAmplifier() + 1 > 1) {
            strafe(player, 0.0185 * (speed.getAmplifier() + 1));
        } else if (speed != null && speed.getAmplifier() + 1 == 1) {
            strafe(player, 0.0635 * (speed.getAmplifier() + 1));
        } else {
            strafe(player, 0.09);
        }
    }

    private static void strafe(LocalPlayer player, double magnitude) {
        float yawRad = (float) Math.toRadians(player.getYRot());
        Vec3 motion = player.getDeltaMovement();
        player.setDeltaMovement(-Mth.sin(yawRad) * magnitude, motion.y, Mth.cos(yawRad) * magnitude);
    }
}
