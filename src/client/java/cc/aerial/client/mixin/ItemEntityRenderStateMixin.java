package cc.aerial.client.mixin;

import cc.aerial.client.render.ItemEntityRenderStateExtender;
import cc.aerial.client.render.ItemPhysicsUtility;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ItemEntityRenderState.class)
public abstract class ItemEntityRenderStateMixin implements ItemEntityRenderStateExtender {
    @Unique
    private float aerial$rotX;
    @Unique
    private float aerial$rotY;
    @Unique
    private boolean aerial$isBlock;
    @Unique
    private boolean aerial$additionalOffset;

    @Override
    public float aerial$getXRot() {
        return aerial$rotX;
    }

    @Override
    public float aerial$getYRot() {
        return aerial$rotY;
    }

    @Override
    public boolean aerial$isBlock() {
        return aerial$isBlock;
    }

    @Override
    public boolean aerial$hasAdditionalOffset() {
        return aerial$additionalOffset;
    }

    @Override
    public void aerial$extractPhysics(ItemEntity entity) {
        ItemEntityRenderState state = (ItemEntityRenderState) (Object) this;
        aerial$isBlock = state.item.usesBlockLight();

        Minecraft mc = Minecraft.getInstance();
        ItemPhysicsUtility.calculateRotation(entity, state, mc.getDeltaTracker().getRealtimeDeltaTicks(), mc.isPaused());

        aerial$additionalOffset = ItemPhysicsUtility.needsOffset(entity.blockPosition(), entity.level());
        aerial$rotX = entity.getXRot();
        aerial$rotY = entity.getYRot();
    }
}
