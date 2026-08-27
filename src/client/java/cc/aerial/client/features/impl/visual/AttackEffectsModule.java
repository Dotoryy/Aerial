package cc.aerial.client.features.impl.visual;

import cc.aerial.client.event.impl.game.player.interaction.AttackEvent;
import cc.aerial.client.event.impl.game.world.PlaySoundEvent;
import cc.aerial.client.event.subscriber.Subscribe;
import cc.aerial.client.features.Module;
import cc.aerial.client.features.ModuleCategory;
import cc.aerial.client.property.BooleanProperty;
import cc.aerial.client.property.MultipleBooleanProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;

import java.util.Map;
import java.util.function.Supplier;

public final class AttackEffectsModule extends Module {
    public static final AttackEffectsModule INSTANCE = new AttackEffectsModule();

    private final MultipleBooleanProperty particles = new MultipleBooleanProperty("Particles",
            new BooleanProperty("Critical", false),
            new BooleanProperty("Sharpness", true));

    private final MultipleBooleanProperty sounds = new MultipleBooleanProperty("Sounds",
            new BooleanProperty("Critical", false),
            new BooleanProperty("Knockback", false),
            new BooleanProperty("Strong", false),
            new BooleanProperty("Sweep", false),
            new BooleanProperty("Weak", false),
            new BooleanProperty("No damage", false));

    private final Map<Identifier, Supplier<Boolean>> soundValues = Map.of(
            SoundEvents.PLAYER_ATTACK_CRIT.location(), sounds.getProperty("Critical")::getValue,
            SoundEvents.PLAYER_ATTACK_KNOCKBACK.location(), sounds.getProperty("Knockback")::getValue,
            SoundEvents.PLAYER_ATTACK_STRONG.location(), sounds.getProperty("Strong")::getValue,
            SoundEvents.PLAYER_ATTACK_SWEEP.location(), sounds.getProperty("Sweep")::getValue,
            SoundEvents.PLAYER_ATTACK_WEAK.location(), sounds.getProperty("Weak")::getValue,
            SoundEvents.PLAYER_ATTACK_NODAMAGE.location(), sounds.getProperty("No damage")::getValue
    );

    private AttackEffectsModule() {
        super("Attack Effects", "Adds or changes effects that happen when attacking an entity.", ModuleCategory.VISUAL);
        setEnabled(true);
        addProperties(particles, sounds);
    }

    @Subscribe
    public void onAttack(AttackEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }

        if (particles.getProperty("Critical").getValue()) {
            player.crit(event.getTarget());
        }
        if (particles.getProperty("Sharpness").getValue()) {
            player.magicCrit(event.getTarget());
        }
    }

    @Subscribe
    public void onPlaySound(PlaySoundEvent event) {
        Supplier<Boolean> supplier = soundValues.get(event.getIdentifier());
        if (supplier != null && !supplier.get()) {
            event.setCancelled();
        }
    }
}
