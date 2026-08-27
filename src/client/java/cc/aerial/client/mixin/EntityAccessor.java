package cc.aerial.client.mixin;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Entity.class)
public interface EntityAccessor {
    @Accessor("DATA_POSE")
    static EntityDataAccessor<Pose> aerial$dataPose() {
        throw new AssertionError();
    }

    @Accessor("stuckSpeedMultiplier")
    Vec3 aerial$getStuckSpeedMultiplier();
}
