package cc.aerial.client.features.impl.utility;

import cc.aerial.client.event.impl.game.player.teleport.PostTeleportEvent;
import cc.aerial.client.event.impl.game.player.teleport.PreTeleportEvent;
import cc.aerial.client.event.subscriber.Subscribe;
import cc.aerial.client.features.Module;
import cc.aerial.client.features.ModuleCategory;
import cc.aerial.client.property.BooleanProperty;
import cc.aerial.client.rotation.RotationHelper;
import cc.aerial.client.rotation.RotationProperty;
import cc.aerial.client.rotation.handler.RotationMouseHandler;
import cc.aerial.client.rotation.model.impl.InstantRotationModel;
import cc.aerial.client.utility.HypixelServer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.phys.Vec2;

public final class NoRotateModule extends Module {
    public static final NoRotateModule INSTANCE = new NoRotateModule();

    private final RotationProperty rotationProperty = new RotationProperty(InstantRotationModel.INSTANCE);
    private final BooleanProperty ignoreTeleports = new BooleanProperty("Ignore teleports", true);

    private NoRotateModule() {
        super("No Rotate", "Prevents the server from setting your rotation", ModuleCategory.UTILITY);
        addProperties(rotationProperty.get(), ignoreTeleports);
    }

    @Override
    public String getSuffix() {
        return HypixelServer.isCurrent() ? "Hypixel" : "Edit";
    }

    private Vec2 rotation;

    @Subscribe
    public void onPreTeleport(PreTeleportEvent event) {
        LocalPlayer self = Minecraft.getInstance().player;
        if (self == null) {
            return;
        }
        if (ignoreTeleports.getValue()) {
            PositionMoveRotation change = event.getChange();
            if (change.position().distanceToSqr(self.position()) >= 100.0D) {
                return;
            }
        }
        if (!event.getRelatives().contains(Relative.X_ROT) || !event.getRelatives().contains(Relative.Y_ROT)) {
            this.rotation = RotationHelper.getClientHandler().getRotation();
        }
    }

    @Subscribe
    public void onPostTeleport(PostTeleportEvent event) {
        if (this.rotation == null) {
            return;
        }
        RotationHelper.getClientHandler().setRotation(this.rotation);

        RotationMouseHandler rotationHandler = RotationHelper.getHandler();
        rotationHandler.rotate(this.rotation, rotationProperty.createModel(), this);

        PositionMoveRotation change = event.change();
        rotationHandler.setTickRotation(new Vec2(change.yRot(), change.xRot()));

        rotationHandler.reverse();

        this.rotation = null;
    }
}
