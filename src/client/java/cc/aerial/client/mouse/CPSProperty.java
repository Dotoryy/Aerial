package cc.aerial.client.mouse;

import cc.aerial.client.property.BooleanProperty;
import cc.aerial.client.property.GroupProperty;
import cc.aerial.client.property.NumberProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import java.util.function.BooleanSupplier;

public final class CPSProperty {
    private final BooleanProperty modernDelay;
    private final NumberProperty delay;
    private final GroupProperty groupProperty;

    public CPSProperty(String groupName, boolean allowModernDelay) {
        this.modernDelay = allowModernDelay ? new BooleanProperty("Modern delay", false) : null;
        this.delay = new NumberProperty("CPS", 10, 1, 20, 1).hideIf(this::isModernDelay);
        this.groupProperty = allowModernDelay
                ? new GroupProperty(groupName, this.modernDelay, this.delay)
                : new GroupProperty(groupName, this.delay);
    }

    public CPSProperty hideIf(BooleanSupplier hiddenSupplier) {
        this.groupProperty.hideIf(hiddenSupplier);
        return this;
    }

    public GroupProperty get() {
        return this.groupProperty;
    }

    public boolean isModernDelay() {
        return modernDelay != null && modernDelay.getValue();
    }

    public int getCPS() {
        return this.delay.getValue().intValue();
    }

    private long nextClickTime;

    public boolean isReady() {
        if (isModernDelay()) {
            LocalPlayer player = Minecraft.getInstance().player;
            return player != null && player.getAttackStrengthScale(0.5f) >= 1.0f;
        }
        return System.currentTimeMillis() >= nextClickTime;
    }

    public boolean canClick() {
        if (isModernDelay()) {
            LocalPlayer player = Minecraft.getInstance().player;
            return player != null && player.getAttackStrengthScale(0.5f) >= 1.0f;
        }
        long now = System.currentTimeMillis();
        if (now >= nextClickTime) {
            long delay = 1000L / Math.max(1, getCPS());
            long base = Math.max(nextClickTime, now - delay);
            nextClickTime = base + delay;
            return true;
        }
        return false;
    }
}
