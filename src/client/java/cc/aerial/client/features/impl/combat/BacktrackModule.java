package cc.aerial.client.features.impl.combat;

import cc.aerial.client.event.impl.game.packet.ReceivePacketEvent;
import cc.aerial.client.event.impl.game.player.movement.PostMovementPacketEvent;
import cc.aerial.client.event.impl.render.Render2DEvent;
import cc.aerial.client.event.subscriber.Subscribe;
import cc.aerial.client.features.Module;
import cc.aerial.client.features.ModuleCategory;
import cc.aerial.client.features.impl.combat.killaura.KillauraModule;
import cc.aerial.client.packet.blockage.BlockHolder;
import cc.aerial.client.packet.blockage.NetworkDirection;
import cc.aerial.client.property.BooleanProperty;
import cc.aerial.client.property.NumberProperty;
import cc.aerial.client.render.TextRenderUtil;
import cc.aerial.client.render.font.AerialFont;
import cc.aerial.client.utility.PlayerUtility;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public final class BacktrackModule extends Module {
    public static final BacktrackModule INSTANCE = new BacktrackModule();

    private static final double MIN_DISTANCE = 2.5;
    private static final double MAX_DISTANCE = 6.0;
    private static final double MAX_DISTANCE_WATCHDOG = 4.4;
    private static final double HUD_DISTANCE_WATCHDOG = 3.0;

    private static final double DELTA_SCALE = 4096.0;

    private final NumberProperty maxPingSpoof = new NumberProperty("Max Ping Spoof", 1000, 50, 10000, 1);
    private final BooleanProperty watchdogMode = new BooleanProperty("Watchdog Mode", false);
    private final BooleanProperty showDistance = new BooleanProperty("Show Distance", true);
    private final NumberProperty range = new NumberProperty("Target Range", 9.0, 3.0, 12.0, 0.5);

    private final BlockHolder outbound = new BlockHolder(NetworkDirection.OUTBOUND);

    private static AerialFont FONT;

    private Entity target;

    private Vec3 realPosition = Vec3.ZERO;
    private long blinkStartedAt;

    public boolean isSuppressingLag() {
        return isEnabled() && this.outbound.isBlocking();
    }

    private BacktrackModule() {
        super("Back Track", "Holds your packets while the target runs, so hits land at their old position",
                ModuleCategory.COMBAT);
        addProperties(maxPingSpoof, watchdogMode, showDistance, range);
    }

    @Override
    protected void onDisable() {
        stopBlink();
        this.target = null;
    }

    @Subscribe
    public void onPostMovementPacket(PostMovementPacketEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return;
        }

        Entity nearest = findTarget(player);
        if (nearest == null) {
            this.target = null;
            stopBlink();
            return;
        }
        if (nearest != this.target) {
            this.target = nearest;
            this.realPosition = nearest.position();
        }

        boolean fighting = player.swinging || KillauraModule.INSTANCE.isEnabled();
        if (!fighting || velocityActive()) {
            stopBlink();
            return;
        }

        double realDistance = this.realPosition.distanceTo(player.position());
        double renderedDistance = this.target.distanceTo(player);
        double ceiling = watchdogMode.getValue() ? MAX_DISTANCE_WATCHDOG : MAX_DISTANCE;

        boolean shouldBlink = realDistance > renderedDistance
                && realDistance > MIN_DISTANCE
                && realDistance < ceiling;

        if (shouldBlink && watchdogMode.getValue() && player.invulnerableTime <= 2) {
            shouldBlink = false;
        }

        if (shouldBlink) {
            startBlink();
        } else {
            stopBlink();
        }
    }

    @Subscribe
    public void onReceivePacket(ReceivePacketEvent event) {
        Entity current = this.target;
        if (current == null) {
            return;
        }
        Packet<?> packet = event.getPacket();
        Minecraft minecraft = Minecraft.getInstance();

        minecraft.execute(() -> applyPacket(packet, current));
    }

    private void applyPacket(Packet<?> packet, Entity current) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || this.target != current) {
            return;
        }

        if (packet instanceof ClientboundMoveEntityPacket move) {
            if (!move.hasPosition() || move.getEntity(minecraft.level) != current) {
                return;
            }
            this.realPosition = this.realPosition.add(
                    move.getXa() / DELTA_SCALE,
                    move.getYa() / DELTA_SCALE,
                    move.getZa() / DELTA_SCALE);
        } else if (packet instanceof ClientboundTeleportEntityPacket teleport
                && teleport.id() == current.getId()) {
            this.realPosition = teleport.change().position();
        }
    }

    @Subscribe
    public void onRender2D(Render2DEvent event) {
        if (!watchdogMode.getValue() || !showDistance.getValue() || this.target == null) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !player.swinging) {
            return;
        }
        double realDistance = this.realPosition.distanceTo(player.position());
        if (realDistance <= HUD_DISTANCE_WATCHDOG) {
            return;
        }
        if (FONT == null) {
            FONT = AerialFont.createFromResource("OpalProductSansMedium.ttf");
        }
        String text = String.format("Reach: %.2f", realDistance);
        TextRenderUtil.drawString(event.extractor(), FONT, text,
                event.extractor().guiWidth() / 2.0f, event.extractor().guiHeight() / 2.0f + 20.0f,
                8.0f, 0xFFFFFFFF);
    }

    private boolean velocityActive() {
        return VelocityModule.INSTANCE.isEnabled();
    }

    private void startBlink() {
        long now = System.currentTimeMillis();
        if (!this.outbound.isBlocking()) {
            this.blinkStartedAt = now;
            this.outbound.block();
            return;
        }

        if (now - this.blinkStartedAt >= maxPingSpoof.getValue().longValue()) {
            stopBlink();
        }
    }

    private void stopBlink() {
        if (this.outbound.isBlocking()) {
            this.outbound.release();
            this.outbound.flush();
        }
    }

    private Entity findTarget(LocalPlayer player) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return null;
        }
        Entity best = null;
        double bestDistance = range.getValue();
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity) || entity == player) {
                continue;
            }
            if (entity instanceof Player other && PlayerUtility.areOnSameTeam(player, other)) {
                continue;
            }
            double distance = entity.distanceTo(player);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = entity;
            }
        }
        return best;
    }

    public Vec3 getRealPosition() {
        return realPosition;
    }

    public Entity getTarget() {
        return target;
    }
}
