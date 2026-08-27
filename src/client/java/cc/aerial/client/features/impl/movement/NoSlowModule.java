package cc.aerial.client.features.impl.movement;

import cc.aerial.client.event.impl.game.PreGameTickEvent;
import cc.aerial.client.event.impl.game.input.MouseHandleInputEvent;
import cc.aerial.client.event.impl.game.input.PostHandleInputEvent;
import cc.aerial.client.event.impl.game.packet.ReceivePacketEvent;
import cc.aerial.client.event.impl.game.player.movement.SlowdownEvent;
import cc.aerial.client.rotation.ServerRotation;
import cc.aerial.client.event.impl.game.input.MoveInputEvent;
import cc.aerial.client.event.subscriber.Subscribe;
import cc.aerial.client.features.Module;
import cc.aerial.client.features.ModuleCategory;
import cc.aerial.client.features.impl.combat.AutoBlockModule;
import cc.aerial.client.features.impl.combat.killaura.KillauraModule;
import cc.aerial.client.mixin.ClientInputAccessor;
import cc.aerial.client.mouse.MouseButton;
import cc.aerial.client.mouse.MouseHelper;
import cc.aerial.client.packet.blockage.BlockHolder;
import cc.aerial.client.packet.blockage.NetworkDirection;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import cc.aerial.client.event.impl.game.player.interaction.ItemUseEvent;
import cc.aerial.client.event.impl.game.player.movement.PreMovementPacketEvent;
import net.minecraft.client.multiplayer.ClientPacketListener;
import cc.aerial.client.property.BooleanProperty;
import cc.aerial.client.property.ModeProperty;
import cc.aerial.client.property.NumberProperty;
import cc.aerial.client.rotation.RotationHelper;
import cc.aerial.client.rotation.model.impl.LinearRotationModel;
import cc.aerial.client.utility.GroundTickTracker;
import cc.aerial.client.utility.InventoryUtility;
import cc.aerial.client.utility.PlayerUtility;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

public final class NoSlowModule extends Module {
    public static final NoSlowModule INSTANCE = new NoSlowModule();

    public enum Mode {
        VANILLA("Vanilla"),
        WATCHDOG("Hypixel"),
        WATCHDOG_PREDICTION("Watchdog Prediction"),
        UNIVERSAL("Universal"),
        MATRIX("Matrix"),
        GRIM_30("Grim 3.0"),
        INTAVE("Intave"),
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

    public enum Action {
        BLOCKABLE,
        USEABLE,
        BOW,
        NONE
    }

    private final ModeProperty<Mode> mode = new ModeProperty<>("Mode", Mode.VANILLA);
    private final BooleanProperty allowSprinting = new BooleanProperty("Allow Sprinting", true);
    private final BooleanProperty universalSlowdown = new BooleanProperty("Slow Down", false).hideIf(() -> mode.getValue() != Mode.UNIVERSAL);

    private final BooleanProperty universalBlink = new BooleanProperty("Blink", true).hideIf(() -> mode.getValue() != Mode.UNIVERSAL);

    private final NumberProperty predictionMaxPingSpoof = new NumberProperty("Max Ping Spoof", 8, 0, 30, 1).hideIf(() -> mode.getValue() != Mode.WATCHDOG_PREDICTION);

    private final NumberProperty predictionWhenToFinishEating = new NumberProperty("When to finish eating", 30, 20, 36, 1).hideIf(() -> mode.getValue() != Mode.WATCHDOG_PREDICTION);

    private final BooleanProperty predictionNonBlinkSpeedBypass = new BooleanProperty("Non-Blink Speed Bypass", true).hideIf(() -> mode.getValue() != Mode.WATCHDOG_PREDICTION);

    private final BooleanProperty slowDownOnSlabs = new BooleanProperty("Slow down on Slabs", true)
            .hideIf(() -> mode.getValue() != Mode.WATCHDOG_2);

    private int watchdog2AirTicks;
    private boolean watchdog2OnSlab;

    private final BooleanProperty swordEnabled = new BooleanProperty("Sword", true);
    private final BooleanProperty foodEnabled = new BooleanProperty("Food", true);
    private final BooleanProperty potionEnabled = new BooleanProperty("Potion", true);
    private final BooleanProperty bowEnabled = new BooleanProperty("Bow", true);

    private Action action = Action.NONE;

    private final BlockHolder blockHolder = new BlockHolder(NetworkDirection.OUTBOUND);
    private boolean stopUse;

