package cc.aerial.client.features.impl.combat.killaura.target;

import cc.aerial.client.utility.PlayerUtility;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;

public final class KillauraTarget {
    private final LivingEntity target;
    private LastAttackData lastAttackData;

    public KillauraTarget(LivingEntity target) {
        this.target = target;
    }

    public LivingEntity getTarget() {
        return target;
    }

    public void onAttack(boolean reset) {
        double damage = this.getDamage();
        if (this.lastAttackData == null) {
            this.lastAttackData = new LastAttackData(damage);
        } else {
            this.lastAttackData.reset(reset, damage);
        }
    }

    public double getDamage() {
        LocalPlayer player = Minecraft.getInstance().player;
        double damage = PlayerUtility.getStackAttackDamage(player.getMainHandItem());
        if (damage < 0.5D) {
            damage = 0.5D;
        }
        if (PlayerUtility.isCriticalHitAvailable(player) && player.fallDistance > 0.0F) {
            damage *= 1.5D;
        }
        return damage;
    }

    public float getFullHealth() {
        return this.target.getHealth() + this.target.getAbsorptionAmount();
    }

    public LastAttackData getLastAttackData() {
        return lastAttackData;
    }
}
