package cc.aerial.client.mixin;

import net.minecraft.client.player.ClientInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientInput.class)
public interface ClientInputAccessor {
    @Accessor("moveVector")
    Vec2 aerial$getMoveVector();

    @Accessor("moveVector")
    void aerial$setMoveVector(Vec2 value);

    @Accessor("keyPresses")
    Input aerial$getKeyPresses();

    @Accessor("keyPresses")
    void aerial$setKeyPresses(Input value);
}
