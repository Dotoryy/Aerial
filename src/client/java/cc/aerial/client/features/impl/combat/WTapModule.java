package cc.aerial.client.features.impl.combat;

import cc.aerial.client.event.impl.game.PreGameTickEvent;
import cc.aerial.client.event.impl.game.player.interaction.AttackEvent;
import cc.aerial.client.event.subscriber.Subscribe;
import cc.aerial.client.features.Module;
import cc.aerial.client.features.ModuleCategory;
import cc.aerial.client.property.BooleanProperty;
import cc.aerial.client.property.NumberProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public final class WTapModule extends Module {
    public static final WTapModule INSTANCE = new WTapModule();

    private final NumberProperty chance = new NumberProperty("Chance", 100, 0, 100, 1);
    private final NumberProperty delayUntilReset = new NumberProperty("Delay Until Reset", 150, 0, 1000, 50);
    private final NumberProperty delayBetweenReset = new NumberProperty("Delay Between Reset", 300, 0, 1000, 50);
    private final BooleanProperty playersOnly = new BooleanProperty("Players Only", true);

    private long pendingResetAtMs;
    private long lastResetStartMs;
    private boolean stopSprintPending;

    private WTapModule() {
        super("WTap", "Automatically performs W-tapping for increased knockback and combos", ModuleCategory.COMBAT);
        addProperties(chance, delayUntilReset, delayBetweenReset, playersOnly);
    }

    @Override
    public String getSuffix() {
        return "Legit";
    }

    @Override
    protected void onEnable() {
        resetState();
    }

    @Override
    protected void onDisable() {
        resetState();
    }

    private void resetState() {
        pendingResetAtMs = 0L;
        lastResetStartMs = 0L;
        stopSprintPending = false;
    }

    @Subscribe
    public void onAttack(AttackEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !player.isSprinting()) {
            return;
        }

        Entity target = event.getTarget();
        if (playersOnly.getValue()) {
            if (!(target instanceof Player)) {
                return;
            }
        } else if (!(target instanceof LivingEntity)) {
            return;
        }
        if (((LivingEntity) target).deathTime != 0) {
            return;
        }

        if (pendingResetAtMs > 0L || stopSprintPending) {
            return;
        }

        long now = System.currentTimeMillis();
        if (lastResetStartMs > 0L && now - lastResetStartMs < delayBetweenReset.getValue().longValue()) {
            return;
        }

        double chanceValue = chance.getValue();
        if (chanceValue < 100.0 && Math.random() * 100.0 >= chanceValue) {
            return;
        }

        pendingResetAtMs = now + delayUntilReset.getValue().longValue();
    }

    @Subscribe
    public void onPreGameTick(PreGameTickEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            resetState();
            return;
        }

        long now = System.currentTimeMillis();
        if (pendingResetAtMs > 0L && now >= pendingResetAtMs) {
            stopSprintPending = true;
            pendingResetAtMs = 0L;
            lastResetStartMs = now;
        }
    }

    public boolean consumeStopSprint() {
        if (!stopSprintPending) {
            return false;
        }
        stopSprintPending = false;
        return true;
    }
}
