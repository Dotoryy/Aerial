package cc.aerial.client.mixin;

import net.minecraft.client.renderer.Projection;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.ShaderManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ShaderManager.class)
public interface ShaderManagerAccessor {
    @Accessor("postChainProjection")
    Projection aerial$postChainProjection();

    @Accessor("postChainProjectionMatrixBuffer")
    ProjectionMatrixBuffer aerial$postChainProjectionMatrixBuffer();
}
