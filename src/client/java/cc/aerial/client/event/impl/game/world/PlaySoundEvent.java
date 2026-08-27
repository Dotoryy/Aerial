package cc.aerial.client.event.impl.game.world;

import cc.aerial.client.event.EventCancellable;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;

public final class PlaySoundEvent extends EventCancellable {
    private final SoundInstance instance;

    public PlaySoundEvent(SoundInstance instance) {
        this.instance = instance;
    }

    public SoundInstance getInstance() {
        return instance;
    }

    public Identifier getIdentifier() {
        return instance.getIdentifier();
    }
}
