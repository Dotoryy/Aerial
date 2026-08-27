package cc.aerial.client.mixin;

import cc.aerial.client.event.EventDispatcher;
import cc.aerial.client.event.impl.game.JoinWorldEvent;
import cc.aerial.client.event.impl.game.PostGameTickEvent;
import cc.aerial.client.event.impl.game.PreGameTickEvent;
import cc.aerial.client.event.impl.game.input.MouseHandleInputEvent;
import cc.aerial.client.event.impl.game.input.PostHandleInputEvent;
import cc.aerial.client.event.impl.game.player.interaction.ItemUseEvent;
import cc.aerial.client.event.impl.game.server.ServerDisconnectEvent;
import cc.aerial.client.features.impl.combat.AttackDelayModule;
import cc.aerial.client.features.impl.combat.AutoBlockModule;
import cc.aerial.client.features.impl.combat.AutoClickerModule;
import cc.aerial.client.features.impl.combat.killaura.KillauraModule;
import cc.aerial.client.features.impl.movement.SprintModule;
import cc.aerial.client.features.impl.utility.DisablerModule;
import cc.aerial.client.features.impl.world.ScaffoldModule;
import cc.aerial.client.features.impl.utility.FastBreakModule;
import cc.aerial.client.features.impl.utility.FastPlaceModule;
import cc.aerial.client.features.impl.utility.InvMoveModule;
import cc.aerial.client.features.impl.visual.AnimationsModule;
import cc.aerial.client.mouse.MouseButton;
import cc.aerial.client.mouse.MouseHelper;
import cc.aerial.client.utility.KeyMappingUtility;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import com.mojang.blaze3d.platform.InputConstants;
import org.objectweb.asm.Opcodes;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @ModifyReturnValue(method = "createTitle", at = @At("RETURN"))
    private String aerial$windowTitle(String original) {
        return "Aerial";
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void aerial$tickHead(CallbackInfo ci) {
        EventDispatcher.dispatch(new PreGameTickEvent());
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void aerial$tickTail(CallbackInfo ci) {
        EventDispatcher.dispatch(new PostGameTickEvent());
    }

    @Inject(method = "setLevel", at = @At("HEAD"))
    private void aerial$joinWorld(ClientLevel level, CallbackInfo ci) {
        EventDispatcher.dispatch(new JoinWorldEvent());
    }

    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;Z)V", at = @At("HEAD"))
    private void aerial$disconnect(Screen screen, boolean bl, CallbackInfo ci) {
        EventDispatcher.dispatch(new ServerDisconnectEvent());
    }

    @Inject(method = "handleKeybinds", at = @At("TAIL"))
    private void aerial$handleKeybindsTail(CallbackInfo ci) {
        EventDispatcher.dispatch(new PostHandleInputEvent());
        MouseHelper.getInstance().tick();
    }

    @Inject(method = "handleKeybinds", at = @At("HEAD"))
    private void aerial$mouseHandleInput(CallbackInfo ci) {
        EventDispatcher.dispatch(new MouseHandleInputEvent());
    }

    @Redirect(method = "handleKeybinds", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/KeyMapping;isDown()Z"))
    private boolean aerial$redirectKeyDown(KeyMapping instance) {
        MouseButton mouseButton = MouseHelper.getButtonFromBinding(instance);
        if (mouseButton != null) {
            return mouseButton.isDown();
        }
        return instance.isDown();
    }

    @Redirect(method = "handleKeybinds", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/KeyMapping;consumeClick()Z"))
    private boolean aerial$redirectConsumeClick(KeyMapping instance) {
        Minecraft self = (Minecraft) (Object) this;
        LocalPlayer player = self.player;

        if (instance == self.options.keyAttack && player != null && player.isUsingItem()
                && AutoBlockModule.INSTANCE.isEnabled() && AutoBlockModule.INSTANCE.isSwingAllowed()
                && !KillauraModule.INSTANCE.isEnabled()) {
            if (MouseHelper.getLeftButton().consumeClick()) {
                this.startAttack();
                return true;
            }
            return false;
        }

        if (instance == self.options.keyAttack && player != null && KillauraModule.INSTANCE.isEnabled()
                && KillauraModule.INSTANCE.getTargeting().getTarget() != null) {
            MouseButton killauraButton = MouseHelper.getButtonFromBinding(instance);
            if (killauraButton != null) {
                killauraButton.consumeClick();
            } else {
                instance.consumeClick();
            }
            return false;
        }

        MouseButton mouseButton = MouseHelper.getButtonFromBinding(instance);
        if (mouseButton != null) {
            return mouseButton.consumeClick();
        }
        return instance.consumeClick();
    }

    @Inject(method = "handleKeybinds", at = @At("HEAD"))
    private void aerial$visualSwingOnUse(CallbackInfo ci) {
        Minecraft self = (Minecraft) (Object) this;
        LocalPlayer player = self.player;
        AnimationsModule module = AnimationsModule.INSTANCE;
        if (!module.isEnabled() || !module.isSwingWhileUsing() || player == null || !player.isUsingItem()) {
            return;
        }

        if (AutoBlockModule.INSTANCE.isEnabled() && AutoBlockModule.INSTANCE.isSwingAllowed()) {
            return;
        }
        boolean targetingBlock = self.hitResult != null && self.hitResult.getType() == HitResult.Type.BLOCK;
        if (targetingBlock && self.options.keyAttack.isDown()) {
            self.options.keyAttack.consumeClick();
            player.swing(InteractionHand.MAIN_HAND, false);
        } else {
            while (self.options.keyAttack.consumeClick()) {
                player.swing(InteractionHand.MAIN_HAND, false);
            }
        }
    }

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void aerial$startUseItem(CallbackInfo ci) {
        ItemUseEvent event = new ItemUseEvent();
        EventDispatcher.dispatch(event);
        if (event.isCancelled()) {
            this.rightClickDelay = 4;
            ci.cancel();
        }
    }

    @Shadow
    private boolean startAttack() {
        throw new AssertionError();
    }

    @Shadow
    private void startUseItem() {
        throw new AssertionError();
    }

    @Redirect(method = "startAttack", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/Minecraft;missTime:I", opcode = Opcodes.PUTFIELD))
    private void aerial$attackDelay(Minecraft instance, int value) {
        AttackDelayModule module = AttackDelayModule.INSTANCE;
        this.missTime = module.isEnabled() ? module.getMaxCooldown() : value;
    }

    @Shadow
    protected int missTime;

    @Inject(method = "handleKeybinds", at = @At("HEAD"))
    private void aerial$autoClicker(CallbackInfo ci) {
        Minecraft self = (Minecraft) (Object) this;
        AutoClickerModule module = AutoClickerModule.INSTANCE;
        LocalPlayer player = self.player;
        if (!module.isEnabled() || player == null || player.isUsingItem() || !module.canClick()) {
            return;
        }
        if (module.isLeftEnabled() && self.hitResult != null && self.hitResult.getType() != HitResult.Type.BLOCK
                && (!module.isRequirePressed() || self.options.keyAttack.isDown())) {
            this.startAttack();
        }
        if (module.isRightEnabled() && (!module.isRequirePressed() || self.options.keyUse.isDown())) {
            this.startUseItem();
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void aerial$autoSprint(CallbackInfo ci) {
        SprintModule module = SprintModule.INSTANCE;

        if (module.isEnabled() && !ScaffoldModule.INSTANCE.isSuppressingSprint()
                && !DisablerModule.INSTANCE.isSuppressingSprint()) {
            KeyMappingUtility.press(((Minecraft) (Object) this).options.keySprint);
        }
    }

    @Shadow
    private int rightClickDelay;

    @Inject(method = "tick", at = @At("HEAD"))
    private void aerial$fastPlace(CallbackInfo ci) {
        FastPlaceModule module = FastPlaceModule.INSTANCE;
        if (!module.isEnabled()) {
            return;
        }
        if (this.rightClickDelay == 4) {
            module.armDelay();
        }
        module.tickDelay();
        if (module.isDelayElapsed() && this.rightClickDelay > 1 && module.canPlace()) {
            this.rightClickDelay = 0;
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void aerial$fastBreak(CallbackInfo ci) {
        Minecraft self = (Minecraft) (Object) this;
        FastBreakModule module = FastBreakModule.INSTANCE;
        LocalPlayer player = self.player;
        ClientLevel level = self.level;
        if (!module.isEnabled() || player == null || level == null || self.gameMode == null
                || player.getAbilities().instabuild) {
            return;
        }

        if (module.isIgnoringMiningFatigue()) {
            player.removeEffect(MobEffects.MINING_FATIGUE);
        }

        int offGroundTicks = module.tickOffGround(player.onGround());
        MultiPlayerGameModeAccessor gameMode = (MultiPlayerGameModeAccessor) self.gameMode;
        gameMode.aerial$setDestroyDelay(0);

        BlockPos verticalCheckPos = BlockPos.containing(player.getX(), player.getY() + player.getDeltaMovement().y, player.getZ());
        boolean crossingIntoBlock = level.getBlockState(verticalCheckPos).getBlock() != Blocks.AIR && !player.onGround()
                && module.isEqualAirGroundDig();

        float percentageFaster;
        float airGroundBase;
        if (module.getMode() == FastBreakModule.Mode.PERCENTAGE) {
            percentageFaster = module.getSpeed() / 100.0f;
            airGroundBase = 0.8f;
        } else {
            percentageFaster = 0.0f;
            if (self.hitResult instanceof BlockHitResult blockHit && blockHit.getType() == HitResult.Type.BLOCK) {
                BlockPos pos = blockHit.getBlockPos();
                BlockState state = level.getBlockState(pos);
                percentageFaster = state.getDestroyProgress(player, level, pos) * module.getTicks();
            }
            airGroundBase = 0.81f;
        }

        if (offGroundTicks == 1 && module.isEqualAirGroundDig()) {
            gameMode.aerial$setDestroyProgress(gameMode.aerial$getDestroyProgress() / 5.0f);
            percentageFaster = airGroundBase;
        }
        if (crossingIntoBlock) {
            gameMode.aerial$setDestroyProgress(gameMode.aerial$getDestroyProgress() * 5.0f);
            percentageFaster -= airGroundBase;
        }

        float curBlockDamageMP = gameMode.aerial$getDestroyProgress();
        if (curBlockDamageMP > 1.0f - percentageFaster && curBlockDamageMP < 0.99f) {
            gameMode.aerial$setDestroyProgress(0.99f);
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void aerial$invMove(CallbackInfo ci) {
        InvMoveModule module = InvMoveModule.INSTANCE;
        Minecraft self = (Minecraft) (Object) this;
        if (!module.isEnabled()) {
            return;
        }
        module.drainClickQueue();

        KeyMapping[] movementKeys = {
                self.options.keyUp, self.options.keyDown, self.options.keyLeft, self.options.keyRight,
                self.options.keyJump, self.options.keyShift
        };
        Screen screen = self.gui.screen();
        if (module.canWalk(screen)) {
            for (KeyMapping mapping : movementKeys) {
                InputConstants.Key key = ((KeyMappingAccessor) mapping).aerial$getKey();
                boolean realDown = InputConstants.isKeyDown(self.getWindow(), key.getValue());
                if (realDown) {
                    KeyMappingUtility.press(mapping);
                } else {
                    KeyMappingUtility.release(mapping);
                }
            }

            module.applyAllowFilters(self);
            module.setKeysPressed(true);
        } else {
            module.tickDelay();
            if (module.isKeysPressed()) {
                if (screen == null) {
                    for (KeyMapping mapping : movementKeys) {
                        InputConstants.Key key = ((KeyMappingAccessor) mapping).aerial$getKey();
                        boolean realDown = InputConstants.isKeyDown(self.getWindow(), key.getValue());
                        if (realDown) {
                            KeyMappingUtility.press(mapping);
                        } else {
                            KeyMappingUtility.release(mapping);
                        }
                    }
                } else {
                    for (KeyMapping mapping : movementKeys) {
                        KeyMappingUtility.release(mapping);
                    }
                }
                module.setKeysPressed(false);
            }
        }
    }
}
