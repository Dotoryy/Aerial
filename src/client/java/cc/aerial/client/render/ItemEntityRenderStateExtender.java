package cc.aerial.client.render;

import net.minecraft.world.entity.item.ItemEntity;

public interface ItemEntityRenderStateExtender {
    boolean aerial$isBlock();

    float aerial$getXRot();

    float aerial$getYRot();

    boolean aerial$hasAdditionalOffset();

    void aerial$extractPhysics(ItemEntity entity);
}
