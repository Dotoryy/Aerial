package cc.aerial.client.features.impl.combat;

import cc.aerial.client.event.impl.game.PreGameTickEvent;
import cc.aerial.client.event.subscriber.Subscribe;
import cc.aerial.client.features.Module;
import cc.aerial.client.features.ModuleCategory;
import cc.aerial.client.property.NumberProperty;

public final class ReachModule extends Module {
    public static final ReachModule INSTANCE = new ReachModule();

    private final NumberProperty range = new NumberProperty("Range", 3.1, 3.0, 6.0, 0.1);
    private final NumberProperty chance = new NumberProperty("Chance", 100, 0, 100, 1);

    private boolean expanding = true;

    private ReachModule() {
        super("Reach", "Extends the reach for attacking entities.", ModuleCategory.COMBAT);
        addProperties(range, chance);
    }

    @Override
    public String getSuffix() {
        return range.getValue().toString();
    }

    @Override
    protected void onDisable() {
        expanding = true;
    }

    @Subscribe
    public void onPreGameTick(PreGameTickEvent event) {
        expanding = Math.random() * 100.0 <= chance.getValue();
    }

    public boolean isExpanding() {
        return expanding;
    }

    public double getRange() {
        return range.getValue();
    }
}
