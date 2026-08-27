package cc.aerial.client.features.impl.combat.killaura;

import cc.aerial.client.features.impl.combat.AntiBotModule;
import cc.aerial.client.features.impl.combat.killaura.target.CurrentTarget;
import cc.aerial.client.features.impl.combat.killaura.target.KillauraTarget;
import cc.aerial.client.rotation.RotationUtility;
import cc.aerial.client.target.TargetFlags;
import cc.aerial.client.utility.PlayerUtility;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class KillauraTargeting {
    private final KillauraSettings settings;

    public KillauraTargeting(KillauraSettings settings) {
        this.settings = settings;
    }

    private CurrentTarget target, rotationTarget;
    private double closestDistance = Double.MAX_VALUE;
    private Map<Integer, KillauraTarget> targetMap = new HashMap<>();

    public void update() {
        this.closestDistance = Double.MAX_VALUE;
        this.findAttackTarget();
        this.findRotationTarget();
    }

    private void findAttackTarget() {
        LocalPlayer player = Minecraft.getInstance().player;
        double interactionRange = player.entityInteractionRange();
        List<LivingEntity> targets = this.getTargets(interactionRange);
        if (targets == null || targets.isEmpty()) {
            this.target = null;
            return;
        }
        this.target = this.selectTarget(targets, interactionRange, false);
    }

    private void findRotationTarget() {
        if (this.target != null) {
            this.rotationTarget = this.target;
            return;
        }
        double interactionRange = this.settings.getRotationRange();
        List<LivingEntity> targets = this.getTargets(interactionRange);
        if (targets == null || targets.isEmpty()) {
            this.rotationTarget = null;
            return;
        }
        this.rotationTarget = this.selectTarget(targets, interactionRange, true);
    }

    public boolean hasTargetsInRange() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return false;
        }
        List<LivingEntity> targets = getTargets(player.entityInteractionRange());
        return targets != null && !targets.isEmpty();
    }

    private List<LivingEntity> getTargets(double interactionRange) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null) {
            return null;
        }

        int flags = this.settings.getTargetProperty().getTargetFlags();
        Vec3 eyePos = player.getEyePosition();

        List<LivingEntity> targets = new ArrayList<>();
        Map<Integer, KillauraTarget> updatedTargetMap = new HashMap<>();

        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living) || living == player) {
                continue;
            }
            if (living.isDeadOrDying()) {
                continue;
            }
            if (!isMatchingFlags(player, living, flags)) {
                continue;
            }
            if (!RotationUtility.isEntityInFOV(living, this.settings.getFov())) {
                continue;
            }

            double distance = PlayerUtility.getDistanceToEntity(eyePos, living);
            if (distance < this.closestDistance) {
                this.closestDistance = distance;
            }
            if (distance > interactionRange) {
                continue;
            }

            targets.add(living);
            updatedTargetMap.put(living.getId(), this.getKillauraTarget(living));
        }

        this.targetMap = updatedTargetMap;

        return targets;
    }

    private boolean isMatchingFlags(LocalPlayer self, LivingEntity entity, int flags) {
        if (entity instanceof net.minecraft.world.entity.decoration.ArmorStand
                || entity instanceof net.minecraft.world.entity.npc.villager.Villager) {
            return false;
        }
        if (entity instanceof Player otherPlayer) {
            if (AntiBotModule.isBot(otherPlayer)) {
                return false;
            }
            if ((flags & TargetFlags.FRIENDLY) == 0 && PlayerUtility.areOnSameTeam(self, otherPlayer)) {
                return false;
            }
            return (flags & TargetFlags.PLAYERS) != 0;
        }
        if (entity instanceof Monster) {
            return (flags & TargetFlags.HOSTILE) != 0;
        }
        return (flags & TargetFlags.PASSIVE) != 0;
    }

    private CurrentTarget selectTarget(List<LivingEntity> targets, double entityInteractionRange, boolean distanceSorting) {
        LocalPlayer player = Minecraft.getInstance().player;

        targets.sort(Comparator.comparingDouble(t -> {
            double score = distanceSorting ? t.distanceToSqr(player) : this.getKillauraTarget(t).getFullHealth();
            if (!(t instanceof Player)) {
                return score * 2.0D;
            }
            return score;
        }));

        List<CurrentTarget> targetList = this.convertTargets(targets, entityInteractionRange);

        if (!distanceSorting && this.settings.getMode() == KillauraSettings.Mode.SWITCH && targetList.size() > 1) {
            targetList.sort(Comparator.comparingDouble(t ->
                    -((t.getKillauraTarget().getFullHealth() * 25.0D)
                            + (t.getKillauraTarget().getLastAttackData() == null ? 0 : t.getKillauraTarget().getLastAttackData().getTime()))));
        }

        if (targetList.isEmpty()) {
            return null;
        }

        return targetList.getFirst();
    }

    private List<CurrentTarget> convertTargets(List<LivingEntity> targets, double entityInteractionRange) {
        LocalPlayer player = Minecraft.getInstance().player;
        Vec3 eyePos = player.getEyePosition();
        List<CurrentTarget> targetList = new ArrayList<>();

        for (LivingEntity entity : targets) {
            Vec3 closestVector = PlayerUtility.getMultipointVector(eyePos, entity,
                    this.settings.getHorizontalMultipoint(), this.settings.getVerticalMultipoint());
            RotationUtility.RaytracedRotation tickedRotation = RotationUtility.getRotationFromRaycastedEntity(entity, closestVector, entityInteractionRange);
            if (tickedRotation != null) {
                targetList.add(new CurrentTarget(this.getKillauraTarget(entity), tickedRotation));
            }
        }

        return targetList;
    }

    private KillauraTarget getKillauraTarget(LivingEntity entity) {
        int id = entity.getId();
        if (this.targetMap.containsKey(id)) {
            return this.targetMap.get(id);
        }
        KillauraTarget killauraTarget = new KillauraTarget(entity);
        this.targetMap.put(id, killauraTarget);
        return killauraTarget;
    }

    public void reset() {
        this.closestDistance = Double.MAX_VALUE;
        this.targetMap = new HashMap<>();
        this.target = null;
    }

    public CurrentTarget getTarget() {
        return target;
    }

    public CurrentTarget getRotationTarget() {
        return rotationTarget;
    }

    public double getClosestDistance() {
        return closestDistance;
    }

    public boolean isTargetSelected() {
        return getTarget() != null;
    }
}
