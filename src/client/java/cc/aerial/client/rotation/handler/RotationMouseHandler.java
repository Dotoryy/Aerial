package cc.aerial.client.rotation.handler;

import cc.aerial.client.event.EventDispatcher;
import cc.aerial.client.event.impl.game.PreGameTickEvent;
import cc.aerial.client.event.impl.game.input.MouseUpdateEvent;
import cc.aerial.client.event.subscriber.IEventSubscriber;
import cc.aerial.client.event.subscriber.Subscribe;
import cc.aerial.client.features.Module;
import cc.aerial.client.features.impl.combat.killaura.KillauraModule;
import cc.aerial.client.rotation.RotationHelper;
import cc.aerial.client.rotation.RotationUtility;
import cc.aerial.client.rotation.model.IRotationModel;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec2;

public final class RotationMouseHandler implements IEventSubscriber {
    public RotationMouseHandler() {
        EventDispatcher.subscribe(this);
    }

    private IRotationModel rotationModel;
    private Vec2 targetRotation;
    private boolean active, forward;

    private boolean substituting;

    private Module owner;

    @Subscribe
    public void onMouseUpdate(MouseUpdateEvent event) {
        Minecraft mc = Minecraft.getInstance();
        this.substituting = false;
        if (this.tickRotation == null || this.targetRotation == null || mc.player == null || !this.active) {
            this.ticked = false;
            return;
        }

        if (!this.forward) {
            this.resetToClient();
            if (this.targetRotation == null) {
                this.ticked = false;
                return;
            }
        }

        float tickDelta;
        if (this.ticked) {
            tickDelta = 1.F;
            this.ticked = false;
        } else {
            tickDelta = (float) mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        }
        double sensitivityMultiplier = event.getSensitivityMultiplier();
        Vec2 tickedRotation = this.rotationModel.tick(this.tickRotation, this.targetRotation, tickDelta);

        double deltaYaw = tickedRotation.x - mc.player.getYRot();
        double cursorDeltaX = RotationUtility.getCursorDelta(deltaYaw, sensitivityMultiplier);
        double deltaPitch = tickedRotation.y - mc.player.getXRot();
        double cursorDeltaY = RotationUtility.getCursorDelta(deltaPitch, sensitivityMultiplier);
        if (mc.options.invertMouseY().get()) {
            cursorDeltaY *= -1.D;
        }

        event.setDeltaX(cursorDeltaX);
        event.setDeltaY(cursorDeltaY);
        event.setHandled();
        this.substituting = true;

        if (!this.forward && RotationUtility.getRotationDifference(tickedRotation, this.targetRotation) == 0.D) {
            this.rotationModel = null;
            this.targetRotation = null;
            this.active = false;
        }
    }

    private Vec2 tickRotation;
    private boolean ticked;

    @Subscribe(priority = 8)
    public void onPreTick(PreGameTickEvent event) {
        if (Minecraft.getInstance().player == null) {
            return;
        }
        this.reverse();
        this.setTickRotation(RotationUtility.getRotation());
    }

    public void setTickRotation(Vec2 tickRotation) {
        this.tickRotation = tickRotation;
    }

    public void reverse() {
        if (this.forward) {
            this.resetToClient();
            this.forward = false;
        }
    }

    private void resetToClient() {
        KillauraModule killaura = KillauraModule.INSTANCE;
        boolean silent = this.owner != killaura || !killaura.isEnabled() || killaura.getSettings().isSilent();
        if (silent) {
            this.targetRotation = RotationHelper.getClientHandler().getRotation();
            return;
        }
        Vec2 current = RotationUtility.getRotation();
        RotationHelper.getClientHandler().setRotation(current);
        this.targetRotation = current;
    }

    public void rotate(Vec2 targetRotation, IRotationModel rotationModel, Module owner) {
        this.targetRotation = targetRotation;
        this.rotationModel = rotationModel;
        this.owner = owner;
        this.forward = true;
        this.active = true;
    }

    public boolean isSubstituting() {
        return this.substituting;
    }

    public Module getOwner() {
        return owner;
    }

    public Vec2 getTargetRotation() {
        return targetRotation;
    }

    public IRotationModel getRotationModel() {
        return rotationModel;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isForward() {
        return forward;
    }
}
