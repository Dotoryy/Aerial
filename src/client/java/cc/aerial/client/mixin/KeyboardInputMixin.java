package cc.aerial.client.mixin;

import cc.aerial.client.event.EventDispatcher;
import cc.aerial.client.event.impl.game.input.MoveInputEvent;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void aerial$dispatchMoveInput(CallbackInfo ci) {
        ClientInputAccessor accessor = (ClientInputAccessor) this;
        Vec2 moveVector = accessor.aerial$getMoveVector();
        Input keyPresses = accessor.aerial$getKeyPresses();

        MoveInputEvent event = new MoveInputEvent(moveVector.y, moveVector.x,
                keyPresses.jump(), keyPresses.shift(), keyPresses.sprint());
        EventDispatcher.dispatch(event);

        float forward = event.getForward();
        float sideways = event.getSideways();

        accessor.aerial$setKeyPresses(new Input(
                forward > 0.0f,
                forward < 0.0f,
                sideways > 0.0f,
                sideways < 0.0f,
                event.isJump(),
                event.isSneak(),

                event.isSprint()
        ));
        accessor.aerial$setMoveVector(new Vec2(sideways, forward).normalized());
    }
}
