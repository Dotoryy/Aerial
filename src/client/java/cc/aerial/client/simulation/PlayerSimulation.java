package cc.aerial.client.simulation;

import cc.aerial.client.mixin.LivingEntityAccessor;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public final class PlayerSimulation {
    private final RemotePlayer simulatedEntity;
    private final Player player;

    public PlayerSimulation(Player player) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            this.player = null;
            this.simulatedEntity = null;
            return;
        }

        GameProfile profile = new GameProfile(UUID.randomUUID(), "Simulated Player");
        this.simulatedEntity = new RemotePlayer(level, profile) {
            @Override
            public void push(Entity entity) {
            }
        };
        this.player = player;
        cloneState();
    }

    private void cloneState() {
        simulatedEntity.noPhysics = player.noPhysics;

        simulatedEntity.xo = player.xo;
        simulatedEntity.yo = player.yo;
        simulatedEntity.zo = player.zo;
        simulatedEntity.yRotO = player.yRotO;
        simulatedEntity.xRotO = player.xRotO;

        simulatedEntity.setPos(player.position());
        simulatedEntity.setBoundingBox(player.getBoundingBox());
        simulatedEntity.setDeltaMovement(player.getDeltaMovement());
        simulatedEntity.setYRot(player.getYRot());
        simulatedEntity.setXRot(player.getXRot());
        simulatedEntity.setShiftKeyDown(player.isShiftKeyDown());
        simulatedEntity.setOnGround(player.onGround());
        simulatedEntity.setSprinting(player.isSprinting());
        for (MobEffectInstance effect : player.getActiveEffects()) {
            simulatedEntity.addEffect(new MobEffectInstance(effect));
        }

        simulatedEntity.fallDistance = player.fallDistance;
    }

    public void simulateTicks(int tickCount) {
        LivingEntityAccessor accessor = (LivingEntityAccessor) simulatedEntity;
        for (int i = 0; i < tickCount; i++) {
            accessor.aerial$travelInAir(Vec3.ZERO);
        }
    }

    public void simulateTick() {
        simulateTicks(1);
    }

    public RemotePlayer getSimulatedEntity() {
        return simulatedEntity;
    }
}
