package cc.aerial.client.mixin;

import cc.aerial.client.features.impl.visual.CapeModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.core.ClientAsset;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;"
            + "Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V", at = @At("TAIL"))
    private void aerial$overrideCape(Avatar entity, AvatarRenderState state, float partialTick, CallbackInfo ci) {
        CapeModule module = CapeModule.INSTANCE;
        if (!module.isEnabled()) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || state.id != player.getId()) {
            return;
        }

        PlayerSkin skin = state.skin;
        state.skin = new PlayerSkin(skin.body(), aerial$capeTexture(module.getTexture()),
                skin.elytra(), skin.model(), skin.secure());
        state.showCape = true;
    }

    @Unique
    private static Identifier aerial$lastCapeId;
    @Unique
    private static ClientAsset.ResourceTexture aerial$lastCapeTexture;

    @Unique
    private static ClientAsset.ResourceTexture aerial$capeTexture(Identifier texture) {
        if (!texture.equals(aerial$lastCapeId)) {
            aerial$lastCapeId = texture;
            aerial$lastCapeTexture = new ClientAsset.ResourceTexture(texture);
        }
        return aerial$lastCapeTexture;
    }
}