    private int nextCycleTick = -1;
    private int slotChangeTick;
    private boolean runThisTick;
    private int lastSelectedSlot = -1;

    private int predictionUsingTicks;

    private boolean predictionWasUsingItem;

    private NoSlowModule() {
        super("No Slowdown", "Removes vanilla slowdowns such as item usage", ModuleCategory.MOVEMENT);
        addProperties(mode, allowSprinting, universalSlowdown, universalBlink,
                predictionMaxPingSpoof, predictionWhenToFinishEating, predictionNonBlinkSpeedBypass,
                slowDownOnSlabs, swordEnabled, foodEnabled, potionEnabled, bowEnabled);
    }

    @Override
    public String getSuffix() {
        return mode.getValue().toString();
    }

    @Override
    protected void onDisable() {
        blockHolder.release();
        resetCycle();
        predictionUsingTicks = 0;
        predictionWasUsingItem = false;
    }

    public boolean isSprintingAllowed() {
        return allowSprinting.getValue();
    }

    public Action getAction() {
        return action;
    }

    public boolean isFakeBlockingState() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !isEnabled() || action != Action.BLOCKABLE) {
            return false;
        }
        if (!isRightMouseButtonPhysicallyDown() && !isAutoBlockHolding()) {
            return false;
        }
        ItemStack mainHand = player.getMainHandItem();
        if (!mainHand.is(ItemTags.SWORDS)) {
            return false;
        }
        if (InventoryUtility.isBlockInteractable(PlayerUtility.getBlockOver())) {
            return false;
        }
        return player.getOffhandItem().getItem() instanceof ShieldItem || mainHand.getUseAnimation() == ItemUseAnimation.BLOCK;
    }

    private boolean isRightMouseButtonPhysicallyDown() {
        long window = Minecraft.getInstance().getWindow().handle();
        return org.lwjgl.glfw.GLFW.glfwGetMouseButton(window, org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
    }

    private boolean isAutoBlockHolding() {
        return AutoBlockModule.INSTANCE.isEnabled() && AutoBlockModule.INSTANCE.isBlocking();
    }

    private void block() {
        boolean queuePackets = mode.getValue() != Mode.UNIVERSAL || universalBlink.getValue();
        blockHolder.block(null, packet -> queuePackets
                && (packet instanceof net.minecraft.network.protocol.game.ServerboundUseItemPacket
                        || packet instanceof net.minecraft.network.protocol.game.ServerboundUseItemOnPacket
                        || packet instanceof net.minecraft.network.protocol.game.ServerboundPlayerActionPacket));
    }

    private void release() {
        blockHolder.release();
    }

    private void resetCycle() {
        stopUse = false;
        runThisTick = false;
        nextCycleTick = -1;
    }

    private Action classifyAction(ItemStack stack) {
        ItemUseAnimation animation = stack.getUseAnimation();
        boolean isSword = stack.is(ItemTags.SWORDS);
        boolean isPotion = stack.getItem() instanceof PotionItem;
        boolean isFood = !isPotion && stack.has(DataComponents.FOOD);

        if (animation == ItemUseAnimation.BLOCK) {
            return Action.BLOCKABLE;
        }
        if (animation == ItemUseAnimation.BOW) {
            return bowEnabled.getValue() ? Action.BOW : Action.NONE;
        }
        if (animation == ItemUseAnimation.NONE) {
            return isSword && swordEnabled.getValue() ? Action.BLOCKABLE : Action.NONE;
        }
        if (isPotion) {
            return potionEnabled.getValue() ? Action.USEABLE : Action.NONE;
        }
        if (isFood) {
            return foodEnabled.getValue() ? Action.USEABLE : Action.NONE;
        }
        return Action.USEABLE;
    }

    private void onWatchdog2Slowdown(SlowdownEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }
        boolean sword = player.getUseItem().is(net.minecraft.tags.ItemTags.SWORDS);

        if (!this.watchdog2OnSlab || player.onGround()) {
            if (action == Action.USEABLE || action == Action.BOW) {
                event.setCancelled();
            }
        }

        if (swordEnabled.getValue() && player.isUsingItem() && sword) {
            ClientPacketListener connection = minecraft.getConnection();
            if (connection != null) {
                int slot = player.getInventory().getSelectedSlot();

                connection.send(new ServerboundSetCarriedItemPacket(
                        slot % 7 + (int) (Math.random() * 2.0) + 1));
                connection.send(new ServerboundSetCarriedItemPacket(slot));
            }
            event.setCancelled();
        }
    }

    @Subscribe
    public void onWatchdog2Packet(PreMovementPacketEvent event) {
        if (mode.getValue() != Mode.WATCHDOG_2) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        boolean sword = player.isUsingItem() && player.getUseItem().is(net.minecraft.tags.ItemTags.SWORDS);

        double y = event.getY();
        if (Math.abs(y - Math.round(y)) > 0.03 && player.onGround()) {
            this.watchdog2OnSlab = true;
        } else if (!player.isUsingItem() && slowDownOnSlabs.getValue()) {
            this.watchdog2OnSlab = false;
        }

        if (player.isUsingItem() && !sword) {
            this.watchdog2AirTicks = player.onGround() ? 0 : this.watchdog2AirTicks + 1;
            if (this.watchdog2AirTicks < 2 && player.onGround() && !this.watchdog2OnSlab) {
                event.setY(event.getY() + 0.001);
            }
        }

        if (this.watchdog2OnSlab && !player.onGround() && player.isUsingItem() && !sword
                && slowDownOnSlabs.getValue()) {
            player.setDeltaMovement(player.getDeltaMovement().x * 0.1,
                    player.getDeltaMovement().y, player.getDeltaMovement().z * 0.1);
        }
    }

    @Subscribe
    public void onWatchdog2Use(ItemUseEvent event) {
        if (mode.getValue() != Mode.WATCHDOG_2) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || player.getMainHandItem().isEmpty()) {
            return;
        }
        if (action != Action.USEABLE && action != Action.BOW && !player.isUsingItem()) {
            return;
        }
        if (player.onGround() && !this.watchdog2OnSlab) {
            player.jumpFromGround();
            event.setCancelled();
        }
    }

    @Subscribe
    public void onSlowdown(SlowdownEvent event) {
        switch (mode.getValue()) {
            case VANILLA -> event.setCancelled();
            case UNIVERSAL -> {
                if (!universalSlowdown.getValue()) {
                    event.setCancelled();
                }
            }
            case WATCHDOG -> {
                LocalPlayer player = Minecraft.getInstance().player;
                if (player != null && action != Action.BOW
                        && (action != Action.USEABLE || blockHolder.isBlocking())
                        && player.tickCount - slotChangeTick != 1) {
                    event.setCancelled();
                }
            }
            case WATCHDOG_PREDICTION -> onWatchdogPredictionSlowdown(event);
            case WATCHDOG_2 -> onWatchdog2Slowdown(event);
            case MATRIX -> onMatrixSlowdown(event);
            case GRIM_30 -> onGrim30Slowdown(event);

            case INTAVE -> onMatrixSlowdown(event);
        }
    }

    private void onMatrixSlowdown(SlowdownEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !player.isUsingItem()) {
            return;
        }
        ItemStack mainHand = player.getMainHandItem();
        if (swordEnabled.getValue() && mainHand.is(ItemTags.SWORDS)
                || foodEnabled.getValue() && isFoodItem(mainHand)
                || potionEnabled.getValue() && isPotionItem(mainHand)
                || bowEnabled.getValue() && action == Action.BOW) {
            event.setCancelled();
        }
    }

    @Subscribe
    public void onMatrixTick(PreGameTickEvent event) {
        if (mode.getValue() != Mode.MATRIX) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !player.isUsingItem()) {
            return;
        }
        ItemStack mainHand = player.getMainHandItem();
        boolean matches = swordEnabled.getValue() && mainHand.is(ItemTags.SWORDS)
                || foodEnabled.getValue() && isFoodItem(mainHand)
                || potionEnabled.getValue() && isPotionItem(mainHand)
                || bowEnabled.getValue() && action == Action.BOW;
        if (!matches) {
            return;
        }
        Vec3 motion = player.getDeltaMovement();
        if (GroundTickTracker.getGroundTicks() > 1) {
            float yawRad = (float) Math.toRadians(player.getYRot());
            double dx = -Math.sin(yawRad) * 0.0265;
            double dz = Math.cos(yawRad) * 0.0265;
            player.setDeltaMovement(dx, motion.y, dz);
        } else {
            double factor = SpeedModule.INSTANCE.isEnabled() ? 0.99 : 0.992;
            player.setDeltaMovement(motion.x * factor, motion.y, motion.z * factor);
        }
    }

    @Subscribe
    public void onGrim30Tick(PreGameTickEvent event) {
        if (mode.getValue() != Mode.GRIM_30) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }

        grimSilentYaw = null;
        boolean strafeKeyDown = Minecraft.getInstance().options.keyLeft.isDown() || Minecraft.getInstance().options.keyRight.isDown();

        if (player.isUsingItem() && !player.onGround() && !strafeKeyDown) {
            rotateFortyFiveOff(player);
        }

        Vec3 stuck = ((cc.aerial.client.mixin.EntityAccessor) player).aerial$getStuckSpeedMultiplier();
        if (stuck.lengthSqr() > 1.0E-7) {
            strafe(player, 0.64);
        }

        if (player.isUsingItem() && GroundTickTracker.getGroundTicks() > 1 && !Minecraft.getInstance().options.keyJump.isDown()) {
            double nudge = SpeedModule.INSTANCE.isEnabled() ? 1.0E-4 : 2.0E-4;
            moveFlying(player, nudge);
            if (!strafeKeyDown && action != Action.BOW) {
                rotateFortyFiveOff(player);
            }
        }

        Input keyPresses = ((ClientInputAccessor) player.input).aerial$getKeyPresses();
        if (player.isUsingItem() && keyPresses.forward()) {
            player.setSprinting(true);
        }
    }

    private void rotateFortyFiveOff(LocalPlayer player) {
        grimSilentYaw = player.getYRot() + 45.0f;
    }

    private Float grimSilentYaw;

    public Float getGrimMovementYaw() {
        return mode.getValue() == Mode.GRIM_30 && isEnabled() ? grimSilentYaw : null;
    }

    @Subscribe
    public void onGrim30MovementPacket(PreMovementPacketEvent event) {
        if (mode.getValue() == Mode.GRIM_30 && grimSilentYaw != null) {
            event.setYaw(grimSilentYaw);
        }
    }

    @Subscribe(priority = 1)
    public void onGrim30MoveInput(MoveInputEvent event) {
        if (mode.getValue() != Mode.GRIM_30 || grimSilentYaw == null) {
            return;
        }
        ServerRotation.submit(grimSilentYaw, 0);
        if (!MovementFixModule.INSTANCE.isFixMovement()) {
            MovementFixModule.applyNormalFix(event);
        }
    }

    private static void moveFlying(LocalPlayer player, double magnitude) {
        if (!isMoving(player)) {
            return;
        }
        double direction = movementDirection(player);
        Vec3 motion = player.getDeltaMovement();
        player.setDeltaMovement(motion.x - Math.sin(direction) * magnitude, motion.y,
                motion.z + Math.cos(direction) * magnitude);
    }

    private static void strafe(LocalPlayer player, double speed) {
        if (!isMoving(player)) {
            return;
        }
        double direction = movementDirection(player);
        player.setDeltaMovement(-Math.sin(direction) * speed, player.getDeltaMovement().y,
                Math.cos(direction) * speed);
    }

    private static boolean isMoving(LocalPlayer player) {
        Vec2 move = player.input.getMoveVector();
        return move.x != 0.0f || move.y != 0.0f;
    }

    private static double movementDirection(LocalPlayer player) {
        Vec2 move = player.input.getMoveVector();
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

    @Subscribe
    public void onIntavePreMotion(PreMovementPacketEvent event) {
        if (mode.getValue() != Mode.INTAVE) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.gameMode == null) {
            return;
        }
        ItemStack mainHand = player.getMainHandItem();
        if (mainHand.isEmpty()) {
            return;
        }
        if (!player.isUsingItem()) {
            if (intaveUsingItem) {
                intaveUsingItem = false;
                release();
            }
            return;
        }
        if (swordEnabled.getValue() && mainHand.is(ItemTags.SWORDS)) {
            block();
            if (player.tickCount % 5 == 0) {
                mc.gameMode.releaseUsingItem(player);
                release();
                mc.gameMode.useItem(player, net.minecraft.world.InteractionHand.MAIN_HAND);
            }
        } else if (foodEnabled.getValue() && isFoodItem(mainHand)
                || bowEnabled.getValue() && action == Action.BOW) {
            block();
        }
        intaveUsingItem = true;
    }

    private boolean intaveUsingItem;

    private void onGrim30Slowdown(SlowdownEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        if (player.isUsingItem() && !SpeedModule.INSTANCE.isEnabled()) {
            moveFlying(player, 1.0E-4);
        }

        int groundTicks = GroundTickTracker.getGroundTicks();
        int airTicks = GroundTickTracker.getAirTicks();
        boolean eligibleTick = groundTicks == 1
                || (airTicks % 2 == 0 && !player.onGround())
                || (groundTicks % 2 == 1 && player.onGround());
        if (!eligibleTick) {
            return;
        }
        ItemStack mainHand = player.getMainHandItem();
        if (foodEnabled.getValue() && player.isUsingItem() && isFoodItem(mainHand)) {
            event.setCancelled();
        }
        if (potionEnabled.getValue() && player.isUsingItem() && isPotionItem(mainHand)) {
            event.setCancelled();
        }
        if (swordEnabled.getValue() && player.isUsingItem() && mainHand.is(ItemTags.SWORDS)) {
            event.setCancelled();
        }
        if (bowEnabled.getValue() && player.isUsingItem() && action == Action.BOW) {
            event.setCancelled();
        }
    }

    private void onWatchdogPredictionSlowdown(SlowdownEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }
        ItemStack mainHand = player.getMainHandItem();
        boolean pastSpoofWindow = predictionUsingTicks > predictionMaxPingSpoof.getValue().intValue();

        if (swordEnabled.getValue() && mainHand.is(ItemTags.SWORDS) && player.isUsingItem()) {
            mc.gameMode.releaseUsingItem(player);
            release();
            mc.gameMode.useItem(player, net.minecraft.world.InteractionHand.MAIN_HAND);
            event.setCancelled();
            return;
        }
        if (foodEnabled.getValue() && action == Action.USEABLE && isFoodItem(mainHand) && pastSpoofWindow) {
            event.setCancelled();
        }
        if (potionEnabled.getValue() && action == Action.USEABLE && isPotionItem(mainHand) && pastSpoofWindow) {
            event.setCancelled();
        }
        if (bowEnabled.getValue() && action == Action.BOW && pastSpoofWindow) {
            event.setCancelled();
        }
    }

    private static boolean isPotionItem(ItemStack stack) {
        return stack.getItem() instanceof net.minecraft.world.item.PotionItem;
    }

    private static boolean isFoodItem(ItemStack stack) {
        return !isPotionItem(stack) && stack.has(DataComponents.FOOD);
    }

    @Subscribe(priority = 2)
    public void onPreGameTick(PreGameTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;

        if (player == null || mc.gui.screen() != null) {
            action = Action.NONE;
        } else {
            action = classifyAction(player.getMainHandItem());
        }

        if (mode.getValue() == Mode.WATCHDOG) {
            if (player != null) {
                int selected = player.getInventory().getSelectedSlot();
                if (selected != lastSelectedSlot) {
                    lastSelectedSlot = selected;
                    release();
                    resetCycle();
                    slotChangeTick = player.tickCount;
                }
            }

            if (player == null || mc.gui.screen() != null) {
                resetCycle();
                release();
            }
        } else if (mode.getValue() == Mode.WATCHDOG_PREDICTION) {
            tickWatchdogPrediction(player, mc);
        } else if (mode.getValue() == Mode.UNIVERSAL) {
            if (player == null || mc.gui.screen() != null) {
                release();
                return;
            }
            if (stopUse) {
                block();
                MouseHelper.getRightButton().setDisabled();
                stopUse = false;
            } else if (action == Action.BLOCKABLE || !player.isUsingItem()) {
                release();
            }
        }
    }

    private void tickWatchdogPrediction(LocalPlayer player, Minecraft mc) {
        if (player == null || mc.gui.screen() != null) {
            predictionUsingTicks = 0;
            predictionWasUsingItem = false;
            release();
            return;
        }

        boolean usingTrackedItem = player.isUsingItem() && (action == Action.USEABLE || action == Action.BOW);
        if (usingTrackedItem) {
            predictionUsingTicks++;
            if (predictionUsingTicks > predictionMaxPingSpoof.getValue().intValue()) {
                block();
            }
            predictionWasUsingItem = true;
        } else if (predictionWasUsingItem) {
            predictionUsingTicks = 0;
            predictionWasUsingItem = false;
            release();
        }

        if (predictionUsingTicks > predictionWhenToFinishEating.getValue().intValue()) {
            MouseHelper.getRightButton().setDisabled();
        }

        if (predictionNonBlinkSpeedBypass.getValue() && player.isUsingItem()
                && predictionUsingTicks <= predictionMaxPingSpoof.getValue().intValue()) {
            Input keyPresses = ((ClientInputAccessor) player.input).aerial$getKeyPresses();
            if (keyPresses.forward()) {
                player.setSprinting(true);
            }
        }

        tickWatchdogPredictionRotationFake(player, mc);
    }

    private void tickWatchdogPredictionRotationFake(LocalPlayer player, Minecraft mc) {
        if (action != Action.USEABLE || !player.isUsingItem()) {
            return;
        }
        if (player.getTicksUsingItem() <= 5) {
            return;
        }
        if (player.onGround() && GroundTickTracker.getGroundTicks() <= 2) {
            return;
        }
        if (mc.options.keyLeft.isDown() || mc.options.keyRight.isDown()) {
            return;
        }
        KillauraModule killaura = KillauraModule.INSTANCE;
        if (killaura.isEnabled() && killaura.getTargeting().getTarget() != null) {
            return;
        }
        Vec2 target = new Vec2(player.getYRot() + 45.0f, player.getXRot());
        RotationHelper.getHandler().rotate(target, new LinearRotationModel(360.0), this);
    }

    @Subscribe
    public void onPostHandleInput(PostHandleInputEvent event) {
        if (mode.getValue() != Mode.WATCHDOG) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || action != Action.BLOCKABLE) {
            return;
        }
        if (stopUse && player.isUsingItem()) {
            block();
            Minecraft.getInstance().gameMode.releaseUsingItem(player);
            stopUse = false;
        }
    }

    @Subscribe
    public void onReceivePacket(ReceivePacketEvent event) {
        if (mode.getValue() != Mode.WATCHDOG && mode.getValue() != Mode.UNIVERSAL) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (event.getPacket() instanceof ClientboundEntityEventPacket entityEvent
                && player != null && entityEvent.getEntity(Minecraft.getInstance().level) == player) {
            release();
        }
    }

    @Subscribe(priority = 1)
    public void onMouseHandleInput(MouseHandleInputEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }

        if (mode.getValue() == Mode.WATCHDOG) {
            onWatchdogMouseInput(player);
        } else if (mode.getValue() == Mode.UNIVERSAL) {
            onUniversalMouseInput(player);
        }
    }

    private void onWatchdogMouseInput(LocalPlayer player) {
        MouseButton rightButton = MouseHelper.getRightButton();
        runThisTick = false;

        if (rightButton.isDown() && action == Action.BLOCKABLE) {
            int age = player.tickCount;

            if (nextCycleTick < 0) {
                nextCycleTick = age;
            }

            if (age >= nextCycleTick) {
                if (blockHolder.isBlocking()) {
                    release();
                }
                runThisTick = true;
                nextCycleTick = age + 2;
            } else if (!blockHolder.isBlocking()) {
                block();
            }
        } else {
            resetCycle();
            if (!player.isUsingItem()) {
                release();
            } else if (!blockHolder.isBlocking() && action == Action.BLOCKABLE) {
                block();
            }
        }

        if (action == Action.BLOCKABLE) {
            if (rightButton.isDown()) {
                if (runThisTick) {
                    if (!player.isUsingItem() || !blockHolder.isBlocking()) {
                        Block blockOver = PlayerUtility.getBlockOver();
                        if (InventoryUtility.isBlockInteractable(blockOver) || Minecraft.getInstance().gameMode.isDestroying()) {
                            return;
                        }
                        stopUse = true;
                        rightButton.setPressed();
                    } else {
                        rightButton.setDisabled();
                    }
                } else {
                    rightButton.setDisabled();
                    if (!blockHolder.isBlocking()) {
                        block();
                    }
                }
            } else {
                stopUse = false;
            }
        } else {
            stopUse = false;
        }
    }

    private void onUniversalMouseInput(LocalPlayer player) {
        MouseButton rightButton = MouseHelper.getRightButton();
        if (action != Action.BLOCKABLE || !rightButton.isDown()) {
            return;
        }

        Input keyPresses = ((ClientInputAccessor) player.input).aerial$getKeyPresses();
        Vec3 velocity = player.getDeltaMovement();
        boolean aboutToLand = !keyPresses.jump()
                || (!player.onGround() && (velocity.y >= 0.0
                        || PlayerUtility.isBoxEmpty(player.level(), player.getBoundingBox().move(0.0, velocity.y, 0.0))));

        if (!aboutToLand) {
            rightButton.setDisabled();
            return;
        }

        if (!player.isUsingItem() || !blockHolder.isBlocking()) {
            Block blockOver = PlayerUtility.getBlockOver();
            if (InventoryUtility.isBlockInteractable(blockOver) || Minecraft.getInstance().gameMode.isDestroying()) {
                return;
            }
            stopUse = true;
            rightButton.setPressed();
        } else {
            rightButton.setDisabled();
        }
    }
}
