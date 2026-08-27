package cc.aerial.client.features.impl.utility;

import cc.aerial.client.event.impl.game.PostGameTickEvent;
import cc.aerial.client.event.impl.game.chat.ChatReceivedEvent;
import cc.aerial.client.event.impl.game.packet.ReceivePacketEvent;
import cc.aerial.client.event.impl.render.Render2DEvent;
import cc.aerial.client.event.subscriber.Subscribe;
import cc.aerial.client.features.Module;
import cc.aerial.client.features.ModuleCategory;
import cc.aerial.client.features.impl.visual.InterfaceModule;
import cc.aerial.client.notification.NotificationManager;
import cc.aerial.client.notification.NotificationType;
import cc.aerial.client.property.BooleanProperty;
import cc.aerial.client.property.GroupProperty;
import cc.aerial.client.property.NumberProperty;
import cc.aerial.client.render.BlurConsumer;
import cc.aerial.client.render.AerialBlur;
import cc.aerial.client.render.RenderUtil;
import cc.aerial.client.render.TextRenderUtil;
import cc.aerial.client.render.font.GlyphQuad;
import cc.aerial.client.render.font.AerialFont;
import cc.aerial.client.utility.HudDrag;
import cc.aerial.client.utility.BedwarsTeams;
import cc.aerial.client.utility.ChatUtility;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.block.BedBlock;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BedwarsUtilModule extends Module {
    public static final BedwarsUtilModule INSTANCE = new BedwarsUtilModule();

    private static final long ALERT_COOLDOWN_MS = 1600L;

    private static final String CHECK = "H";
    private static final String CROSS = "I";
    private static final float ICON_SCALE = 0.8f;

    private static final float ICON_NUDGE_Y = 1.0f;
    private static final int CHECK_COLOR = 0xFF87E331;
    private static final int CROSS_COLOR = 0xFFFF5050;

    private static final Pattern MAP_PATTERN = Pattern.compile("You are currently playing on (.+)");

    private final BooleanProperty bedTracker = new BooleanProperty("Bed Tracker", false);
    private final BooleanProperty bedTrackerHud = new BooleanProperty("Show HUD", true);
    private final NumberProperty bedTrackerDistance = new NumberProperty("Alert distance", 50, 5, 120, 5);
    private final NumberProperty bedTrackerFrequency = new NumberProperty("Alert frequency", 10, 1, 60, 1);
    private final GroupProperty bedTrackerGroup =
            new GroupProperty("Bed Tracker Settings", bedTrackerHud, bedTrackerDistance, bedTrackerFrequency)
                    .hideIf(() -> !bedTracker.getValue());

    private final BooleanProperty consumeAlert = new BooleanProperty("Consume Alert", false);
    private final NumberProperty consumeDistance = new NumberProperty("Max distance", 0, 0, 250, 5);
    private final BooleanProperty consumeShowDistance = new BooleanProperty("Show distance", true);
    private final BooleanProperty consumeGoldenApple = new BooleanProperty("Golden Apple", true);
    private final BooleanProperty consumeMilk = new BooleanProperty("Milk", true);
    private final BooleanProperty consumeSpeed = new BooleanProperty("Speed Potion", true);
    private final BooleanProperty consumeJump = new BooleanProperty("Jump Potion", true);
    private final BooleanProperty consumeInvis = new BooleanProperty("Invis Potion", true);
    private final GroupProperty consumeGroup =
            new GroupProperty("Consume Alert Settings", consumeDistance, consumeShowDistance,
                    consumeGoldenApple, consumeMilk, consumeSpeed, consumeJump, consumeInvis)
                    .hideIf(() -> !consumeAlert.getValue());

    private final BooleanProperty heightOverlay = new BooleanProperty("Height Overlay", false);
    private final BooleanProperty heightShowHud = new BooleanProperty("Show HUD", true);
    private final GroupProperty heightGroup =
            new GroupProperty("Height Overlay Settings", heightShowHud)
                    .hideIf(() -> !heightOverlay.getValue());

    private final BooleanProperty itemAlert = new BooleanProperty("Item Alert", false);
    private final NumberProperty itemAlertCooldown = new NumberProperty("Cooldown", 5, 1, 30, 1);
    private final BooleanProperty itemSwords = new BooleanProperty("Swords", true);
    private final BooleanProperty itemBows = new BooleanProperty("Bows", true);
    private final BooleanProperty itemPickaxes = new BooleanProperty("Pickaxes", true);
    private final BooleanProperty itemFireball = new BooleanProperty("Fireball", true);
    private final BooleanProperty itemTnt = new BooleanProperty("TNT", true);
    private final BooleanProperty itemWaterBucket = new BooleanProperty("Water Bucket", true);
    private final BooleanProperty itemPearl = new BooleanProperty("Ender Pearl", true);
    private final BooleanProperty itemGoldenApple = new BooleanProperty("Golden Apple", true);
    private final GroupProperty itemAlertGroup =
            new GroupProperty("Item Alert Settings", itemAlertCooldown, itemSwords, itemBows,
                    itemPickaxes, itemFireball, itemTnt, itemWaterBucket, itemPearl, itemGoldenApple)
                    .hideIf(() -> !itemAlert.getValue());

    private final BooleanProperty pickupAlert = new BooleanProperty("Pickup Alert", false);
    private final NumberProperty pickupCooldown = new NumberProperty("Cooldown", 3, 0, 10, 1);
    private final BooleanProperty pickupIron = new BooleanProperty("Iron", true);
    private final BooleanProperty pickupGold = new BooleanProperty("Gold", true);
    private final BooleanProperty pickupDiamond = new BooleanProperty("Diamond", true);
    private final BooleanProperty pickupEmerald = new BooleanProperty("Emerald", true);
    private final GroupProperty pickupGroup =
            new GroupProperty("Pickup Alert Settings", pickupCooldown, pickupIron, pickupGold,
                    pickupDiamond, pickupEmerald)
                    .hideIf(() -> !pickupAlert.getValue());

    private final BooleanProperty shopHelper = new BooleanProperty("Shop Helper", false);
    private final BooleanProperty shopHighlight = new BooleanProperty("Highlight affordable", true);
    private final BooleanProperty shopReplaceClicks = new BooleanProperty("Replace clicks", false);
    private final BooleanProperty shopPreventDuplicate = new BooleanProperty("Prevent duplicate", false);
    private final NumberProperty shopOpacity = new NumberProperty("Opacity", 50, 0, 100, 5);
    private final GroupProperty shopGroup =
            new GroupProperty("Shop Helper Settings", shopHighlight, shopReplaceClicks,
                    shopPreventDuplicate, shopOpacity)
                    .hideIf(() -> !shopHelper.getValue());

    private final BooleanProperty trapNotifier = new BooleanProperty("Trap Notifier", false);
    private final BooleanProperty trapTriggerAlert = new BooleanProperty("Triggered trap alert", true);
    private final BooleanProperty trapMissingReminder = new BooleanProperty("Missing trap reminder", true);
    private final GroupProperty trapGroup =
            new GroupProperty("Trap Notifier Settings", trapTriggerAlert, trapMissingReminder)
                    .hideIf(() -> !trapNotifier.getValue());

    private final BooleanProperty upgradeAlert = new BooleanProperty("Upgrade Alert", false);

    private final BooleanProperty upgradeHud = new BooleanProperty("Upgrade HUD", false);
    private final BooleanProperty upgradeShowSharpness = new BooleanProperty("Sharpness", true);
    private final BooleanProperty upgradeShowProtection = new BooleanProperty("Protection", true);
    private final BooleanProperty upgradeShowTraps = new BooleanProperty("Traps", true);
    private final BooleanProperty upgradeShowHealPool = new BooleanProperty("Heal Pool", true);
    private final BooleanProperty upgradeShowForge = new BooleanProperty("Forge", true);
    private final GroupProperty upgradeHudGroup =
            new GroupProperty("Upgrade HUD Settings", upgradeShowSharpness, upgradeShowProtection,
                    upgradeShowTraps, upgradeShowHealPool, upgradeShowForge)
                    .hideIf(() -> !upgradeHud.getValue());

    private final BooleanProperty removeBackground = new BooleanProperty("Remove Background", false);

    private final NumberProperty bedHudX = new NumberProperty("Bed HUD X", 6.0, -10000.0, 10000.0, 1.0).hideIf(() -> true);
    private final NumberProperty bedHudY = new NumberProperty("Bed HUD Y", 60.0, -10000.0, 10000.0, 1.0).hideIf(() -> true);
    private final NumberProperty heightHudX = new NumberProperty("Height HUD X", 6.0, -10000.0, 10000.0, 1.0).hideIf(() -> true);
    private final NumberProperty heightHudY = new NumberProperty("Height HUD Y", 84.0, -10000.0, 10000.0, 1.0).hideIf(() -> true);
    private final NumberProperty upgradeHudX = new NumberProperty("Upgrade HUD X", 6.0, -10000.0, 10000.0, 1.0).hideIf(() -> true);
    private final NumberProperty upgradeHudY = new NumberProperty("Upgrade HUD Y", 108.0, -10000.0, 10000.0, 1.0).hideIf(() -> true);

    private static AerialFont font;
    private static AerialFont boldFont;
    private static AerialFont iconFont;

    private final Map<String, Float> iconOffsets = new HashMap<>();

    private final Map<UUID, Long> lastAlert = new HashMap<>();
    private final Map<UUID, ItemStack> consuming = new HashMap<>();
    private final Map<String, String> seenUpgrades = new HashMap<>();

    private BlockPos bedPos;
    private long bedScanAt;

    private int mapHeight = -1;
    private boolean awaitingMapReply;
    private static Map<String, Integer> mapHeights;

    private int sharpnessLevel;
    private int protectionLevel;
    private int featherFallingLevel;
    private String trapName = "";
    private boolean healPool;
    private String forgeLevel = "";

    private boolean trapTriggered;
    private int trapMissingSeconds = -1;
    private boolean trapMissingWarned;
    private int slowTickCounter;

    private NumberProperty draggingX;
    private float dragOffsetX;
    private float dragOffsetY;

    private BedwarsUtilModule() {
        super("Bedwars Util", "Bedwars helpers -- trackers, alerts and HUDs, each independently toggleable.",
                ModuleCategory.UTILITY);
        addProperties(
                bedTracker, bedTrackerGroup,
                consumeAlert, consumeGroup,
                heightOverlay, heightGroup,
                itemAlert, itemAlertGroup,
                pickupAlert, pickupGroup,
                shopHelper, shopGroup,
                trapNotifier, trapGroup,
                upgradeAlert,
                upgradeHud, upgradeHudGroup,
                removeBackground,
                bedHudX, bedHudY, heightHudX, heightHudY, upgradeHudX, upgradeHudY);
    }

    private static void ensureFontsLoaded() {
        if (font == null) {
            font = AerialFont.createFromResource("OpalProductSansMedium.ttf");
            boldFont = AerialFont.createFromResource("OpalProductSansBold.ttf");
            iconFont = AerialFont.createIconFromResource("stylesicons.ttf",
                    CHECK.charAt(0), CROSS.charAt(0));
        }
    }

    @Override
    protected void onDisable() {
        reset();
    }

    private void reset() {
        lastAlert.clear();
        consuming.clear();
        seenUpgrades.clear();
        bedPos = null;
        bedScanAt = 0L;
        mapHeight = -1;
        sharpnessLevel = 0;
        protectionLevel = 0;
        featherFallingLevel = 0;
        trapName = "";
        healPool = false;
        forgeLevel = "";
        trapTriggered = false;
        trapMissingSeconds = -1;
        trapMissingWarned = false;
        slowTickCounter = 0;
    }

    private static void warn(String title, String description) {
        NotificationManager.INSTANCE.builder(NotificationType.WARNING)
                .title(title)
                .description(description)
                .duration(2500)
                .buildAndPublish();
    }

    @Subscribe
    public void onChatReceived(ChatReceivedEvent event) {
        String message = event.getText().getString();

        boolean serverMessage = !message.contains(":");

        if (serverMessage) {
            handleMapReply(event, message);
            handleGameStart(message);
            handleBedMessages(message);
            handleUpgradeMessages(message);
            handleTrapMessages(message);
        }
    }

    private void handleGameStart(String message) {
        if (message.contains("The game starts in 1 second!")) {
            bedPos = null;
            bedScanAt = System.currentTimeMillis() + 6000L;
            resetUpgrades();
            requestMapName();
        } else if (message.equals("You will respawn in 6 seconds!")) {
            requestMapName();
            bedPos = null;
            bedScanAt = System.currentTimeMillis() + 9000L;
        } else if (message.contains("Your team swapped and you are now:")) {
            bedPos = null;
            bedScanAt = System.currentTimeMillis() + 1000L;
        }
    }

    private void requestMapName() {
        if (!heightOverlay.getValue()) {
            return;
        }
        awaitingMapReply = true;
        ChatUtility.sendCommand("map");
    }

    private void handleMapReply(ChatReceivedEvent event, String message) {
        if (!awaitingMapReply) {
            return;
        }
        Matcher matcher = MAP_PATTERN.matcher(message);
        if (!matcher.find()) {
            return;
        }
        awaitingMapReply = false;
        event.setCancelled();
        String name = matcher.group(1).trim().toLowerCase(Locale.ROOT);
        mapHeight = loadMapHeights().getOrDefault(name, 255);
    }

    private static Map<String, Integer> loadMapHeights() {
        if (mapHeights != null) {
            return mapHeights;
        }
        Map<String, Integer> loaded = new HashMap<>();
        try (InputStream in = BedwarsUtilModule.class
                .getResourceAsStream("/assets/aerial/bedwars/map_heights.json")) {
            if (in != null) {
                JsonObject json = JsonParser.parseReader(
                        new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                for (String key : json.keySet()) {
                    loaded.put(key.toLowerCase(Locale.ROOT), json.get(key).getAsInt());
                }
            }
        } catch (Exception ignored) {
        }
        mapHeights = loaded;
        return mapHeights;
    }

    private void resetUpgrades() {
        sharpnessLevel = 0;
        protectionLevel = 0;
        featherFallingLevel = 0;
        trapName = "";
        healPool = false;
        forgeLevel = "";
        seenUpgrades.clear();
    }

    private void handleBedMessages(String message) {
        if (!bedTracker.getValue()) {
            return;
        }
        if (message.contains("BED DESTRUCTION > Your Bed") || message.contains("Your Bed was destroyed")) {
            bedPos = null;
            warn("Bed Tracker", "Your bed was destroyed");
        }
    }

    private void handleUpgradeMessages(String message) {
        if (!message.contains("purchased")) {
            return;
        }
        if (message.contains("Sharpened Swords")) {
            sharpnessLevel = 1;
        }
        if (message.contains("Reinforced Armor")) {
            protectionLevel = romanLevel(message);
        }
        if (message.contains("Cushioned Boots")) {
            featherFallingLevel = romanLevel(message);
        }
        if (message.contains("Heal Pool")) {
            healPool = true;
        }
        if (message.contains("Forge")) {
            if (message.contains("Iron")) {
                forgeLevel = "Iron";
            } else if (message.contains("Golden")) {
                forgeLevel = "Gold";
            } else if (message.contains("Emerald")) {
                forgeLevel = "Emerald";
            } else if (message.contains("Molten")) {
                forgeLevel = "Molten";
            }
        }
        if (message.contains("Trap")) {
            if (message.contains("Miner Fatigue")) {
                trapName = "Miner Fatigue";
            } else if (message.contains("Blindness")) {
                trapName = "Blindness";
            } else if (message.contains("Reveal")) {
                trapName = "Reveal";
            } else if (message.contains("Counter-Offensive")) {
                trapName = "Counter-Offensive";
            }

            trapMissingSeconds = 0;
            trapMissingWarned = true;
        }
    }

    private static int romanLevel(String message) {
        if (message.contains("IV")) {
            return 4;
        }
        if (message.contains("III")) {
            return 3;
        }
        if (message.contains("II")) {
            return 2;
        }
        return 1;
    }

    private void handleTrapMessages(String message) {
        if (!trapNotifier.getValue()) {
            return;
        }
        if (message.contains("Trap was set off!")) {
            trapMissingSeconds = 30;
            trapMissingWarned = false;
            trapName = "";
        }
        if (message.contains("Your Bed was destroyed")) {
            trapMissingSeconds = 0;
            trapMissingWarned = true;
        }
        if (trapTriggerAlert.getValue() && message.equals("Your invisibility was removed by an Reveal Trap!")) {
            warn("Trap Triggered", "Reveal");
            trapTriggered = true;
        }
    }

    @Subscribe
    public void onPostGameTick(PostGameTickEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer self = minecraft.player;
        ClientLevel level = minecraft.level;
        if (self == null || level == null) {
            return;
        }

        tickBedScan(self, level);
        tickShopHelper(self);

        slowTickCounter++;
        boolean halfSecond = slowTickCounter % 10 == 0;
        boolean second = slowTickCounter % 20 == 0;

        if (consumeAlert.getValue()) {
            tickConsumeAlert(self, level);
        }
        if (halfSecond) {
            if (bedTracker.getValue()) {
                tickBedProximity(self, level);
            }
            if (trapNotifier.getValue()) {
                tickTrapNotifier(self);
            }
        }
        if (second) {
            if (itemAlert.getValue()) {
                tickItemAlert(self, level);
            }
            if (upgradeAlert.getValue()) {
                tickUpgradeAlert(self, level);
            }
        }
    }

    private void tickBedScan(LocalPlayer self, ClientLevel level) {
        if (!bedTracker.getValue() || bedPos != null || bedScanAt == 0L
                || System.currentTimeMillis() < bedScanAt) {
            return;
        }
        bedScanAt = 0L;
        bedPos = findNearbyBed(level, self.blockPosition(), 25);
        if (bedPos == null) {
            warn("Bed Tracker", "Could not locate your bed");
        }
    }

    private static BlockPos findNearbyBed(ClientLevel level, BlockPos centre, int radius) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    cursor.set(centre.getX() + dx, centre.getY() + dy, centre.getZ() + dz);
                    if (level.getBlockState(cursor).getBlock() instanceof BedBlock) {
                        return cursor.immutable();
                    }
                }
            }
        }
        return null;
    }

    private void tickBedProximity(LocalPlayer self, ClientLevel level) {
        if (bedPos == null) {
            return;
        }
        long now = System.currentTimeMillis();
        int limit = (int) bedTrackerDistance.getValue().doubleValue();
        long frequency = (long) bedTrackerFrequency.getValue().doubleValue() * 1000L;

        for (Player player : level.players()) {
            if (!isEnemy(self, player)) {
                continue;
            }
            int distance = (int) Math.sqrt(player.distanceToSqr(
                    bedPos.getX() + 0.5, bedPos.getY() + 0.5, bedPos.getZ() + 0.5));
            if (distance > limit) {
                continue;
            }
            UUID uuid = player.getUUID();
            if (now - lastAlert.getOrDefault(uuid, 0L) < frequency) {
                continue;
            }
            lastAlert.put(uuid, now);
            warn("Bed Tracker", BedwarsTeams.describe(player) + " is " + distance + "m from your bed");
        }
    }

    private void tickConsumeAlert(LocalPlayer self, ClientLevel level) {
        int limit = (int) consumeDistance.getValue().doubleValue();
        for (Player player : level.players()) {
            if (!isEnemy(self, player)) {
                continue;
            }
            if (limit > 0 && player.distanceTo(self) > limit) {
                continue;
            }
            UUID uuid = player.getUUID();
            ItemStack held = player.getMainHandItem();
            ItemStack previous = consuming.get(uuid);

            if (player.isUsingItem() && isConsumable(held)) {
                if (previous == null || !ItemStack.isSameItemSameComponents(held, previous)) {
                    consuming.put(uuid, held.copy());
                }
            } else if (previous != null) {
                consuming.remove(uuid);
                if (held.isEmpty() || !ItemStack.isSameItemSameComponents(held, previous)) {
                    alertConsume(self, player, previous);
                }
            }
        }
    }

    private static boolean isConsumable(ItemStack stack) {
        return !stack.isEmpty() && (stack.is(Items.GOLDEN_APPLE) || stack.is(Items.MILK_BUCKET)
                || stack.is(Items.POTION));
    }

    private void alertConsume(LocalPlayer self, Player player, ItemStack stack) {
        UUID uuid = player.getUUID();
        long now = System.currentTimeMillis();
        if (now - lastAlert.getOrDefault(uuid, 0L) < ALERT_COOLDOWN_MS) {
            return;
        }

        String what = null;
        if (stack.is(Items.GOLDEN_APPLE) && consumeGoldenApple.getValue()) {
            what = "Golden Apple";
        } else if (stack.is(Items.MILK_BUCKET) && consumeMilk.getValue()) {
            what = "Milk";
        } else if (stack.is(Items.POTION)) {
            String name = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
            if (name.contains("speed") && consumeSpeed.getValue()) {
                what = "Speed Potion";
            } else if (name.contains("jump") && consumeJump.getValue()) {
                what = "Jump Potion";
            } else if (name.contains("invis") && consumeInvis.getValue()) {
                what = "Invis Potion";
            }
        }
        if (what == null) {
            return;
        }
        lastAlert.put(uuid, now);

        String suffix = consumeShowDistance.getValue()
                ? " (" + (int) player.distanceTo(self) + "m)" : "";
        warn("Consume Alert", BedwarsTeams.describe(player) + " used " + what + suffix);
    }

    private void tickItemAlert(LocalPlayer self, ClientLevel level) {
        long cooldown = (long) itemAlertCooldown.getValue().doubleValue() * 1000L;
        long now = System.currentTimeMillis();

        for (Player player : level.players()) {
            if (!isEnemy(self, player)) {
                continue;
            }
            String name = describeHeldItem(player.getMainHandItem());
            if (name == null) {
                continue;
            }
            UUID uuid = player.getUUID();
            if (now - lastAlert.getOrDefault(uuid, 0L) < cooldown) {
                continue;
            }
            lastAlert.put(uuid, now);
            warn("Item Alert", BedwarsTeams.describe(player) + " is holding " + name);
        }
    }

    private String describeHeldItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        if (itemSwords.getValue() && (stack.is(Items.IRON_SWORD) || stack.is(Items.DIAMOND_SWORD))) {
            return stack.is(Items.DIAMOND_SWORD) ? "Diamond Sword" : "Iron Sword";
        }
        if (itemBows.getValue() && stack.is(Items.BOW)) {
            return stack.isEnchanted() ? "Enchanted Bow" : "Bow";
        }
        if (itemPickaxes.getValue() && (stack.is(Items.GOLDEN_PICKAXE) || stack.is(Items.DIAMOND_PICKAXE))) {
            return stack.is(Items.DIAMOND_PICKAXE) ? "Diamond Pickaxe" : "Golden Pickaxe";
        }
        if (itemFireball.getValue() && stack.is(Items.FIRE_CHARGE)) {
            return "Fireball";
        }
        if (itemTnt.getValue() && stack.is(Items.TNT)) {
            return "TNT";
        }
        if (itemWaterBucket.getValue() && stack.is(Items.WATER_BUCKET)) {
            return "Water Bucket";
        }
        if (itemPearl.getValue() && stack.is(Items.ENDER_PEARL)) {
            return "Ender Pearl";
        }
        if (itemGoldenApple.getValue() && stack.is(Items.GOLDEN_APPLE)) {
            return "Golden Apple";
        }
        return null;
    }

    private void tickUpgradeAlert(LocalPlayer self, ClientLevel level) {
        for (Player player : level.players()) {
            if (!isEnemy(self, player)) {
                continue;
            }
            String name = BedwarsTeams.describe(player);
            if (player.getMainHandItem().isEnchanted()
                    && player.getMainHandItem().getItem().toString().contains("sword")) {
                announceUpgrade(name, "Sharpened Swords");
            }
            if (player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).isEnchanted()) {
                announceUpgrade(name, "Reinforced Armor");
            }
        }
    }

    private void announceUpgrade(String player, String upgrade) {
        if (upgrade.equals(seenUpgrades.get(player))) {
            return;
        }
        seenUpgrades.put(player, upgrade);
        warn("Upgrade Alert", player + " has " + upgrade);
    }

    private void tickTrapNotifier(LocalPlayer self) {
        boolean underTrap = self.hasEffect(MobEffects.MINING_FATIGUE) || self.hasEffect(MobEffects.BLINDNESS);
        if (trapTriggerAlert.getValue() && !trapTriggered && underTrap) {
            trapTriggered = true;
            warn("Trap Triggered", self.hasEffect(MobEffects.BLINDNESS) ? "Blindness" : "Miner Fatigue");
        }
        if (!underTrap) {
            trapTriggered = false;
        }

        if (!trapMissingReminder.getValue() || trapMissingSeconds < 0) {
            return;
        }

        if (slowTickCounter % 20 != 0) {
            return;
        }
        if (trapMissingSeconds > 0) {
            trapMissingSeconds--;
        }
        if (!trapMissingWarned && trapMissingSeconds == 0) {
            trapMissingWarned = true;
            warn("Trap Notifier", "No trap active");
        }
    }

    private static boolean isEnemy(LocalPlayer self, Player player) {
        return player.isAlive() && !BedwarsTeams.isTeammate(self, player);
    }

    @Subscribe
    public void onReceivePacket(ReceivePacketEvent event) {
        if (!pickupAlert.getValue() || !(event.getPacket() instanceof ClientboundTakeItemEntityPacket packet)) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer self = minecraft.player;
        ClientLevel level = minecraft.level;
        if (self == null || level == null) {
            return;
        }
        Entity collector = level.getEntity(packet.getPlayerId());
        Entity item = level.getEntity(packet.getItemId());
        if (!(collector instanceof Player player) || !(item instanceof ItemEntity itemEntity)) {
            return;
        }
        if (!isEnemy(self, player)) {
            return;
        }
        String resource = describeResource(itemEntity.getItem());
        if (resource == null) {
            return;
        }
        UUID uuid = player.getUUID();
        long now = System.currentTimeMillis();
        long cooldown = (long) pickupCooldown.getValue().doubleValue() * 1000L;
        if (now - lastAlert.getOrDefault(uuid, 0L) < cooldown) {
            return;
        }
        lastAlert.put(uuid, now);
        warn("Pickup Alert", BedwarsTeams.describe(player) + " picked up " + resource);
    }

    private String describeResource(ItemStack stack) {
        if (pickupIron.getValue() && stack.is(Items.IRON_INGOT)) {
            return "Iron";
        }
        if (pickupGold.getValue() && stack.is(Items.GOLD_INGOT)) {
            return "Gold";
        }
        if (pickupDiamond.getValue() && stack.is(Items.DIAMOND)) {
            return "Diamond";
        }
        if (pickupEmerald.getValue() && stack.is(Items.EMERALD)) {
            return "Emerald";
        }
        return null;
    }

    @Subscribe
    public void onRender2D(Render2DEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer self = minecraft.player;
        if (self == null || minecraft.level == null) {
            return;
        }
        boolean chatOpen = minecraft.gui.screen() instanceof ChatScreen;

        if (minecraft.gui.screen() != null && !chatOpen) {
            return;
        }
        ensureFontsLoaded();
        if (!chatOpen) {
            draggingX = null;
        }

        if (bedTracker.getValue() && bedTrackerHud.getValue()) {
            List<Line> lines = List.of(bedPos == null
                    ? Line.icon("Bed: ", CROSS, CROSS_COLOR)
                    : Line.icon("Bed: ", CHECK, CHECK_COLOR, "  " + distanceToBed(self) + "m"));
            drawPanel(event.extractor(), bedHudX, bedHudY, "Bed", lines, chatOpen);
        }
        if (heightOverlay.getValue() && heightShowHud.getValue() && mapHeight > 0) {
            drawPanel(event.extractor(), heightHudX, heightHudY, "Height",
                    List.of(Line.text(self.blockPosition().getY() + " / " + mapHeight)), chatOpen);
        }
        if (upgradeHud.getValue()) {
            List<UpgradeIcon> icons = collectUpgradeIcons();
            if (!icons.isEmpty()) {
                drawUpgradeRow(event.extractor(), icons, chatOpen);
            }
        }
    }

    private record Line(String prefix, String icon, int iconColor, String suffix) {
        static Line text(String text) {
            return new Line(text, null, 0, "");
        }

        static Line icon(String prefix, String icon, int color) {
            return new Line(prefix, icon, color, "");
        }

        static Line icon(String prefix, String icon, int color, String suffix) {
            return new Line(prefix, icon, color, suffix);
        }
    }

    private record UpgradeIcon(ItemStack stack, String label) {
    }

    private final Map<Item, ItemStack> iconStacks = new HashMap<>();

    private ItemStack icon(Item item) {
        return iconStacks.computeIfAbsent(item, ItemStack::new);
    }

    private List<UpgradeIcon> collectUpgradeIcons() {
        List<UpgradeIcon> icons = new ArrayList<>();

        if (upgradeShowSharpness.getValue() && sharpnessLevel > 0) {
            icons.add(new UpgradeIcon(icon(Items.IRON_SWORD), ""));
        }
        if (upgradeShowProtection.getValue() && protectionLevel > 0) {
            icons.add(new UpgradeIcon(icon(Items.IRON_CHESTPLATE), roman(protectionLevel)));
        }
        if (upgradeShowTraps.getValue() && featherFallingLevel > 0) {
            icons.add(new UpgradeIcon(icon(Items.DIAMOND_BOOTS), roman(featherFallingLevel)));
        }
        if (upgradeShowHealPool.getValue() && healPool) {
            icons.add(new UpgradeIcon(icon(Items.BEACON), ""));
        }
        if (upgradeShowForge.getValue() && !forgeLevel.isEmpty()) {
            icons.add(new UpgradeIcon(icon(Items.FURNACE), forgeLevel));
        }
        if (upgradeShowTraps.getValue() && !trapName.isEmpty()) {
            icons.add(new UpgradeIcon(icon(Items.TRIPWIRE_HOOK), trapName));
        }
        return icons;
    }

    private static String roman(int level) {
        return switch (level) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            default -> String.valueOf(level);
        };
    }

    private void drawUpgradeRow(GuiGraphicsExtractor extractor, List<UpgradeIcon> icons,
                                boolean chatOpen) {
        float padding = 6f;
        float titleSize = 8f;
        float bodySize = 7.5f;

        float iconSize = 11f;

        float labelGap = 1.5f;
        float entryGap = 6f;

        float row = 0.0f;
        for (int i = 0; i < icons.size(); i++) {
            if (i > 0) {
                row += entryGap;
            }
            row += iconSize;
            if (!icons.get(i).label().isEmpty()) {
                row += labelGap + font.stringWidth(icons.get(i).label(), bodySize);
            }
        }

        float width = Math.max(boldFont.stringWidth("Upgrades", titleSize), row) + padding * 2f;
        float height = padding * 2f + titleSize + 4f + iconSize;

        if (chatOpen) {
            handleDragging(upgradeHudX, upgradeHudY, width, height);
        }

        float x = upgradeHudX.getValue().floatValue();
        float y = upgradeHudY.getValue().floatValue();

        if (!removeBackground.getValue()) {
            AerialBlur.drawGlass(extractor, BlurConsumer.OVERLAY, x, y, width, height, 6f,
                    0x78141418, 1f, null);
        }

        int accent = InterfaceModule.INSTANCE.getTheme().getAccentColor(0, 0).getRGB() | 0xFF000000;
        TextRenderUtil.drawString(extractor, boldFont, "Upgrades", x + padding, y + padding,
                titleSize, accent);

        float rowY = y + padding + titleSize + 4f;

        float labelY = rowY + iconSize * 0.5f - inkCenter(font, "H", bodySize);

        float cursorX = x + padding;
        for (UpgradeIcon icon : icons) {
            drawItem(extractor, icon.stack(), cursorX, rowY, iconSize / 16f);
            cursorX += iconSize;
            if (!icon.label().isEmpty()) {
                cursorX += labelGap;
                cursorX += TextRenderUtil.drawString(extractor, font, icon.label(),
                        cursorX, labelY, bodySize, 0xFFE6E6EA);
            }
            cursorX += entryGap;
        }
    }

    private static void drawItem(GuiGraphicsExtractor extractor, ItemStack stack,
                                 float x, float y, float scale) {
        extractor.pose().pushMatrix();
        extractor.pose().translate(x, y);
        extractor.pose().scale(scale, scale);
        extractor.item(stack, 0, 0);
        extractor.pose().popMatrix();
    }

    private int distanceToBed(LocalPlayer self) {
        if (bedPos == null) {
            return 0;
        }
        return (int) Math.sqrt(self.distanceToSqr(
                bedPos.getX() + 0.5, bedPos.getY() + 0.5, bedPos.getZ() + 0.5));
    }

    private void drawPanel(GuiGraphicsExtractor extractor, NumberProperty px, NumberProperty py,
                           String title, List<Line> lines, boolean chatOpen) {
        float padding = 6f;
        float titleSize = 8f;
        float bodySize = 7.5f;
        float lineHeight = bodySize + 3f;

        float width = boldFont.stringWidth(title, titleSize);
        for (Line line : lines) {
            width = Math.max(width, lineWidth(line, bodySize));
        }
        width += padding * 2f;
        float height = padding * 2f + titleSize + 4f + lines.size() * lineHeight;

        if (chatOpen) {
            handleDragging(px, py, width, height);
        }

        float x = px.getValue().floatValue();
        float y = py.getValue().floatValue();

        if (!removeBackground.getValue()) {
            AerialBlur.drawGlass(extractor, BlurConsumer.OVERLAY, x, y, width, height, 6f,
                    0x78141418, 1f, null);
        }

        int accent = InterfaceModule.INSTANCE.getTheme().getAccentColor(0, 0).getRGB() | 0xFF000000;
        TextRenderUtil.drawString(extractor, boldFont, title, x + padding, y + padding,
                titleSize, accent);

        float cursorY = y + padding + titleSize + 4f;
        for (Line line : lines) {
            float cursorX = x + padding;

            cursorX += TextRenderUtil.drawString(extractor, font, line.prefix(),
                    cursorX, cursorY, bodySize, 0xFFE6E6EA);
            if (line.icon() != null) {
                cursorX += TextRenderUtil.drawString(extractor, iconFont, line.icon(),
                        cursorX, cursorY + iconOffsetY(line.icon(), bodySize), bodySize * ICON_SCALE,
                        line.iconColor());
            }
            if (!line.suffix().isEmpty()) {
                TextRenderUtil.drawString(extractor, font, line.suffix(),
                        cursorX, cursorY, bodySize, 0xFFE6E6EA);
            }
            cursorY += lineHeight;
        }
    }

    private float iconOffsetY(String glyph, float bodySize) {
        Float cached = iconOffsets.get(glyph);
        if (cached != null) {
            return cached;
        }
        float offset = inkCenter(iconFont, glyph, bodySize * ICON_SCALE);

        float textCenter = inkCenter(font, "H", bodySize);
        float result = textCenter - offset + ICON_NUDGE_Y;
        iconOffsets.put(glyph, result);
        return result;
    }

    private static float inkCenter(AerialFont face, String glyph, float size) {
        GlyphQuad[] quads = face.layout(glyph, 0.0f, 0.0f, size);
        if (quads.length == 0) {
            return size * 0.5f;
        }
        return (quads[0].y0 + quads[0].y1) * 0.5f;
    }

    private float lineWidth(Line line, float bodySize) {
        float width = font.stringWidth(line.prefix(), bodySize);
        if (line.icon() != null) {
            width += iconFont.stringWidth(line.icon(), bodySize * ICON_SCALE);
        }
        if (!line.suffix().isEmpty()) {
            width += font.stringWidth(line.suffix(), bodySize);
        }
        return width;
    }

    private void handleDragging(NumberProperty px, NumberProperty py, float width, float height) {
        Minecraft minecraft = Minecraft.getInstance();
        float mouseX = (float) minecraft.mouseHandler.getScaledXPos(minecraft.getWindow());
        float mouseY = (float) minecraft.mouseHandler.getScaledYPos(minecraft.getWindow());
        boolean pressed = org.lwjgl.glfw.GLFW.glfwGetMouseButton(minecraft.getWindow().handle(),
                org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;

        if (!pressed) {
            if (draggingX == px) {
                draggingX = null;
            }
            return;
        }

        float x = px.getValue().floatValue();
        float y = py.getValue().floatValue();
        boolean hovered = mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;

        if (draggingX == null && hovered) {
            draggingX = px;
            dragOffsetX = mouseX - x;
            dragOffsetY = mouseY - y;
        }
        if (draggingX == px) {
            float screenW = minecraft.getWindow().getGuiScaledWidth();
            float screenH = minecraft.getWindow().getGuiScaledHeight();
            px.setValue((double) HudDrag.clamp(mouseX - dragOffsetX, width, screenW));
            py.setValue((double) HudDrag.clamp(mouseY - dragOffsetY, height, screenH));
        }
    }

    private static final Set<String> SHOP_TITLES = Set.of(
            "Quick Buy", "Blocks", "Melee", "Armor", "Tools", "Ranged",
            "Potions", "Utility", "Rotating Items", "Upgrades & Traps");

    private static final List<List<Item>> ITEM_TIERS = List.of(
            List.of(Items.DIAMOND_SWORD, Items.IRON_SWORD, Items.STONE_SWORD),
            List.of(Items.DIAMOND_BOOTS, Items.IRON_BOOTS, Items.CHAINMAIL_BOOTS),
            List.of(Items.DIAMOND_PICKAXE, Items.GOLDEN_PICKAXE, Items.STONE_PICKAXE, Items.WOODEN_PICKAXE),
            List.of(Items.DIAMOND_AXE, Items.IRON_AXE, Items.STONE_AXE, Items.WOODEN_AXE),
            List.of(Items.STICK),
            List.of(Items.SHEARS));

    private final Map<Item, Integer> shopResources = new HashMap<>();
    private final Map<Integer, Integer> shopBestOwned = new HashMap<>();

    private void tickShopHelper(LocalPlayer self) {
        shopResources.clear();
        shopBestOwned.clear();
        if (!shopHelper.getValue() || !isShopOpen()) {
            return;
        }
        for (int i = 0; i < self.getInventory().getContainerSize(); i++) {
            ItemStack stack = self.getInventory().getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            Item item = stack.getItem();
            if (isResource(item)) {
                shopResources.merge(item, stack.getCount(), Integer::sum);
            }
            int category = categoryOf(item);
            if (category >= 0) {
                shopBestOwned.merge(category, priorityOf(item), Math::min);
            }
        }
    }

    private static boolean isResource(Item item) {
        return item == Items.IRON_INGOT || item == Items.GOLD_INGOT
                || item == Items.DIAMOND || item == Items.EMERALD;
    }

    private static int categoryOf(Item item) {
        for (int i = 0; i < ITEM_TIERS.size(); i++) {
            if (ITEM_TIERS.get(i).contains(item)) {
                return i;
            }
        }
        return -1;
    }

    private static int priorityOf(Item item) {
        int category = categoryOf(item);
        return category < 0 ? Integer.MAX_VALUE : ITEM_TIERS.get(category).indexOf(item);
    }

    private static String openShopTitle() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.gui.screen() instanceof AbstractContainerScreen<?> screen)) {
            return null;
        }
        String title = screen.getTitle().getString();
        return SHOP_TITLES.contains(title) ? title : null;
    }

    private static boolean isShopOpen() {
        return openShopTitle() != null;
    }

    public void drawShopHighlight(GuiGraphicsExtractor extractor, Slot slot) {
        if (!isEnabled() || !shopHelper.getValue() || !shopHighlight.getValue()) {
            return;
        }
        if (slot == null || !slot.hasItem() || !isShopOpen()) {
            return;
        }
        Cost cost = readCost(slot.getItem());
        if (cost == null || !shouldHighlight(slot.getItem(), cost)) {
            return;
        }
        RenderUtil.flatRect(extractor, slot.x, slot.y, 16f, 16f, resourceColor(cost.item()));
    }

    private int resourceColor(Item resource) {
        int rgb;
        if (resource == Items.IRON_INGOT) {
            rgb = 0xFFFFFF;
        } else if (resource == Items.GOLD_INGOT) {
            rgb = 0xFFAA00;
        } else if (resource == Items.DIAMOND) {
            rgb = 0x55FFFF;
        } else if (resource == Items.EMERALD) {
            rgb = 0x00AA00;
        } else {
            rgb = 0xAAAAAA;
        }
        int alpha = (int) (shopOpacity.getValue().doubleValue() / 100.0 * 255.0);
        return (Math.max(0, Math.min(255, alpha)) << 24) | rgb;
    }

    private boolean shouldHighlight(ItemStack stack, Cost cost) {
        int available = shopResources.getOrDefault(cost.item(), 0);
        boolean affordable = available >= cost.amount();
        if (!affordable) {
            return false;
        }
        String title = openShopTitle();
        if (title != null && title.contains("Upgrades & Traps")) {
            return true;
        }
        if (cost.item() == Items.DIAMOND) {
            return true;
        }
        int category = categoryOf(stack.getItem());
        if (category < 0) {
            return true;
        }
        return priorityOf(stack.getItem()) < shopBestOwned.getOrDefault(category, Integer.MAX_VALUE);
    }

    public boolean shouldReplaceShopClick(Slot slot) {
        return isEnabled() && shopHelper.getValue() && shopReplaceClicks.getValue()
                && slot != null && isShopOpen();
    }

    public boolean shouldPreventShopClick(Slot slot) {
        if (!isEnabled() || !shopHelper.getValue() || !shopPreventDuplicate.getValue()) {
            return false;
        }
        String title = openShopTitle();
        if (title == null || title.contains("Upgrades & Traps") || slot == null || !slot.hasItem()) {
            return false;
        }
        ItemStack stack = slot.getItem();
        Item item = stack.getItem();
        boolean guarded = (item == Items.STICK || categoryOf(item) == 0) && item != Items.GOLDEN_SWORD;
        if (!guarded) {
            return false;
        }
        Cost cost = readCost(stack);
        if (cost == null || shouldHighlight(stack, cost)) {
            return false;
        }
        warn("Shop Helper", "Prevented a duplicate purchase");
        return true;
    }

    private record Cost(Item item, int amount) {
    }

    private static Cost readCost(ItemStack stack) {
        LocalPlayer self = Minecraft.getInstance().player;
        for (Component line : stack.getTooltipLines(
                Item.TooltipContext.EMPTY, self, TooltipFlag.Default.NORMAL)) {
            String clean = line.getString();
            boolean costLine = clean.contains("Cost:");
            boolean tierLine = clean.contains("Tier");
            if (!costLine && !tierLine) {
                continue;
            }

            String amountText;
            String typeText;
            if (costLine) {
                String[] words = clean.trim().split("\\s+");
                if (words.length < 3) {
                    continue;
                }
                amountText = words[1];
                typeText = words[2];
            } else {
                int comma = clean.lastIndexOf(',');
                if (comma == -1 || comma + 2 >= clean.length()) {
                    continue;
                }
                String[] words = clean.substring(comma + 2).trim().split("\\s+");
                if (words.length < 2) {
                    continue;
                }
                amountText = words[0];
                typeText = words[1];
            }

            int amount;
            try {
                amount = Integer.parseInt(amountText);
            } catch (NumberFormatException ignored) {
                continue;
            }
            String type = typeText.toLowerCase(Locale.ROOT);
            if (type.contains("unlocked")) {
                return null;
            }
            if (type.startsWith("iron")) {
                return new Cost(Items.IRON_INGOT, amount);
            }
            if (type.startsWith("gold")) {
                return new Cost(Items.GOLD_INGOT, amount);
            }
            if (type.startsWith("diamond")) {
                return new Cost(Items.DIAMOND, amount);
            }
            if (type.startsWith("emerald")) {
                return new Cost(Items.EMERALD, amount);
            }
        }
        return null;
    }

    public int getMapHeight() {
        return heightOverlay.getValue() ? mapHeight : -1;
    }

    public void setMapHeight(int height) {
        this.mapHeight = height;
    }
}
