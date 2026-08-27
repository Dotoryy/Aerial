package cc.aerial.client.features.impl.combat;

import cc.aerial.client.event.impl.game.PreGameTickEvent;
import cc.aerial.client.event.subscriber.Subscribe;
import cc.aerial.client.features.Module;
import cc.aerial.client.features.ModuleCategory;
import cc.aerial.client.features.impl.movement.LongJumpModule;
import cc.aerial.client.property.BooleanProperty;
import cc.aerial.client.property.NumberProperty;
import cc.aerial.client.rotation.RotationHelper;
import cc.aerial.client.rotation.RotationUtility;
import cc.aerial.client.rotation.model.impl.InstantRotationModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.hurtingprojectile.Fireball;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

public final class AntiFireballModule extends Module {
    public static final AntiFireballModule INSTANCE = new AntiFireballModule();

    private final NumberProperty range = new NumberProperty("Range", 5.0, 3.0, 8.0, 0.1);
    private final NumberProperty fov = new NumberProperty("Fov", 360, 1, 360, 1);
    private final BooleanProperty rotate = new BooleanProperty("Rotate", true);
    private final BooleanProperty swing = new BooleanProperty("Swing", true);

    private Fireball target;

    private AntiFireballModule() {
        super("Anti Fireball", "Automatically deflects fireballs", ModuleCategory.COMBAT);
        addProperties(range, fov, rotate, swing);
    }

    @Override
    protected void onDisable() {
        target = null;
    }

    @Subscribe
    public void onPreGameTick(PreGameTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || player.getAbilities().flying) {
            target = null;
            return;
        }

        Fireball closest = null;
        double closestDistance = Double.MAX_VALUE;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof Fireball fireball) || !isValidTarget(player, fireball)) {
                continue;
            }
            double distance = distanceToEntity(player.getEyePosition(), fireball);
            if (distance < closestDistance) {
                closestDistance = distance;
                closest = fireball;
            }
        }
        target = closest;

        if (target == null) {
            return;
        }

        AABB box = target.getBoundingBox().inflate(target.getPickRadius());
        Vec2 rotation = RotationUtility.getRotationsToBox(box, player.getYRot(), player.getXRot(), 180.0f, 0.0f);

        if (rotate.getValue() && player.getMainHandItem().isEmpty() && !player.isUsingItem()) {
            RotationHelper.getHandler().rotate(rotation, InstantRotationModel.INSTANCE, this);
        }

        if (distanceToEntity(player.getEyePosition(), target) <= range.getValue()) {
            ClientPacketListener connection = mc.getConnection();
            if (connection != null) {
                performAerialAttack(connection, player, target);
                if (swing.getValue()) {
                    player.swing(InteractionHand.MAIN_HAND);
                } else {
                    connection.send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
                }
            }
        }
    }

    private void performAerialAttack(ClientPacketListener connection, LocalPlayer player, Fireball target) {
        if (!target.isAttackable()) {
            return;
        }
        connection.send(new ServerboundAttackPacket(target.getId()));

        float baseDamage = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        if (baseDamage <= 0.0f) {
            return;
        }
        DamageSource damageSource = player.damageSources().playerAttack(player);
        if (!target.hurtOrSimulate(damageSource, baseDamage)) {
            return;
        }

        float knockback = (float) player.getAttributeValue(Attributes.ATTACK_KNOCKBACK) / 2.0f;
        if (player.isSprinting() && player.getAttackStrengthScale(0.5f) > 0.9f) {
            knockback += 0.5f;
        }
        if (knockback > 0.0f) {
            float yawRad = player.getYRot() * Mth.DEG_TO_RAD;
            target.push(-Mth.sin(yawRad) * knockback * 0.5f, 0.1, Mth.cos(yawRad) * knockback * 0.5f);
            player.setDeltaMovement(player.getDeltaMovement().multiply(0.6, 1.0, 0.6));
            player.setSprinting(false);
        }
    }

    private boolean isValidTarget(LocalPlayer player, Fireball fireball) {
        if (isOwnFireball(player, fireball)) {
            return false;
        }
        return RotationUtility.isEntityInFOV(fireball, fov.getValue().floatValue())
                && distanceToEntity(player.getEyePosition(), fireball) <= range.getValue() + 3.0;
    }

    private static boolean isOwnFireball(LocalPlayer player, Fireball fireball) {
        if (!LongJumpModule.INSTANCE.isEnabled()) {
            return false;
        }
        return fireball.getOwner() == player || LongJumpModule.INSTANCE.canStartJump();
    }

    private static double distanceToEntity(Vec3 eyePos, Entity entity) {
        AABB box = entity.getBoundingBox().inflate(entity.getPickRadius());
        return box.distanceToSqr(eyePos) <= 0.0 ? 0.0 : Math.sqrt(box.distanceToSqr(eyePos));
    }
}
