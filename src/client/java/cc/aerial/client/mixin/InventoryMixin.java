package cc.aerial.client.mixin;

import cc.aerial.client.scaffold.SlotSpoof;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Inventory.class)
public abstract class InventoryMixin {
    @Shadow
    @Final
    public Player player;

    @ModifyReturnValue(method = "getSelectedSlot", at = @At("RETURN"))
    private int aerial$spoofSelectedSlot(int original) {
        if (!SlotSpoof.isActive()) {
            return original;
        }
        if (this.player != Minecraft.getInstance().player) {
            return original;
        }
        return SlotSpoof.getSlot();
    }
}
