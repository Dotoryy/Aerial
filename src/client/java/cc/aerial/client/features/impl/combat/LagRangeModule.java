package cc.aerial.client.features.impl.combat;

import cc.aerial.client.event.impl.game.PostGameTickEvent;
import cc.aerial.client.event.impl.game.PreGameTickEvent;
import cc.aerial.client.event.impl.game.packet.InstantaneousSendPacketEvent;
import cc.aerial.client.event.impl.render.Render2DEvent;
import cc.aerial.client.event.subscriber.Subscribe;
import cc.aerial.client.features.Module;
import cc.aerial.client.features.ModuleCategory;
import cc.aerial.client.mixin.MultiPlayerGameModeAccessor;
import cc.aerial.client.packet.LagManager;
import cc.aerial.client.property.BooleanProperty;
import cc.aerial.client.property.ModeProperty;
import cc.aerial.client.property.NumberProperty;
import cc.aerial.client.render.CameraRenderStateHelper;
import cc.aerial.client.render.ESPUtility;
import cc.aerial.client.render.RenderUtil;
import cc.aerial.client.utility.InventoryUtility;
import cc.aerial.client.utility.PlayerUtility;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class LagRangeModule extends Module {
    public static final LagRangeModule INSTANCE = new LagRangeModule();

    private final NumberProperty closeDelay = new NumberProperty("Close delay", 150, 0, 1000, 10);
    private final NumberProperty farDelay = new NumberProperty("Far delay", 250, 0, 1000, 10);
    private final NumberProperty closeRange = new NumberProperty("Close range", 3.5, 0.5, 20.0, 0.5);

    private final NumberProperty maxLagTime = new NumberProperty("Max lag time", 2.0, 1.0, 10.0, 0.5);
    private final NumberProperty range = new NumberProperty("Range", 10.0, 3.0, 100.0, 0.5);
    private final BooleanProperty weaponsOnly = new BooleanProperty("Weapons only", true);
    private final BooleanProperty allowTools = new BooleanProperty("Allow tools", false);
    private final BooleanProperty botCheck = new BooleanProperty("Bot check", true);
    private final BooleanProperty teams = new BooleanProperty("Teams", true);
    private final ModeProperty<ShowPosition> showPosition = new ModeProperty<>("Show position", ShowPosition.NONE);

    private int tickIndex = -1;

    private long engagedDelayMs = -1L;

    private long engagementStartMs = -1L;

    private boolean capReached;

    private long delayCounter;
    private boolean hasTarget;
    private Vec3 lastPosition;
    private Vec3 currentPosition;

    private LagRangeModule() {
        super("Lag Range", "Lags your position while an opponent closes in", ModuleCategory.COMBAT);
        addProperties(closeDelay, farDelay, closeRange, maxLagTime, range, weaponsOnly, allowTools, botCheck,
                teams, showPosition);
    }

    @Override
    public String getSuffix() {
        return closeDelay.getValue().intValue() + "/" + farDelay.getValue().intValue() + "ms";
    }

    @Override
    protected void onDisable() {
        LagManager.INSTANCE.setDelay(0);
        endEngagement();
        engagedDelayMs = -1L;
        delayCounter = 0L;
        hasTarget = false;
        lastPosition = null;
        currentPosition = null;
    }

    @Subscribe(priority = -5)
    public void onPreGameTick(PreGameTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;

        LagManager.INSTANCE.setDelay(0);
        hasTarget = false;

        if (player == null || level == null) {
            endEngagement();
            return;
        }

        if (BacktrackModule.INSTANCE.isSuppressingLag()) {
            endEngagement();
            return;
        }
        if (!isSafeToLag(mc, player)) {
            endEngagement();
            return;
        }

        double eyeHeight = player.getEyeHeight();

        Vec3 serverEye = LagManager.INSTANCE.getLastPosition().add(0.0, eyeHeight, 0.0);
        Vec3 previousEye = new Vec3(player.xOld, player.yOld + eyeHeight, player.zOld);
        Vec3 currentEye = new Vec3(player.getX(), player.getY() + eyeHeight, player.getZ());

        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof Player other) || !isValidTarget(player, other)) {
                continue;
            }
            double distanceNow = distanceToBox(other, currentEye);
            if (distanceNow > range.getValue().doubleValue()) {
                continue;
            }

            if (distanceNow < distanceToBox(other, previousEye) || distanceNow < distanceToBox(other, serverEye)) {
                long delayMs = distanceNow <= closeRange.getValue().doubleValue()
                        ? closeDelay.getValue().longValue()
                        : farDelay.getValue().longValue();

                if (capReached) {
                    return;
                }
                long now = System.currentTimeMillis();
                if (engagementStartMs < 0) {
                    engagementStartMs = now;
                }
                long limit = Math.round(maxLagTime.getValue().doubleValue() * 1000.0);
                if (now - engagementStartMs > limit) {
                    capReached = true;
                    tickIndex = -1;
                    return;
                }

                LagManager.INSTANCE.setDelay(engagedTicks(delayMs));
                hasTarget = true;
                return;
            }
        }

        endEngagement();
    }

    private void endEngagement() {
        tickIndex = -1;
        engagementStartMs = -1L;
        capReached = false;
    }

    private int engagedTicks(long delayMs) {
        if (tickIndex < 0 || delayMs != engagedDelayMs) {
            engagedDelayMs = delayMs;
            tickIndex = 0;
            for (delayCounter += delayMs; delayCounter > 0L; delayCounter -= 50L) {
                tickIndex++;
            }
        }
        return tickIndex;
    }

    private boolean isSafeToLag(Minecraft mc, LocalPlayer player) {
        if (mc.gameMode != null && ((MultiPlayerGameModeAccessor) mc.gameMode).aerial$isDestroying()) {
            return false;
        }
        if (player.isUsingItem() && !player.isBlocking()) {
            return false;
        }
        if (!weaponsOnly.getValue()) {
            return true;
        }
        return isHoldingWeapon(player) || (allowTools.getValue() && isHoldingTool(player));
    }

    private static boolean isHoldingWeapon(LocalPlayer player) {
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) {
            return false;
        }
        return stack.is(ItemTags.SWORDS)
                || InventoryUtility.calculateEnchantmentLevel(stack, Enchantments.UNBREAKING) > 0;
    }

    private static boolean isHoldingTool(LocalPlayer player) {
        ItemStack stack = player.getMainHandItem();
        return stack.is(ItemTags.PICKAXES) || stack.is(ItemTags.AXES)
                || stack.is(ItemTags.SHOVELS) || stack.is(ItemTags.HOES);
    }

    private boolean isValidTarget(LocalPlayer self, Player other) {
        if (other == self || other == self.getVehicle() || !other.isAlive()) {
            return false;
        }
        if (teams.getValue() && PlayerUtility.areOnSameTeam(self, other)) {
            return false;
        }
        return !botCheck.getValue() || !AntiBotModule.isBot(other);
    }

    private static double distanceToBox(Entity entity, Vec3 point) {
        AABB box = entity.getBoundingBox();
        if (box.contains(point)) {
            return 0.0;
        }
        return Math.sqrt(box.distanceToSqr(point));
    }

    @Subscribe
    public void onPostGameTick(PostGameTickEvent event) {
        Vec3 saved = LagManager.INSTANCE.getLastPosition();
        lastPosition = currentPosition == null ? saved : currentPosition;
        currentPosition = saved;
    }

    @Subscribe
    public void onSendPacket(InstantaneousSendPacketEvent event) {
        if (!shouldReset(event.getPacket())) {
            return;
        }
        LagManager.INSTANCE.setDelay(0);
        tickIndex = -1;
    }

    private static boolean shouldReset(Packet<?> packet) {
        if (packet instanceof ServerboundInteractPacket) {
            return true;
        }
        if (packet instanceof ServerboundPlayerActionPacket action) {
            return action.getAction() != ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM;
        }
        if (packet instanceof ServerboundUseItemOnPacket || packet instanceof ServerboundUseItemPacket) {
            LocalPlayer player = Minecraft.getInstance().player;
            return player == null || !player.getMainHandItem().is(ItemTags.SWORDS);
        }
        return false;
    }

    @Subscribe
    public void onRender2D(Render2DEvent event) {
        if (showPosition.getValue() == ShowPosition.NONE || !hasTarget
                || lastPosition == null || currentPosition == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.options.getCameraType().isFirstPerson()) {
            return;
        }
        CameraRenderState camera = CameraRenderStateHelper.get();
        if (camera == null) {
            return;
        }

        float partialTick = event.partialTick();
        double x = lerp(currentPosition.x, lastPosition.x, partialTick);
        double y = lerp(currentPosition.y, lastPosition.y, partialTick);
        double z = lerp(currentPosition.z, lastPosition.z, partialTick);

        AABB own = player.getBoundingBox();
        double halfWidth = (own.maxX - own.minX) / 2.0;
        double height = own.maxY - own.minY;
        AABB box = new AABB(x - halfWidth, y, z - halfWidth, x + halfWidth, y + height, z + halfWidth);

        ESPUtility.ScreenBox screen = ESPUtility.project(box, camera, event.width(), event.height());
        if (screen == null) {
            return;
        }
        int color = showPosition.getValue() == ShowPosition.TEAM
                ? 0xFF000000 | (player.getTeamColor() & 0x00FFFFFF)
                : 0xFFFFFFFF;
        float x0 = screen.x();
        float y0 = screen.y();
        float x1 = screen.x() + screen.width();
        float y1 = screen.y() + screen.height();
        RenderUtil.sharpRect(event.extractor(), x0, y0, x1, y0 + 1.0f, color);
        RenderUtil.sharpRect(event.extractor(), x0, y1 - 1.0f, x1, y1, color);
        RenderUtil.sharpRect(event.extractor(), x0, y0, x0 + 1.0f, y1, color);
        RenderUtil.sharpRect(event.extractor(), x1 - 1.0f, y0, x1, y1, color);
    }

    private static double lerp(double from, double to, float progress) {
        return from + (to - from) * progress;
    }

    public enum ShowPosition {
        NONE("None"),
        WHITE("White"),
        TEAM("Team");

        private final String name;

        ShowPosition(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
