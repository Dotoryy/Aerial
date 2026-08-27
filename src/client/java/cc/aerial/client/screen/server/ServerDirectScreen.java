package cc.aerial.client.screen.server;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

public final class ServerDirectScreen extends ServerFormScreen {
    private final Field address;
    private final Consumer<String> onConnect;

    public ServerDirectScreen(@Nullable Screen previousScreen, Consumer<String> onConnect) {
        super("Direct Connect", previousScreen);
        this.onConnect = onConnect;
        this.address = addField("Server address", "play.example.net",
                Minecraft.getInstance().options.lastMpIp, 128);
    }

    @Override
    protected float cardWidth() {
        return 300.0f;
    }

    @Override
    protected void addFormActions() {
        addAction("Connect", this::onSubmit);
        addBackAction();
    }

    @Override
    protected void onSubmit() {
        String host = address.value();
        if (host.isEmpty()) {
            setStatus("&cEnter an address&r");
            return;
        }
        if (!ServerAddress.isValidAddress(host)) {
            setStatus("&cThat address is not valid&r");
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        mc.options.lastMpIp = host;
        mc.options.save();
        onConnect.accept(host);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreenAndShow(previousScreen);
    }
}
