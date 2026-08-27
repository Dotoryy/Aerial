package cc.aerial.client.features.impl.combat;

import cc.aerial.client.event.impl.game.player.interaction.ItemUseEvent;
import cc.aerial.client.event.subscriber.Subscribe;
import cc.aerial.client.features.Module;
import cc.aerial.client.features.ModuleCategory;
import cc.aerial.client.property.BooleanProperty;
import cc.aerial.client.property.NumberProperty;
import cc.aerial.client.rotation.RotationUtility;
import cc.aerial.client.utility.PlayerUtility;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

public final class RodAimbotModule extends Module {
    public static final RodAimbotModule INSTANCE = new RodAimbotModule();

    private final NumberProperty fov = new NumberProperty("FOV", 180, 30, 360, 5);
    private final NumberProperty predictedTicks = new NumberProperty("Predicted ticks", 5, 0, 20, 1);
    private final NumberProperty rotationTicks = new NumberProperty("Rotation ticks", 3, 1, 20, 1);
    private final NumberProperty distance = new NumberProperty("Distance", 6, 3, 30, 0.5);
    private final BooleanProperty aimInvisible = new BooleanProperty("Aim invisible", false);
    private final BooleanProperty ignoreTeammates = new BooleanProperty("Ignore teammates", false);

    private Player target;

    private boolean castPending;

    private boolean rotationApplied;

    private int holdTicks;
    private Player holdTarget;
    private float savedYaw;
    private float savedPitch;

    private RodAimbotModule() {
        super("Rod Aimbot", "Aims your fishing rod before it casts", ModuleCategory.COMBAT);
        addProperties(fov, predictedTicks, rotationTicks, distance, aimInvisible, ignoreTeammates);
    }

    @Override
    protected void onDisable() {
        target = null;
        castPending = false;
        rotationApplied = false;
        holdTicks = 0;
        holdTarget = null;
    }

    @Subscribe
    public void onItemUse(ItemUseEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.gui.screen() != null) {
            return;
        }
        if (!player.getMainHandItem().is(Items.FISHING_ROD) || player.fishing != null) {
            return;
        }
        target = findTarget(player);
        if (target == null) {
            return;
        }
        event.setCancelled();
        castPending = true;
    }

    public void applyRotation(LocalPlayer player) {
        if (rotationApplied) {
            return;
        }

        if (holdTicks > 0) {
            holdTicks--;
            if (holdTarget == null || !holdTarget.isAlive()) {
                holdTarget = null;
                return;
            }
            savedYaw = player.getYRot();
            savedPitch = player.getXRot();
            Vec2 held = aimRotation(player, holdTarget);
            RotationUtility.setRotationSilently(player, held.x, held.y);
            rotationApplied = true;
            if (holdTicks == 0) {
                holdTarget = null;
            }
            return;
        }

        if (!castPending || target == null) {
            return;
        }
        castPending = false;
        if (!player.getMainHandItem().is(Items.FISHING_ROD)) {
            target = null;
            return;
        }

        savedYaw = player.getYRot();
        savedPitch = player.getXRot();
        Vec2 rotation = aimRotation(player, target);
        RotationUtility.setRotationSilently(player, rotation.x, rotation.y);
        rotationApplied = true;

        Minecraft mc = Minecraft.getInstance();
        if (mc.gameMode != null) {
            mc.gameMode.useItem(player, InteractionHand.MAIN_HAND);
        }
        holdTarget = target;

        holdTicks = Math.max(0, rotationTicks.getValue().intValue() - 1);
        target = null;
    }

    public void restoreRotation(LocalPlayer player) {
        if (!rotationApplied) {
            return;
        }
        rotationApplied = false;
        RotationUtility.setRotationSilently(player, savedYaw, savedPitch);
        player.yRotO = player.getYRot();
        player.xRotO = player.getXRot();
    }

    private Vec3 predictedPosition(Player entity) {
        double ticks = predictedTicks.getValue().doubleValue();
        Vec3 position = entity.position();
        double stepX = position.x - entity.xOld;
        double stepZ = position.z - entity.zOld;
        return new Vec3(
                position.x + stepX * ticks,
                position.y + entity.getEyeHeight() * 0.9,
                position.z + stepZ * ticks);
    }

    private Vec2 aimRotation(LocalPlayer player, Player entity) {
        Vec2 direct = RotationUtility.getRotationFromPosition(
                player.getEyePosition(), predictedPosition(entity));

        return RotationUtility.getQuantizedRotation(new Vec2(direct.x, direct.y + 3.0f));
    }

    private Player findTarget(LocalPlayer self) {
        double maxDistance = distance.getValue().doubleValue();
        double maxDistanceSq = maxDistance * maxDistance;
        double maxFov = fov.getValue().doubleValue();

        for (Player other : self.level().players()) {
            if (other == self || !other.isAlive()) {
                continue;
            }
            if (!aimInvisible.getValue() && other.isInvisible()) {
                continue;
            }
            if (self.distanceToSqr(other) > maxDistanceSq) {
                continue;
            }
            if (AntiBotModule.isBot(other)) {
                continue;
            }
            if (ignoreTeammates.getValue() && PlayerUtility.areOnSameTeam(self, other)) {
                continue;
            }

            if (maxFov != 360.0 && Math.abs(RotationUtility.getEntityFOV(other)) > maxFov / 2.0) {
                continue;
            }
            return other;
        }
        return null;
    }
}
