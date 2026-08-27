package cc.aerial.client.screen.server;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

public final class ServerEditScreen extends ServerFormScreen {
    private final Field name;
    private final Field address;
    private final Consumer<ServerData> onDone;
    private final ServerData target;

    public ServerEditScreen(@Nullable Screen previousScreen, String title, ServerData target,
                            Consumer<ServerData> onDone) {
        super(title, previousScreen);
        this.target = target;
        this.onDone = onDone;
        this.name = addField("Name", "Minecraft Server", target.name, 32);
        this.address = addField("Address", "play.example.net", target.ip, 128);
    }

    @Override
    protected float cardWidth() {
        return 300.0f;
    }

    @Override
    protected void addFormActions() {
        addAction("Done", this::onSubmit);
        addBackAction();
    }

    @Override
    protected void onSubmit() {
        String host = address.value();
        if (host.isEmpty()) {
            setStatus("&cAn address is required&r");
            return;
        }

        if (!ServerAddress.isValidAddress(host)) {
            setStatus("&cThat address is not valid&r");
            return;
        }
        apply();
        onDone.accept(target);
    }

    private void apply() {
        target.name = name.value().isEmpty() ? "Minecraft Server" : name.value();
        target.ip = address.value();
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreenAndShow(previousScreen);
    }
}
