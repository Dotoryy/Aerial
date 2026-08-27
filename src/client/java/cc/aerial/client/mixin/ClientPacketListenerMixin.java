package cc.aerial.client.mixin;

import cc.aerial.client.command.CommandHandler;
import cc.aerial.client.event.EventDispatcher;
import cc.aerial.client.event.impl.game.chat.ChatReceivedEvent;
import cc.aerial.client.event.impl.game.player.movement.knockback.KnockbackEvent;
import cc.aerial.client.event.impl.game.player.teleport.PostTeleportEvent;
import cc.aerial.client.event.impl.game.player.teleport.PreTeleportEvent;
import cc.aerial.client.features.impl.visual.AnimationsModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    @Inject(method = "sendChat", at = @At("HEAD"), cancellable = true)
    private void aerial$onSendChat(String message, CallbackInfo ci) {
        String trimmed = message.trim();
        if (trimmed.startsWith(".")) {
            CommandHandler.handle(trimmed.substring(1));
            ci.cancel();
        }
    }

    @Inject(method = "handleSystemChat", at = @At("HEAD"), cancellable = true)
    private void aerial$onSystemChat(ClientboundSystemChatPacket packet, CallbackInfo ci) {
        ChatReceivedEvent event = new ChatReceivedEvent(packet.content());
        EventDispatcher.dispatch(event);
        if (event.isCancelled()) {
            ci.cancel();
        }
    }

    @Redirect(method = "handleSetEntityData", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/syncher/SynchedEntityData;assignValues(Ljava/util/List;)V"))
    private void aerial$fixPoseRepeat(SynchedEntityData data, List<SynchedEntityData.DataValue<?>> values) {
        AnimationsModule module = AnimationsModule.INSTANCE;
        LocalPlayer local = Minecraft.getInstance().player;
        if (module.isEnabled() && module.isFixPoseRepeat() && local != null && data == local.getEntityData()) {
            int poseId = EntityAccessor.aerial$dataPose().id();
            int flagsId = LivingEntityAccessor.aerial$dataLivingEntityFlags().id();
            values = values.stream()
                    .filter(v -> v.id() != poseId && v.id() != flagsId)
                    .collect(Collectors.toList());
        }
        data.assignValues(values);
    }

    @Redirect(method = "handleExplosion", at = @At(value = "INVOKE",
            target = "Ljava/util/Optional;ifPresent(Ljava/util/function/Consumer;)V"))
    private void aerial$onExplosionKnockback(Optional<Vec3> knockback, Consumer<Vec3> consumer) {
        knockback.ifPresent(movement -> {
            KnockbackEvent event = new KnockbackEvent(movement.x, movement.y, movement.z, true);
            EventDispatcher.dispatch(event);
            consumer.accept(event.isOverridden()
                    ? new Vec3(event.getX(), event.getY(), event.getZ())
                    : movement);
        });
    }

    @Unique
    private PreTeleportEvent aerial$preTeleportEvent;

    @Inject(method = "handleMovePlayer", at = @At("HEAD"), cancellable = true)
    private void aerial$onPreTeleport(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
        PreTeleportEvent event = new PreTeleportEvent(packet.id(), packet.change(), packet.relatives());
        EventDispatcher.dispatch(event);
        this.aerial$preTeleportEvent = event;
        if (event.isCancelled()) {
            ci.cancel();
            this.aerial$preTeleportEvent = null;
        }
    }

    @Inject(method = "handleMovePlayer", at = @At("TAIL"))
    private void aerial$onPostTeleport(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
        if (this.aerial$preTeleportEvent == null) {
            return;
        }
        EventDispatcher.dispatch(new PostTeleportEvent(this.aerial$preTeleportEvent.getTeleportId(),
                this.aerial$preTeleportEvent.getChange(), this.aerial$preTeleportEvent.getRelatives()));
        this.aerial$preTeleportEvent = null;
    }
}
