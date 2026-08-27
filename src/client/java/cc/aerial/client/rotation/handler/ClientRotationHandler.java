package cc.aerial.client.rotation.handler;

import cc.aerial.client.event.EventDispatcher;
import cc.aerial.client.event.impl.game.input.MouseUpdateEvent;
import cc.aerial.client.event.subscriber.IEventSubscriber;
import cc.aerial.client.event.subscriber.Subscribe;
import cc.aerial.client.features.impl.visual.FreeLookModule;
import cc.aerial.client.rotation.RotationUtility;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec2;

public final class ClientRotationHandler implements IEventSubscriber {
    public ClientRotationHandler() {
        EventDispatcher.subscribe(this);
    }

    private Vec2 rotation;
    private boolean ticking;

    @Subscribe(priority = 1)
    public void onMouseUpdate(MouseUpdateEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && !event.isUnlockCursorRun() && !FreeLookModule.INSTANCE.isFreeLooking()) {
            if (this.rotation == null) {
                this.rotation = RotationUtility.getRotation();
            }

            double multiplier = event.getSensitivityMultiplier();
            double cursorX = event.getDeltaX() * multiplier;
            double cursorY = event.getDeltaY() * multiplier;

            int yMultiplier = mc.options.invertMouseY().get() ? -1 : 1;

            float deltaYaw = (float) cursorX * 0.15F;
            float deltaPitch = (float) (cursorY * yMultiplier) * 0.15F;
            float yaw = this.rotation.x + deltaYaw;
            float pitch = this.rotation.y + deltaPitch;
            this.rotation = new Vec2(yaw, Math.clamp(pitch % 360.0F, -90.0F, 90.0F));
        }
        this.ticking = true;
    }

    public void onPostMouseUpdate() {
        this.ticking = false;
    }

    public void onRotationSet() {
        if (!this.ticking) {
            this.rotation = null;
        }
    }

    public float getYawOr(float fallback) {
        return this.rotation == null ? fallback : this.rotation.x;
    }

    public float getPitchOr(float fallback) {
        return this.rotation == null ? fallback : this.rotation.y;
    }

    public Vec2 getRotation() {
        return rotation;
    }

    public void setRotation(Vec2 rotation) {
        this.rotation = rotation;
    }

    public void setTicking(boolean ticking) {
        this.ticking = ticking;
    }
}
