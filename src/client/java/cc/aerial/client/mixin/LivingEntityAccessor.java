package cc.aerial.client.mixin;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
    @Accessor("DATA_LIVING_ENTITY_FLAGS")
    static EntityDataAccessor<Byte> aerial$dataLivingEntityFlags() {
        throw new AssertionError();
    }

    @Invoker("travelInAir")
    void aerial$travelInAir(Vec3 movementInput);

    @Accessor("noJumpDelay")
    void aerial$setNoJumpDelay(int noJumpDelay);

    @Invoker("getJumpPower")
    float aerial$getJumpPower();

    @Invoker("jumpFromGround")
    void aerial$jumpFromGround();
}
