package cc.aerial.client.features.impl.utility;

import cc.aerial.client.event.impl.game.JoinWorldEvent;
import cc.aerial.client.event.impl.game.PreGameTickEvent;
import cc.aerial.client.event.impl.game.packet.InstantaneousSendPacketEvent;
import cc.aerial.client.event.impl.game.packet.SendPacketEvent;
import cc.aerial.client.event.impl.game.player.movement.PreMovementPacketEvent;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import cc.aerial.client.event.impl.game.packet.ReceivePacketEvent;
import cc.aerial.client.features.impl.combat.killaura.KillauraModule;
import cc.aerial.client.features.impl.combat.killaura.target.CurrentTarget;
import cc.aerial.client.features.impl.movement.FlightModule;
import cc.aerial.client.features.impl.movement.SpeedModule;
import cc.aerial.client.notification.NotificationManager;
import cc.aerial.client.notification.NotificationType;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ClientboundPingPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import java.util.ArrayList;
import java.util.List;
import cc.aerial.client.event.subscriber.Subscribe;
import cc.aerial.client.features.Module;
import cc.aerial.client.features.ModuleCategory;
import cc.aerial.client.mixin.LocalPlayerInvoker;
import cc.aerial.client.packet.blockage.BlockHolder;
import cc.aerial.client.packet.blockage.NetworkDirection;
import cc.aerial.client.property.BooleanProperty;
import cc.aerial.client.property.ModeProperty;
import cc.aerial.client.property.MultipleBooleanProperty;
import cc.aerial.client.utility.HypixelServer;
import cc.aerial.client.utility.MoveUtility;
import cc.aerial.client.utility.PacketUtility;
import cc.aerial.client.utility.TeleportTickTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ServerboundPongPacket;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class DisablerModule extends Module {
    public static final DisablerModule INSTANCE = new DisablerModule();

    public enum Mode {
        WATCHDOG("Hypixel"),
        CUBECRAFT("Cubecraft"),
        GRIM_INVENTORY_MOVE("Grim Inventory Move");

        private final String label;

        Mode(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private final ModeProperty<Mode> mode = new ModeProperty<>("Mode", Mode.WATCHDOG);
    private final MultipleBooleanProperty options = new MultipleBooleanProperty("Options",
            new BooleanProperty("Inventory Move", true),
            new BooleanProperty("Watchdog Inv", false),
            new BooleanProperty("Rotation", false)
    ).hideIf(() -> mode.getValue() != Mode.WATCHDOG);

    private final MultipleBooleanProperty cubecraftOptions = new MultipleBooleanProperty("Cubecraft Options",
            new BooleanProperty("Warning", true),
            new BooleanProperty("Ground Spoof Disabler", true),
            new BooleanProperty("Auto Resync", false)
    ).hideIf(() -> mode.getValue() != Mode.CUBECRAFT);

    private final BlockHolder blockHolder = new BlockHolder(NetworkDirection.OUTBOUND);
    private boolean shouldBlink;

    private boolean sendingWatchdogSandwich;

    private float rotationLastSentYaw;
    private float rotationLastSentPitch;
    private boolean rotationHasLastSent;

    private float rotationPreviousYaw;

    private float rotationDeltaYaw;
    private float rotationLastPlacedDeltaYaw;

    private boolean rotationRotated;

    private static final float ROTATION_MIN_DELTA = 2.0f;
    private static final float ROTATION_MATCH_EPSILON = 0.0001f;
    private static final float ROTATION_NUDGE = 0.0002f;

    private static final double CUBECRAFT_Y_STEP = 0.015625;

    private record CubecraftHeld(Packet<?> packet, long time) {
    }
    private final java.util.LinkedHashSet<CubecraftHeld> cubecraftQueue = new java.util.LinkedHashSet<>();
    private boolean cubecraftNotified;

    private volatile long cubecraftLastSwing;
    private volatile boolean cubecraftSwingPending;
    private volatile boolean cubecraftMissed;
    private volatile boolean cubecraftReleased;
    private volatile net.minecraft.world.entity.LivingEntity cubecraftTargetWhenSwung;

    private boolean grimSprinting;
    private boolean grimSprintStateKnown;
    private boolean grimResending;

    private DisablerModule() {
        super("Disabler", "Lessens anti-cheat strength", ModuleCategory.UTILITY);
        addProperties(mode, options, cubecraftOptions);
    }

    @Override
    public String getSuffix() {
        return mode.getValue().toString();
    }

    @Override
    protected void onDisable() {
        blockHolder.release();
        shouldBlink = false;
        grimSprinting = false;
        grimSprintStateKnown = false;
        grimResending = false;

        cubecraftFlush(true);
        cubecraftReleased = false;
    }

    private boolean isInventoryMoveDisabler() {
        return mode.getValue() == Mode.WATCHDOG
                && options.getProperty("Inventory Move").getValue()
                && HypixelServer.isCurrent();
    }

    private boolean isWatchdogInvDisabler() {
        return mode.getValue() == Mode.WATCHDOG
                && options.getProperty("Watchdog Inv").getValue()
                && HypixelServer.isCurrent();
    }

    public boolean isSuppressingSprint() {
        if (!isEnabled() || mode.getValue() != Mode.GRIM_INVENTORY_MOVE) {
            return false;
        }
        return Minecraft.getInstance().gui.screen() instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>;
    }

    private boolean isRotationDisabler() {
        return mode.getValue() == Mode.WATCHDOG && options.getProperty("Rotation").getValue();
    }

    @Subscribe(priority = 100)
    public void onRotationPreMovementPacket(PreMovementPacketEvent event) {
        if (!isRotationDisabler()) {
            rotationHasLastSent = false;
            return;
        }
        float yaw = event.getYaw();
        float pitch = event.getPitch();

        boolean rotationChanged = !rotationHasLastSent
                || yaw != rotationLastSentYaw || pitch != rotationLastSentPitch;
        rotationLastSentYaw = yaw;
        rotationLastSentPitch = pitch;
        rotationHasLastSent = true;
        if (!rotationChanged) {
            return;
        }

        float previous = rotationPreviousYaw;
        rotationPreviousYaw = yaw;
        rotationDeltaYaw = Math.abs(yaw - previous);
        rotationRotated = true;

        if (rotationDeltaYaw > ROTATION_MIN_DELTA
                && Math.abs(rotationDeltaYaw - rotationLastPlacedDeltaYaw) < ROTATION_MATCH_EPSILON) {
            event.setYaw(yaw + ROTATION_NUDGE);
        }
    }

    @Subscribe
    public void onRotationSendPacket(SendPacketEvent event) {
        if (!isRotationDisabler()) {
            return;
        }
        if (event.getPacket() instanceof ServerboundUseItemOnPacket && rotationRotated) {
            rotationLastPlacedDeltaYaw = rotationDeltaYaw;
            rotationRotated = false;
        }
    }

    private boolean isCubecraftDisabler() {
        return mode.getValue() == Mode.CUBECRAFT;
    }

    private void cubecraftReceiveNoEvent(Packet<?> packet) {
        Minecraft mc = Minecraft.getInstance();
        Runnable handle = () -> {
            ClientPacketListener listener = mc.getConnection();
            if (listener != null) {
                cubecraftHandle(listener, packet);
            }
        };
        if (mc.isSameThread()) {
            handle.run();
        } else {
            mc.execute(handle);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void cubecraftHandle(ClientPacketListener listener, Packet<?> packet) {
        ((Packet) packet).handle(listener);
    }

    private void cubecraftFlush(boolean all) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            if (all) {
                synchronized (cubecraftQueue) {
                    cubecraftQueue.clear();
                }
            }
            return;
        }
        long ageLimit = player.tickCount < 150 ? 5000L : 10000L;
        long now = System.currentTimeMillis();

        List<Packet<?>> due = new ArrayList<>();
        synchronized (cubecraftQueue) {
            cubecraftQueue.removeIf(held -> {
                if (all || held.time() <= now - ageLimit) {
                    due.add(held.packet());
                    return true;
                }
                return false;
            });
        }
        for (Packet<?> packet : due) {
            cubecraftReceiveNoEvent(packet);
        }
    }

    @Subscribe
    public void onCubecraftReceivePacket(ReceivePacketEvent event) {
        if (!isCubecraftDisabler()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }
        Packet<?> packet = event.getPacket();

        if (cubecraftOptions.getProperty("Auto Resync").getValue()) {
            cubecraftAutoResyncReceive(player, packet);
        }

        if (packet instanceof ClientboundPlayerPositionPacket) {
            SpeedModule.INSTANCE.setEnabled(false);
            FlightModule.INSTANCE.setEnabled(false);
        }

        if ((packet instanceof ClientboundPingPacket || packet instanceof ClientboundKeepAlivePacket)) {
            event.setCancelled();
            synchronized (cubecraftQueue) {
                cubecraftQueue.add(new CubecraftHeld(packet, System.currentTimeMillis()));
            }
        }
    }

    @Subscribe
    public void onCubecraftPreMovementPacket(PreMovementPacketEvent event) {
        if (!isCubecraftDisabler() || !cubecraftOptions.getProperty("Ground Spoof Disabler").getValue()) {
            return;
        }
        double y = event.getY();
        event.setY(y - (y % CUBECRAFT_Y_STEP));
    }

    @Subscribe
    public void onCubecraftSwingSendPacket(SendPacketEvent event) {
        if (!isCubecraftDisabler() || !cubecraftOptions.getProperty("Auto Resync").getValue()) {
            return;
        }
        if (event.getPacket() instanceof net.minecraft.network.protocol.game.ServerboundSwingPacket) {
            CurrentTarget target = KillauraModule.INSTANCE.getTargeting().getTarget();
            net.minecraft.world.entity.LivingEntity entity = target == null ? null : target.getEntity();
            if (entity != null) {
                cubecraftLastSwing = System.currentTimeMillis();
                cubecraftSwingPending = true;
                cubecraftMissed = false;
                cubecraftReleased = false;
                cubecraftTargetWhenSwung = entity;
            }
        }
    }

    private void cubecraftAutoResyncReceive(LocalPlayer player, Packet<?> packet) {
        net.minecraft.world.entity.LivingEntity target = cubecraftTargetWhenSwung;
        if (packet instanceof ClientboundSoundPacket sound && cubecraftSwingPending && target != null) {
            net.minecraft.resources.Identifier id =
                    net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.getKey(sound.getSound().value());
            if (id != null && id.getPath().contains("hurt")) {
                long since = System.currentTimeMillis() - cubecraftLastSwing;
                double dx = target.getX() - sound.getX();
                double dy = target.getY() - sound.getY();
                double dz = target.getZ() - sound.getZ();
                double distSq = dx * dx + dy * dy + dz * dz;
                double range = KillauraModule.INSTANCE.getSettings().getSwingRange();
                if (since < 150 && distSq < range) {
                    cubecraftSwingPending = false;
                    cubecraftTargetWhenSwung = null;
                    cubecraftMissed = false;
                    cubecraftReleased = false;
                }
            }
        }

        if (cubecraftSwingPending && !cubecraftMissed
                && System.currentTimeMillis() - cubecraftLastSwing > 150) {
            cubecraftMissed = true;
            cubecraftSwingPending = false;
            if (!cubecraftReleased && cubecraftTargetWhenSwung != null) {
                cubecraftReleased = true;
                cubecraftFlush(true);
            }
            cubecraftTargetWhenSwung = null;
        }
    }

    @Subscribe
    public void onCubecraftTick(PreGameTickEvent event) {
        if (!isCubecraftDisabler()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }
        if (mc.hasSingleplayerServer()) {
            setEnabled(false);
            return;
        }
        ClientPacketListener connection = mc.getConnection();
        if (connection != null) {
            net.minecraft.client.multiplayer.PlayerInfo info = connection.getPlayerInfo(player.getUUID());
            if (info != null) {
                if (info.getLatency() > 500) {
                    if (!cubecraftNotified) {
                        NotificationManager.INSTANCE.builder(NotificationType.INFO)
                                .title("Disabler")
                                .description("Done, you can now fly etc.")
                                .duration(10000)
                                .buildAndPublish();
                        cubecraftNotified = true;
                    }
                } else if (cubecraftOptions.getProperty("Warning").getValue()) {
                    if (SpeedModule.INSTANCE.isEnabled()) {
                        cubecraftWarn();
                        SpeedModule.INSTANCE.setEnabled(false);
                    }
                    if (FlightModule.INSTANCE.isEnabled()) {
                        cubecraftWarn();
                        FlightModule.INSTANCE.setEnabled(false);
                    }
                }
            }
        }
        cubecraftFlush(player.tickCount <= 50);
    }

    private void cubecraftWarn() {
        NotificationManager.INSTANCE.builder(NotificationType.WARNING)
                .title("Disabler")
                .description("Wait until disabler is finished.")
                .duration(1000)
                .buildAndPublish();
    }

    private boolean isWatchdogInvPausedForChest() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return false;
        }
        HitResult hitResult = mc.player.pick(5.0D, 0.0F, false);
        if (hitResult.getType() != HitResult.Type.BLOCK) {
            return false;
        }
        BlockHitResult blockHit = (BlockHitResult) hitResult;
        return mc.level.getBlockState(blockHit.getBlockPos()).getBlock() instanceof ChestBlock;
    }

    private void sendWatchdogInvSandwich(net.minecraft.network.protocol.Packet<?> originalClick) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientPacketListener connection = mc.getConnection();
        if (player == null || connection == null) {
            return;
        }
        sendingWatchdogSandwich = true;
        try {
            connection.send(new ServerboundContainerClosePacket(player.inventoryMenu.containerId));
            connection.send(new ServerboundPlayerCommandPacket(player, ServerboundPlayerCommandPacket.Action.OPEN_INVENTORY));
            connection.send(originalClick);
            connection.send(new ServerboundContainerClosePacket(player.inventoryMenu.containerId));
        } finally {
            sendingWatchdogSandwich = false;
        }
    }

    @Subscribe
    public void onGrimInventoryMoveSendPacket(InstantaneousSendPacketEvent event) {
        if (mode.getValue() != Mode.GRIM_INVENTORY_MOVE) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }

        if (event.getPacket() instanceof ServerboundPlayerCommandPacket command) {
            if (command.getAction() == ServerboundPlayerCommandPacket.Action.START_SPRINTING) {
                if (grimSprintStateKnown && grimSprinting) {
                    event.setCancelled();
                    return;
                }
                grimSprinting = true;
                grimSprintStateKnown = true;
            } else if (command.getAction() == ServerboundPlayerCommandPacket.Action.STOP_SPRINTING) {
                if (grimSprintStateKnown && !grimSprinting) {
                    event.setCancelled();
                    return;
                }
                grimSprinting = false;
                grimSprintStateKnown = true;
            }
            return;
        }

        if (grimResending) {
            return;
        }
        if (!(event.getPacket() instanceof net.minecraft.network.protocol.game.ServerboundContainerClickPacket
                || event.getPacket() instanceof ServerboundContainerClosePacket)) {
            return;
        }
        if (!grimSprintStateKnown || !grimSprinting) {
            return;
        }

        event.setCancelled();
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) {
            return;
        }
        connection.send(new ServerboundPlayerCommandPacket(player, ServerboundPlayerCommandPacket.Action.STOP_SPRINTING));
        ((LocalPlayerInvoker) player).aerial$setWasSprinting(false);
        grimSprinting = false;
        grimSprintStateKnown = true;

        grimResending = true;
        try {
            connection.send(event.getPacket());
        } finally {
            grimResending = false;
        }

        if (player.isSprinting() && !grimSprinting) {
            connection.send(new ServerboundPlayerCommandPacket(player, ServerboundPlayerCommandPacket.Action.START_SPRINTING));
            ((LocalPlayerInvoker) player).aerial$setWasSprinting(true);
            grimSprinting = true;
            grimSprintStateKnown = true;
        }
    }

    @Subscribe
    public void onInstantaneousSendPacket(InstantaneousSendPacketEvent event) {
        if (isWatchdogInvDisabler() && !sendingWatchdogSandwich && !isWatchdogInvPausedForChest()
                && event.getPacket() instanceof net.minecraft.network.protocol.game.ServerboundContainerClickPacket click) {
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer watchdogPlayer = mc.player;
            if (watchdogPlayer != null && click.containerId() == watchdogPlayer.inventoryMenu.containerId
                    && !(mc.gui.screen() instanceof InventoryScreen)) {
                event.setCancelled();
                sendWatchdogInvSandwich(click);
                return;
            }
        }

        if (!isInventoryMoveDisabler()) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }

        if (event.getPacket() instanceof net.minecraft.network.protocol.game.ServerboundContainerClickPacket clickSlot) {
            boolean allowedAction = clickSlot.containerInput() == ContainerInput.QUICK_MOVE
                    || clickSlot.containerInput() == ContainerInput.SWAP
                    || clickSlot.containerInput() == ContainerInput.THROW;

            HypixelServer.ModAPI.Location location = HypixelServer.ModAPI.get().getCurrentLocation();
            if (location != null && location.isLobby()) {
                shouldBlink = false;
                return;
            }

            if (clickSlot.containerId() == player.inventoryMenu.containerId && allowedAction) {
                ClientPacketListener connection = Minecraft.getInstance().getConnection();
                if (connection != null) {
                    connection.send(new ServerboundContainerClosePacket(clickSlot.containerId()));
                }
            } else if (clickSlot.containerId() == player.inventoryMenu.containerId) {
                shouldBlink = true;
            }
        } else if (event.getPacket() instanceof ServerboundContainerClosePacket closeScreen
                && closeScreen.getContainerId() == player.inventoryMenu.containerId) {
            shouldBlink = false;
        }
    }

    @Subscribe(priority = 2)
    public void onPreGameTick(PreGameTickEvent event) {
        if (!isInventoryMoveDisabler()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() == null) {
            shouldBlink = false;
        }

        if (shouldBlink) {
            blockHolder.block(packet -> packet, packet -> !(
                    packet instanceof net.minecraft.network.protocol.game.ServerboundContainerClickPacket
                            || packet instanceof ServerboundContainerClosePacket
                            || packet instanceof ServerboundPongPacket
                            || packet instanceof ServerboundKeepAlivePacket));
        } else {
            blockHolder.release();
        }
    }

    @Subscribe
    public void onJoinWorld(JoinWorldEvent event) {
        shouldBlink = false;

        cubecraftFlush(true);
        cubecraftNotified = false;
        cubecraftReleased = false;
    }
}
