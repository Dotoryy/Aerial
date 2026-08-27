package cc.aerial.client.utility;

import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class PlayerUtility {
    private PlayerUtility() {
    }

    public static boolean isCriticalHitAvailable(Player player) {
        return !player.isInWater() && !player.onClimbable()
                && !player.hasEffect(MobEffects.BLINDNESS) && !player.isPassenger();
    }

    public static float getMaxFallDistance(Player player) {
        float distance = 3.0f;
        if (player.hasEffect(MobEffects.JUMP_BOOST)) {
            distance += player.getEffect(MobEffects.JUMP_BOOST).getAmplifier() + 1;
        }
        return distance;
    }

    public static boolean isBoxEmpty(net.minecraft.world.level.Level level, AABB box) {
        return net.minecraft.core.BlockPos.betweenClosedStream(box).noneMatch(pos -> {
            net.minecraft.world.level.block.state.BlockState blockState = level.getBlockState(pos);
            net.minecraft.world.phys.shapes.VoxelShape voxelShape = blockState.getCollisionShape(level, pos);
            return !voxelShape.isEmpty() && net.minecraft.world.phys.shapes.Shapes.joinIsNotEmpty(
                    voxelShape.move(pos.getX(), pos.getY(), pos.getZ()),
                    net.minecraft.world.phys.shapes.Shapes.create(box),
                    net.minecraft.world.phys.shapes.BooleanOp.AND
            );
        });
    }

    public static boolean isOverVoid(net.minecraft.world.level.Level level, AABB playerBox) {
        AABB shrunk = playerBox.inflate(-0.005, 0.0, -0.005);
        AABB column = new AABB(shrunk.minX, 0.0, shrunk.minZ, shrunk.maxX, shrunk.maxY, shrunk.maxZ);
        return isBoxEmpty(level, column);
    }

    public static Vec3 getClosestVectorToBox(Vec3 from, AABB box) {
        double closestX = Math.max(box.minX, Math.min(from.x, box.maxX));
        double closestY = Math.max(box.minY, Math.min(from.y, box.maxY));
        double closestZ = Math.max(box.minZ, Math.min(from.z, box.maxZ));
        return new Vec3(closestX, closestY, closestZ);
    }

    public static Vec3 getClosestVectorToBoundingBox(Vec3 from, LivingEntity entity) {
        return getClosestVectorToBox(from, entity.getBoundingBox().inflate(entity.getPickRadius()));
    }

    public static Vec3 getMultipointVector(Vec3 from, LivingEntity entity, double horizontalPercent, double verticalPercent) {
        AABB box = entity.getBoundingBox().inflate(entity.getPickRadius());
        Vec3 center = box.getCenter();
        Vec3 closest = getClosestVectorToBox(from, box);

        double horizontalFactor = Mth.clamp(horizontalPercent, 0.0, 100.0) / 100.0;
        double verticalFactor = Mth.clamp(verticalPercent, 0.0, 100.0) / 100.0;

        double x = Mth.lerp(horizontalFactor, center.x, closest.x);
        double z = Mth.lerp(horizontalFactor, center.z, closest.z);
        double y = Mth.lerp(verticalFactor, center.y, closest.y);
        return new Vec3(x, y, z);
    }

    public static double getDistanceToEntity(Vec3 eyePos, LivingEntity entity) {
        return eyePos.distanceTo(getClosestVectorToBox(eyePos, entity.getBoundingBox().inflate(entity.getPickRadius())));
    }

    public static boolean areOnSameTeam(LivingEntity entity, LivingEntity other) {
        return entity.getTeamColor() == other.getTeamColor();
    }

    public static double getStackAttackDamage(ItemStack stack) {
        double[] attackDamage = {0.0};
        ItemAttributeModifiers modifiers = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        modifiers.forEach(EquipmentSlotGroup.MAINHAND, (attribute, modifier) -> {
            if (attribute.equals(Attributes.ATTACK_DAMAGE)) {
                attackDamage[0] += modifier.amount();
            }
        });
        return attackDamage[0];
    }

    public static double getArmorProtection(ItemStack stack) {
        double protection = 0.0;
        ItemAttributeModifiers modifiers = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        for (ItemAttributeModifiers.Entry entry : modifiers.modifiers()) {
            if (entry.attribute().equals(Attributes.ARMOR)) {
                protection += entry.modifier().amount();
            }
        }
        return protection;
    }

    public static net.minecraft.world.level.block.Block getBlockOver() {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult blockHit && mc.level != null) {
            return mc.level.getBlockState(blockHit.getBlockPos()).getBlock();
        }
        return null;
    }
}
