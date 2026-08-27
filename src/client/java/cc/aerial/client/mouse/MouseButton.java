package cc.aerial.client.mouse;

import net.minecraft.client.KeyMapping;

public final class MouseButton {
    private final KeyMapping keyMapping;

    public MouseButton(KeyMapping keyMapping) {
        this.keyMapping = keyMapping;
    }

    private boolean pressed;
    private boolean disabled;
    private int holdTicks;

    public void setDisabled() {
        this.pressed = false;
        this.holdTicks = 0;
        this.disabled = true;
    }

    public void setPressed() {
        this.setPressed(true, 0);
    }

    public void setPressed(boolean pressed, int holdTicks) {
        this.pressed = pressed;
        this.holdTicks = holdTicks;
    }

    public boolean consumeClick() {
        if (this.disabled) {
            return false;
        }
        boolean firedFake = false;
        if (this.pressed) {
            firedFake = true;
            this.pressed = false;
        }
        return this.keyMapping.consumeClick() || firedFake;
    }

    public boolean isDown() {
        if (this.disabled) {
            return false;
        }
        return this.keyMapping.isDown() || this.pressed || this.holdTicks > 0;
    }

    public boolean isForcePressed() {
        return this.pressed;
    }

    public void tick() {
        if (this.holdTicks > 0) {
            this.holdTicks--;
        }
        this.pressed = false;
        this.disabled = false;
    }

    public int getHoldTicks() {
        return holdTicks;
    }

    public boolean isDisabled() {
        return disabled;
    }

    public KeyMapping getKeyMapping() {
        return keyMapping;
    }
}
