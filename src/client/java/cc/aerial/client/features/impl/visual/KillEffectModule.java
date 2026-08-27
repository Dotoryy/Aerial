package cc.aerial.client.features.impl.visual;

import cc.aerial.client.event.impl.game.player.interaction.AttackEvent;
import cc.aerial.client.event.impl.game.player.movement.PreMovementPacketEvent;
import cc.aerial.client.event.subscriber.Subscribe;
import cc.aerial.client.features.Module;
import cc.aerial.client.features.ModuleCategory;
import cc.aerial.client.property.BooleanProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;

public final class KillEffectModule extends Module {
    public static final KillEffectModule INSTANCE = new KillEffectModule();

    private final BooleanProperty lightning = new BooleanProperty("Lightning", true);
    private final BooleanProperty bloodExplosion = new BooleanProperty("Blood Explosion", true);
    private final BooleanProperty explosion = new BooleanProperty("Explosion", true);

    private LivingEntity target;

    private KillEffectModule() {
        super("Kill Effect", "Plays a local send-off wherever the last thing you attacked died.",
                ModuleCategory.VISUAL);
        addProperties(lightning, bloodExplosion, explosion);
    }

    @Override
    protected void onDisable() {
        target = null;
    }

    @Subscribe
    public void onPreMovementPacket(PreMovementPacketEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (target == null || level == null) {
            return;
        }

        if (level.getEntity(target.getId()) != null) {
            return;
        }

        double px = target.getX();
        double py = target.getY();
        double pz = target.getZ();

        if (lightning.getValue()) {
            LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
            if (bolt != null) {
                bolt.setPos(px, py, pz);

                bolt.setVisualOnly(true);

                bolt.setId((int) (-Math.random() * 100000.0));
                level.addEntity(bolt);
            }

            level.playLocalSound(px, py, pz, SoundEvents.LIGHTNING_BOLT_THUNDER,
                    SoundSource.WEATHER, 10000.0f, 0.95f, false);
            level.playLocalSound(px, py, pz, SoundEvents.GENERIC_EXPLODE.value(),
                    SoundSource.BLOCKS, 2.0f, 0.57f, false);
        }

        if (explosion.getValue()) {
            for (int i = 0; i <= 8; i++) {
                minecraft.particleEngine.createTrackingEmitter(target, ParticleTypes.FLAME);
            }
            level.playLocalSound(px, py, pz, SoundEvents.FIRECHARGE_USE,
                    SoundSource.BLOCKS, 1.0f, 1.0f, false);
        }

        if (bloodExplosion.getValue()) {
            double bottom = py;
            double top = py + target.getBbHeight() + 0.4;
            double step = 0.4;
            BlockParticleOption blood = new BlockParticleOption(ParticleTypes.BLOCK,
                    Blocks.REDSTONE_BLOCK.defaultBlockState());
            for (int i = 0; i < 100; i++) {
                for (double h = bottom; h <= top; h += step) {
                    level.addParticle(blood, px, h, pz, 0.0, 0.0, 0.0);
                }
            }

            for (double h = bottom; h <= top; h += step) {
                level.playLocalSound(px, h, pz, SoundEvents.STONE_BREAK,
                        SoundSource.BLOCKS, 1.0f, 1.0f, false);
            }
        }

        target = null;
    }

    @Subscribe
    public void onAttack(AttackEvent event) {
        if (event.getTarget() instanceof LivingEntity living) {
            target = living;
        }
    }
}
