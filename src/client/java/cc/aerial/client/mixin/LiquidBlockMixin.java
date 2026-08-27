package cc.aerial.client.mixin;

import cc.aerial.client.features.impl.movement.JesusModule;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LiquidBlock.class)
public abstract class LiquidBlockMixin {
    @ModifyReturnValue(method = "getCollisionShape", at = @At("RETURN"))
    private VoxelShape aerial$jesus(VoxelShape original, BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (JesusModule.INSTANCE.isSolidifyingLiquids()) {
            return Shapes.block();
        }
        return original;
    }
}
