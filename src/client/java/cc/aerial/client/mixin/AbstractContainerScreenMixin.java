package cc.aerial.client.mixin;

import cc.aerial.client.features.impl.utility.BedwarsUtilModule;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {
    @Inject(method = "extractSlot", at = @At("HEAD"))
    private void aerial$shopHelperHighlight(GuiGraphicsExtractor extractor, Slot slot,
                                             int mouseX, int mouseY, CallbackInfo ci) {
        BedwarsUtilModule.INSTANCE.drawShopHighlight(extractor, slot);
    }

    @Inject(method = "slotClicked", at = @At("HEAD"), cancellable = true)
    private void aerial$shopHelperPrevent(Slot slot, int slotId, int button,
                                           ContainerInput input, CallbackInfo ci) {
        if (BedwarsUtilModule.INSTANCE.shouldPreventShopClick(slot)) {
            ci.cancel();
        }
    }

    @ModifyVariable(method = "slotClicked", at = @At("HEAD"), argsOnly = true)
    private ContainerInput aerial$shopHelperReplaceClick(ContainerInput input, Slot slot) {
        if (input == ContainerInput.PICKUP && BedwarsUtilModule.INSTANCE.shouldReplaceShopClick(slot)) {
            return ContainerInput.CLONE;
        }
        return input;
    }
}
