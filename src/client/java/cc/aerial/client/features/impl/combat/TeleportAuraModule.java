package cc.aerial.client.features.impl.combat;

import cc.aerial.client.event.impl.game.PreGameTickEvent;
import cc.aerial.client.event.impl.render.Render2DEvent;
import cc.aerial.client.event.subscriber.Subscribe;
import cc.aerial.client.features.Module;
import cc.aerial.client.features.ModuleCategory;
import cc.aerial.client.pathfinding.TeleportPathFinder;
import cc.aerial.client.property.BooleanProperty;
import cc.aerial.client.property.ModeProperty;
import cc.aerial.client.property.NumberProperty;
import cc.aerial.client.render.RenderUtil;
import cc.aerial.client.target.TargetProperty;
import cc.aerial.client.render.ESPUtility;
import cc.aerial.client.render.CameraRenderStateHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.List;

public final class TeleportAuraModule extends Module {
    public static final TeleportAuraModule INSTANCE = new TeleportAuraModule();

    public enum Mode {
        SINGLE("Single"),
        MULTIPLE("Multiple");

        private final String label;

        Mode(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private final ModeProperty<Mode> mode = new ModeProperty<>("Mode", Mode.SINGLE);
    private final NumberProperty range = new NumberProperty("Range", 32, 3, 100, 0.1);
    private final NumberProperty minimumCps = new NumberProperty("Min CPS", 10, 1, 20, 1);
    private final NumberProperty maximumCps = new NumberProperty("Max CPS", 15, 1, 20, 1);

    private final BooleanProperty cooldown19 = new BooleanProperty("1.9 Cooldown", false);

    private final BooleanProperty render = new BooleanProperty("Render", true);

    private final TargetProperty targetProperty =
            new TargetProperty(true, false, false, false, false, false);

    private long nextAttackAt;
    private List<Vec3> lastPath;
    private LivingEntity target;

    private TeleportAuraModule() {
        super("Teleport Aura", "Attacks far targets by walking to them in packets", ModuleCategory.COMBAT);
        addProperties(mode, range, minimumCps, maximumCps, cooldown19, render, targetProperty.get());
    }

    @Override
    protected void onDisable() {
        target = null;
        lastPath = null;
    }

    @Subscribe
    public void onPreGameTick(PreGameTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null || player.isDeadOrDying()) {
            target = null;
            return;
        }
        List<LivingEntity> candidates = new ArrayList<>();
        double reach = range.getValue().doubleValue();
        for (Entity entity : level.entitiesForRendering()) {
            if (entity instanceof LivingEntity living
                    && living != player
                    && !living.isDeadOrDying()
                    && player.distanceTo(living) <= reach) {
                candidates.add(living);
            }
        }
        if (candidates.isEmpty()) {
            target = null;
            return;
        }
        candidates.sort(Comparator.comparingDouble(player::distanceTo));
        target = candidates.get(0);
        tryAttack(player, candidates);
    }

    private void tryAttack(LocalPlayer player, List<LivingEntity> candidates) {
        Minecraft mc = Minecraft.getInstance();
        long now = System.currentTimeMillis();
        if (now < nextAttackAt) {
            return;
        }

        if (mc.options.keyAttack.isDown() || mc.options.keyUse.isDown()) {
            return;
        }
        nextAttackAt = now + intervalMs(player);

        if (mode.getValue() == Mode.SINGLE) {
            if (target != null) {
                attack(player, target);
            }
            return;
        }
        double reach = range.getValue().doubleValue();
        for (LivingEntity living : candidates) {
            if (player.distanceTo(living) <= reach) {
                attack(player, living);
            }
        }
    }

    private long intervalMs(LocalPlayer player) {
        if (cooldown19.getValue()) {
            double perSecond = player.getAttributeValue(Attributes.ATTACK_SPEED);
            if (perSecond <= 0.0) {
                return 0L;
            }
            return Math.max(0L, Math.round((1.0 / perSecond * 20.0 - 1.0) * 50.0));
        }
        int min = Math.min(minimumCps.getValue().intValue(), maximumCps.getValue().intValue());
        int max = Math.max(minimumCps.getValue().intValue(), maximumCps.getValue().intValue());
        int cps = min == max ? min : min + (int) (Math.random() * (max - min + 1));
        return 1000L / Math.max(1, cps);
    }

    private void attack(LocalPlayer player, LivingEntity living) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) {
            return;
        }
        List<Vec3> path = TeleportPathFinder.find(player.position(), living.position(), true);
        if (path == null || path.isEmpty()) {
            return;
        }
        lastPath = path;

        for (Vec3 point : path) {
            connection.send(new ServerboundMovePlayerPacket.Pos(point.x, point.y, point.z, true, false));
        }

        connection.send(new ServerboundAttackPacket(living.getId()));
        player.swing(InteractionHand.MAIN_HAND);

        List<Vec3> back = new ArrayList<>(path);
        Collections.reverse(back);
        for (Vec3 point : back) {
            connection.send(new ServerboundMovePlayerPacket.Pos(point.x, point.y, point.z, true, false));
        }
    }

    @Subscribe
    public void onRender2D(Render2DEvent event) {
        if (!render.getValue() || lastPath == null || target == null) {
            return;
        }
        CameraRenderState camera = CameraRenderStateHelper.get();
        if (camera == null) {
            return;
        }
        for (Vec3 point : lastPath) {
            Vector4f screen = ESPUtility.project(point.add(0.0, 0.01, 0.0), camera, event.width(), event.height());
            if (screen == null) {
                continue;
            }
            RenderUtil.sharpRect(event.extractor(), screen.x - 1.0f, screen.y - 1.0f,
                    screen.x + 1.0f, screen.y + 1.0f, 0xFFFFFFFF);
        }
    }
}
