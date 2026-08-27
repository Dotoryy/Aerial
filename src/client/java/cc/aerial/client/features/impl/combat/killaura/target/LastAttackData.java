package cc.aerial.client.features.impl.combat.killaura.target;

import cc.aerial.client.utility.Stopwatch;

public final class LastAttackData {
    private final Stopwatch stopwatch = new Stopwatch();
    private double damage;

    public LastAttackData(double damage) {
        this.reset(false, damage);
    }

    public void reset(boolean reset, double damage) {
        if (reset) {
            this.stopwatch.reset();
        }
        this.damage = damage;
    }

    public long getTime() {
        return this.stopwatch.getTime();
    }

    public double getDamage() {
        return damage;
    }
}
